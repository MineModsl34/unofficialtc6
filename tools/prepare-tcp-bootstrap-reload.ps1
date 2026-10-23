[CmdletBinding()]
param(
    [string] $RunId = ('tcp-reload-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)),
    [ValidateRange(1024, 65535)][int] $Port = 25576,
    [Parameter(Mandatory = $true)][string] $SourceRunId
)

$ErrorActionPreference = 'Stop'
$reloadRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$reloadBase = [IO.Path]::GetFullPath((Join-Path $reloadRepo 'port\build\tcp-bootstrap'))
foreach ($reloadName in @($RunId, $SourceRunId)) {
    if ($reloadName -notmatch '^[A-Za-z0-9_-]{1,80}$') { throw 'Only immediate private ASCII run IDs are accepted.' }
}
if ($RunId -ceq $SourceRunId) { throw 'Reload and generation must be separate cohorts.' }
$reloadRoot = [IO.Path]::GetFullPath((Join-Path $reloadBase $RunId))
$reloadSource = [IO.Path]::GetFullPath((Join-Path $reloadBase $SourceRunId))
if ([IO.Path]::GetDirectoryName($reloadRoot) -ne $reloadBase -or [IO.Path]::GetDirectoryName($reloadSource) -ne $reloadBase) {
    throw 'Private root escaped the dedicated audit base.'
}
function Assert-ReloadUnlinked([string] $Candidate) {
    $reloadCursor = [IO.Path]::GetFullPath($Candidate)
    while ($reloadCursor) {
        if ((Test-Path -LiteralPath $reloadCursor) -and
            (((Get-Item -LiteralPath $reloadCursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
            throw "Linked/reparse path forbidden: $reloadCursor"
        }
        $reloadCursor = [IO.Path]::GetDirectoryName($reloadCursor)
    }
}
function Read-ReloadJson([string] $Path) {
    Assert-ReloadUnlinked $Path
    $reloadEntry = Get-Item -LiteralPath $Path -Force
    if ($reloadEntry.PSIsContainer -or $reloadEntry.Length -gt 4194304) { throw "Invalid/unbounded source JSON: $Path" }
    return ([IO.File]::ReadAllText($Path) | ConvertFrom-Json)
}
function Get-ReloadHash([string] $Path) {
    Assert-ReloadUnlinked $Path
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Write-ReloadNew([string] $Path, [string] $Content) {
    Assert-ReloadUnlinked $Path
    $reloadBytes = [Text.UTF8Encoding]::new($false, $true).GetBytes($Content)
    $reloadStream = [IO.FileStream]::new($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $reloadStream.Write($reloadBytes, 0, $reloadBytes.Length); $reloadStream.Flush($true) } finally { $reloadStream.Dispose() }
}
function Assert-ReloadSourceIdentity($Value, [string] $Role) {
    if ($Value.marker -ne 'TC_PORT_TCP_BOOTSTRAP_AUDIT_V1' -or $Value.schemaVersion -ne 1 -or
        $Value.purpose -ne 'native-bootstrap' -or $Value.networkProtocolVersion -cne '6' -or
        $Value.runId -cne $SourceRunId -or $Value.auditPhase -cne 'generate' -or $Value.goal -cne 'firststeps' -or
        $Value.token -cne $reloadServerMarker.token -or $Value.port -ne $reloadServerMarker.port -or
        ($Role -and $Value.role -cne $Role)) { throw 'Generation source identities disagree.' }
}
function Assert-ReloadSourceStopped($Runtime) {
    if ($Runtime.status -cne 'completed' -or $Runtime.buildExitCode -ne 0 -or $Runtime.serverReady -ne $true -or
        @($Runtime.processes).Count -ne 2 -or @($Runtime.processes | Select-Object -ExpandProperty role -Unique).Count -ne 2) {
        throw 'Only fully completed generation server/client engines may be copied.'
    }
    foreach ($reloadProcess in $Runtime.processes) {
        if ($reloadProcess.role -notin @('server', 'client') -or $reloadProcess.exitCode -ne 0 -or
            $reloadProcess.forcedStop -ne $false -or $reloadProcess.pid -lt 1) { throw 'Generation engine failed or required forced stop.' }
        $reloadLive = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$reloadProcess.pid)"
        if ($null -ne $reloadLive) {
            $reloadLiveProcess = Get-Process -Id ([int]$reloadProcess.pid) -ErrorAction Stop
            if ($reloadLiveProcess.StartTime.ToUniversalTime().ToString('o') -ceq $reloadProcess.startedAtUtc) {
                throw 'The recorded generation JVM is still alive; refusing to copy a live world.'
            }
        }
    }
    if (Get-NetTCPConnection -LocalPort ([int]$reloadServerMarker.port) -State Listen -ErrorAction SilentlyContinue) {
        throw 'The generation endpoint must be closed before its world is copied.'
    }
}
function Get-ReloadWorldManifest([string] $World) {
    $reloadWorldFull = [IO.Path]::GetFullPath($World)
    Assert-ReloadUnlinked $reloadWorldFull
    if (-not (Test-Path -LiteralPath (Join-Path $reloadWorldFull 'level.dat') -PathType Leaf)) { throw 'Missing complete private source world.' }
    $reloadEntries = @(Get-ChildItem -LiteralPath $reloadWorldFull -Recurse -Force)
    if ($reloadEntries.Count -gt 10000) { throw 'Unbounded private source world.' }
    [long] $reloadTotal = 0
    $reloadFiles = @()
    foreach ($reloadEntry in @($reloadEntries | Sort-Object FullName)) {
        Assert-ReloadUnlinked $reloadEntry.FullName
        if ($reloadEntry.PSIsContainer) { continue }
        $reloadFull = [IO.Path]::GetFullPath($reloadEntry.FullName)
        if (-not $reloadFull.StartsWith($reloadWorldFull + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
            $reloadEntry.Length -gt 67108864) { throw 'Unsafe/unbounded world entry.' }
        $reloadTotal += $reloadEntry.Length
        if ($reloadTotal -gt 536870912) { throw 'Unbounded private world total bytes.' }
        $reloadFiles += [ordered]@{ path = $reloadFull.Substring($reloadWorldFull.Length + 1).Replace('\', '/');
            bytes = $reloadEntry.Length; sha256 = Get-ReloadHash $reloadFull }
    }
    return [ordered]@{ totalBytes = $reloadTotal; fileCount = $reloadFiles.Count; files = $reloadFiles }
}

Assert-ReloadUnlinked $reloadRoot
Assert-ReloadUnlinked $reloadSource
if (Test-Path -LiteralPath $reloadRoot) { throw "An existing cohort is never reused: $reloadRoot" }
if (-not (Test-Path -LiteralPath $reloadSource -PathType Container)) { throw 'Missing private generation source.' }
$reloadServerMarker = Read-ReloadJson (Join-Path $reloadSource 'server\.tcp-bootstrap-audit.json')
$null = [guid]::Parse($reloadServerMarker.token)
Assert-ReloadSourceIdentity $reloadServerMarker 'server'
$reloadClientMarker = Read-ReloadJson (Join-Path $reloadSource 'client\.tcp-bootstrap-audit.json')
Assert-ReloadSourceIdentity $reloadClientMarker 'client'
$reloadServerReportPath = Join-Path $reloadSource 'server\server-report.json'
$reloadRuntimePath = Join-Path $reloadSource 'runtime.json'
$reloadClientReportPath = Join-Path $reloadSource 'client\client-report.json'
$reloadServerReport = Read-ReloadJson $reloadServerReportPath
$reloadRuntime = Read-ReloadJson $reloadRuntimePath
$reloadClientReport = Read-ReloadJson $reloadClientReportPath
Assert-ReloadSourceIdentity $reloadServerReport 'server'
Assert-ReloadSourceIdentity $reloadClientReport 'client'
Assert-ReloadSourceIdentity $reloadRuntime ''
Assert-ReloadSourceStopped $reloadRuntime
if ($reloadServerReport.passed -ne $true -or $reloadServerReport.status -cne 'completed' -or
    $reloadServerReport.saveFlushed -ne $true -or $reloadServerReport.diskNbtVerified -ne $true -or
    $reloadServerReport.finalFirststepsStage -ne 4 -or $reloadServerReport.finalFirststepsComplete -ne $true -or
    $reloadServerReport.reconnectProven -ne $true -or $reloadServerReport.ownerEpoch -ne 2 -or
    $reloadClientReport.passed -ne $true -or $reloadClientReport.status -cne 'completed' -or
    $reloadClientReport.firststepsStage -ne 4 -or $null -eq $reloadServerReport.persistenceBaseline) {
    throw 'Generation must complete full FIRSTSTEPS and actual same-JVM TCP reconnect before restart acceptance.'
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "Port $Port already listening." }
$reloadWorldSource = Join-Path $reloadSource 'server\tcp-bootstrap-audit'
$reloadWorldBefore = Get-ReloadWorldManifest $reloadWorldSource
$reloadEulaPath = Join-Path $reloadSource 'server\eula.txt'
$reloadOptionsPath = Join-Path $reloadSource 'client\options.txt'
Assert-ReloadUnlinked $reloadEulaPath
Assert-ReloadUnlinked $reloadOptionsPath
if ((Get-Item -LiteralPath $reloadEulaPath).Length -gt 4096 -or
    [IO.File]::ReadAllText($reloadEulaPath) -notmatch '(?m)^\s*eula\s*=\s*true\s*$' -or
    (Get-Item -LiteralPath $reloadOptionsPath).Length -gt 1048576 -or
    [IO.File]::ReadAllText($reloadOptionsPath) -notmatch '(?m)^pauseOnLostFocus:false\s*$') {
    throw 'Missing source accepted EULA or valid private options; no preferences are fabricated.'
}
$reloadToken = [guid]::NewGuid().ToString()
$null = New-Item -ItemType Directory -Path $reloadRoot
foreach ($reloadRole in @('server', 'client', 'preparation-inputs')) {
    $null = New-Item -ItemType Directory -Path (Join-Path $reloadRoot $reloadRole)
}
$reloadInputs = Join-Path $reloadRoot 'preparation-inputs'
foreach ($reloadCopy in @(
    @($reloadServerReportPath, 'generation-server-report.json'), @($reloadRuntimePath, 'generation-runtime.json'),
    @($reloadClientReportPath, 'generation-client-report.json'), @($reloadEulaPath, 'eula.txt'), @($reloadOptionsPath, 'options.txt'))) {
    [IO.File]::Copy($reloadCopy[0], (Join-Path $reloadInputs $reloadCopy[1]), $false)
}
[IO.File]::Copy($reloadEulaPath, (Join-Path $reloadRoot 'server\eula.txt'), $false)
[IO.File]::Copy($reloadOptionsPath, (Join-Path $reloadRoot 'client\options.txt'), $false)
$reloadWorldTarget = Join-Path $reloadRoot 'server\tcp-bootstrap-audit'
Assert-ReloadUnlinked $reloadWorldTarget
Copy-Item -LiteralPath $reloadWorldSource -Destination $reloadWorldTarget -Recurse
$reloadWorldCopy = Get-ReloadWorldManifest $reloadWorldTarget
$reloadWorldAfter = Get-ReloadWorldManifest $reloadWorldSource
$reloadBeforeJson = $reloadWorldBefore | ConvertTo-Json -Depth 8 -Compress
if ($reloadBeforeJson -cne ($reloadWorldAfter | ConvertTo-Json -Depth 8 -Compress) -or
    $reloadBeforeJson -cne ($reloadWorldCopy | ConvertTo-Json -Depth 8 -Compress)) {
    throw 'Source changed while copying or copied world bytes differ; this incomplete cohort is preserved.'
}
Assert-ReloadSourceStopped $reloadRuntime
$reloadManifest = [ordered]@{ sourceRunId = $SourceRunId; sourceToken = $reloadServerMarker.token;
    sourceRoot = $reloadSource; sourceWorld = $reloadWorldSource; copiedWorld = $reloadWorldTarget;
    copyVerifiedAtUtc = [DateTime]::UtcNow.ToString('o'); sourceStopped = $true; sourceUnchangedDuringCopy = $true;
    copiedBytesExact = $true; totalBytes = $reloadWorldBefore.totalBytes; fileCount = $reloadWorldBefore.fileCount; files = $reloadWorldBefore.files }
$reloadManifestPath = Join-Path $reloadInputs 'world-copy-manifest.json'
Write-ReloadNew $reloadManifestPath ($reloadManifest | ConvertTo-Json -Depth 8)
$reloadIdentity = [ordered]@{
    marker = 'TC_PORT_TCP_BOOTSTRAP_AUDIT_V1'; schemaVersion = 1; purpose = 'native-bootstrap'; auditPhase = 'reload'; goal = 'firststeps'
    networkProtocolVersion = '6'; runId = $RunId; token = $reloadToken; port = $Port
    sourceRunId = $SourceRunId; sourceToken = $reloadServerMarker.token; sourcePort = [int]$reloadServerMarker.port
    sourceServerReportSha256 = Get-ReloadHash (Join-Path $reloadInputs 'generation-server-report.json')
    sourceRuntimeSha256 = Get-ReloadHash (Join-Path $reloadInputs 'generation-runtime.json')
    worldManifestSha256 = Get-ReloadHash $reloadManifestPath
}
foreach ($reloadRole in @('server', 'client')) {
    $reloadMarker = [ordered]@{}
    foreach ($reloadField in $reloadIdentity.Keys) { $reloadMarker[$reloadField] = $reloadIdentity[$reloadField] }
    $reloadMarker.role = $reloadRole
    Write-ReloadNew (Join-Path $reloadRoot "$reloadRole\.tcp-bootstrap-audit.json") ($reloadMarker | ConvertTo-Json)
}
$reloadPropertiesPath = Join-Path $reloadSource 'server\server.properties'
Assert-ReloadUnlinked $reloadPropertiesPath
if ((Get-Item -LiteralPath $reloadPropertiesPath).Length -gt 8192) { throw 'Unbounded source server properties.' }
$reloadProperties = [IO.File]::ReadAllText($reloadPropertiesPath)
if ($reloadProperties -notmatch '(?m)^server-ip=127\.0\.0\.1\s*$' -or
    $reloadProperties -notmatch '(?m)^level-name=tcp-bootstrap-audit\s*$' -or
    $reloadProperties -notmatch '(?m)^online-mode=false\s*$' -or
    $reloadProperties -notmatch '(?m)^gamemode=survival\s*$') { throw 'Source server properties lost their private scope.' }
$reloadProperties = [regex]::Replace($reloadProperties, '(?m)^server-port=\d+\s*$', "server-port=$Port")
Write-ReloadNew (Join-Path $reloadRoot 'server\server.properties') $reloadProperties
$reloadPreparation = [ordered]@{}
foreach ($reloadField in $reloadIdentity.Keys) { $reloadPreparation[$reloadField] = $reloadIdentity[$reloadField] }
$reloadPreparation.sourceRoot = $reloadSource
$reloadPreparation.preparedAtUtc = [DateTime]::UtcNow.ToString('o')
$reloadPreparation.eulaSource = $reloadEulaPath
$reloadPreparation.eulaSha256 = Get-ReloadHash $reloadEulaPath
$reloadPreparation.optionsSource = $reloadOptionsPath
$reloadPreparation.optionsSha256 = Get-ReloadHash $reloadOptionsPath
Write-ReloadNew (Join-Path $reloadRoot 'preparation.json') ($reloadPreparation | ConvertTo-Json -Depth 8)
[pscustomobject]@{ RunId = $RunId; RunDirectory = $reloadRoot; Address = "127.0.0.1:$Port"; Token = $reloadToken;
    SourceRunId = $SourceRunId; Note = 'Fresh new-JVM reload cohort; exact stopped private world copied and independently hashed, with zero research/material fixtures.' } | ConvertTo-Json
