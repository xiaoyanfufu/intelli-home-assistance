param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$IdempotencyKey = ([guid]::NewGuid().ToString())
)
$ErrorActionPreference = 'Stop'
# Requires intelli.notification.api.enabled=true; recipients always come from configuration.
$body = @{
    subject = 'intelli-home SMTP verification'
    body = 'This is an explicitly requested email channel verification.'
    idempotencyKey = $IdempotencyKey
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/notifications/email" -ContentType 'application/json' -Body $body |
    ConvertTo-Json -Depth 10
