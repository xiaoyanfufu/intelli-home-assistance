param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [switch]$Send
)
$ErrorActionPreference = 'Stop'
# Preview by default. Explicit -Send requests real delivery using configured recipients.
$dryRun = if ($Send) { 'false' } else { 'true' }
$result = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/daily-advice/run?dryRun=$dryRun"
$result | ConvertTo-Json -Depth 20
if ($result.status -eq 'FAILED' -or $result.status -eq 'DELIVERY_NOT_CONFIGURED') {
    throw "Daily advice did not complete: $($result.status)"
}
