$Base = if ($env:BASE) { $env:BASE } else { "http://localhost:8080" }
$body = Get-Content -Raw -Path "examples/request.json"
$result = Invoke-RestMethod -Method Post -Uri "$Base/api/v1/manifests/generate-pdf" `
    -ContentType "application/json" -Body $body
$result | ConvertTo-Json | Set-Content -Encoding UTF8 manifest-link.json
Write-Host "URL dokumen:" $result.url
Write-Host "Buka URL, lalu masukkan tanggal hari ini DDMMYYYY (WIB)."
