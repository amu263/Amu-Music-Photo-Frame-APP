$b = [System.IO.File]::ReadAllBytes("E:\download\MVIMG_20260411_072801.jpg")
$totalLen = $b.Length

# Search for XMP Container markers
# XMP in JPEGs is typically in APP1 segment starting with "http://ns.adobe.com/xap/1.0/"
# But motion photo containers use different markers

Write-Host "Searching for various markers..."

# Check around position 7510 (after JPEG EOI at 7509)
Write-Host ""
Write-Host "Bytes 7500-7550 (after first JPEG):"
for ($i = 7500; $i -lt 7550; $i++) {
    Write-Host ([String]::Format("{0:X2} ", $b[$i])) -NoNewline
}
Write-Host ""

# Search for mpvd (HEIC motion photo container)
$mpvd = [byte[]](0x6D, 0x70, 0x76, 0x64) # "mpvd"
for ($i = 0; $i -lt $totalLen - 4; $i++) {
    if ($b[$i] -eq $mpvd[0] -and $b[$i+1] -eq $mpvd[1] -and $b[$i+2] -eq $mpvd[2] -and $b[$i+3] -eq $mpvd[3]) {
        Write-Host "mpvd found at: $i"
    }
}

# Search for CFRM (possible Samsung format marker)
$cfrm = [byte[]](0x43, 0x46, 0x52, 0x4D) # "CFRM"
for ($i = 0; $i -lt $totalLen - 4; $i++) {
    if ($b[$i] -eq $cfrm[0] -and $b[$i+1] -eq $cfrm[1] -and $b[$i+2] -eq $cfrm[2] -and $b[$i+3] -eq $cfrm[3]) {
        Write-Host "CFRM found at: $i"
    }
}

# Search for ftyp and show context
Write-Host ""
Write-Host "ftyp context (at 3159792):"
for ($i = 3159782; $i -lt 3159822; $i++) {
    Write-Host ([String]::Format("{0:X2} ", $b[$i])) -NoNewline
}
Write-Host ""

# Check what's between JPEG end (7509) and ftyp (3159792)
Write-Host ""
Write-Host "Bytes 7510-7520 (first 10 bytes after JPEG end):"
for ($i = 7510; $i -lt 7520; $i++) {
    Write-Host ([String]::Format("{0:X2} ", $b[$i])) -NoNewline
}
Write-Host ""
