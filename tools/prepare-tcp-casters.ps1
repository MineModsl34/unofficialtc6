[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $RunId,
    [ValidateRange(1024, 65535)][int] $Port = 25577,
    [ValidateSet('generate', 'reload')][string] $Phase = 'generate',
    [Parameter(Mandatory = $true)][string] $SourceRunId
)

$ErrorActionPreference = 'Stop'
$casterRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$casterBase = [IO.Path]::GetFullPath((Join-Path $casterRepo 'port\build\tcp-casters'))
$casterSourceBase = if ($Phase -eq 'generate') {
    [IO.Path]::GetFullPath((Join-Path $casterRepo 'port\build\tcp-alchemy'))
} else { $casterBase }
foreach ($casterName in @($RunId, $SourceRunId)) {
    if ($casterName -notmatch '^[A-Za-z0-9_-]{1,80}$') { throw 'Only immediate private ASCII run IDs are accepted.' }
}
if ($RunId -ceq $SourceRunId) { throw 'Each caster phase requires a distinct cohort.' }
$casterRoot = [IO.Path]::GetFullPath((Join-Path $casterBase $RunId))
$casterSource = [IO.Path]::GetFullPath((Join-Path $casterSourceBase $SourceRunId))
if ([IO.Path]::GetDirectoryName($casterRoot) -ne $casterBase -or
    [IO.Path]::GetDirectoryName($casterSource) -ne $casterSourceBase) { throw 'Private root escaped its audit base.' }

function Assert-CasterUnlinked([string] $Candidate) {
    $casterCursor = [IO.Path]::GetFullPath($Candidate)
    while ($casterCursor) {
        if ((Test-Path -LiteralPath $casterCursor) -and
            (((Get-Item -LiteralPath $casterCursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
            throw "Linked/reparse path forbidden: $casterCursor"
        }
        $casterCursor = [IO.Path]::GetDirectoryName($casterCursor)
    }
}
function Read-CasterJson([string] $Path) {
    Assert-CasterUnlinked $Path
    $casterEntry = Get-Item -LiteralPath $Path -Force
    if ($casterEntry.PSIsContainer -or $casterEntry.Length -gt 4194304) { throw "Invalid/unbounded source JSON: $Path" }
    return ([IO.File]::ReadAllText($Path) | ConvertFrom-Json)
}
function Get-CasterHash([string] $Path) {
    Assert-CasterUnlinked $Path
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Write-CasterNew([string] $Path, [string] $Content) {
    Assert-CasterUnlinked $Path
    $casterBytes = [Text.UTF8Encoding]::new($false, $true).GetBytes($Content)
    $casterStream = [IO.FileStream]::new($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $casterStream.Write($casterBytes, 0, $casterBytes.Length); $casterStream.Flush($true) }
    finally { $casterStream.Dispose() }
}
function Assert-CasterSourceIdentity($Value, [string] $Role) {
    foreach ($casterKey in @('marker', 'schemaVersion', 'purpose', 'networkProtocolVersion', 'runId', 'token', 'port', 'goal', 'auditPhase')) {
        if ($Value.$casterKey -cne $casterSourceMarker.$casterKey) { throw "Source identity differs: $casterKey" }
    }
    if ($Value.runId -cne $SourceRunId -or ($Role -and $Value.role -cne $Role)) { throw 'Source run/role identity differs.' }
}
function Assert-CasterSourceStopped($Runtime) {
    if ($Runtime.status -cne 'completed' -or $Runtime.buildExitCode -ne 0 -or $Runtime.serverReady -ne $true -or
        @($Runtime.processes).Count -ne 2 -or @($Runtime.processes | Select-Object -ExpandProperty role -Unique).Count -ne 2) {
        throw 'Only fully completed source server/client engines may be copied.'
    }
    foreach ($casterProcess in $Runtime.processes) {
        if ($casterProcess.role -notin @('server', 'client') -or $casterProcess.exitCode -ne 0 -or
            $casterProcess.forcedStop -ne $false -or $casterProcess.pid -lt 1) { throw 'Source engine failed or required forced stop.' }
        $casterLive = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$casterProcess.pid)"
        if ($null -ne $casterLive) {
            $casterLiveProcess = Get-Process -Id ([int]$casterProcess.pid) -ErrorAction Stop
            if ($casterLiveProcess.StartTime.ToUniversalTime().ToString('o') -ceq $casterProcess.startedAtUtc) {
                throw 'Recorded source JVM is still alive; refusing to copy a live world.'
            }
        }
    }
    if (Get-NetTCPConnection -LocalPort ([int]$casterSourceMarker.port) -State Listen -ErrorAction SilentlyContinue) {
        throw 'The source endpoint must be closed before copying its world.'
    }
}
function Get-CasterWorldManifest([string] $World) {
    $casterWorldFull = [IO.Path]::GetFullPath($World)
    Assert-CasterUnlinked $casterWorldFull
    if (-not (Test-Path -LiteralPath (Join-Path $casterWorldFull 'level.dat') -PathType Leaf)) { throw 'Missing complete private source world.' }
    $casterEntries = @(Get-ChildItem -LiteralPath $casterWorldFull -Recurse -Force)
    if ($casterEntries.Count -gt 10000) { throw 'Unbounded private source world.' }
    [long] $casterTotal = 0
    $casterFiles = @()
    foreach ($casterEntry in @($casterEntries | Sort-Object FullName)) {
        Assert-CasterUnlinked $casterEntry.FullName
        if ($casterEntry.PSIsContainer) { continue }
        $casterFull = [IO.Path]::GetFullPath($casterEntry.FullName)
        if (-not $casterFull.StartsWith($casterWorldFull + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
            $casterEntry.Length -gt 67108864) { throw 'Unsafe/unbounded world entry.' }
        $casterTotal += $casterEntry.Length
        if ($casterTotal -gt 536870912) { throw 'Unbounded private world total bytes.' }
        $casterFiles += [ordered]@{ path = $casterFull.Substring($casterWorldFull.Length + 1).Replace('\', '/');
            bytes = $casterEntry.Length; sha256 = Get-CasterHash $casterFull }
    }
    return [ordered]@{ totalBytes = $casterTotal; fileCount = $casterFiles.Count; files = $casterFiles }
}

Assert-CasterUnlinked $casterRoot
Assert-CasterUnlinked $casterSource
if (Test-Path -LiteralPath $casterRoot) { throw "An existing cohort is never reused: $casterRoot" }
$casterSourceMarkerName = if ($Phase -eq 'generate') { '.tcp-alchemy-audit.json' } else { '.tcp-casters-audit.json' }
$casterSourceMarker = Read-CasterJson (Join-Path $casterSource "server\$casterSourceMarkerName")
$null = [guid]::Parse($casterSourceMarker.token)
Assert-CasterSourceIdentity $casterSourceMarker 'server'
Assert-CasterSourceIdentity (Read-CasterJson (Join-Path $casterSource "client\$casterSourceMarkerName")) 'client'
if ($casterSourceMarker.schemaVersion -ne 1 -or $casterSourceMarker.networkProtocolVersion -cne '6') { throw 'Unknown source schema/protocol.' }
if ($Phase -eq 'generate') {
    if ($casterSourceMarker.marker -cne 'TC_PORT_TCP_ALCHEMY_AUDIT_V1' -or
        $casterSourceMarker.purpose -cne 'native-alchemy' -or $casterSourceMarker.goal -cne 'alchemy' -or
        $casterSourceMarker.auditPhase -cne 'reload') { throw 'Generation requires an accepted completed alchemy reload source.' }
} else {
    if ($casterSourceMarker.marker -cne 'TC_PORT_TCP_CASTERS_AUDIT_V1' -or
        $casterSourceMarker.purpose -cne 'native-casters' -or $casterSourceMarker.goal -cne 'casters' -or
        $casterSourceMarker.auditPhase -cne 'generate') { throw 'Reload requires an accepted caster generation source.' }
}
$casterServerReportPath = Join-Path $casterSource 'server\server-report.json'
$casterClientReportPath = Join-Path $casterSource 'client\client-report.json'
$casterRuntimePath = Join-Path $casterSource 'runtime.json'
$casterVerificationPath = Join-Path $casterSource 'verification.json'
$casterServerReport = Read-CasterJson $casterServerReportPath
$casterClientReport = Read-CasterJson $casterClientReportPath
$casterRuntime = Read-CasterJson $casterRuntimePath
$casterVerification = Read-CasterJson $casterVerificationPath
Assert-CasterSourceIdentity $casterServerReport 'server'
Assert-CasterSourceIdentity $casterClientReport 'client'
Assert-CasterSourceIdentity $casterRuntime ''
Assert-CasterSourceIdentity $casterVerification ''
Assert-CasterSourceStopped $casterRuntime
if ($casterServerReport.passed -ne $true -or $casterClientReport.passed -ne $true -or
    $casterServerReport.status -cne 'completed' -or $casterClientReport.status -cne 'completed' -or
    $casterServerReport.saveFlushed -ne $true -or $casterServerReport.diskNbtVerified -ne $true -or
    $casterVerification.passed -ne $true -or $null -eq $casterServerReport.persistenceBaseline) {
    throw 'Source needs accepted native reports, independently verified saved NBT and a complete owner baseline.'
}
if ($Phase -eq 'generate' -and $casterServerReport.alchemyComplete -ne $true) {
    throw 'Generation source must preserve the accepted complete alchemy owner.'
}
if ($Phase -eq 'reload' -and ($casterServerReport.castersComplete -ne $true -or $casterServerReport.reconnectProven -ne $true)) {
    throw 'Reload requires completed native caster progression and a genuine reconnect.'
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "Port $Port already listening." }
$casterSourceWorldName = if ($Phase -eq 'generate') { 'tcp-alchemy-audit' } else { 'tcp-caster-audit' }
$casterWorldSource = Join-Path $casterSource "server\$casterSourceWorldName"
$casterWorldBefore = Get-CasterWorldManifest $casterWorldSource
$casterEulaPath = Join-Path $casterSource 'server\eula.txt'
$casterOptionsPath = Join-Path $casterSource 'client\options.txt'
Assert-CasterUnlinked $casterEulaPath
Assert-CasterUnlinked $casterOptionsPath
if ((Get-Item -LiteralPath $casterEulaPath).Length -gt 4096 -or
    [IO.File]::ReadAllText($casterEulaPath) -notmatch '(?m)^\s*eula\s*=\s*true\s*$' -or
    (Get-Item -LiteralPath $casterOptionsPath).Length -gt 1048576 -or
    [IO.File]::ReadAllText($casterOptionsPath) -notmatch '(?m)^pauseOnLostFocus:false\s*$') {
    throw 'Missing existing accepted EULA or valid private options.'
}
$casterToken = [guid]::NewGuid().ToString()
$null = New-Item -ItemType Directory -Path $casterRoot
foreach ($casterRole in @('server', 'client', 'preparation-inputs')) {
    $null = New-Item -ItemType Directory -Path (Join-Path $casterRoot $casterRole)
}
$casterInputs = Join-Path $casterRoot 'preparation-inputs'
foreach ($casterCopy in @(
    @($casterServerReportPath, 'source-server-report.json'), @($casterClientReportPath, 'source-client-report.json'),
    @($casterRuntimePath, 'source-runtime.json'), @($casterVerificationPath, 'source-verification.json'),
    @($casterEulaPath, 'eula.txt'), @($casterOptionsPath, 'options.txt'))) {
    [IO.File]::Copy($casterCopy[0], (Join-Path $casterInputs $casterCopy[1]), $false)
}
[IO.File]::Copy($casterEulaPath, (Join-Path $casterRoot 'server\eula.txt'), $false)
[IO.File]::Copy($casterOptionsPath, (Join-Path $casterRoot 'client\options.txt'), $false)
$casterWorldTarget = Join-Path $casterRoot 'server\tcp-caster-audit'
Assert-CasterUnlinked $casterWorldTarget
Copy-Item -LiteralPath $casterWorldSource -Destination $casterWorldTarget -Recurse
$casterWorldCopy = Get-CasterWorldManifest $casterWorldTarget
$casterWorldAfter = Get-CasterWorldManifest $casterWorldSource
$casterBeforeJson = $casterWorldBefore | ConvertTo-Json -Depth 8 -Compress
if ($casterBeforeJson -cne ($casterWorldAfter | ConvertTo-Json -Depth 8 -Compress) -or
    $casterBeforeJson -cne ($casterWorldCopy | ConvertTo-Json -Depth 8 -Compress)) {
    throw 'Source changed while copying or copied world bytes differ; incomplete cohort preserved.'
}
Assert-CasterSourceStopped $casterRuntime
$casterManifest = [ordered]@{ sourceRunId = $SourceRunId; sourceToken = $casterSourceMarker.token;
    sourceRoot = $casterSource; sourceWorld = $casterWorldSource; copiedWorld = $casterWorldTarget;
    copyVerifiedAtUtc = [DateTime]::UtcNow.ToString('o'); sourceStopped = $true; sourceUnchangedDuringCopy = $true;
    copiedBytesExact = $true; totalBytes = $casterWorldBefore.totalBytes; fileCount = $casterWorldBefore.fileCount; files = $casterWorldBefore.files }
$casterManifestPath = Join-Path $casterInputs 'world-copy-manifest.json'
Write-CasterNew $casterManifestPath ($casterManifest | ConvertTo-Json -Depth 8)
$casterIdentity = [ordered]@{
    marker = 'TC_PORT_TCP_CASTERS_AUDIT_V1'; schemaVersion = 1; purpose = 'native-casters'; auditPhase = $Phase; goal = 'casters'
    networkProtocolVersion = '6'; runId = $RunId; token = $casterToken; port = $Port
    sourceKind = $(if ($Phase -eq 'generate') { 'alchemy' } else { 'casters' })
    sourceRunId = $SourceRunId; sourceToken = $casterSourceMarker.token; sourcePort = [int]$casterSourceMarker.port
    sourceMarker = $casterSourceMarker.marker; sourcePurpose = $casterSourceMarker.purpose
    sourceGoal = $casterSourceMarker.goal; sourceAuditPhase = $casterSourceMarker.auditPhase
    sourceServerReportSha256 = Get-CasterHash (Join-Path $casterInputs 'source-server-report.json')
    sourceClientReportSha256 = Get-CasterHash (Join-Path $casterInputs 'source-client-report.json')
    sourceRuntimeSha256 = Get-CasterHash (Join-Path $casterInputs 'source-runtime.json')
    sourceVerificationSha256 = Get-CasterHash (Join-Path $casterInputs 'source-verification.json')
    worldManifestSha256 = Get-CasterHash $casterManifestPath
}
foreach ($casterRole in @('server', 'client')) {
    $casterMarker = [ordered]@{}
    foreach ($casterField in $casterIdentity.Keys) { $casterMarker[$casterField] = $casterIdentity[$casterField] }
    $casterMarker.role = $casterRole
    Write-CasterNew (Join-Path $casterRoot "$casterRole\.tcp-casters-audit.json") ($casterMarker | ConvertTo-Json)
}
$casterPropertiesPath = Join-Path $casterSource 'server\server.properties'
Assert-CasterUnlinked $casterPropertiesPath
if ((Get-Item -LiteralPath $casterPropertiesPath).Length -gt 8192) { throw 'Unbounded source server properties.' }
$casterProperties = [IO.File]::ReadAllText($casterPropertiesPath)
if ($casterProperties -notmatch '(?m)^server-ip=127\.0\.0\.1\s*$' -or
    $casterProperties -notmatch '(?m)^online-mode=false\s*$' -or
    $casterProperties -notmatch '(?m)^gamemode=survival\s*$') { throw 'Source server properties lost private scope.' }
$casterProperties = [regex]::Replace($casterProperties, '(?m)^server-port=\d+\s*$', "server-port=$Port")
$casterProperties = [regex]::Replace($casterProperties, '(?m)^level-name=[^\r\n]+\r?$', 'level-name=tcp-caster-audit')
Write-CasterNew (Join-Path $casterRoot 'server\server.properties') $casterProperties
$casterPreparation = [ordered]@{}
foreach ($casterField in $casterIdentity.Keys) { $casterPreparation[$casterField] = $casterIdentity[$casterField] }
$casterPreparation.sourceRoot = $casterSource
$casterPreparation.preparedAtUtc = [DateTime]::UtcNow.ToString('o')
$casterPreparation.eulaSha256 = Get-CasterHash $casterEulaPath
$casterPreparation.optionsSha256 = Get-CasterHash $casterOptionsPath
Write-CasterNew (Join-Path $casterRoot 'preparation.json') ($casterPreparation | ConvertTo-Json -Depth 8)
[pscustomobject]@{ RunId = $RunId; RunDirectory = $casterRoot; Address = "127.0.0.1:$Port"; Token = $casterToken;
    Phase = $Phase; SourceRunId = $SourceRunId; Note = 'Exact stopped private-world copy; fresh owned native TCP engines.' } | ConvertTo-Json
