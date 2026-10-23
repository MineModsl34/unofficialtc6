[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $EulaSource,
    [string] $AuditDirectory,
    [long] $Seed = 123456789,
    [ValidateRange(1, 10)]
    [int] $Radius = 4
)

$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$auditRoot = [IO.Path]::GetFullPath((Join-Path $projectDirectory 'port\build\worldgen-audit'))
$eulaPath = (Resolve-Path -LiteralPath $EulaSource).ProviderPath
$acceptedEula = [IO.File]::ReadAllText($eulaPath)
if ($acceptedEula -notmatch '(?m)^\s*eula\s*=\s*true\s*$') {
    throw 'The source EULA has not already been accepted. This script never accepts it for you.'
}

if ([string]::IsNullOrWhiteSpace($AuditDirectory)) {
    $AuditDirectory = Join-Path $auditRoot ([guid]::NewGuid().ToString())
}
$auditPath = [IO.Path]::GetFullPath($AuditDirectory)
$allowedPrefix = $auditRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (-not $auditPath.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Audit directory must be a new child of $auditRoot"
}
if (Test-Path -LiteralPath $auditPath) {
    throw "Refusing to reuse or overwrite an existing directory: $auditPath"
}

$null = New-Item -ItemType Directory -Path $auditPath
Copy-Item -LiteralPath $eulaPath -Destination (Join-Path $auditPath 'eula.txt')
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $auditPath '.thaumcraft-worldgen-audit'), 'TC_PORT_ISOLATED_WORLDGEN_AUDIT_V1', $utf8)
$properties = @"
level-name=worldgen-audit
level-seed=$Seed
level-type=minecraft:normal
generate-structures=true
server-ip=127.0.0.1
server-port=0
online-mode=true
white-list=true
spawn-protection=0
spawn-chunk-radius=0
view-distance=2
simulation-distance=2
max-tick-time=300000
enable-rcon=false
enable-query=false
"@
[IO.File]::WriteAllText((Join-Path $auditPath 'server.properties'), $properties, $utf8)
[pscustomobject]@{
    AuditDirectory = $auditPath
    GenerationReport = Join-Path $auditPath 'generate-report.json'
    ReloadReport = Join-Path $auditPath 'reload-report.json'
    Seed = $Seed
    Radius = $Radius
    Note = 'Prepared only. No server launched; no EULA accepted; user saves untouched.'
} | ConvertTo-Json
