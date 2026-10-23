[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $EulaSource,
    [string] $OptionsSource,
    [string] $RunId = ('audit-' + [guid]::NewGuid().ToString()),
    [ValidateRange(1024, 65535)]
    [int] $Port = 25566,
    [ValidateSet('generate', 'reload')]
    [string] $Phase = 'generate',
    [string] $Baseline
)

$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$auditBase = [IO.Path]::GetFullPath((Join-Path $projectDirectory 'port\build\player-data-network'))
if ($RunId -notmatch '^[A-Za-z0-9_-]{1,80}$') {
    throw 'RunId must be a simple new directory basename.'
}
$runDirectory = [IO.Path]::GetFullPath((Join-Path $auditBase $RunId))
if (Test-Path -LiteralPath $runDirectory) {
    throw "Refusing to reuse or overwrite an existing audit: $runDirectory"
}

function Assert-NoLinkedAncestors([string] $Candidate) {
    $cursor = [IO.Path]::GetFullPath($Candidate)
    while ($cursor.StartsWith($projectDirectory, [StringComparison]::OrdinalIgnoreCase)) {
        if (Test-Path -LiteralPath $cursor) {
            $entry = Get-Item -LiteralPath $cursor -Force
            if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Refusing a linked audit path: $cursor"
            }
        }
        if ($cursor.Equals($projectDirectory, [StringComparison]::OrdinalIgnoreCase)) { break }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
}
Assert-NoLinkedAncestors $runDirectory

$eulaPath = (Resolve-Path -LiteralPath $EulaSource).ProviderPath
if ([IO.File]::ReadAllText($eulaPath) -notmatch '(?m)^\s*eula\s*=\s*true\s*$') {
    throw 'The EULA source must already be human-accepted. This script does not accept it.'
}
if ([string]::IsNullOrWhiteSpace($OptionsSource)) {
    $OptionsSource = Join-Path $projectDirectory 'port\run\options.txt'
}
$optionsPath = (Resolve-Path -LiteralPath $OptionsSource).ProviderPath
if ([IO.File]::ReadAllText($optionsPath) -notmatch '(?m)^onboardAccessibility:false\s*$') {
    throw 'The existing options have not completed accessibility onboarding. Complete it normally; no preference or warning flag is edited here.'
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $Port is already listening. Choose a free private port."
}

$token = [guid]::NewGuid().ToString()
$baselinePath = $null
$sourceWorld = $null
if ($Phase -eq 'reload') {
    if ([string]::IsNullOrWhiteSpace($Baseline)) { throw 'Reload requires a completed generate baseline.' }
    $baselinePath = (Resolve-Path -LiteralPath $Baseline).ProviderPath
    $allowedPrefix = $auditBase.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $baselinePath.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($baselinePath) -ne 'server-report.json' -or
        [IO.Path]::GetFileName([IO.Path]::GetDirectoryName($baselinePath)) -ne 'server') {
        throw 'Baseline must be an earlier private player-data-network/<runId>/server/server-report.json.'
    }
    Assert-NoLinkedAncestors $baselinePath
    if ((Get-Item -LiteralPath $baselinePath).Length -gt 2000000) { throw 'Unbounded baseline report.' }
    $previous = Get-Content -LiteralPath $baselinePath -Raw | ConvertFrom-Json
    if ($previous.marker -ne 'TC_PORT_PLAYER_DATA_NETWORK_AUDIT_V1' -or
        $previous.role -ne 'server' -or $previous.phase -ne 'generate' -or
        $previous.status -ne 'completed' -or $previous.saveFlushed -ne $true) {
        throw 'Baseline must be a completed, saved generate audit.'
    }
    if (Get-NetTCPConnection -LocalPort ([int]$previous.port) -State Listen -ErrorAction SilentlyContinue) {
        throw 'The baseline server must have fully stopped before its world is copied.'
    }
    $token = ([guid]::Parse($previous.token)).ToString()
    $sourceWorld = Join-Path ([IO.Path]::GetDirectoryName($baselinePath)) 'player-data-network-audit'
    Assert-NoLinkedAncestors $sourceWorld
    if (-not (Test-Path -LiteralPath (Join-Path $sourceWorld 'level.dat') -PathType Leaf) -or
        (Get-ChildItem -LiteralPath $sourceWorld -Recurse -Force | Where-Object {
            ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0
        })) {
        throw 'Reload source must be a complete private world without linked contents.'
    }
} elseif (-not [string]::IsNullOrWhiteSpace($Baseline)) {
    throw 'A generate audit must not consume a baseline.'
}

$null = New-Item -ItemType Directory -Path $runDirectory
$utf8 = [Text.UTF8Encoding]::new($false)
foreach ($role in @('server', 'client-a', 'client-b')) {
    $roleDirectory = Join-Path $runDirectory $role
    $null = New-Item -ItemType Directory -Path $roleDirectory
    $marker = [ordered]@{
        marker = 'TC_PORT_PLAYER_DATA_NETWORK_AUDIT_V1'
        runId = $RunId
        token = $token
        role = $role
        phase = $Phase
        port = $Port
    }
    if ($baselinePath) { $marker.baseline = $baselinePath }
    [IO.File]::WriteAllText((Join-Path $roleDirectory '.player-data-network-audit.json'), ($marker | ConvertTo-Json), $utf8)
    if ($role -ne 'server') {
        # Exact existing preferences: no fabricated onboarding/confirmation flags.
        Copy-Item -LiteralPath $optionsPath -Destination (Join-Path $roleDirectory 'options.txt')
    }
}
$serverDirectory = Join-Path $runDirectory 'server'
Copy-Item -LiteralPath $eulaPath -Destination (Join-Path $serverDirectory 'eula.txt')
if ($sourceWorld) {
    # Copy only the validated stopped private audit world, never a user's save or a live world.
    Copy-Item -LiteralPath $sourceWorld -Destination (Join-Path $serverDirectory 'player-data-network-audit') -Recurse
}
$properties = @"
level-name=player-data-network-audit
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
    RunId = $RunId
    Phase = $Phase
    Address = "127.0.0.1:$Port"
    ServerDirectory = $serverDirectory
    Baseline = $baselinePath
    Note = 'Prepared only; no process started, EULA accepted or preference changed. User saves untouched.'
} | ConvertTo-Json
