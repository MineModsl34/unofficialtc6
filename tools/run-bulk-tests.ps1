[CmdletBinding()]
param(
    [ValidateSet('All', 'Regression', 'Client')][string] $Mode = 'All',
    [string] $RunId = ('bulk-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
)
$ErrorActionPreference = 'Stop'
if ($RunId -notmatch '^[A-Za-z0-9_-]{1,65}$') { throw 'Invalid bulk run ID.' }
$bulkRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$bulkPort = Join-Path $bulkRepo 'port'
$bulkRoot = Join-Path $bulkPort "build\bulk-tests\$RunId"
if (Test-Path -LiteralPath $bulkRoot) { throw 'Choose a fresh run ID.' }
# All game files are private, and only our own launches are used. Never stop a user's game.
$bulkGames = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
    Where-Object { $_.CommandLine -match 'devlaunch.Main|BootstrapLauncher|net.minecraft.client.main.Main|forge.*nogui' })
if ($bulkGames.Count) { throw "Another Minecraft is running (PID $($bulkGames.ProcessId -join ', '))." }
. (Join-Path (Split-Path -Parent $bulkRepo) 'toolchain\env.ps1') -Java 21
$null = New-Item -ItemType Directory -Path $bulkRoot
$bulkReport = [ordered]@{
    runId = $RunId; mode = $Mode; status = 'running'; startedUtc = [DateTime]::UtcNow.ToString('o')
    codeCommit = (& git -C $bulkRepo rev-parse HEAD).Trim()
    workingTreeChanged = [bool](& git -C $bulkRepo status --porcelain)
    phases = @(); failure = $null
}
$bulkEncoding = [Text.UTF8Encoding]::new($false)
function Save-BulkReport {
    [IO.File]::WriteAllText((Join-Path $bulkRoot 'report.json'), ($bulkReport | ConvertTo-Json -Depth 8) + "`n", $bulkEncoding)
}
function Invoke-BulkPhase([string] $Name, [string[]] $Tasks) {
    $bulkLog = Join-Path $bulkRoot "$Name.log"
    $bulkPhase = [ordered]@{ name = $Name; tasks = $Tasks; log = $bulkLog; startedUtc = [DateTime]::UtcNow.ToString('o'); exitCode = $null }
    $bulkReport.phases += $bulkPhase
    Save-BulkReport
    Write-Host "$Name -> $bulkLog"
    $bulkArgs = @($Tasks) + @("-PtcBulkRun=$RunId", "-PtcJeiReviewRun=$RunId", '--offline', '--no-daemon',
        '--no-configuration-cache', '--max-workers=1', '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=2')
    & .\gradlew.bat @bulkArgs *> $bulkLog
    $bulkPhase.exitCode = $LASTEXITCODE
    $bulkPhase.endedUtc = [DateTime]::UtcNow.ToString('o')
    if ($Name -eq 'regression') {
        $bulkUnitCopy = Join-Path $bulkRoot 'unit-results'
        $null = New-Item -ItemType Directory -Path $bulkUnitCopy
        $bulkXmlFiles = @(Get-ChildItem -LiteralPath (Join-Path $bulkPort 'build\test-results\test') -Filter 'TEST-*.xml')
        $bulkTotals = [ordered]@{ tests = 0; failures = 0; errors = 0; skipped = 0; suites = 0 }
        foreach ($bulkXmlFile in $bulkXmlFiles) {
            if ($bulkXmlFile.LastWriteTimeUtc -lt [DateTime]::Parse($bulkPhase.startedUtc).ToUniversalTime()) { continue }
            [IO.File]::Copy($bulkXmlFile.FullName, (Join-Path $bulkUnitCopy $bulkXmlFile.Name), $false)
            [xml]$bulkXml = [IO.File]::ReadAllText($bulkXmlFile.FullName)
            foreach ($bulkField in @('tests', 'failures', 'errors', 'skipped')) { $bulkTotals[$bulkField] += [int]$bulkXml.testsuite.$bulkField }
            $bulkTotals.suites++
        }
        $bulkPhase.unit = $bulkTotals
        $bulkText = [IO.File]::ReadAllText($bulkLog)
        $bulkSummary = [regex]::Match($bulkText, 'All (\d+) required tests passed')
        if ($bulkSummary.Success) { $bulkPhase.requiredGameTestsPassed = [int]$bulkSummary.Groups[1].Value }
        $bulkPhase.gameTestFailures = @([regex]::Matches($bulkText, 'LogTestReporter\]: ([^\r\n]+ failed [^\r\n]+)') | ForEach-Object { $_.Groups[1].Value })
        Save-BulkReport
        if ($bulkPhase.exitCode -eq 0 -and (!$bulkSummary.Success -or $bulkTotals.tests -eq 0 -or $bulkTotals.failures -or $bulkTotals.errors)) {
            throw 'Regression returned without complete unit/GameTest results.'
        }
    }
    Save-BulkReport
    if ($bulkPhase.exitCode -ne 0) { throw "$Name failed; see $bulkLog" }
    if ($Name -eq 'client') {
        $bulkClientRoot = Join-Path $bulkPort "build\jei-review\$RunId\client"
        foreach ($bulkName in @('jei-review-scenario.json', 'source-recipe-client-audit.json')) {
            $bulkEvidence = [IO.File]::ReadAllText((Join-Path $bulkClientRoot $bulkName)) | ConvertFrom-Json
            if ($bulkEvidence.passed -ne $true) { throw "Client report failed: $bulkName" }
        }
        $bulkPhase.sourceRecipes = $bulkEvidence
        $bulkPhase.clientDirectory = $bulkClientRoot
        Save-BulkReport
    }
    Write-Host "$Name completed"
}
Save-BulkReport
Push-Location -LiteralPath $bulkPort
try {
    if ($Mode -in @('All', 'Regression')) { Invoke-BulkPhase 'regression' @('test', 'jar', 'runBulkGameTestServer') }
    if ($Mode -in @('All', 'Client')) { Invoke-BulkPhase 'client' @('jar', 'runJeiReviewClient') }
    $bulkReport.status = 'passed'
} catch {
    $bulkReport.status = 'failed'; $bulkReport.failure = $_.Exception.Message
    throw
} finally {
    $bulkJarPath = Join-Path $bulkPort 'build\libs\thaumcraft-6.1.0-port.jar'
    if (Test-Path -LiteralPath $bulkJarPath) {
        $bulkJar = Get-Item -LiteralPath $bulkJarPath
        $bulkReport.jar = [ordered]@{ path = $bulkJar.FullName; bytes = $bulkJar.Length; sha256 = (Get-FileHash -LiteralPath $bulkJar.FullName).Hash.ToLowerInvariant() }
    }
    $bulkReport.launchMode = 'NeoForge development classes/resources; matching distributable jar built in this run'
    $bulkReport.endedUtc = [DateTime]::UtcNow.ToString('o')
    Save-BulkReport
    Pop-Location
    Write-Host "Bulk report: $bulkRoot\report.json"
}
