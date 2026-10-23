[CmdletBinding()]
param(
    [ValidateSet('Prepare', 'Run')][string] $Mode = 'Run',
    [string] $RunId = ('tcp-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)),
    [ValidateRange(1024, 65535)][int] $Port = 25575,
    [string] $EulaSource,
    [string] $OptionsSource,
    [string] $ReloadSourceRunId,
    [ValidateRange(120, 3600)][int] $TimeoutSeconds = 900,
    [ValidateRange(30, 600)][int] $ServerStartupSeconds = 180
)

$ErrorActionPreference = 'Stop'
$tcpRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$tcpMine = Split-Path -Parent $tcpRepo
$tcpPortDirectory = Join-Path $tcpRepo 'port'
$tcpBase = [IO.Path]::GetFullPath((Join-Path $tcpPortDirectory 'build\tcp-bootstrap'))
if ($RunId -notmatch '^[A-Za-z0-9_-]{1,80}$') { throw 'Invalid private audit RunId.' }
$tcpRoot = [IO.Path]::GetFullPath((Join-Path $tcpBase $RunId))
if ([IO.Path]::GetDirectoryName($tcpRoot) -ne $tcpBase) { throw 'Invalid private audit directory.' }

function Assert-TcpUnlinked([string] $Candidate) {
    $tcpCursor = [IO.Path]::GetFullPath($Candidate)
    while ($tcpCursor) {
        if ((Test-Path -LiteralPath $tcpCursor) -and
            (((Get-Item -LiteralPath $tcpCursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
            throw "Linked/reparse audit path is forbidden: $tcpCursor"
        }
        $tcpCursor = [IO.Path]::GetDirectoryName($tcpCursor)
    }
}
function Read-TcpJson([string] $Path) {
    Assert-TcpUnlinked $Path
    $tcpItem = Get-Item -LiteralPath $Path -Force
    if ($tcpItem.PSIsContainer -or $tcpItem.Length -gt 1048576) { throw "Invalid audit JSON: $Path" }
    return ([IO.File]::ReadAllText($Path) | ConvertFrom-Json)
}
function Read-TcpLiveText([string] $Path) {
    Assert-TcpUnlinked $Path
    if (-not (Test-Path -LiteralPath $Path)) { return '' }
    $tcpLiveStream = $null
    $tcpLiveReader = $null
    try {
        # Start-Process keeps its redirected writer open throughout the JVM lifetime.
        # Share that writer explicitly; missing/incomplete startup output is retried
        # by the bounded server-ready loop rather than terminating its owned JVM.
        $tcpLiveStream = [IO.FileStream]::new($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read,
            ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
        if ($tcpLiveStream.Length -gt 33554432) { throw "Unbounded live engine log: $Path" }
        $tcpLiveReader = [IO.StreamReader]::new($tcpLiveStream, [Text.Encoding]::UTF8, $true)
        return $tcpLiveReader.ReadToEnd()
    } catch [IO.IOException] {
        return ''
    } finally {
        if ($null -ne $tcpLiveReader) { $tcpLiveReader.Dispose() }
        elseif ($null -ne $tcpLiveStream) { $tcpLiveStream.Dispose() }
    }
}
function Assert-TcpMarker([string] $Role) {
    $tcpMarker = Read-TcpJson (Join-Path $tcpRoot "$Role\.tcp-bootstrap-audit.json")
    if ($tcpMarker.marker -ne 'TC_PORT_TCP_BOOTSTRAP_AUDIT_V1' -or $tcpMarker.schemaVersion -ne 1 -or
        $tcpMarker.purpose -ne 'native-bootstrap' -or $tcpMarker.networkProtocolVersion -cne '6' -or
        $tcpMarker.runId -cne $RunId -or $tcpMarker.role -cne $Role -or $tcpMarker.port -ne $Port) {
        throw "Private isolation marker mismatch for $Role."
    }
    $null = [guid]::Parse($tcpMarker.token)
    return $tcpMarker
}
Assert-TcpUnlinked $tcpRoot
if (-not (Test-Path -LiteralPath $tcpRoot)) {
    $tcpPrepare = @{ RunId = $RunId; Port = $Port }
    $tcpPreparer = 'prepare-tcp-bootstrap.ps1'
    if (-not [string]::IsNullOrWhiteSpace($ReloadSourceRunId)) {
        $tcpPreparer = 'prepare-tcp-bootstrap-reload.ps1'
        $tcpPrepare.SourceRunId = $ReloadSourceRunId
    } else {
        if (-not [string]::IsNullOrWhiteSpace($EulaSource)) { $tcpPrepare.EulaSource = $EulaSource }
        if (-not [string]::IsNullOrWhiteSpace($OptionsSource)) { $tcpPrepare.OptionsSource = $OptionsSource }
    }
    $tcpPrepared = & (Join-Path $PSScriptRoot $tcpPreparer) @tcpPrepare | Out-String | ConvertFrom-Json
    if ([IO.Path]::GetFullPath($tcpPrepared.RunDirectory) -ne $tcpRoot) { throw 'Prepared root mismatch.' }
}
$tcpServerMarker = Assert-TcpMarker 'server'
$tcpClientMarker = Assert-TcpMarker 'client'
if ($tcpServerMarker.token -cne $tcpClientMarker.token) { throw 'Role isolation tokens differ.' }
$tcpAuditPhase = if ($tcpServerMarker.auditPhase) { $tcpServerMarker.auditPhase } else { 'generate' }
if ($tcpAuditPhase -notin @('generate', 'reload') -or $tcpClientMarker.auditPhase -cne $tcpServerMarker.auditPhase) {
    throw 'Role gameplay phases disagree.'
}
foreach ($tcpField in @('goal', 'sourceRunId', 'sourceToken', 'sourcePort', 'sourceServerReportSha256', 'sourceRuntimeSha256', 'worldManifestSha256')) {
    if ($tcpServerMarker.$tcpField -cne $tcpClientMarker.$tcpField) { throw "Role source identity mismatch: $tcpField" }
}
if ($tcpAuditPhase -eq 'reload') {
    if ($tcpServerMarker.sourceRunId -notmatch '^[A-Za-z0-9_-]{1,80}$' -or
        $tcpServerMarker.worldManifestSha256 -notmatch '^[a-f0-9]{64}$') { throw 'Invalid reload provenance marker.' }
    $null = [guid]::Parse($tcpServerMarker.sourceToken)
    if ($ReloadSourceRunId -and $tcpServerMarker.sourceRunId -cne $ReloadSourceRunId) { throw 'Prepared reload source differs from requested source.' }
}
if ($Mode -eq 'Prepare') {
    [pscustomobject]@{ RunId = $RunId; RunDirectory = $tcpRoot; Address = "127.0.0.1:$Port"; Token = $tcpServerMarker.token } | ConvertTo-Json
    return
}
# A separately prepared root may be launched once. Any earlier engine output forbids reuse.
foreach ($tcpPrior in @('runtime.json', 'prepare-gradle.log', 'launch',
    'server\logs', 'client\logs', 'server\server-report.json', 'client\client-report.json')) {
    if (Test-Path -LiteralPath (Join-Path $tcpRoot $tcpPrior)) { throw "Choose a fresh run ID: existing output $tcpPrior" }
}
if ($tcpAuditPhase -eq 'generate' -and (Test-Path -LiteralPath (Join-Path $tcpRoot 'server\tcp-bootstrap-audit'))) {
    throw 'Generate phase requires a fresh world.'
}
if ($tcpAuditPhase -eq 'reload' -and -not (Test-Path -LiteralPath (Join-Path $tcpRoot 'preparation-inputs\world-copy-manifest.json'))) {
    throw 'Reload phase requires its copied-world manifest.'
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "Port $Port is already listening." }
. (Join-Path $tcpMine 'toolchain\env.ps1') -Java 21
$tcpJava = [IO.Path]::GetFullPath((Join-Path $env:JAVA_HOME 'bin\java.exe'))
$tcpPython = Join-Path $tcpMine 'toolchain\python\python.exe'
$tcpRecord = [ordered]@{
    marker = 'TC_PORT_TCP_BOOTSTRAP_AUDIT_V1'; schemaVersion = 1; purpose = 'native-bootstrap'
    networkProtocolVersion = '6'; runId = $RunId; token = $tcpServerMarker.token; port = $Port
    status = 'preparing'; startedAtUtc = [DateTime]::UtcNow.ToString('o'); serverReady = $false
    buildExitCode = $null; processes = @(); failure = $null
}
foreach ($tcpField in @('auditPhase', 'goal', 'sourceRunId', 'sourceToken', 'sourcePort', 'sourceServerReportSha256', 'sourceRuntimeSha256', 'worldManifestSha256')) {
    if ($null -ne $tcpServerMarker.$tcpField) { $tcpRecord[$tcpField] = $tcpServerMarker.$tcpField }
}
$tcpUtf8 = [Text.UTF8Encoding]::new($false, $true)
function Save-TcpRuntime {
    Assert-TcpUnlinked (Join-Path $tcpRoot 'runtime.json')
    [IO.File]::WriteAllText((Join-Path $tcpRoot 'runtime.json'), ($tcpRecord | ConvertTo-Json -Depth 10), $tcpUtf8)
}
function Get-TcpHash([string] $Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Copy-TcpLaunch([string] $Name, [string] $Role) {
    $tcpLaunch = Join-Path $tcpRoot "launch\$Role"
    Assert-TcpUnlinked $tcpLaunch
    $null = New-Item -ItemType Directory -Path $tcpLaunch
    $tcpGenerated = Join-Path $tcpPortDirectory 'build\moddev'
    $tcpCopied = @{}
    foreach ($tcpSuffix in @('RunVmArgs.txt', 'RunProgramArgs.txt', 'RunClasspath.txt', 'LegacyClasspath.txt', 'Log4j2.xml')) {
        $tcpSource = Join-Path $tcpGenerated ($Name + $tcpSuffix)
        Assert-TcpUnlinked $tcpSource
        $tcpTarget = Join-Path $tcpLaunch ($Name + $tcpSuffix)
        [IO.File]::Copy($tcpSource, $tcpTarget, $false)
        $tcpCopied[$tcpSuffix] = $tcpTarget
    }
    # These arguments are NeoForge's generated launch values. Only their mutable log/classpath
    # references are relocated into this cohort; production classes and assets stay at absolute paths.
    $tcpVm = [IO.File]::ReadAllText($tcpCopied['RunVmArgs.txt'])
    foreach ($tcpSuffix in @('LegacyClasspath.txt', 'Log4j2.xml')) {
        $tcpOld = (Join-Path $tcpGenerated ($Name + $tcpSuffix)).Replace('\', '\\')
        $tcpNew = $tcpCopied[$tcpSuffix].Replace('\', '\\')
        if (-not $tcpVm.Contains($tcpOld)) { throw "Generated VM file lacks expected $tcpSuffix reference." }
        $tcpVm = $tcpVm.Replace($tcpOld, $tcpNew)
    }
    [IO.File]::WriteAllText($tcpCopied['RunVmArgs.txt'], $tcpVm, $tcpUtf8)
    $tcpBatch = Join-Path $tcpGenerated ('run' + $Name.Substring(0, 1).ToUpperInvariant() + $Name.Substring(1) + '.cmd')
    if (-not (Test-Path -LiteralPath $tcpBatch)) { throw "Missing generated launch script for $Name." }
    $tcpNativeScript = [IO.File]::ReadAllText($tcpBatch)
    $tcpMatch = [regex]::Match($tcpNativeScript, '(?:^|\s)"?(-Dfml\.modFolders=[^"\r\n]*?)(?:"?\s+net\.neoforged\.devlaunch\.Main)')
    if (-not $tcpMatch.Success) { throw 'Cannot read generated NeoForge mod-folder argument.' }
    $tcpModArgument = $tcpMatch.Groups[1].Value.Replace('%%', '%')
    [IO.File]::Copy($tcpBatch, (Join-Path $tcpLaunch 'generated-launch.cmd'), $false)
    return [pscustomobject]@{ Role = $Role; Files = $tcpCopied; ModArgument = $tcpModArgument }
}
function Start-TcpEngine($Launch) {
    $tcpRole = $Launch.Role
    $tcpArgs = @('"@' + $Launch.Files['RunClasspath.txt'] + '"',
        '"@' + $Launch.Files['RunVmArgs.txt'] + '"', '"' + $Launch.ModArgument + '"',
        'net.neoforged.devlaunch.Main', '"@' + $Launch.Files['RunProgramArgs.txt'] + '"')
    $tcpStdout = Join-Path $tcpRoot "$tcpRole.stdout.log"
    $tcpStderr = Join-Path $tcpRoot "$tcpRole.stderr.log"
    $tcpProcess = Start-Process -FilePath $tcpJava -ArgumentList ($tcpArgs -join ' ') -WorkingDirectory (Join-Path $tcpRoot $tcpRole) `
        -WindowStyle Hidden -RedirectStandardOutput $tcpStdout -RedirectStandardError $tcpStderr -PassThru
    $tcpMetadata = [ordered]@{
        role = $tcpRole; pid = $tcpProcess.Id; startedAtUtc = $tcpProcess.StartTime.ToUniversalTime().ToString('o')
        javaExecutable = $tcpJava; arguments = ($tcpArgs -join ' '); stdoutFile = $tcpStdout; stderrFile = $tcpStderr
        vmArgsSha256 = Get-TcpHash $Launch.Files['RunVmArgs.txt']
        programArgsSha256 = Get-TcpHash $Launch.Files['RunProgramArgs.txt']
        classpathArgsSha256 = Get-TcpHash $Launch.Files['RunClasspath.txt']
        forcedStop = $false; exitCode = $null
    }
    $tcpRecord.processes += $tcpMetadata
    Save-TcpRuntime
    Write-Host "$tcpRole PID $($tcpProcess.Id) -> $tcpStdout"
    return [pscustomobject]@{ Process = $tcpProcess; Metadata = $tcpMetadata; Launch = $Launch }
}
function Stop-TcpOwned($Engine) {
    if ($null -eq $Engine -or $Engine.Process.HasExited) { return }
    $null = Assert-TcpMarker $Engine.Launch.Role
    $tcpLive = Get-CimInstance Win32_Process -Filter "ProcessId=$($Engine.Process.Id)"
    if ($null -eq $tcpLive) { return }
    $tcpLiveProcess = Get-Process -Id $Engine.Process.Id
    if ([IO.Path]::GetFullPath($tcpLive.ExecutablePath) -ne $tcpJava -or
        $tcpLiveProcess.StartTime.ToUniversalTime().ToString('o') -cne $Engine.Metadata.startedAtUtc -or
        -not $tcpLive.CommandLine.Contains($Engine.Launch.Files['RunVmArgs.txt']) -or
        -not $tcpLive.CommandLine.Contains($Engine.Launch.Files['RunProgramArgs.txt'])) {
        throw 'Owned engine identity changed; refusing to stop a different process.'
    }
    $Engine.Metadata.forcedStop = $true
    $Engine.Process.Kill()
    $null = $Engine.Process.WaitForExit(5000)
}
$tcpServer = $null
$tcpClient = $null
Save-TcpRuntime
try {
    $tcpGradleArguments = @('classes', 'createTcpBootstrapAuditServerLaunchScript', 'createTcpBootstrapAuditClientLaunchScript',
        "-PtcTcpBootstrapRoot=$tcpRoot", "-PtcTcpBootstrapAddress=127.0.0.1:$Port", '--no-daemon',
        '--no-configuration-cache', '--max-workers=1', '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=2')
    Push-Location -LiteralPath $tcpPortDirectory
    try {
        & .\gradlew.bat @tcpGradleArguments *> (Join-Path $tcpRoot 'prepare-gradle.log')
        $tcpRecord.buildExitCode = $LASTEXITCODE
    } finally { Pop-Location }
    Save-TcpRuntime
    if ($tcpRecord.buildExitCode -ne 0) { throw "Gradle preparation failed: $tcpRoot\prepare-gradle.log" }
    $tcpServerLaunch = Copy-TcpLaunch 'tcpBootstrapAuditServer' 'server'
    $tcpClientLaunch = Copy-TcpLaunch 'tcpBootstrapAuditClient' 'client'
    $tcpRecord.status = 'running'
    $tcpServer = Start-TcpEngine $tcpServerLaunch
    $tcpStarted = [DateTime]::UtcNow
    $tcpStartupDeadline = $tcpStarted.AddSeconds($ServerStartupSeconds)
    do {
        if ($tcpServer.Process.HasExited) { throw 'Dedicated server exited before opening its loopback listener.' }
        $tcpListeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
        $tcpReadyText = Read-TcpLiveText (Join-Path $tcpRoot 'server.stdout.log')
        if ($tcpListeners.Count -gt 0 -and $tcpReadyText -match 'Done \(') {
            if (@($tcpListeners | Where-Object { $_.OwningProcess -ne $tcpServer.Process.Id -or $_.LocalAddress -ne '127.0.0.1' }).Count -gt 0) {
                throw 'Listener identity/address mismatch; refusing to connect the client.'
            }
            $tcpRecord.serverReady = $true
            Save-TcpRuntime
            break
        }
        if ([DateTime]::UtcNow -ge $tcpStartupDeadline) { throw 'Dedicated server startup timed out.' }
        Start-Sleep -Seconds 1
    } while ($true)
    $tcpClient = Start-TcpEngine $tcpClientLaunch
    $tcpDeadline = $tcpStarted.AddSeconds($TimeoutSeconds)
    $tcpNextStatus = [DateTime]::UtcNow.AddSeconds(30)
    while (-not ($tcpServer.Process.HasExited -and $tcpClient.Process.HasExited)) {
        if ([DateTime]::UtcNow -ge $tcpDeadline) { throw 'Native TCP bootstrap scenario timed out.' }
        foreach ($tcpEngine in @($tcpServer, $tcpClient)) {
            if ($tcpEngine.Process.HasExited -and $tcpEngine.Process.ExitCode -ne 0) {
                throw "$($tcpEngine.Launch.Role) exited with code $($tcpEngine.Process.ExitCode)."
            }
        }
        if ([DateTime]::UtcNow -ge $tcpNextStatus) {
            Write-Host "TCP audit running: server exited=$($tcpServer.Process.HasExited), client exited=$($tcpClient.Process.HasExited)"
            $tcpNextStatus = [DateTime]::UtcNow.AddSeconds(30)
        }
        Start-Sleep -Seconds 1
    }
    foreach ($tcpEngine in @($tcpServer, $tcpClient)) {
        $tcpEngine.Process.WaitForExit()
        $tcpEngine.Metadata.exitCode = $tcpEngine.Process.ExitCode
        if ($tcpEngine.Process.ExitCode -ne 0) { throw "$($tcpEngine.Launch.Role) failed with exit code $($tcpEngine.Process.ExitCode)." }
    }
    $tcpRecord.status = 'completed'
    $tcpRecord.endedAtUtc = [DateTime]::UtcNow.ToString('o')
    Save-TcpRuntime
    $tcpVerifier = if ($tcpAuditPhase -eq 'reload') { 'audit-tcp-bootstrap-reload.py' } else { 'audit-tcp-bootstrap.py' }
    & $tcpPython (Join-Path $PSScriptRoot $tcpVerifier) --root $tcpRoot --output (Join-Path $tcpRoot 'verification.json')
    if ($LASTEXITCODE -ne 0) { throw 'TCP bootstrap reports failed independent verification.' }
    Write-Host "Native TCP bootstrap verified: $tcpRoot\verification.json"
} catch {
    $tcpRecord.status = 'failed'
    $tcpRecord.failure = $_.Exception.Message
    throw
} finally {
    foreach ($tcpEngine in @($tcpClient, $tcpServer)) {
        if ($null -ne $tcpEngine) {
            try { Stop-TcpOwned $tcpEngine } catch { Write-Warning $_.Exception.Message }
            if ($tcpEngine.Process.HasExited) {
                $tcpEngine.Process.WaitForExit()
                $tcpEngine.Metadata.exitCode = $tcpEngine.Process.ExitCode
            }
        }
    }
    $tcpRecord.endedAtUtc = [DateTime]::UtcNow.ToString('o')
    Save-TcpRuntime
}
