$b = [System.IO.File]::ReadAllBytes("E:\download\MVIMG_20260411_072801.jpg")
$ftypPos = 3159792
Write-Host "Analyzing structure around ftyp at position $ftypPos"
Write-Host "Bytes around ftyp (offset -20 to +40):"
for ($i = $ftypPos - 20; $i -lt $ftypPos + 40; $i++) {
    Write-Host ([String]::Format("{0:X2} ", $b[$i])) -NoNewline
}
Write-Host ""
Write-Host ""

# Check for mdat (media data) which usually follows moov in MP4
for ($i = 0; $i -lt $b.Length-4; $i++) {
    if ($b[$i] -eq 0x6D -and $b[$i+1] -eq 0x64 -and $b[$i+2] -eq 0x61 -and $b[$i+3] -eq 0x74) {
        Write-Host "mdat found at:" $i
    }
}

# Check for moov
for ($i = 0; $i -lt $b.Length-4; $i++) {
    if ($b[$i] -eq 0x6D -and $b[$i+1] -eq 0x6F -and $b[$i+2] -eq 0x6F -and $b[$i+3] -eq 0x76) {
        Write-Host "moov found at:" $i
    }
}
