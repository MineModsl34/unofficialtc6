[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$EulaSource,[string]$OptionsSource,[ValidateRange(1024,65535)][int]$Port=25574,[ValidateRange(180,2400)][int]$TimeoutSeconds=1200)
$ErrorActionPreference='Stop'
$netRepo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$netPort=Join-Path $netRepo 'port'
if(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue){throw 'Audit port already in use.'}
$netGames=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -match 'devlaunch.Main|BootstrapLauncher|net.minecraft.client.main.Main'})
if($netGames.Count){throw 'Another Minecraft is running.'}
$netArguments=@{EulaSource=$EulaSource;Port=$Port}
if($OptionsSource){$netArguments.OptionsSource=$OptionsSource}
$netPrepared=& (Join-Path $PSScriptRoot 'prepare_research_stage_four_network_audit.ps1') @netArguments | Out-String | ConvertFrom-Json
$netRoot=[IO.Path]::GetFullPath($netPrepared.RunDirectory)
if([IO.Path]::GetDirectoryName($netRoot) -ne [IO.Path]::GetFullPath((Join-Path $netPort 'build\research-catalog-network'))){throw 'Unexpected prepared audit root.'}
. (Join-Path (Split-Path -Parent $netRepo) 'toolchain\env.ps1') -Java 21
$netJava=Join-Path $env:JAVA_HOME 'bin\java.exe'
$netEncoding=[Text.UTF8Encoding]::new($false)
$netRecord=[ordered]@{status='preparing';codeCommit=(& git -C $netRepo rev-parse HEAD).Trim();startedUtc=[DateTime]::UtcNow.ToString('o');root=$netRoot;port=$Port;processes=@();failure=$null}
$netEngines=@()
function Save-NetworkRecord {[IO.File]::WriteAllText((Join-Path $netRoot 'runtime.json'),($netRecord|ConvertTo-Json -Depth 8)+"`n",$netEncoding)}
function Copy-NetworkLaunch([string]$Name,[string]$Role){
    $netLaunch=Join-Path $netRoot "launch\$Role";$null=New-Item -ItemType Directory -Path $netLaunch
    $netGenerated=Join-Path $netPort 'build\moddev';$netFiles=@{}
    foreach($netSuffix in @('RunVmArgs.txt','RunProgramArgs.txt','RunClasspath.txt','LegacyClasspath.txt','Log4j2.xml')){$netFiles[$netSuffix]=Join-Path $netLaunch ($Name+$netSuffix);[IO.File]::Copy((Join-Path $netGenerated ($Name+$netSuffix)),$netFiles[$netSuffix],$false)}
    $netVm=[IO.File]::ReadAllText($netFiles['RunVmArgs.txt'])
    foreach($netSuffix in @('LegacyClasspath.txt','Log4j2.xml')){$netOld=(Join-Path $netGenerated ($Name+$netSuffix)).Replace('\','\\');if(!$netVm.Contains($netOld)){throw 'Generated VM reference missing.'};$netVm=$netVm.Replace($netOld,$netFiles[$netSuffix].Replace('\','\\'))}
    [IO.File]::WriteAllText($netFiles['RunVmArgs.txt'],$netVm,$netEncoding)
    $netScript=Join-Path $netGenerated ('run'+$Name.Substring(0,1).ToUpperInvariant()+$Name.Substring(1)+'.cmd')
    $netMatch=[regex]::Match([IO.File]::ReadAllText($netScript),'(?:^|\s)"?(-Dfml\.modFolders=[^"\r\n]*?)(?:"?\s+net\.neoforged\.devlaunch\.Main)')
    if(!$netMatch.Success){throw 'Generated mod folders argument missing.'}
    return [pscustomobject]@{role=$Role;files=$netFiles;modArgument=$netMatch.Groups[1].Value.Replace('%%','%')}
}
function Start-NetworkEngine($Launch){
    $netArgs=@('"@'+$Launch.files['RunClasspath.txt']+'"','"@'+$Launch.files['RunVmArgs.txt']+'"','"'+$Launch.modArgument+'"','net.neoforged.devlaunch.Main','"@'+$Launch.files['RunProgramArgs.txt']+'"') -join ' '
    $netProcess=Start-Process -FilePath $netJava -ArgumentList $netArgs -WorkingDirectory (Join-Path $netRoot $Launch.role) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $netRoot ($Launch.role+'.stdout.log')) -RedirectStandardError (Join-Path $netRoot ($Launch.role+'.stderr.log')) -PassThru
    $netMetadata=[ordered]@{role=$Launch.role;pid=$netProcess.Id;startedUtc=$netProcess.StartTime.ToUniversalTime().ToString('o');exitCode=$null;forcedStop=$false;arguments=$netArgs}
    $netRecord.processes+=$netMetadata;Save-NetworkRecord;Write-Host "$($Launch.role) started (PID $($netProcess.Id))"
    return [pscustomobject]@{process=$netProcess;metadata=$netMetadata;launch=$Launch}
}
function Stop-NetworkOwned($Engine){
    if($Engine.process.HasExited){return};$netLive=Get-CimInstance Win32_Process -Filter "ProcessId=$($Engine.process.Id)"
    if(!$netLive){return}
    if([IO.Path]::GetFullPath($netLive.ExecutablePath) -ne [IO.Path]::GetFullPath($netJava) -or (Get-Process -Id $Engine.process.Id).StartTime.ToUniversalTime().ToString('o') -cne $Engine.metadata.startedUtc -or !$netLive.CommandLine.Contains($Engine.launch.files['RunVmArgs.txt']) -or !$netLive.CommandLine.Contains($Engine.launch.files['RunProgramArgs.txt'])){throw 'Refusing to stop an engine with changed ownership.'}
    $Engine.metadata.forcedStop=$true;$Engine.process.Kill();$null=$Engine.process.WaitForExit(5000)
}
Save-NetworkRecord
try{
    Push-Location -LiteralPath $netPort
    try{& .\gradlew.bat classes createResearchStageFourAuditServerLaunchScript createResearchStageFourAuditClientALaunchScript createResearchStageFourAuditClientBLaunchScript "-PtcResearchStageFourNetworkRoot=$netRoot" "-PtcResearchStageFourNetworkAddress=127.0.0.1:$Port" --offline --no-daemon --no-configuration-cache --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=2' *> (Join-Path $netRoot 'prepare-gradle.log');$netBuildExit=$LASTEXITCODE}finally{Pop-Location}
    if($netBuildExit -ne 0){throw 'Network launch preparation failed.'}
    $netRecord.status='running';$netServer=Start-NetworkEngine (Copy-NetworkLaunch 'researchStageFourAuditServer' 'server');$netEngines+=,$netServer
    $netDeadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds);$netStartup=[DateTime]::UtcNow.AddSeconds(180)
    do{if($netServer.process.HasExited){throw 'Owned server exited before listener.'};$netListener=Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Where-Object {$_.OwningProcess -eq $netServer.process.Id};if($netListener){break};if([DateTime]::UtcNow -gt $netStartup){throw 'Owned server listener timed out.'};Start-Sleep -Seconds 2}while($true)
    $netEngines+=,(Start-NetworkEngine (Copy-NetworkLaunch 'researchStageFourAuditClientA' 'client-a'))
    $netEngines+=,(Start-NetworkEngine (Copy-NetworkLaunch 'researchStageFourAuditClientB' 'client-b'))
    do{if(@($netEngines|Where-Object {!$_.process.HasExited}).Count -eq 0){break};if([DateTime]::UtcNow -gt $netDeadline){throw 'Three-engine network audit timed out.'};Start-Sleep -Seconds 2}while($true)
    foreach($netEngine in $netEngines){$netEngine.process.WaitForExit();$netEngine.metadata.exitCode=$netEngine.process.ExitCode;if($netEngine.metadata.exitCode -ne 0){throw "Native engine failed: $($netEngine.metadata.role)"}}
    foreach($netRole in @('server','client-a','client-b')){$netReportPath=Join-Path $netRoot "$netRole\$(if($netRole -eq 'server'){'server'}else{'client'})-report.json";$netReport=[IO.File]::ReadAllText($netReportPath)|ConvertFrom-Json;if($netReport.status -cne 'completed'){throw "Role report not completed: $netRole"}}
    $netRecord.status='completed'
}catch{$netRecord.status='failed';$netRecord.failure=$_.Exception.Message;throw}finally{foreach($netEngine in $netEngines){Stop-NetworkOwned $netEngine;if($netEngine.process.HasExited){$netEngine.process.WaitForExit();$netEngine.metadata.exitCode=$netEngine.process.ExitCode}};$netRecord.endedUtc=[DateTime]::UtcNow.ToString('o');Save-NetworkRecord;Write-Host "Network report: $netRoot\runtime.json"}
