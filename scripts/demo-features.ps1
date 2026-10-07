param([string]$BaseUrl='http://127.0.0.1:8080')
$ErrorActionPreference='Stop'
function Send-Json($Path,$Body) {
    Invoke-RestMethod -Uri "$BaseUrl$Path" -Method Post -ContentType 'application/json' -Body ($Body | ConvertTo-Json -Depth 12)
}
$device='demo-feature-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$null=Send-Json '/api/devices' @{deviceKey=$device;displayName='环境演示';modelKey='environment';modelVersion=1;source='MOCK';location='INDOOR'}
$event=Send-Json "/api/mock/devices/$device/events" @{temperature=25;humidity=55}
$capabilities=Invoke-RestMethod "$BaseUrl/api/devices/$device/capabilities"
$baseline=Invoke-RestMethod "$BaseUrl/api/events/snapshot"
$from=$event.occurredAt-1000
$to=$event.occurredAt+1000
$history=Invoke-RestMethod "$BaseUrl/api/devices/$device/history?property=temperature&from=$from&to=$to&interval=minute"
$definition=Get-Content (Join-Path (Split-Path $PSScriptRoot -Parent) 'contracts/examples/environment-page.json') -Raw | ConvertFrom-Json
foreach($widget in $definition.components) { if($widget.binding.deviceKey) { $widget.binding.deviceKey=$device } }
$page=Send-Json '/api/pages' $definition
$definition.title='环境演示第二版'
$updated=Invoke-RestMethod "$BaseUrl/api/pages/$($page.id)" -Method Put -SkipHeaderValidation -Headers @{'If-Match'="$($page.revision)"} -ContentType 'application/json' -Body ($definition | ConvertTo-Json -Depth 12)
$restored=Invoke-RestMethod "$BaseUrl/api/pages/$($page.id)/restore" -Method Post -SkipHeaderValidation -Headers @{'If-Match'="$($updated.revision)"} -ContentType 'application/json' -Body '{"revision":1}'
@{deviceKey=$device;capabilities=$capabilities;history=$history;page=$restored;streamUrl="$BaseUrl/api/events/stream?cursor=$($baseline.cursor)&deviceKey=$device"} | ConvertTo-Json -Depth 15
# 本脚本保留自己的演示设备/页面供检查；完整收衣、控制、SSE 和失败验证见 verify.ps1。
