package com.example.musicframe.image

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

class PhotoMetadataReader(private val context: Context) {

    private fun logDebug(msg: String) {
        try {
            // 写到 Download 目录，方便用户查看
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val logFile = java.io.File(downloadDir, "music-frame-gps-debug.log")
            logFile.appendText("${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())} - $msg\n")
        } catch (e: Exception) { /* 静默失败 */ }
    }

    fun read(uri: Uri): PhotoMetadata {
            android.util.Log.d("PhotoMetadata", "read: 被调用了，URI = $uri")
        return try {
            // 检测实况照片（需要读取文件内容）
            val motionPhotoInfo = detectMotionPhoto(uri)
            logDebug("read: 实况照片检测结果: isMotionPhoto=${motionPhotoInfo.first}, offset=${motionPhotoInfo.second}")
            
            // 方法1（推荐）：直接从 MediaStore 读取 GPS 坐标，最可靠
            val mediaStoreResult = readFromMediaStore(uri)
            if (mediaStoreResult.latitude != null || mediaStoreResult.longitude != null) {
                logDebug("read: 从MediaStore获取到GPS: ${mediaStoreResult.latitude}, ${mediaStoreResult.longitude}")
                return mediaStoreResult.copy(
                    isMotionPhoto = motionPhotoInfo.first,
                    motionVideoOffset = motionPhotoInfo.second
                )
            }
            
            // 方法2：回退到通过文件路径读取 EXIF
            val filePath = getFilePathFromUri(uri)
            if (filePath != null) {
                // 检查路径是否是临时转换文件（PhotoPicker的临时文件没有GPS）
                val isTempTransformPath = filePath.contains(".transforms") || filePath.contains("synthetic/picker")
                if (isTempTransformPath) {
                    logDebug("read: 检测到临时转换路径，跳过直接读取EXIF")
                } else {
                    val result = readFromExif(File(filePath))
                    logDebug("read: readFromExif结果: lat=${result.latitude}, lon=${result.longitude}, loc=${result.locationText}")
                    if (result.latitude != null || result.longitude != null) {
                        logDebug("read: 从EXIF获取到GPS: ${result.latitude}, ${result.longitude}")
                        return result.copy(
                            isMotionPhoto = motionPhotoInfo.first,
                            motionVideoOffset = motionPhotoInfo.second
                        )
                    }
                }
            }
            
            // 方法3：从 URI 读取 EXIF（创建临时文件）
            val uriResult = readFromUri(uri)
            logDebug("read: readFromUri结果: lat=${uriResult.latitude}, lon=${uriResult.longitude}, loc=${uriResult.locationText}")
            return uriResult.copy(
                isMotionPhoto = motionPhotoInfo.first,
                motionVideoOffset = motionPhotoInfo.second
            )
        } catch (e: Exception) {
            logDebug("read: 异常 = ${e.message}")
            PhotoMetadata(null, null, null, null, null, false, null, null)
        }
    }

    /**
     * 检测是否为实况照片，并返回视频偏移量
     * @return Pair(isMotionPhoto, videoOffset)
     */
    private fun detectMotionPhoto(uri: Uri): Pair<Boolean, Long?> {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return Pair(false, null)
            inputStream.use { stream ->
                // 读取文件头部的 JPEG 数据
                val jpegData = stream.readBytes()
                
                // 方法1: 检测 Samsung/Motion Photo 格式
                // Samsung 实况照片在 JPEG 结束后有 MP4 视频，前面有特定的标记
                val samsungOffset = findSamsungMotionPhotoOffset(jpegData)
                if (samsungOffset != null) {
                    logDebug("detectMotionPhoto: 检测到 Samsung 实况照片, offset=$samsungOffset")
                    return Pair(true, samsungOffset)
                }
                
                // 方法2: 检测 Google Photos Motion Photo (Heif container)
                // Google Photos 的实况照片通常是 HEIF 格式
                val googleOffset = findGoogleMotionPhotoOffset(jpegData)
                if (googleOffset != null) {
                    logDebug("detectMotionPhoto: 检测到 Google Photos 实况照片, offset=$googleOffset")
                    return Pair(true, googleOffset)
                }
                
                // 方法3: 检测 iOS Live Photos (MOV in JPEG)
                val iosOffset = findIOSLivePhotoOffset(jpegData)
                if (iosOffset != null) {
                    logDebug("detectMotionPhoto: 检测到 iOS Live Photos, offset=$iosOffset")
                    return Pair(true, iosOffset)
                }
                
                // 方法4: 通用 Motion Photo 标记检测
                val genericOffset = findGenericMotionPhotoOffset(jpegData)
                if (genericOffset != null) {
                    logDebug("detectMotionPhoto: 检测到通用实况照片, offset=$genericOffset")
                    return Pair(true, genericOffset)
                }
                
                Pair(false, null)
            }
        } catch (e: Exception) {
            logDebug("detectMotionPhoto: 检测异常 = ${e.message}")
            Pair(false, null)
        }
    }

    /**
     * 查找 Samsung Motion Photo 的视频偏移量
     * Samsung 使用 ftyp 标记来标识嵌入的 MP4 视频
     */
    private fun findSamsungMotionPhotoOffset(jpegData: ByteArray): Long? {
        // Samsung: 在 JPEG EOI (0xFF 0xD9) 后紧跟 ftyp atom
        val eoiMarker = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val ftypMarker = "ftyp".toByteArray()
        
        var eoiIndex = -1
        for (i in 0 until jpegData.size - 1) {
            if (jpegData[i] == eoiMarker[0] && jpegData[i + 1] == eoiMarker[1]) {
                eoiIndex = i
                break
            }
        }
        
        if (eoiIndex == -1) return null
        
        // 在 EOI 后查找 ftyp
        for (i in eoiIndex + 2 until jpegData.size - 4) {
            var found = true
            for (j in 0 until 4) {
                if (jpegData[i + j] != ftypMarker[j]) {
                    found = false
                    break
                }
            }
            if (found) {
                return (i - 2).toLong() // 返回 EOI 位置作为偏移量
            }
        }
        
        return null
    }

    /**
     * 查找 Google Photos Motion Photo 的偏移量
     * Google 使用不同的容器结构
     */
    private fun findGoogleMotionPhotoOffset(jpegData: ByteArray): Long? {
        // Google Motion Photo 通常有 MicroVideo 标记
        // 或者检查是否有 ftyp 在文件中间
        
        // 查找文件中第二个 ftyp 位置（第一个是视频容器头，第二个可能是嵌入的）
        val ftypMarker = "ftyp".toByteArray()
        var firstFtyp = -1
        var secondFtyp = -1
        
        for (i in 0 until jpegData.size - 4) {
            var found = true
            for (j in 0 until 4) {
                if (jpegData[i + j] != ftypMarker[j]) {
                    found = false
                    break
                }
            }
            if (found) {
                if (firstFtyp == -1) {
                    firstFtyp = i
                } else {
                    secondFtyp = i
                    break
                }
            }
        }
        
        // 如果找到两个 ftyp，可能是 Motion Photo
        if (firstFtyp > 0 && secondFtyp > firstFtyp) {
            // 返回第一个 ftyp 位置
            return firstFtyp.toLong()
        }
        
        return null
    }

    /**
     * 查找 iOS Live Photos 的偏移量
     * iOS 使用 mov 格式
     */
    private fun findIOSLivePhotoOffset(jpegData: ByteArray): Long? {
        // iOS Live Photos 可能在 JPEG 后有 mov 数据
        // mov 数据通常以 moov atom 开头
        val moovMarker = "moov".toByteArray()
        val mdatMarker = "mdat".toByteArray()
        
        // 查找 moov 或 mdat
        for (i in 10 until jpegData.size - 4) {
            var foundMoov = true
            var foundMdat = true
            for (j in 0 until 4) {
                if (jpegData[i + j] != moovMarker[j]) foundMoov = false
                if (jpegData[i + j] != mdatMarker[j]) foundMdat = false
            }
            if (foundMoov || foundMdat) {
                // 检查前面是否是 size 字段（atom 结构）
                if (i >= 4) {
                    return (i - 4).toLong()
                }
            }
        }
        
        return null
    }

    /**
     * 通用 Motion Photo 检测
     */
    private fun findGenericMotionPhotoOffset(jpegData: ByteArray): Long? {
        // 查找文件末尾附近的 ftyp 或 moov 标记
        // Motion Photo 的视频部分通常在 JPEG 数据之后
        
        val markers = listOf(
            "ftyp".toByteArray(),
            "moov".toByteArray()
        )
        
        // 从文件末尾开始搜索，JPEG 结束标记是 0xFF 0xD9
        val eoiMarker = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        var lastEoi = -1
        
        for (i in 0 until jpegData.size - 1) {
            if (jpegData[i] == eoiMarker[0] && jpegData[i + 1] == eoiMarker[1]) {
                lastEoi = i
            }
        }
        
        if (lastEoi == -1) return null
        
        // 在 EOI 之后查找视频数据
        for (marker in markers) {
            for (i in lastEoi + 2 until jpegData.size - 4) {
                var found = true
                for (j in 0 until 4) {
                    if (jpegData[i + j] != marker[j]) {
                        found = false
                        break
                    }
                }
                if (found) {
                    return (i - 4).toLong() // 返回 atom size 前的位置
                }
            }
        }
        
        return null
    }

    /**
     * 从 MediaStore 直接读取 GPS 位置信息
     * 这是最可靠的方法，直接读取系统保存的位置数据
     */
    private fun readFromMediaStore(uri: Uri): PhotoMetadata {
        return try {
            // 直接用传入的 URI 查询所有列（包括 GPS），兼容 PhotoPicker 的 URI
            val projection = arrayOf(
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.LATITUDE,
                MediaStore.Images.Media.LONGITUDE,
                MediaStore.Images.Media._ID
            )
            
            // 尝试直接查询传入的 URI（PhotoPicker 返回的 URI 也可以这样查询）
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    logDebug("readFromMediaStore: 直接查询 URI 成功，列数 = ${cursor.columnCount}")
                    
                    // 读取拍摄时间
                    val dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    val formattedDate = if (dateTakenIndex >= 0) {
                        val dateTaken = cursor.getLong(dateTakenIndex)
                        if (dateTaken > 0) {
                            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(dateTaken))
                        } else null
                    } else null
                    
                    // 读取 GPS 坐标
                    val latitudeIndex = cursor.getColumnIndex(MediaStore.Images.Media.LATITUDE)
                    val longitudeIndex = cursor.getColumnIndex(MediaStore.Images.Media.LONGITUDE)
                    
                    logDebug("readFromMediaStore: LATITUDE列索引=$latitudeIndex, LONGITUDE列索引=$longitudeIndex")
                    
                    if (latitudeIndex >= 0 && longitudeIndex >= 0) {
                        val latitude = cursor.getDouble(latitudeIndex)
                        val longitude = cursor.getDouble(longitudeIndex)
                        
                        logDebug("readFromMediaStore: GPS = $latitude, $longitude")
                        
                        if (latitude != 0.0 || longitude != 0.0) {
                            val locationText = reverseGeocode(latitude, longitude)
                            logDebug("readFromMediaStore: 获取到有效GPS，准备返回结果")
                            return PhotoMetadata(
                                createdDateTime = formattedDate,
                                latitude = latitude,
                                longitude = longitude,
                                altitude = null,
                                deviceModel = null,
                                isMotionPhoto = false,
                                motionVideoOffset = null,
                                locationText = locationText,
                                focalLength = null,
                                aperture = null,
                                exposureTime = null,
                                iso = null
                            )
                        }
                    }
                    
                    // 如果直接查询没 GPS，尝试通过 ID 查询
                    val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    if (idIndex >= 0) {
                        val mediaId = cursor.getLong(idIndex)
                        logDebug("readFromMediaStore: 通过ID二次查询, id = $mediaId")
                        return queryMediaStoreById(mediaId, formattedDate)
                    }
                }
            }
            
            // 方法2：尝试从 URI 解析出 MediaStore 的 ID，然后查询
            val mediaId = getMediaStoreId(uri)
            if (mediaId != null) {
                logDebug("readFromMediaStore: 解析到MediaStore ID = $mediaId")
                return queryMediaStoreById(mediaId, null)
            }
            
            PhotoMetadata(null, null, null, null, null, false, null, null)
        } catch (e: Exception) {
            logDebug("readFromMediaStore: 异常 = ${e.message}")
            PhotoMetadata(null, null, null, null, null, false, null, null)
        }
    }
    
    /**
     * 从 Uri 解析 MediaStore ID
     */
    private fun getMediaStoreId(uri: Uri): Long? {
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                try {
                    val pathSegments = uri.pathSegments
                    if (pathSegments.size >= 2 && pathSegments[pathSegments.size - 2] == "images") {
                        uri.lastPathSegment?.toLongOrNull()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
    
    /**
     * 通过 MediaStore ID 查询详细信息
     */
    private fun queryMediaStoreById(mediaId: Long, existingDate: String?): PhotoMetadata {
        return try {
            val queryUri = android.content.ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId
            )
            
            val projection = arrayOf(
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.LATITUDE,
                MediaStore.Images.Media.LONGITUDE
            )
            
            context.contentResolver.query(queryUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    // 读取拍摄时间
                    val dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    val formattedDate = if (dateTakenIndex >= 0) {
                        val dateTaken = cursor.getLong(dateTakenIndex)
                        if (dateTaken > 0) {
                            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(dateTaken))
                        } else existingDate
                    } else existingDate
                    
                    // 读取 GPS 坐标
                    val latitudeIndex = cursor.getColumnIndex(MediaStore.Images.Media.LATITUDE)
                    val longitudeIndex = cursor.getColumnIndex(MediaStore.Images.Media.LONGITUDE)
                    
                    logDebug("queryMediaStoreById: LATITUDE列索引=$latitudeIndex, LONGITUDE列索引=$longitudeIndex")
                    
                    if (latitudeIndex >= 0 && longitudeIndex >= 0) {
                        val latitude = cursor.getDouble(latitudeIndex)
                        val longitude = cursor.getDouble(longitudeIndex)
                        
                        logDebug("queryMediaStoreById: GPS = $latitude, $longitude")
                        
                        if (latitude != 0.0 || longitude != 0.0) {
                            val locationText = reverseGeocode(latitude, longitude)
                            logDebug("queryMediaStoreById: 获取到有效GPS，准备返回结果")
                            return PhotoMetadata(
                                createdDateTime = formattedDate,
                                latitude = latitude,
                                longitude = longitude,
                                altitude = null,
                                deviceModel = null,
                                isMotionPhoto = false,
                                motionVideoOffset = null,
                                locationText = locationText,
                                focalLength = null,
                                aperture = null,
                                exposureTime = null,
                                iso = null
                            )
                        }
                    }
                }
            }
            
            logDebug("queryMediaStoreById: 未找到记录")
            PhotoMetadata(null, null, null, null, null, false, null, null).copy(createdDateTime = existingDate)
        } catch (e: Exception) {
            logDebug("queryMediaStoreById: 异常 = ${e.message}")
            PhotoMetadata(null, null, null, null, null, false, null, null).copy(createdDateTime = existingDate)
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                try {
                    // 通过 MediaStore 查询真实文件路径
                    val projection = arrayOf(MediaStore.Images.Media.DATA)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                            if (index >= 0) {
                                val path = cursor.getString(index)
                                logDebug("getFilePathFromUri: MediaStore路径 = $path")
                                path
                            } else {
                                logDebug("getFilePathFromUri: DATA列索引 = -1")
                                null
                            }
                        } else {
                            logDebug("getFilePathFromUri: cursor没有数据")
                            null
                        }
                    }
                } catch (e: Exception) {
                    logDebug("getFilePathFromUri: 异常 = ${e.message}")
                    null
                }
            }
            ContentResolver.SCHEME_FILE -> {
                logDebug("getFilePathFromUri: FILE scheme, path = ${uri.path}")
                uri.path
            }
            else -> {
                logDebug("getFilePathFromUri: 未知scheme = ${uri.scheme}")
                null
            }
        }
    }

    private fun readFromExif(file: File): PhotoMetadata {
        if (!file.exists()) {
            logDebug("readFromExif: 文件不存在: ${file.absolutePath}")
            return PhotoMetadata(null, null, null, null, null, false, null, null)
        }

        return try {
            logDebug("readFromExif: 读取文件 ${file.absolutePath}")
            val exif = ExifInterface(file.absolutePath)
            
            // 读取 GPS 坐标
            val latLong = exif.latLong
            val latitude = latLong?.getOrNull(0)?.toDouble()
            val longitude = latLong?.getOrNull(1)?.toDouble()
            
            logDebug("readFromExif: latLong = ${latLong?.getOrNull(0)}, ${latLong?.getOrNull(1)}")

            // 读取日期
            val dateTaken = runCatching {
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            }.getOrNull()

            val formattedDate = dateTaken?.let { raw ->
                runCatching {
                    val parser = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                    val result = parser.parse(raw) ?: java.util.Date()
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(result)
                }.getOrNull() ?: raw
            }

            // 读取设备信息
            val model = runCatching {
                listOfNotNull(
                    exif.getAttribute(ExifInterface.TAG_MAKE),
                    exif.getAttribute(ExifInterface.TAG_MODEL)
                ).joinToString(" ").ifBlank { null }
            }.getOrNull()

            // 读取相机参数
            val focalLength = runCatching {
                exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.toFloatOrNull()?.let { fl ->
                    "${fl.toInt()} mm"
                }
            }.getOrNull()

            val aperture = runCatching {
                exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.toFloatOrNull()?.let { fNum ->
                    "f/${String.format("%.1f", fNum)}"
                }
            }.getOrNull()

            val exposureTime = runCatching {
                exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toFloatOrNull()?.let { expTime ->
                    if (expTime >= 1f) {
                        "${expTime.toInt()}s"
                    } else {
                        "1/${(1f / expTime).toInt()}s"
                    }
                }
            }.getOrNull()

            val iso = runCatching {
                exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
            }.getOrNull()

            // 反向地理编码
            val locationText = if (latitude != null && longitude != null && 
                !(latitude == 0.0 && longitude == 0.0)) {
                reverseGeocode(latitude, longitude)
            } else null

            PhotoMetadata(
                createdDateTime = formattedDate,
                latitude = latitude,
                longitude = longitude,
                altitude = null,
                deviceModel = model,
                isMotionPhoto = false,
                motionVideoOffset = null,
                locationText = locationText,
                focalLength = focalLength,
                aperture = aperture,
                exposureTime = exposureTime,
                iso = iso
            )
        } catch (e: Exception) {
            logDebug("readFromExif: 异常 = ${e.message}")
            PhotoMetadata(null, null, null, null, null, false, null, null)
        }
    }

    private fun readFromUri(uri: Uri): PhotoMetadata {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // 创建临时文件
                val tempFile = File(context.cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                val result = readFromExif(tempFile)
                tempFile.delete()
                result
            } ?: PhotoMetadata(null, null, null, null, null, false, null, null)
        } catch (e: Exception) {
            logDebug("readFromUri: 异常 = ${e.message}")
            PhotoMetadata(null, null, null, null, null, false, null, null)
        }
    }

    private fun reverseGeocode(latitude: Double, longitude: Double): String? {
        // 简化处理：如果有GPS坐标就返回格式化的地址
        // 不做城市匹配，直接返回GPS坐标的格式化字符串（经测试发现问题后改回）
        val nearestCity = findNearestCity(latitude, longitude)
        return if (nearestCity != null) {
            "中国·${nearestCity.province}·${nearestCity.name}"
        } else {
            // 没有匹配到城市时，也返回GPS坐标字符串（不要返回null，否则不显示）
            val latDir = if (latitude >= 0) "N" else "S"
            val lonDir = if (longitude >= 0) "E" else "W"
            "GPS: ${String.format("%.2f°$latDir", kotlin.math.abs(latitude))}, ${String.format("%.2f°$lonDir", kotlin.math.abs(longitude))}"
        }
    }

    private fun findNearestCity(lat: Double, lon: Double): CityCoord? {
        var nearestCity: CityCoord? = null
        var minDistanceKm = Double.MAX_VALUE

        for (city in CHINESE_CITIES) {
            val distance = haversineDistance(lat, lon, city.lat, city.lon)
            if (distance < minDistanceKm) {
                minDistanceKm = distance
                nearestCity = city
            }
        }

        // 扩大匹配范围到 400km
        return if (minDistanceKm <= 400.0) nearestCity else null
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadius * c
    }

    private data class CityCoord(val name: String, val province: String, val lat: Double, val lon: Double)

    private val CHINESE_CITIES = listOf(
        CityCoord("北京", "北京", 39.9042, 116.4074),
        CityCoord("上海", "上海", 31.2304, 121.4737),
        CityCoord("广州", "广东", 23.1291, 113.2644),
        CityCoord("深圳", "广东", 22.5431, 114.0579),
        CityCoord("杭州", "浙江", 30.2741, 120.1551),
        CityCoord("成都", "四川", 30.5728, 104.0668),
        CityCoord("武汉", "湖北", 30.5928, 114.3055),
        CityCoord("西安", "陕西", 34.3416, 108.9398),
        CityCoord("南京", "江苏", 32.0603, 118.7969),
        CityCoord("重庆", "重庆", 29.4316, 106.9123),
        CityCoord("天津", "天津", 39.3434, 117.3616),
        CityCoord("苏州", "江苏", 31.2990, 120.5853),
        CityCoord("青岛", "山东", 36.0671, 120.3826),
        CityCoord("厦门", "福建", 24.4798, 118.0894),
        CityCoord("长沙", "湖南", 28.2282, 112.9388),
        CityCoord("郑州", "河南", 34.7466, 113.6253),
        CityCoord("济南", "山东", 36.6512, 117.1209),
        CityCoord("沈阳", "辽宁", 41.8057, 123.4315),
        CityCoord("哈尔滨", "黑龙江", 45.8038, 126.5340),
        CityCoord("长春", "吉林", 43.8171, 125.3235),
        CityCoord("石家庄", "河北", 38.0428, 114.5149),
        CityCoord("太原", "山西", 37.8706, 112.5489),
        CityCoord("合肥", "安徽", 31.8206, 117.2272),
        CityCoord("南昌", "江西", 28.6829, 115.8579),
        CityCoord("福州", "福建", 26.0745, 119.2965),
        CityCoord("贵阳", "贵州", 26.6470, 106.6302),
        CityCoord("昆明", "云南", 25.0406, 102.7125),
        CityCoord("南宁", "广西", 22.8170, 108.3665),
        CityCoord("海口", "海南", 20.0444, 110.1999),
        CityCoord("兰州", "甘肃", 36.0611, 103.8343),
        CityCoord("西宁", "青海", 36.6171, 101.7782),
        CityCoord("银川", "宁夏", 38.4872, 106.2309),
        CityCoord("乌鲁木齐", "新疆", 43.8256, 87.6168),
        CityCoord("拉萨", "西藏", 29.6500, 91.1409),
        CityCoord("呼和浩特", "内蒙古", 40.8414, 111.7519),
        CityCoord("大连", "辽宁", 38.9140, 121.6147),
        CityCoord("宁波", "浙江", 29.8683, 121.5440),
        CityCoord("无锡", "江苏", 31.4912, 120.3119),
        CityCoord("佛山", "广东", 23.0218, 113.1219),
        CityCoord("东莞", "广东", 23.0205, 113.7518),
        CityCoord("温州", "浙江", 28.0006, 120.6719),
        CityCoord("珠海", "广东", 22.2719, 113.5767),
        CityCoord("中山", "广东", 22.5170, 113.3926),
        CityCoord("惠州", "广东", 23.1115, 114.4152),
        CityCoord("烟台", "山东", 37.4638, 121.4478),
        CityCoord("泉州", "福建", 24.8740, 118.6757),
        CityCoord("南通", "江苏", 32.0085, 120.8943),
        CityCoord("常州", "江苏", 31.8122, 119.9692),
        CityCoord("嘉兴", "浙江", 30.7467, 120.7508),
        CityCoord("绍兴", "浙江", 30.0003, 120.5820),
        CityCoord("台州", "浙江", 28.6561, 121.4286),
        CityCoord("桂林", "广西", 25.2736, 110.2900),
        CityCoord("三亚", "海南", 18.2528, 109.5117),
        CityCoord("北海", "广西", 21.4733, 109.5117),
        CityCoord("绵阳", "四川", 31.4677, 104.6794),
        CityCoord("德阳", "四川", 31.1270, 104.3979),
        CityCoord("遵义", "贵州", 27.7256, 106.9279),
        CityCoord("大理", "云南", 25.6065, 100.2679),
        CityCoord("丽江", "云南", 26.8721, 100.2299),
        // 安徽城市（你照片在安庆）
        CityCoord("安庆", "安徽", 30.5431, 117.0634),
        CityCoord("芜湖", "安徽", 31.3350, 118.4332),
        CityCoord("蚌埠", "安徽", 32.9167, 117.3888),
        CityCoord("淮南", "安徽", 32.6264, 116.9997),
        CityCoord("马鞍山", "安徽", 31.6686, 118.5076),
        CityCoord("淮北", "安徽", 33.9560, 116.7980),
        CityCoord("铜陵", "安徽", 30.9294, 117.8124),
        CityCoord("黄山", "安徽", 29.7148, 118.3380),
        CityCoord("滁州", "安徽", 32.3017, 118.3274),
        CityCoord("阜阳", "安徽", 32.8899, 115.8141),
        CityCoord("宿州", "安徽", 33.6466, 116.9643),
        CityCoord("六安", "安徽", 31.7348, 116.5080),
        CityCoord("亳州", "安徽", 33.8446, 115.7784),
        CityCoord("池州", "安徽", 30.6644, 117.4916),
        CityCoord("宣城", "安徽", 30.9404, 118.7586)
    )

    companion object {
        fun readLogContent(context: Context): String? {
            return try {
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val logFile = java.io.File(downloadDir, "music-frame-gps-debug.log")
                if (logFile.exists()) {
                    logFile.readText()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        fun copyLogToDownloads(context: Context): File? {
            return try {
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val logFile = java.io.File(downloadDir, "music-frame-gps-debug.log")
                if (logFile.exists()) {
                    logFile
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}