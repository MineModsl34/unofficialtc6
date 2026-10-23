param([string]$BuildLog='phase11-final-verification.log',[string]$TestLog='phase11-final-verification.log',[switch]$IncludeAuditFixes)
$ErrorActionPreference='Stop'
# Read-only artifact/test audit. Does not launch Java, Gradle, Minecraft, datagen or a server.
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskPort=Join-Path $taskRoot 'port'
$taskLog=Join-Path $taskPort "build/$BuildLog"
if(!(Get-Content -Raw -LiteralPath $taskLog).Contains('BUILD SUCCESSFUL')){throw 'No successful selected verification in supplied build log.'}
$taskTestLog=Join-Path $taskPort "build/$TestLog"
if(!(Get-Content -Raw -LiteralPath $taskTestLog).Contains('BUILD SUCCESSFUL')){throw 'No successful selected tests in supplied test log.'}
$taskJar=Join-Path $taskPort 'build/libs/thaumcraft-6.1.0-port.jar'
function GetTaskBytesHash([byte[]]$Bytes){[Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()}
function GetTaskEntryHash($Entry){$taskStream=$Entry.Open();$taskMemory=[System.IO.MemoryStream]::new();try{$taskStream.CopyTo($taskMemory);GetTaskBytesHash $taskMemory.ToArray()}finally{$taskStream.Dispose();$taskMemory.Dispose()}}
$taskZip=[System.IO.Compression.ZipFile]::OpenRead($taskJar)
$taskMismatches=[System.Collections.Generic.List[string]]::new()
$taskCounts=@{}
try{
    $taskEntries=@{}
    foreach($taskEntry in $taskZip.Entries){if($taskEntries.ContainsKey($taskEntry.FullName)){throw "Duplicate JAR entry: $($taskEntry.FullName)"};$taskEntries[$taskEntry.FullName]=$taskEntry}
    foreach($taskGroup in @(@('classes','build/classes/java/main'),@('resources','build/resources/main'))){
        $taskBase=(Resolve-Path -LiteralPath (Join-Path $taskPort $taskGroup[1])).Path
        $taskCount=0
        foreach($taskFile in Get-ChildItem -LiteralPath $taskBase -Recurse -File){
            $taskPath=$taskFile.FullName.Substring($taskBase.Length+1).Replace('\','/')
            if(!$taskEntries.ContainsKey($taskPath)){$taskMismatches.Add("missing:$taskPath");continue}
            if((GetTaskBytesHash ([System.IO.File]::ReadAllBytes($taskFile.FullName))) -ne (GetTaskEntryHash $taskEntries[$taskPath])){$taskMismatches.Add("bytes:$taskPath");continue}
            $taskCount++
        }
        $taskCounts[$taskGroup[0]]=$taskCount
    }
    $taskEntryCount=$taskEntries.Count
}finally{$taskZip.Dispose()}
if($taskMismatches.Count){throw "JAR differs from current compiled/processed files: $($taskMismatches -join ', ')"}
$taskNames=@(
    'thaumcraft.client.fx.FxAnimationRulesTest','thaumcraft.client.fx.SourceFxResourcesTest',
    'thaumcraft.client.lib.ender.SourceRenderMathTest','thaumcraft.datagen.ShaderResourcesTest',
    'thaumcraft.client.lib.events.HudGeometryTest','thaumcraft.client.renderers.entity.SpecialItemGeometryTest',
    'thaumcraft.client.lib.events.HudAssetsTest','thaumcraft.common.lib.network.fx.PacketSourceFXTest',
    'thaumcraft.datagen.BannerResourcesTest','thaumcraft.client.lib.fx.SourceBlockFXRandomTest',
    'thaumcraft.client.renderers.entity.projectile.ProjectileParticleRulesTest',
    'thaumcraft.client.lib.events.SourceProjectileIntegrationTest','thaumcraft.client.lib.fx.SourceFXIntegrationTest',
    'thaumcraft.common.lib.network.misc.PacketKnowledgeGainTest','thaumcraft.common.lib.network.misc.PacketAuraToClientTest')
if($IncludeAuditFixes){
    $taskNames+=@('thaumcraft.client.fx.ParticleEngineRenderContractTest',
        'thaumcraft.client.renderers.tile.Stage11RendererContractsTest',
        'thaumcraft.common.lib.events.ToolEventNativeContractTest',
        'thaumcraft.common.lib.enchantment.InfusionEnchantmentsTest',
        'thaumcraft.common.lib.enchantment.ToolMiningRulesTest')
}
$taskSuites=@(foreach($taskName in $taskNames){
    $taskXmlPath=Join-Path $taskPort "build/test-results/test/TEST-$taskName.xml"
    $taskXml=[xml](Get-Content -Raw -LiteralPath $taskXmlPath)
    $taskSuite=$taskXml.testsuite
    if([int]$taskSuite.failures -ne 0 -or [int]$taskSuite.errors -ne 0 -or [int]$taskSuite.skipped -ne 0){throw "Unsuccessful suite: $taskName"}
    if((Get-Item -LiteralPath $taskXmlPath).LastWriteTimeUtc -lt (Get-Item -LiteralPath $taskTestLog).CreationTimeUtc){throw "Old suite results: $taskName"}
    [pscustomobject]@{name=$taskName;tests=[int]$taskSuite.tests;failures=0;errors=0;skipped=0;sha256=(Get-FileHash -LiteralPath $taskXmlPath -Algorithm SHA256).Hash.ToLower();modifiedUtc=(Get-Item -LiteralPath $taskXmlPath).LastWriteTimeUtc.ToString('o')}
})
[pscustomobject]@{
    schema=1;stage=11;date='2026-10-09';buildLog="port/build/$BuildLog";testLog="port/build/$TestLog";
    jar='port/build/libs/thaumcraft-6.1.0-port.jar';jarBytes=(Get-Item -LiteralPath $taskJar).Length;
    jarSha256=(Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash.ToLower();jarEntries=$taskEntryCount;
    duplicateEntries=0;compiledClassesMatched=$taskCounts.classes;processedResourcesMatched=$taskCounts.resources;
    mismatches=@();selectedSuites=$taskSuites;selectedTests=($taskSuites|Measure-Object -Property tests -Sum).Sum;
    includesAuditFixes=[bool]$IncludeAuditFixes;
    runtimeAcceptance='deferred by user';launchedMinecraft=$false;launchedServer=$false;launchedDatagen=$false;launchedGameTest=$false;
    verificationBounds='one Gradle worker, 768MiB Gradle JVM, two active processors, one 256MiB test fork';
    note='Selected results from one successful current test execution identified by testLog; buildLog may be a subsequent compile/JAR-only run. Byte equality checks JAR against all current compiled classes and processed resources, not GPU or multiplayer acceptance.'
}|ConvertTo-Json -Depth 6 -Compress
