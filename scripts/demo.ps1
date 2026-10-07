param([string]$BaseUrl='http://localhost:8080')
$ErrorActionPreference='Stop'

Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/mock/scenes/NORMAL"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/devices/indoor-node-01/recommendation" | ConvertTo-Json -Depth 10
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/mock/scenes/FIRE"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/devices/indoor-node-01/recommendation" | ConvertTo-Json -Depth 10
Invoke-RestMethod -Uri "$BaseUrl/api/alerts?limit=5" | ConvertTo-Json -Depth 10
# Leave standard demo nodes in normal state; historical alerts remain queryable.
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/mock/scenes/NORMAL"
