param(
    [ValidateSet('All', 'Unit', 'Server', 'Client', 'ClientBuild', 'WithoutJei')]
    [string]$Mode = 'All',
    [string]$RunId = ('jei-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)),
    [switch]$CasterVisualReview
)
$ErrorActionPreference = 'Stop'
if ($RunId -notmatch '^[A-Za-z0-9_-]{1,65}$') { throw 'Invalid private JEI review name' }
if ($CasterVisualReview -and $Mode -notin @('Client', 'ClientBuild')) { throw 'Caster visual review requires Client or ClientBuild' }
$taskRepo = Split-Path -Parent $PSScriptRoot
$taskMine = Split-Path -Parent $taskRepo
. (Join-Path $taskMine 'toolchain\env.ps1') -Java 21
$taskPort = Join-Path $taskRepo 'port'
$taskReview = Join-Path $taskPort "build\jei-review\$RunId"
if (Test-Path -LiteralPath $taskReview) { throw 'Choose a fresh run ID; existing test worlds are never replaced' }
New-Item -ItemType Directory -Path $taskReview | Out-Null
$taskCommon = @('--no-daemon', '--no-configuration-cache', '--max-workers=1', '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=2')

function Invoke-JeiCheck([string]$Label, [string[]]$Tasks, [string]$Identity, [switch]$WithoutJei) {
    $taskLog = Join-Path $taskReview "$Label.log"
    $taskArguments = @($Tasks) + @("-PtcJeiReviewRun=$Identity") + $taskCommon
    if ($WithoutJei) { $taskArguments += '-PtcWithoutJei' }
    if ($CasterVisualReview) { $taskArguments += '-PtcCasterVisualReview' }
    Write-Host "$Label -> $taskLog"
    $taskStartedUtc = [DateTimeOffset]::UtcNow.ToString('o')
    & .\gradlew.bat @taskArguments *> $taskLog
    if ($LASTEXITCODE -ne 0) { throw "$Label failed; see $taskLog" }
    if ($CasterVisualReview -and 'runJeiReviewClient' -in $Tasks) {
        $taskCasterReport = Join-Path $taskPort "build\jei-review\$Identity\client\caster-texture-review.json"
        $taskCasterEvidence = Get-Content -LiteralPath $taskCasterReport -Raw | ConvertFrom-Json
        if ($taskCasterEvidence.passed -ne $true -or $taskCasterEvidence.purpose -ne 'thaumcraft-caster-texture-review') { throw 'Native caster visual report did not pass' }
        [ordered]@{
            schemaVersion = 1
            purpose = 'thaumcraft-caster-texture-runtime'
            startedUtc = $taskStartedUtc
            completedUtc = [DateTimeOffset]::UtcNow.ToString('o')
            task = 'runJeiReviewClient'
            gradleExitCode = 0
            normalJavaExecExitCode = 0
            terminationMethod = 'normal-javaexec-return'
            clientProcess = $taskCasterEvidence.clientProcess
            clientLog = $taskLog
            clientReport = $taskCasterReport
        } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path (Split-Path -Parent $taskCasterReport) 'caster-runtime.json') -Encoding utf8
    }
    if ('runJeiGameTestServer' -in $Tasks) {
        $taskContent = Get-Content -LiteralPath $taskLog -Raw
        if ($taskContent -notmatch 'All 11 required tests passed') {
            throw "Server did not complete all 11 JEI GameTests; see $taskLog"
        }
    }
}
function Assert-ClientReport([string]$Identity) {
    $taskReport = Join-Path $taskPort "build\jei-review\$Identity\client\jei-review-scenario.json"
    if (!(Test-Path -LiteralPath $taskReport)) { throw "Client did not produce its scenario report: $taskReport" }
    $taskEvidence = Get-Content -LiteralPath $taskReport -Raw | ConvertFrom-Json
    if ($taskEvidence.passed -ne $true) { throw "Client scenario failed: $taskReport" }
    Write-Host "Client scenario passed: $taskReport"
}

Push-Location -LiteralPath $taskPort
try {
    if ($Mode -in @('All', 'Unit', 'ClientBuild')) { Invoke-JeiCheck 'unit' @('test', 'jar') $RunId }
    if ($Mode -in @('All', 'Server')) { Invoke-JeiCheck 'server' @('runJeiGameTestServer') $RunId }
    if ($Mode -in @('All', 'Client', 'ClientBuild')) {
        Invoke-JeiCheck 'client' @('runJeiReviewClient') $RunId
        Assert-ClientReport $RunId
    }
    if ($Mode -in @('All', 'WithoutJei')) {
        $taskWithoutId = "$RunId-nojei"
        if (!$CasterVisualReview) { Invoke-JeiCheck 'server-without-jei' @('runJeiGameTestServer') $taskWithoutId -WithoutJei }
        Invoke-JeiCheck 'client-without-jei' @('runJeiReviewClient') $taskWithoutId -WithoutJei
        Assert-ClientReport $taskWithoutId
    }
    Write-Host "JEI review completed: $taskReview"
} finally { Pop-Location }
