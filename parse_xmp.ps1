$b = [System.IO.File]::ReadAllBytes("E:\download\MVIMG_20260411_072801.jpg")

# Find XMP data (after FF E1)
$xmpStart = -1
for ($i = 7500; $i -lt 8000; $i++) {
    if ($b[$i] -eq 0xFF -and $b[$i+1] -eq 0xE1) {
        $xmpStart = $i + 2
        # Get length (big-endian from $i+2)
        $len = ($b[$i+2] -shl 8) + $b[$i+3]
        Write-Host "Found APP1 XMP at offset $($i+2), length: $len"
        break
    }
}

if ($xmpStart -gt 0) {
    # Extract XMP as string
    $xmpEnd = $xmpStart + $len - 2  # subtract 2 for the length field itself
    $xmpBytes = @()
    for ($i = $xmpStart; $i -lt [Math]::Min($xmpStart + 5000, $b.Length); $i++) {
        if ($b[$i] -eq 0x00) { break }
        $xmpBytes += $b[$i]
    }
    $xmpStr = -join [char[]]$xmpBytes
    Write-Host ""
    Write-Host "XMP content (first 2000 chars):"
    Write-Host $xmpStr.Substring(0, [Math]::Min(2000, $xmpStr.Length))
    
    # Search for MicroVideoOffset
    if ($xmpStr -match 'MicroVideoOffset["\s>]+([0-9]+)') {
        Write-Host ""
        Write-Host "Found MicroVideoOffset: $matches[1]"
    } else {
        Write-Host ""
        Write-Host "MicroVideoOffset NOT found in XMP"
    }
    
    # Also search for MotionPhoto related tags
    if ($xmpStr -match 'GCameraMotionPhoto') {
        Write-Host "Found GCameraMotionPhoto"
    }
    if ($xmpStr -match 'MotionPhoto') {
        Write-Host "Found MotionPhoto related tag"
    }
}
