[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $EulaSource,
    [string] $OptionsSource,
    [ValidateRange(1024, 65535)][int] $Port = 25573
)

$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$requestRunId = 'request-' + [guid]::NewGuid().ToString()
$prepareArguments = @{ EulaSource = $EulaSource; RunId = $requestRunId; Port = $Port }
if (-not [string]::IsNullOrWhiteSpace($OptionsSource)) { $prepareArguments.OptionsSource = $OptionsSource }

# The unchanged preparer alone checks fresh paths, accepted EULA, copied AS-IS options and port.
# It does not launch a process, accept an EULA, change a warning or copy any existing world.
$prepared = (& (Join-Path $PSScriptRoot 'prepare_research_catalog_network_audit.ps1') @prepareArguments |
    Out-String | ConvertFrom-Json)
$expectedRunDirectory = [IO.Path]::GetFullPath((Join-Path $projectDirectory "port\build\research-catalog-network\$requestRunId"))
if ([IO.Path]::GetFullPath($prepared.RunDirectory) -ne $expectedRunDirectory -or
    $prepared.Address -ne "127.0.0.1:$Port") {
    throw 'Fresh preparer result does not match this browser-request audit.'
}

function Assert-NoLinkedRequestAncestors([string] $Candidate) {
    $cursor = [IO.Path]::GetFullPath($Candidate)
    while ($cursor) {
        if ((Test-Path -LiteralPath $cursor) -and
            (((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
            throw "Linked/reparse browser-request marker ancestor is forbidden: $cursor"
        }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
}

$utf8 = [Text.UTF8Encoding]::new($false, $true)
$requestToken = $null
$companions = @()
# Preflight ALL three fresh roles before writing any purpose marker.
foreach ($role in @('server', 'client-a', 'client-b')) {
    $roleDirectory = Join-Path $expectedRunDirectory $role
    $basePath = Join-Path $roleDirectory '.research-catalog-network-audit.json'
    $companionPath = Join-Path $roleDirectory '.research-progress-network-audit.json'
    Assert-NoLinkedRequestAncestors $basePath
    Assert-NoLinkedRequestAncestors $companionPath
    if ((Get-Item -LiteralPath $basePath -Force).Length -gt 4096) { throw 'Unbounded isolation marker.' }
    if (Test-Path -LiteralPath $companionPath) { throw "A purpose marker is never overwritten: $companionPath" }
    $base = [IO.File]::ReadAllText($basePath, $utf8) | ConvertFrom-Json
    if ($base.marker -ne 'TC_PORT_RESEARCH_CATALOG_NETWORK_AUDIT_V1' -or $base.schemaVersion -ne 1 -or
        $base.runId -ne $requestRunId -or $base.role -ne $role -or $base.port -ne $Port -or
        $base.phase -ne 'generate') { throw "Fresh isolation marker mismatch for $role." }
    $null = [guid]::Parse($base.token)
    if ($null -eq $requestToken) { $requestToken = $base.token }
    if ($base.token -ne $requestToken) { throw 'Fresh isolation tokens disagree.' }
    $companion = [ordered]@{
        marker = $base.marker
        schemaVersion = $base.schemaVersion
        runId = $base.runId
        token = $base.token
        role = $base.role
        phase = $base.phase
        port = $base.port
        purpose = 'browser-c2s'
        networkProtocolVersion = '6'
    }
    $companions += [pscustomobject]@{
        Path = $companionPath
        Bytes = $utf8.GetBytes(($companion | ConvertTo-Json))
    }
}
foreach ($companion in $companions) {
    Assert-NoLinkedRequestAncestors $companion.Path
    $stream = [IO.FileStream]::new($companion.Path, [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $stream.Write($companion.Bytes, 0, $companion.Bytes.Length)
        $stream.Flush($true)
    } finally { $stream.Dispose() }
}
[pscustomobject]@{
    RunDirectory = $expectedRunDirectory
    Address = $prepared.Address
    Purpose = 'browser-c2s'
    Note = 'Prepared fresh browser-request roles only. No launch, EULA acceptance, preference change or existing-world copy.'
} | ConvertTo-Json
