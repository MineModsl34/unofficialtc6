[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $RunId,
    [ValidateRange(1024, 65535)][int] $Port = 25576,
    [ValidateSet('generate', 'resume', 'reload')][string] $Phase = 'generate',
    [Parameter(Mandatory = $true)][string] $SourceRunId
)

$ErrorActionPreference = 'Stop'
$alchemyRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$alchemyBase = [IO.Path]::GetFullPath((Join-Path $alchemyRepo 'port\build\tcp-alchemy'))
$alchemySourceBase = if ($Phase -eq 'generate') {
    [IO.Path]::GetFullPath((Join-Path $alchemyRepo 'port\build\tcp-bootstrap'))
} else { $alchemyBase }
foreach ($alchemyName in @($RunId, $SourceRunId)) {
    if ($alchemyName -notmatch '^[A-Za-z0-9_-]{1,80}$') { throw 'Only immediate private ASCII run IDs are accepted.' }
}
if ($RunId -ceq $SourceRunId) { throw 'Each alchemy phase requires a distinct cohort.' }
$alchemyRoot = [IO.Path]::GetFullPath((Join-Path $alchemyBase $RunId))
$alchemySource = [IO.Path]::GetFullPath((Join-Path $alchemySourceBase $SourceRunId))
if ([IO.Path]::GetDirectoryName($alchemyRoot) -ne $alchemyBase -or
    [IO.Path]::GetDirectoryName($alchemySource) -ne $alchemySourceBase) { throw 'Private root escaped its audit base.' }

function Assert-AlchemyUnlinked([string] $Candidate) {
    $alchemyCursor = [IO.Path]::GetFullPath($Candidate)
    while ($alchemyCursor) {
        if ((Test-Path -LiteralPath $alchemyCursor) -and
            (((Get-Item -LiteralPath $alchemyCursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
            throw "Linked/reparse path forbidden: $alchemyCursor"
        }
        $alchemyCursor = [IO.Path]::GetDirectoryName($alchemyCursor)
    }
}
function Read-AlchemyJson([string] $Path) {
    Assert-AlchemyUnlinked $Path
    $alchemyEntry = Get-Item -LiteralPath $Path -Force
    if ($alchemyEntry.PSIsContainer -or $alchemyEntry.Length -gt 4194304) { throw "Invalid/unbounded source JSON: $Path" }
    return ([IO.File]::ReadAllText($Path) | ConvertFrom-Json)
}
function Get-AlchemyHash([string] $Path) {
    Assert-AlchemyUnlinked $Path
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Write-AlchemyNew([string] $Path, [string] $Content) {
    Assert-AlchemyUnlinked $Path
    $alchemyBytes = [Text.UTF8Encoding]::new($false, $true).GetBytes($Content)
    $alchemyStream = [IO.FileStream]::new($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $alchemyStream.Write($alchemyBytes, 0, $alchemyBytes.Length); $alchemyStream.Flush($true) }
    finally { $alchemyStream.Dispose() }
}
function Assert-AlchemySourceIdentity($Value, [string] $Role) {
    foreach ($alchemyKey in @('marker', 'schemaVersion', 'purpose', 'networkProtocolVersion', 'runId', 'token', 'port', 'goal', 'auditPhase')) {
        if ($Value.$alchemyKey -cne $alchemySourceMarker.$alchemyKey) { throw "Source identity differs: $alchemyKey" }
    }
    if ($Value.runId -cne $SourceRunId -or ($Role -and $Value.role -cne $Role)) { throw 'Source run/role identity differs.' }
}
function Assert-AlchemySourceStopped($Runtime) {
    if ($Runtime.status -cne 'completed' -or $Runtime.buildExitCode -ne 0 -or $Runtime.serverReady -ne $true -or
        @($Runtime.processes).Count -ne 2 -or @($Runtime.processes | Select-Object -ExpandProperty role -Unique).Count -ne 2) {
        throw 'Only fully completed source server/client engines may be copied.'
    }
    foreach ($alchemyProcess in $Runtime.processes) {
        if ($alchemyProcess.role -notin @('server', 'client') -or $alchemyProcess.exitCode -ne 0 -or
            $alchemyProcess.forcedStop -ne $false -or $alchemyProcess.pid -lt 1) { throw 'Source engine failed or required forced stop.' }
        $alchemyLive = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$alchemyProcess.pid)"
        if ($null -ne $alchemyLive) {
            $alchemyLiveProcess = Get-Process -Id ([int]$alchemyProcess.pid) -ErrorAction Stop
            if ($alchemyLiveProcess.StartTime.ToUniversalTime().ToString('o') -ceq $alchemyProcess.startedAtUtc) {
                throw 'Recorded source JVM is still alive; refusing to copy a live world.'
            }
        }
    }
    if (Get-NetTCPConnection -LocalPort ([int]$alchemySourceMarker.port) -State Listen -ErrorAction SilentlyContinue) {
        throw 'The source endpoint must be closed before copying its world.'
    }
}
function Get-AlchemyWorldManifest([string] $World) {
    $alchemyWorldFull = [IO.Path]::GetFullPath($World)
    Assert-AlchemyUnlinked $alchemyWorldFull
    if (-not (Test-Path -LiteralPath (Join-Path $alchemyWorldFull 'level.dat') -PathType Leaf)) { throw 'Missing complete private source world.' }
    $alchemyEntries = @(Get-ChildItem -LiteralPath $alchemyWorldFull -Recurse -Force)
    if ($alchemyEntries.Count -gt 10000) { throw 'Unbounded private source world.' }
    [long] $alchemyTotal = 0
    $alchemyFiles = @()
    foreach ($alchemyEntry in @($alchemyEntries | Sort-Object FullName)) {
        Assert-AlchemyUnlinked $alchemyEntry.FullName
        if ($alchemyEntry.PSIsContainer) { continue }
        $alchemyFull = [IO.Path]::GetFullPath($alchemyEntry.FullName)
        if (-not $alchemyFull.StartsWith($alchemyWorldFull + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
            $alchemyEntry.Length -gt 67108864) { throw 'Unsafe/unbounded world entry.' }
        $alchemyTotal += $alchemyEntry.Length
        if ($alchemyTotal -gt 536870912) { throw 'Unbounded private world total bytes.' }
        $alchemyFiles += [ordered]@{ path = $alchemyFull.Substring($alchemyWorldFull.Length + 1).Replace('\', '/');
            bytes = $alchemyEntry.Length; sha256 = Get-AlchemyHash $alchemyFull }
    }
    return [ordered]@{ totalBytes = $alchemyTotal; fileCount = $alchemyFiles.Count; files = $alchemyFiles }
}

Assert-AlchemyUnlinked $alchemyRoot
Assert-AlchemyUnlinked $alchemySource
if (Test-Path -LiteralPath $alchemyRoot) { throw "An existing cohort is never reused: $alchemyRoot" }
$alchemySourceMarkerName = if ($Phase -eq 'generate') { '.tcp-bootstrap-audit.json' } else { '.tcp-alchemy-audit.json' }
$alchemySourceMarker = Read-AlchemyJson (Join-Path $alchemySource "server\$alchemySourceMarkerName")
$null = [guid]::Parse($alchemySourceMarker.token)
Assert-AlchemySourceIdentity $alchemySourceMarker 'server'
Assert-AlchemySourceIdentity (Read-AlchemyJson (Join-Path $alchemySource "client\$alchemySourceMarkerName")) 'client'
if ($alchemySourceMarker.schemaVersion -ne 1 -or $alchemySourceMarker.networkProtocolVersion -cne '6') { throw 'Unknown source schema/protocol.' }
if ($Phase -eq 'generate') {
    if ($alchemySourceMarker.marker -cne 'TC_PORT_TCP_BOOTSTRAP_AUDIT_V1' -or
        $alchemySourceMarker.purpose -cne 'native-bootstrap' -or $alchemySourceMarker.goal -cne 'firststeps' -or
        $alchemySourceMarker.auditPhase -cne 'generate') { throw 'Generation requires an accepted FIRSTSTEPS source.' }
} else {
    $alchemyExpectedSourcePhase = if ($Phase -eq 'resume') { 'generate' } else { 'resume' }
    if ($alchemySourceMarker.marker -cne 'TC_PORT_TCP_ALCHEMY_AUDIT_V1' -or
        $alchemySourceMarker.purpose -cne 'native-alchemy' -or $alchemySourceMarker.goal -cne 'alchemy' -or
        $alchemySourceMarker.auditPhase -cne $alchemyExpectedSourcePhase) { throw 'Wrong alchemy source phase.' }
}
$alchemyServerReportPath = Join-Path $alchemySource 'server\server-report.json'
$alchemyClientReportPath = Join-Path $alchemySource 'client\client-report.json'
$alchemyRuntimePath = Join-Path $alchemySource 'runtime.json'
$alchemyVerificationPath = Join-Path $alchemySource 'verification.json'
$alchemyServerReport = Read-AlchemyJson $alchemyServerReportPath
$alchemyClientReport = Read-AlchemyJson $alchemyClientReportPath
$alchemyRuntime = Read-AlchemyJson $alchemyRuntimePath
$alchemyVerification = Read-AlchemyJson $alchemyVerificationPath
Assert-AlchemySourceIdentity $alchemyServerReport 'server'
Assert-AlchemySourceIdentity $alchemyClientReport 'client'
Assert-AlchemySourceIdentity $alchemyRuntime ''
Assert-AlchemySourceIdentity $alchemyVerification ''
Assert-AlchemySourceStopped $alchemyRuntime
if ($alchemyServerReport.passed -ne $true -or $alchemyClientReport.passed -ne $true -or
    $alchemyServerReport.status -cne 'completed' -or $alchemyClientReport.status -cne 'completed' -or
    $alchemyServerReport.saveFlushed -ne $true -or $alchemyServerReport.diskNbtVerified -ne $true -or
    $alchemyVerification.passed -ne $true -or $null -eq $alchemyServerReport.persistenceBaseline) {
    throw 'Source needs accepted native reports, independently verified saved NBT and a complete owner baseline.'
}
if ($Phase -eq 'generate' -and ($alchemyServerReport.finalFirststepsStage -ne 4 -or
    $alchemyServerReport.finalFirststepsComplete -ne $true -or $alchemyServerReport.reconnectProven -ne $true)) {
    throw 'FIRSTSTEPS generation source must be fully complete and reconnected.'
}
if ($Phase -eq 'resume' -and $alchemyServerReport.checkpointedInfusion -ne $true) { throw 'Resume requires a real partially paid infusion checkpoint.' }
if ($Phase -eq 'reload' -and ($alchemyServerReport.alchemyComplete -ne $true -or $alchemyServerReport.reconnectProven -ne $true)) {
    throw 'Final reload requires completed native alchemy and a genuine reconnect.'
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "Port $Port already listening." }
$alchemySourceWorldName = if ($Phase -eq 'generate') { 'tcp-bootstrap-audit' } else { 'tcp-alchemy-audit' }
$alchemyWorldSource = Join-Path $alchemySource "server\$alchemySourceWorldName"
$alchemyWorldBefore = Get-AlchemyWorldManifest $alchemyWorldSource
$alchemyEulaPath = Join-Path $alchemySource 'server\eula.txt'
$alchemyOptionsPath = Join-Path $alchemySource 'client\options.txt'
Assert-AlchemyUnlinked $alchemyEulaPath
Assert-AlchemyUnlinked $alchemyOptionsPath
if ((Get-Item -LiteralPath $alchemyEulaPath).Length -gt 4096 -or
    [IO.File]::ReadAllText($alchemyEulaPath) -notmatch '(?m)^\s*eula\s*=\s*true\s*$' -or
    (Get-Item -LiteralPath $alchemyOptionsPath).Length -gt 1048576 -or
    [IO.File]::ReadAllText($alchemyOptionsPath) -notmatch '(?m)^pauseOnLostFocus:false\s*$') {
    throw 'Missing existing accepted EULA or valid private options.'
}
$alchemyToken = [guid]::NewGuid().ToString()
$null = New-Item -ItemType Directory -Path $alchemyRoot
foreach ($alchemyRole in @('server', 'client', 'preparation-inputs')) {
    $null = New-Item -ItemType Directory -Path (Join-Path $alchemyRoot $alchemyRole)
}
$alchemyInputs = Join-Path $alchemyRoot 'preparation-inputs'
foreach ($alchemyCopy in @(
    @($alchemyServerReportPath, 'source-server-report.json'), @($alchemyClientReportPath, 'source-client-report.json'),
    @($alchemyRuntimePath, 'source-runtime.json'), @($alchemyVerificationPath, 'source-verification.json'),
    @($alchemyEulaPath, 'eula.txt'), @($alchemyOptionsPath, 'options.txt'))) {
    [IO.File]::Copy($alchemyCopy[0], (Join-Path $alchemyInputs $alchemyCopy[1]), $false)
}
[IO.File]::Copy($alchemyEulaPath, (Join-Path $alchemyRoot 'server\eula.txt'), $false)
[IO.File]::Copy($alchemyOptionsPath, (Join-Path $alchemyRoot 'client\options.txt'), $false)
$alchemyWorldTarget = Join-Path $alchemyRoot 'server\tcp-alchemy-audit'
Assert-AlchemyUnlinked $alchemyWorldTarget
Copy-Item -LiteralPath $alchemyWorldSource -Destination $alchemyWorldTarget -Recurse
$alchemyWorldCopy = Get-AlchemyWorldManifest $alchemyWorldTarget
$alchemyWorldAfter = Get-AlchemyWorldManifest $alchemyWorldSource
$alchemyBeforeJson = $alchemyWorldBefore | ConvertTo-Json -Depth 8 -Compress
if ($alchemyBeforeJson -cne ($alchemyWorldAfter | ConvertTo-Json -Depth 8 -Compress) -or
    $alchemyBeforeJson -cne ($alchemyWorldCopy | ConvertTo-Json -Depth 8 -Compress)) {
    throw 'Source changed while copying or copied world bytes differ; incomplete cohort preserved.'
}
Assert-AlchemySourceStopped $alchemyRuntime
$alchemyManifest = [ordered]@{ sourceRunId = $SourceRunId; sourceToken = $alchemySourceMarker.token;
    sourceRoot = $alchemySource; sourceWorld = $alchemyWorldSource; copiedWorld = $alchemyWorldTarget;
    copyVerifiedAtUtc = [DateTime]::UtcNow.ToString('o'); sourceStopped = $true; sourceUnchangedDuringCopy = $true;
    copiedBytesExact = $true; totalBytes = $alchemyWorldBefore.totalBytes; fileCount = $alchemyWorldBefore.fileCount; files = $alchemyWorldBefore.files }
$alchemyManifestPath = Join-Path $alchemyInputs 'world-copy-manifest.json'
Write-AlchemyNew $alchemyManifestPath ($alchemyManifest | ConvertTo-Json -Depth 8)
$alchemyIdentity = [ordered]@{
    marker = 'TC_PORT_TCP_ALCHEMY_AUDIT_V1'; schemaVersion = 1; purpose = 'native-alchemy'; auditPhase = $Phase; goal = 'alchemy'
    networkProtocolVersion = '6'; runId = $RunId; token = $alchemyToken; port = $Port
    sourceKind = $(if ($Phase -eq 'generate') { 'bootstrap' } else { 'alchemy' })
    sourceRunId = $SourceRunId; sourceToken = $alchemySourceMarker.token; sourcePort = [int]$alchemySourceMarker.port
    sourceMarker = $alchemySourceMarker.marker; sourcePurpose = $alchemySourceMarker.purpose
    sourceGoal = $alchemySourceMarker.goal; sourceAuditPhase = $alchemySourceMarker.auditPhase
    sourceServerReportSha256 = Get-AlchemyHash (Join-Path $alchemyInputs 'source-server-report.json')
    sourceClientReportSha256 = Get-AlchemyHash (Join-Path $alchemyInputs 'source-client-report.json')
    sourceRuntimeSha256 = Get-AlchemyHash (Join-Path $alchemyInputs 'source-runtime.json')
    sourceVerificationSha256 = Get-AlchemyHash (Join-Path $alchemyInputs 'source-verification.json')
    worldManifestSha256 = Get-AlchemyHash $alchemyManifestPath
}
foreach ($alchemyRole in @('server', 'client')) {
    $alchemyMarker = [ordered]@{}
    foreach ($alchemyField in $alchemyIdentity.Keys) { $alchemyMarker[$alchemyField] = $alchemyIdentity[$alchemyField] }
    $alchemyMarker.role = $alchemyRole
    Write-AlchemyNew (Join-Path $alchemyRoot "$alchemyRole\.tcp-alchemy-audit.json") ($alchemyMarker | ConvertTo-Json)
}
$alchemyPropertiesPath = Join-Path $alchemySource 'server\server.properties'
Assert-AlchemyUnlinked $alchemyPropertiesPath
if ((Get-Item -LiteralPath $alchemyPropertiesPath).Length -gt 8192) { throw 'Unbounded source server properties.' }
$alchemyProperties = [IO.File]::ReadAllText($alchemyPropertiesPath)
if ($alchemyProperties -notmatch '(?m)^server-ip=127\.0\.0\.1\s*$' -or
    $alchemyProperties -notmatch '(?m)^online-mode=false\s*$' -or
    $alchemyProperties -notmatch '(?m)^gamemode=survival\s*$') { throw 'Source server properties lost private scope.' }
$alchemyProperties = [regex]::Replace($alchemyProperties, '(?m)^server-port=\d+\s*$', "server-port=$Port")
$alchemyProperties = [regex]::Replace($alchemyProperties, '(?m)^level-name=[^\r\n]+\r?$', 'level-name=tcp-alchemy-audit')
Write-AlchemyNew (Join-Path $alchemyRoot 'server\server.properties') $alchemyProperties
$alchemyPreparation = [ordered]@{}
foreach ($alchemyField in $alchemyIdentity.Keys) { $alchemyPreparation[$alchemyField] = $alchemyIdentity[$alchemyField] }
$alchemyPreparation.sourceRoot = $alchemySource
$alchemyPreparation.preparedAtUtc = [DateTime]::UtcNow.ToString('o')
$alchemyPreparation.eulaSha256 = Get-AlchemyHash $alchemyEulaPath
$alchemyPreparation.optionsSha256 = Get-AlchemyHash $alchemyOptionsPath
Write-AlchemyNew (Join-Path $alchemyRoot 'preparation.json') ($alchemyPreparation | ConvertTo-Json -Depth 8)
[pscustomobject]@{ RunId = $RunId; RunDirectory = $alchemyRoot; Address = "127.0.0.1:$Port"; Token = $alchemyToken;
    Phase = $Phase; SourceRunId = $SourceRunId; Note = 'Exact stopped private-world copy; fresh owned native TCP engines.' } | ConvertTo-Json
