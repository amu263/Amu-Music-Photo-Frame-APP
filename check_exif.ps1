$b = [System.IO.File]::ReadAllBytes("E:\download\MVIMG_20260411_072801.jpg")

# Check EXIF for Samsung-specific tags
# Samsung tags are in IFD
Write-Host "Checking EXIF structure..."

# Find APP1 EXIF marker (FF E1)
for ($i = 0; $i -lt 10000; $i++) {
    if ($b[$i] -eq 0xFF -and $b[$i+1] -eq 0xE1) {
        Write-Host "Found APP1 at offset: $i"
        $exifLen = ($b[$i+2] -shl 8) + $b[$i+3]
        Write-Host "APP1 length: $exifLen bytes"
        
        # EXIF header is typically "Exif\0\0"
        # Check at i+8 for "Exif"
        if ($b[$i+8] -eq 0x45 -and $b[$i+9] -eq 0x78 -and $b[$i+10] -eq 0x69 -and $b[$i+11] -eq 0x66) {
            Write-Host "Valid EXIF header found"
            
            # TIFF starts at i+10
            # Byte order: MM (big endian) or II (little endian)
            $byteOrder = "big"
            if ($b[$i+12] -eq 0x49 -and $b[$i+13] -eq 0x49) {
                $byteOrder = "little"
                Write-Host "Byte order: Little Endian (II)"
            } elseif ($b[$i+12] -eq 0x4D -and $b[$i+13] -eq 0x4D) {
                Write-Host "Byte order: Big Endian (MM)"
            }
            
            # Skip 8 bytes (2 byte order + 2 magic + 4 offset to IFD0)
            $ifdOffset = ($b[$i+16] -shl 24) + ($b[$i+17] -shl 16) + ($b[$i+18] -shl 8) + $b[$i+19]
            Write-Host "IFD0 offset from TIFF header: $ifdOffset"
            
            # IFD0 is at i+20+ifdOffset
            $ifd0Location = $i + 20 + $ifdOffset
            Write-Host "IFD0 location: $ifd0Location"
        }
        break
    }
}

# Check if there's video data after XMP
Write-Host ""
Write-Host "Checking between XMP and ftyp..."
# XMP ends around 7513+207=7720
# ftyp is at 3159792
# Check if there's any structure between them

# Let's just check what markers exist in the range 7700-7800
Write-Host "Bytes 7700-7800:"
for ($i = 7700; $i -lt 7800; $i++) {
    Write-Host ([String]::Format("{0:X2} ", $b[$i])) -NoNewline
}
Write-Host ""
