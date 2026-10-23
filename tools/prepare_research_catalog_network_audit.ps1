[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $EulaSource,
    [string] $OptionsSource,
    [string] $RunId = ('catalog-' + [guid]::NewGuid().ToString()),
    [ValidateRange(1024, 65535)][int] $Port = 25571
)

$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$auditBase = [IO.Path]::GetFullPath((Join-Path $projectDirectory 'port\build\research-catalog-network'))
if ($RunId -notmatch '^[A-Za-z0-9_-]{1,80}$') { throw 'RunId must be a new simple ASCII basename.' }
$runDirectory = [IO.Path]::GetFullPath((Join-Path $auditBase $RunId))
if (Test-Path -LiteralPath $runDirectory) { throw "Existing audit is never overwritten or reused: $runDirectory" }

function Assert-NoLinkedAncestors([string] $Candidate) {
    $cursor = [IO.Path]::GetFullPath($Candidate)
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            if (((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Linked/reparse audit ancestor is forbidden: $cursor"
            }
        }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
}
Assert-NoLinkedAncestors $runDirectory
$eulaPath = (Resolve-Path -LiteralPath $EulaSource).ProviderPath
if ([IO.File]::ReadAllText($eulaPath) -notmatch '(?m)^\s*eula\s*=\s*true\s*$') {
    throw 'Supply an already human-accepted EULA; this script does not accept it.'
}
if ([string]::IsNullOrWhiteSpace($OptionsSource)) { $OptionsSource = Join-Path $projectDirectory 'port\run\options.txt' }
$optionsPath = (Resolve-Path -LiteralPath $OptionsSource).ProviderPath
if ([IO.File]::ReadAllText($optionsPath) -notmatch '(?m)^onboardAccessibility:false\s*$') {
    throw 'Complete accessibility onboarding normally; no preference/confirmation is changed here.'
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $Port is already listening."
}

$null = New-Item -ItemType Directory -Path $runDirectory
$token = [guid]::NewGuid().ToString()
$utf8 = [Text.UTF8Encoding]::new($false)
foreach ($role in @('server', 'client-a', 'client-b')) {
    $roleDirectory = Join-Path $runDirectory $role
    $null = New-Item -ItemType Directory -Path $roleDirectory
    $marker = [ordered]@{
        marker = 'TC_PORT_RESEARCH_CATALOG_NETWORK_AUDIT_V1'
        schemaVersion = 1
        runId = $RunId
        token = $token
        role = $role
        phase = 'generate'
        port = $Port
    }
    [IO.File]::WriteAllText((Join-Path $roleDirectory '.research-catalog-network-audit.json'), ($marker | ConvertTo-Json), $utf8)
    if ($role -ne 'server') { Copy-Item -LiteralPath $optionsPath -Destination (Join-Path $roleDirectory 'options.txt') }
}
$serverDirectory = Join-Path $runDirectory 'server'
Copy-Item -LiteralPath $eulaPath -Destination (Join-Path $serverDirectory 'eula.txt')
$properties = @"
level-name=research-catalog-network-audit
level-seed=123456789
level-type=minecraft:flat
generator-settings={"layers":[{"height":1,"block":"minecraft:bedrock"},{"height":2,"block":"minecraft:dirt"},{"height":1,"block":"minecraft:grass_block"}],"biome":"minecraft:plains","features":false,"lakes":false,"structure_overrides":[]}
generate-structures=false
server-ip=127.0.0.1
server-port=$Port
online-mode=false
enforce-secure-profile=false
hardcore=false
white-list=false
max-players=2
difficulty=peaceful
gamemode=creative
allow-flight=true
spawn-protection=0
spawn-chunk-radius=0
view-distance=2
simulation-distance=2
max-tick-time=300000
enable-rcon=false
enable-query=false
"@
[IO.File]::WriteAllText((Join-Path $serverDirectory 'server.properties'), $properties, $utf8)
[pscustomobject]@{
    RunDirectory = $runDirectory
    Address = "127.0.0.1:$Port"
    Note = 'Prepared only. No process launched, EULA accepted or preferences changed; no existing world copied.'
} | ConvertTo-Json
