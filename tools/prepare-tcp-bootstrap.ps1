[CmdletBinding()]
param(
    [string] $RunId = ('tcp-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)),
    [ValidateRange(1024, 65535)][int] $Port = 25575,
    [string] $EulaSource,
    [string] $OptionsSource
)

$ErrorActionPreference = 'Stop'
$tcpRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$tcpBase = [IO.Path]::GetFullPath((Join-Path $tcpRepo 'port\build\tcp-bootstrap'))
if ($RunId -notmatch '^[A-Za-z0-9_-]{1,80}$') { throw 'RunId must be a fresh simple ASCII basename.' }
$tcpRoot = [IO.Path]::GetFullPath((Join-Path $tcpBase $RunId))
if ([IO.Path]::GetDirectoryName($tcpRoot) -ne $tcpBase) { throw 'The run must be an immediate child of the private audit base.' }

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
function Assert-TcpSource([string] $Candidate, [long] $Limit) {
    $tcpSourcePath = (Resolve-Path -LiteralPath $Candidate).ProviderPath
    Assert-TcpUnlinked $tcpSourcePath
    $tcpSourceItem = Get-Item -LiteralPath $tcpSourcePath -Force
    if ($tcpSourceItem.PSIsContainer -or $tcpSourceItem.Length -gt $Limit) { throw "Invalid or unbounded source file: $tcpSourcePath" }
    return $tcpSourcePath
}
function Write-TcpNew([string] $Path, [string] $Content) {
    Assert-TcpUnlinked $Path
    $tcpBytes = [Text.UTF8Encoding]::new($false, $true).GetBytes($Content)
    $tcpStream = [IO.FileStream]::new($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $tcpStream.Write($tcpBytes, 0, $tcpBytes.Length); $tcpStream.Flush($true) } finally { $tcpStream.Dispose() }
}

Assert-TcpUnlinked $tcpRoot
if (Test-Path -LiteralPath $tcpRoot) { throw "Existing audit is never overwritten: $tcpRoot" }
if ([string]::IsNullOrWhiteSpace($EulaSource)) {
    $EulaSource = Join-Path $tcpRepo 'reference\validation\original-player-20261007-2128\eula.txt'
}
if ([string]::IsNullOrWhiteSpace($OptionsSource)) {
    $OptionsSource = Join-Path $tcpRepo 'port\build\jei-review\jei-20261009-review-06\client\options.txt'
}
$tcpEulaPath = Assert-TcpSource $EulaSource 4096
$tcpOptionsPath = Assert-TcpSource $OptionsSource 1048576
if ([IO.File]::ReadAllText($tcpEulaPath) -notmatch '(?m)^\s*eula\s*=\s*true\s*$') {
    throw 'Supply an already accepted EULA source. This preparer only copies its existing bytes.'
}
$tcpOptions = [IO.File]::ReadAllText($tcpOptionsPath)
if ($tcpOptions -notmatch '(?m)^onboardAccessibility:false\s*$' -or
    $tcpOptions -notmatch '(?m)^pauseOnLostFocus:false\s*$') {
    throw 'Supply existing client options with completed accessibility onboarding and pauseOnLostFocus:false.'
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $Port is already listening."
}
$null = New-Item -ItemType Directory -Path $tcpRoot
$tcpToken = [guid]::NewGuid().ToString()
foreach ($tcpRole in @('server', 'client')) {
    $tcpRoleDirectory = Join-Path $tcpRoot $tcpRole
    Assert-TcpUnlinked $tcpRoleDirectory
    $null = New-Item -ItemType Directory -Path $tcpRoleDirectory
    $tcpMarker = [ordered]@{
        marker = 'TC_PORT_TCP_BOOTSTRAP_AUDIT_V1'
        schemaVersion = 1
        purpose = 'native-bootstrap'
        auditPhase = 'generate'
        goal = 'firststeps'
        networkProtocolVersion = '6'
        runId = $RunId
        token = $tcpToken
        role = $tcpRole
        port = $Port
    }
    Write-TcpNew (Join-Path $tcpRoleDirectory '.tcp-bootstrap-audit.json') ($tcpMarker | ConvertTo-Json)
}
[IO.File]::Copy($tcpEulaPath, (Join-Path $tcpRoot 'server\eula.txt'), $false)
[IO.File]::Copy($tcpOptionsPath, (Join-Path $tcpRoot 'client\options.txt'), $false)
$tcpInputs = Join-Path $tcpRoot 'preparation-inputs'
Assert-TcpUnlinked $tcpInputs
$null = New-Item -ItemType Directory -Path $tcpInputs
[IO.File]::Copy($tcpEulaPath, (Join-Path $tcpInputs 'eula.txt'), $false)
[IO.File]::Copy($tcpOptionsPath, (Join-Path $tcpInputs 'options.txt'), $false)
$tcpProperties = @"
level-name=tcp-bootstrap-audit
level-seed=62121120261009
level-type=minecraft:flat
generator-settings={"layers":[{"height":1,"block":"minecraft:bedrock"},{"height":2,"block":"minecraft:dirt"},{"height":1,"block":"minecraft:grass_block"}],"biome":"minecraft:plains","features":false,"lakes":false,"structure_overrides":[]}
generate-structures=false
server-ip=127.0.0.1
server-port=$Port
online-mode=false
enforce-secure-profile=false
hardcore=false
white-list=false
max-players=1
difficulty=peaceful
gamemode=survival
force-gamemode=true
allow-flight=false
spawn-protection=0
spawn-chunk-radius=0
view-distance=4
simulation-distance=4
max-tick-time=300000
enable-rcon=false
enable-query=false
"@
Write-TcpNew (Join-Path $tcpRoot 'server\server.properties') $tcpProperties
Write-TcpNew (Join-Path $tcpRoot 'preparation.json') ([ordered]@{
    marker = 'TC_PORT_TCP_BOOTSTRAP_AUDIT_V1'; schemaVersion = 1; purpose = 'native-bootstrap'
    auditPhase = 'generate'; goal = 'firststeps'
    networkProtocolVersion = '6'; runId = $RunId; token = $tcpToken; port = $Port
    preparedAtUtc = [DateTime]::UtcNow.ToString('o')
    eulaSource = $tcpEulaPath; eulaSha256 = (Get-FileHash -LiteralPath $tcpEulaPath -Algorithm SHA256).Hash.ToLowerInvariant()
    optionsSource = $tcpOptionsPath; optionsSha256 = (Get-FileHash -LiteralPath $tcpOptionsPath -Algorithm SHA256).Hash.ToLowerInvariant()
} | ConvertTo-Json)
[pscustomobject]@{
    RunId = $RunId
    RunDirectory = $tcpRoot
    Address = "127.0.0.1:$Port"
    Token = $tcpToken
    Note = 'Fresh private server and client prepared. Existing EULA/options copied exactly; no game launched or world copied.'
} | ConvertTo-Json
