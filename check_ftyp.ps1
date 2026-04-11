$b = [System.IO.File]::ReadAllBytes("E:\download\MVIMG_20260411_072801.jpg")
Write-Host "Total bytes:" $b.Length
for ($i = 0; $i -lt $b.Length-4; $i++) {
    if ($b[$i] -eq 0x66 -and $b[$i+1] -eq 0x74 -and $b[$i+2] -eq 0x79 -and $b[$i+3] -eq 0x70) {
        Write-Host "ftyp found at:" $i
    }
}
