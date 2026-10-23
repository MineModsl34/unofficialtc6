[CmdletBinding()]
param([string]$RunId=('final-'+(Get-Date -Format 'yyyyMMdd-HHmmss')),[string]$SourceAlchemyRunId='alchemy-20261009-reload-02',[string]$RegressionEvidenceRunId,[string]$ClientEvidenceRunId)
$ErrorActionPreference='Stop'
if($RunId -notmatch '^[A-Za-z0-9_-]{1,45}$' -or $SourceAlchemyRunId -notmatch '^[A-Za-z0-9_-]{1,80}$'){throw 'Invalid acceptance/source run ID.'}
$finalRepo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$finalRoot=Join-Path $finalRepo "port\build\final-acceptance\$RunId"
if(Test-Path -LiteralPath $finalRoot){throw 'Choose a fresh acceptance ID.'}
$finalSource=Join-Path $finalRepo "port\build\tcp-alchemy\$SourceAlchemyRunId"
$finalEula=Join-Path $finalSource 'server\eula.txt'
if(!(Test-Path -LiteralPath $finalEula)){throw 'Accepted source cohort EULA missing.'}
$null=New-Item -ItemType Directory -Path $finalRoot
$finalEncoding=[Text.UTF8Encoding]::new($false)
$finalReport=[ordered]@{runId=$RunId;status='running';codeCommit=(& git -C $finalRepo rev-parse HEAD).Trim();startedUtc=[DateTime]::UtcNow.ToString('o');phases=@();failure=$null}
function Save-FinalReport {[IO.File]::WriteAllText((Join-Path $finalRoot 'report.json'),($finalReport|ConvertTo-Json -Depth 12)+"`n",$finalEncoding)}
function Invoke-FinalPhase([string]$Name,[scriptblock]$Action){$finalPhase=[ordered]@{name=$Name;startedUtc=[DateTime]::UtcNow.ToString('o');passed=$false};$finalReport.phases+=$finalPhase;Save-FinalReport;Write-Host "Acceptance phase: $Name";& $Action;$finalPhase.passed=$true;$finalPhase.endedUtc=[DateTime]::UtcNow.ToString('o');Save-FinalReport}
function Read-FinalBulkEvidence([string]$Id,[string]$Phase){
    if($Id -notmatch '^[A-Za-z0-9_-]{1,65}$'){throw 'Invalid accepted bulk evidence ID.'}
    $finalEvidence=[IO.File]::ReadAllText((Join-Path $finalRepo "port\build\bulk-tests\$Id\report.json"))|ConvertFrom-Json
    if($finalEvidence.runId -cne $Id -or $finalEvidence.workingTreeChanged -or $finalEvidence.codeCommit -notmatch '^[a-f0-9]{40}$'){throw 'Bulk evidence identity or clean launch mismatch.'}
    $finalChanged=@(& git -C $finalRepo diff --name-only $finalEvidence.codeCommit HEAD)
    if($LASTEXITCODE -ne 0 -or @($finalChanged|Where-Object {$_ -notmatch '^(tools/|docs/|PORTING-PLAN\.md$|port/src/main/java/thaumcraft/client/lib/Client(GameplayReview|GolemRenderReview|SealLogisticsReview|EntityRenderReview|JeiReviewScenario)\.java$)'}).Count){throw 'Gameplay code changed since accepted evidence; run the phase again.'}
    $finalAccepted=@($finalEvidence.phases|Where-Object {$_.name -ceq $Phase -and $_.exitCode -eq 0})
    if($finalAccepted.Count -ne 1){throw 'Accepted phase missing.'}
    if($Phase -eq 'regression' -and (!$finalAccepted[0].requiredGameTestsPassed -or $finalAccepted[0].unit.tests -eq 0 -or $finalAccepted[0].unit.failures -or $finalAccepted[0].unit.errors)){throw 'Incomplete regression evidence.'}
    if($Phase -eq 'client'){
        $finalCurrentJar=(Get-FileHash -LiteralPath (Join-Path $finalRepo 'port\build\libs\thaumcraft-6.1.0-port.jar')).Hash.ToLowerInvariant()
        if($finalEvidence.status -cne 'passed' -or $finalEvidence.jar.sha256 -cne $finalCurrentJar){throw 'Client evidence or matching current jar rejected.'}
    }
    return $finalEvidence
}
Save-FinalReport
try{
    if($RegressionEvidenceRunId -or $ClientEvidenceRunId){
        if(!$RegressionEvidenceRunId -or !$ClientEvidenceRunId){throw 'Both accepted server and client phases are needed for reuse.'}
        Invoke-FinalPhase 'accepted-bulk-server-and-client' { $script:finalRegression=Read-FinalBulkEvidence $RegressionEvidenceRunId 'regression';$script:finalBulk=Read-FinalBulkEvidence $ClientEvidenceRunId 'client' }
        $finalReport.regression=[ordered]@{runId=$finalRegression.runId;codeCommit=$finalRegression.codeCommit;workingTreeChanged=$finalRegression.workingTreeChanged;phase=@($finalRegression.phases|Where-Object {$_.name -ceq 'regression'})[0]};$finalLoadRun=$RegressionEvidenceRunId
        $finalReport.reuseScope='Server gameplay code unchanged; only client review/runner/documentation edits allowed. Client matches current jar. Failed unrelated phases are not accepted.'
    }else{
        Invoke-FinalPhase 'bulk-server-and-client' { & (Join-Path $PSScriptRoot 'run-bulk-tests.ps1') -Mode All -RunId "$RunId-bulk" }
        $finalBulkPath=Join-Path $finalRepo "port\build\bulk-tests\$RunId-bulk\report.json";$finalBulk=[IO.File]::ReadAllText($finalBulkPath)|ConvertFrom-Json
        if($finalBulk.status -cne 'passed'){throw 'Bulk evidence failed.'};$finalLoadRun="$RunId-bulk"
    }
    $finalLoad=[IO.File]::ReadAllText((Join-Path $finalRepo "port\build\bulk-tests\$finalLoadRun\server\native-load-sample.json"))|ConvertFrom-Json
    if($finalLoad.passed -ne $true){throw 'Native load evidence failed.'}
    $finalReport.bulk=$finalBulk;$finalReport.nativeLoad=$finalLoad;Save-FinalReport
    Invoke-FinalPhase 'two-real-clients-owner-authority' { & (Join-Path $PSScriptRoot 'run-stage-four-network.ps1') -EulaSource $finalEula }
    # The preparer provides a fresh UUID root; keep the completed record in the aggregate.
    $finalNetwork=Get-ChildItem -LiteralPath (Join-Path $finalRepo 'port\build\research-catalog-network') -Directory -Filter 'stage4-*' | Where-Object {$_.CreationTimeUtc -ge [DateTime]::Parse($finalReport.startedUtc).ToUniversalTime()} | Sort-Object CreationTimeUtc -Descending | Select-Object -First 1
    if(!$finalNetwork){throw 'Fresh network evidence missing.'};$finalNetworkPath=Join-Path $finalNetwork.FullName 'runtime.json';$finalNet=[IO.File]::ReadAllText($finalNetworkPath)|ConvertFrom-Json
    if($finalNet.status -cne 'completed' -or @($finalNet.processes).Count -ne 3 -or @($finalNet.processes|Where-Object {$_.exitCode -ne 0 -or $_.forcedStop}).Count){throw 'Three-role native exits not accepted.'};$finalReport.network=$finalNet;$finalReport.networkRuntime=$finalNetworkPath;Save-FinalReport
    Invoke-FinalPhase 'tcp-paid-casters-and-equipment' { & (Join-Path $PSScriptRoot 'run-tcp-casters.ps1') -RunId "$RunId-casters" -Phase generate -SourceRunId $SourceAlchemyRunId -Port 25577 }
    Invoke-FinalPhase 'tcp-world-and-player-reload' { & (Join-Path $PSScriptRoot 'run-tcp-casters.ps1') -RunId "$RunId-reload" -Phase reload -SourceRunId "$RunId-casters" -Port 25577 }
    $finalTcp=@();foreach($finalSuffix in @('casters','reload')){$finalTcpPath=Join-Path $finalRepo "port\build\tcp-casters\$RunId-$finalSuffix\runtime.json";$finalRuntime=[IO.File]::ReadAllText($finalTcpPath)|ConvertFrom-Json;if($finalRuntime.status -cne 'completed' -or @($finalRuntime.processes|Where-Object {$_.exitCode -ne 0 -or $_.forcedStop}).Count){throw 'Native TCP exits not accepted.'};$finalTcp+=[ordered]@{runtime=$finalTcpPath;evidence=$finalRuntime}}
    $finalReport.tcp=$finalTcp;$finalReport.status='passed'
}catch{$finalReport.status='failed';$finalReport.failure=$_.Exception.Message;throw}finally{$finalReport.endedUtc=[DateTime]::UtcNow.ToString('o');Save-FinalReport;Write-Host "Unified acceptance: $finalRoot\report.json"}
