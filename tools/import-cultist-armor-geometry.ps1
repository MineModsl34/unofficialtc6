param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$rootPath = (Resolve-Path -LiteralPath $ProjectRoot).Path
$originals = Join-Path $rootPath 'reference/tc6/src/thaumcraft/client/renderers/models/gear'
$models = [ordered]@{}
function Number([string]$raw) {
    $token = ($raw -replace '\s','').Replace('(float)','').Trim()
    $pi = [regex]::Match($token, '^\((-?)Math\.PI/([0-9.]+)\)$')
    if ($pi.Success) { $value = [Math]::PI / [double]::Parse($pi.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture); if ($pi.Groups[1].Value -eq '-') { $value = -$value }; return [float]$value }
    if ($token -notmatch '^-?[0-9]+(?:\.[0-9]+)?F?$') { throw "Unsupported source arithmetic: $raw" }
    return [float]::Parse($token.TrimEnd('F'), [Globalization.CultureInfo]::InvariantCulture)
}
function Numbers([string]$raw) { return @($raw.Split(',') | ForEach-Object { Number $_ }) }
foreach ($entry in @(@('knight','ModelKnightArmor'), @('robe','ModelRobe'), @('leader','ModelLeaderArmor'))) {
    $sourcePath = Join-Path $originals ($entry[1] + '.java')
    $source = Get-Content -LiteralPath $sourcePath -Raw
    $constructor = [regex]::Match($source, '(?s)public ' + $entry[1] + '\(float f\) \{(.*?)\n    \}\s*\n    public void render').Groups[1].Value
    if (!$constructor) { throw 'Source constructor not found' }
    $parts = [ordered]@{}
    $attachment = 'both'
    $branch = $null
    foreach ($line in ($constructor -split '\r?\n')) {
        $text = $line.Trim()
        if ($text -match '^this\.(\w+) = new ModelRenderer\(this, (\d+), (\d+)\);$') {
            $parts[$Matches[1]] = [ordered]@{name=$Matches[1];u=[int]$Matches[2];v=[int]$Matches[3];box=$null;mirror=$false;offset=@(0,0,0);rotation=@(0,0,0);parent=$null;variant=$null;mirrorState=$false}; continue
        }
        if ($text -match '^this\.(\w+)\.mirror = true;$') { $parts[$Matches[1]].mirrorState = $true; continue }
        if ($text -match '^this\.(\w+)\.addBox\((.*)\);$') { $part = $parts[$Matches[1]]; $part.box = Numbers $Matches[2]; $part.mirror = $part.mirrorState; continue }
        if ($text -match '^this\.(\w+)\.setRotationPoint\((.*)\);$') { $parts[$Matches[1]].offset = Numbers $Matches[2]; continue }
        if ($text -match '^this\.setRotation\(this\.(\w+), (.*)\);$') { $parts[$Matches[1]].rotation = Numbers $Matches[2]; continue }
        if ($text -eq 'if (f < 1.0F) {') { $attachment = 'inner'; $branch='inner'; continue }
        if ($text -eq 'if (!(f < 1.0F)) {') { $attachment = 'outer'; $branch='outer'; continue }
        if ($text -eq '} else {') { if ($branch -ne 'inner') { throw 'Unsupported branch' }; $attachment='outer'; continue }
        if ($text -eq '}') { $attachment='both'; continue }
        if ($text -match '^this\.(biped\w+)\.addChild\(this\.(\w+)\);$') { $part=$parts[$Matches[2]]; $part.parent=$Matches[1]; $part.variant=$attachment; continue }
        if ($text -match '^(super\(f, 0, 128, 64\);|this\.texture(?:Width|Height) = \d+;|this\.\w+\.setTextureSize\(128, 64\);|this\.biped\w+\.cubeList\.clear\(\);|)$') { continue }
        throw "Unhandled source statement: $text"
    }
    foreach ($part in $parts.Values) {
        if ($null -eq $part.box -or $part.box.Count -ne 6) { throw 'Expected one complete six-coordinate box' }
        $part.Remove('mirrorState')
    }
    $models[$entry[0]] = [ordered]@{source=$entry[1];sha256=(Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant();textureWidth=128;textureHeight=64;parts=@($parts.Values)}
}
# Emit data only. The caller applies it as a patch; never edits the original/extracted sources.
$models | ConvertTo-Json -Depth 8 -Compress
