$Base = if ($env:BASE) { $env:BASE } else { "http://localhost:8080" }
if (-not $env:MANIFEST_API_USERNAME -or -not $env:MANIFEST_API_PASSWORD) {
    throw "Set MANIFEST_API_USERNAME dan MANIFEST_API_PASSWORD"
}
$credentials = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(
    "$($env:MANIFEST_API_USERNAME):$($env:MANIFEST_API_PASSWORD)"))
$body = Get-Content -Raw -Path "examples/request.json"
$result = Invoke-RestMethod -Method Post -Uri "$Base/api/v1/manifests/generate-pdf" `
    -Headers @{ Authorization = "Basic $credentials" } `
    -ContentType "application/json" -Body $body
$result | ConvertTo-Json | Set-Content -Encoding UTF8 manifest-link.json
Write-Host "URL dokumen:" $result.url
Write-Host "Buka URL, lalu masukkan tanggal hari ini DDMMYYYY (WIB)."
