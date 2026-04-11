$b = [System.IO.File]::ReadAllBytes("E:\download\MVIMG_20260411_072801.jpg")
$totalLen = $b.Length
Write-Host "Total bytes: $totalLen"

# Search for MotionPhoto_Data marker
$marker = [byte[]](0x4D, 0x6F, 0x74, 0x69, 0x6F, 0x6E, 0x50, 0x68, 0x6F, 0x74, 0x6F, 0x5F, 0x44, 0x61, 0x74, 0x61)
$markerLen = $marker.Length
$found = $false

for ($i = 0; $i -lt $totalLen - $markerLen; $i++) {
    $match = $true
    for ($j = 0; $j -lt $markerLen; $j++) {
        if ($b[$i + $j] -ne $marker[$j]) {
            $match = $false
            break
        }
    }
    if ($match) {
        Write-Host "MotionPhoto_Data marker found at: $i (0x$([System.Convert]::ToString($i, 16)))"
        # Show bytes before marker
        Write-Host "Bytes before marker (20 bytes):"
        for ($k = [Math]::Max(0, $i-20); $k -lt $i; $k++) {
            Write-Host ([String]::Format("{0:X2} ", $b[$k])) -NoNewline
        }
        Write-Host ""
        $found = $true
    }
}

if (-not $found) {
    Write-Host "MotionPhoto_Data marker NOT found"
}

# Also search for SEFH (Samsung Extended Feature Header)
$sefMarker = [byte[]](0x53, 0x45, 0x46, 0x48) # "SEFH"
for ($i = 0; $i -lt $totalLen - 4; $i++) {
    if ($b[$i] -eq $sefMarker[0] -and $b[$i+1] -eq $sefMarker[1] -and $b[$i+2] -eq $sefMarker[2] -and $b[$i+3] -eq $sefMarker[3]) {
        Write-Host "SEFH marker found at: $i"
    }
}
