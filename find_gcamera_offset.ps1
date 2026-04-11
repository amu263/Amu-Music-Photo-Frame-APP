$b = [System.IO.File]::ReadAllBytes("E:\download\MVIMG_20260411_072801.jpg")

# XMP starts at 7512 (after APP1 marker and length)
# But we need to find the actual GCamera XMP block

# Let's search for "GCamera" in the file
$gcamera = [System.Text.Encoding]::ASCII.GetBytes("GCamera")
$gcameraLen = $gcamera.Length

for ($i = 0; $i -lt $b.Length - $gcameraLen; $i++) {
    $match = $true
    for ($j = 0; $j -lt $gcameraLen; $j++) {
        if ($b[$i + $j] -ne $gcamera[$j]) {
            $match = $false
            break
        }
    }
    if ($match) {
        Write-Host "GCamera found at: $i"
        
        # Extract surrounding context (200 bytes before and after)
        $start = [Math]::Max(0, $i - 100)
        $end = [Math]::Min($b.Length, $i + 500)
        
        Write-Host "Context around GCamera (offset $start to $end):"
        $contextBytes = $b[$start..($end-1)]
        $contextStr = [System.Text.Encoding]::ASCII.GetString($contextBytes)
        
        # Find and print lines containing key tags
        $lines = $contextStr -split "`n"
        foreach ($line in $lines) {
            if ($line -match 'MicroVideo|MotionPhoto|Offset|Video') {
                Write-Host $line.Trim()
            }
        }
        
        break
    }
}

# Also search for MicroVideoOffset directly
Write-Host ""
Write-Host "Searching for MicroVideoOffset string..."
$microVideoStr = [System.Text.Encoding]::ASCII.GetBytes("MicroVideoOffset")
for ($i = 0; $i -lt $b.Length - $microVideoStr.Length; $i++) {
    $match = $true
    for ($j = 0; $j -lt $microVideoStr.Length; $j++) {
        if ($b[$i + $j] -ne $microVideoStr[$j]) {
            $match = $false
            break
        }
    }
    if ($match) {
        Write-Host "MicroVideoOffset found at: $i"
        # Show context
        $contextStart = [Math]::Max(0, $i - 20)
        $contextEnd = [Math]::Min($b.Length, $i + 100)
        $context = [System.Text.Encoding]::ASCII.GetString($b[$contextStart..($contextEnd-1)])
        Write-Host "Context: $context"
        break
    }
}
