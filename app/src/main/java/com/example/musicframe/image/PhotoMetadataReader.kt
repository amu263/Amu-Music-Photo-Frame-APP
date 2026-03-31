package com.example.musicframe.image

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoMetadataReader(private val context: Context) {

    fun read(uri: Uri): PhotoMetadata {
        // 首先检测是否为动态图片
        val animatedInfo = detectAnimatedImage(uri)
        
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            parse(stream, animatedInfo)
        } ?: PhotoMetadata(
            createdDateTime = null,
            latitude = null,
            longitude = null,
            altitude = null,
            deviceModel = null,
            isMotionPhoto = false,
            motionVideoOffset = null,
            locationText = null,
            focalLength = null,
            aperture = null,
            exposureTime = null,
            iso = null,
            isAnimated = animatedInfo?.isAnimated ?: false,
            frameCount = animatedInfo?.frameCount ?: 1,
            duration = animatedInfo?.duration ?: 0L,
            animationType = animatedInfo?.type
        )
    }
    
    /**
     * 检测图片是否为动态图片（GIF/Animated WebP）
     */
    private fun detectAnimatedImage(uri: Uri): AnimatedImageInfo? {
        return try {
            val mimeType = context.contentResolver.getType(uri)
            val isGif = mimeType == "image/gif"
            val isWebp = mimeType == "image/webp"
            
            if (!isGif && !isWebp) {
                return null
            }
            
            // 简单检测：GIF 或 WebP 都视为可能动态图片
            // 详细的帧数和时长信息在导出时再解析
            AnimatedImageInfo(
                isAnimated = true,
                frameCount = 1,  // 暂时设为 1，后续在 FrameComposer 中详细解析
                duration = 0L,
                type = if (isGif) PhotoMetadata.AnimationType.GIF 
                       else PhotoMetadata.AnimationType.WEBP
            )
        } catch (e: Exception) {
            // 检测失败，返回 null
            null
        }
    }
    
    private data class AnimatedImageInfo(
        val isAnimated: Boolean,
        val frameCount: Int,
        val duration: Long,
        val type: PhotoMetadata.AnimationType
    )

    private fun parse(stream: InputStream, animatedInfo: AnimatedImageInfo? = null): PhotoMetadata {
        val tempFile = File(context.cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
        try {
            tempFile.outputStream().use { output ->
                stream.copyTo(output, bufferSize = 1024 * 1024)
            }
            
            val exif = runCatching { ExifInterface(tempFile.absolutePath) }.getOrNull()
            
            if (exif != null) {
                var latitude: Double? = null
                var longitude: Double? = null
                var date: String? = null
                var model: String? = null
                var isMotionPhoto = false
                var motionOffset: Long? = null
                
                // 读取 GPS - 只使用最可靠的 getLatLong() 方法
                val latLong = runCatching { exif.latLong }.getOrNull()
                if (latLong != null) {
                    latitude = latLong[0].toDouble()
                    longitude = latLong[1].toDouble()
                }
                
                // 读取日期
                date = runCatching {
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                }.getOrNull()
                
                // 读取设备信息
                model = runCatching {
                    listOfNotNull(
                        exif.getAttribute(ExifInterface.TAG_MAKE),
                        exif.getAttribute(ExifInterface.TAG_MODEL)
                    ).joinToString(" ").ifBlank { null }
                }.getOrNull()
                
                // Motion Photo 检测
                try {
                    isMotionPhoto = MOTION_PHOTO_FLAG_TAGS.any { tag ->
                        exif.getAttributeIntCompat(tag, 0) == 1
                    }
                    motionOffset = MOTION_PHOTO_OFFSET_TAGS
                        .firstNotNullOfOrNull { tag ->
                            exif.getAttributeLongCompat(tag, 0L).takeIf { it > 0L }
                        }
                } catch (e: Exception) {
                    // 忽略 Motion Photo 检测失败
                }
                
                // 读取海拔高度
                val altitude = runCatching {
                    exif.getAltitude(Double.NaN).takeIf { !it.isNaN() }
                }.getOrNull()
                
                // 反向地理编码
                val locationText = if (latitude != null && longitude != null && 
                    !(latitude == 0.0 && longitude == 0.0)) {
                    runCatching { reverseGeocode(latitude!!, longitude!!) }.getOrNull()
                } else null

                // 格式化日期
                val formattedDate = date?.let { raw ->
                    runCatching {
                        val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                        val result = parser.parse(raw) ?: Date()
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(result)
                    }.getOrNull() ?: raw
                }
                
                // 读取相机参数
                val focalLength = runCatching {
                    exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.toFloatOrNull()?.let { fl ->
                        val focalMM = fl.toInt()
                        "$focalMM mm"
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

                return PhotoMetadata(
                    createdDateTime = formattedDate,
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitude,
                    deviceModel = model,
                    isMotionPhoto = isMotionPhoto,
                    motionVideoOffset = motionOffset,
                    locationText = locationText,
                    focalLength = focalLength,
                    aperture = aperture,
                    exposureTime = exposureTime,
                    iso = iso,
                    isAnimated = animatedInfo?.isAnimated ?: false,
                    frameCount = animatedInfo?.frameCount ?: 1,
                    duration = animatedInfo?.duration ?: 0L,
                    animationType = animatedInfo?.type
                )
            }
            
            // 无 EXIF 信息时，至少返回动态图片信息
            return PhotoMetadata(
                createdDateTime = null,
                latitude = null,
                longitude = null,
                altitude = null,
                deviceModel = null,
                isMotionPhoto = false,
                motionVideoOffset = null,
                locationText = null,
                focalLength = null,
                aperture = null,
                exposureTime = null,
                iso = null,
                isAnimated = animatedInfo?.isAnimated ?: false,
                frameCount = animatedInfo?.frameCount ?: 1,
                duration = animatedInfo?.duration ?: 0L,
                animationType = animatedInfo?.type
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun reverseGeocode(latitude: Double, longitude: Double): String? {
        val nearestCity = findNearestCity(latitude, longitude)
        return if (nearestCity != null) {
            "中国 · $nearestCity"
        } else {
            val latDir = if (latitude >= 0) "N" else "S"
            val lonDir = if (longitude >= 0) "E" else "W"
            "GPS: ${String.format("%.4f°$latDir", Math.abs(latitude))}, ${String.format("%.4f°$lonDir", Math.abs(longitude))}"
        }
    }

    private fun findNearestCity(lat: Double, lon: Double): String? {
        var nearestCity: String? = null
        var minDistanceKm = Double.MAX_VALUE

        for (city in CHINESE_CITIES) {
            val distance = haversineDistance(lat, lon, city.lat, city.lon)
            if (distance < minDistanceKm) {
                minDistanceKm = distance
                nearestCity = city.name
            }
        }

        return if (minDistanceKm <= 100.0) nearestCity else null
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

    private fun ExifInterface.getAttributeIntCompat(tag: String, defaultValue: Int): Int {
        return runCatching { getAttributeInt(tag, defaultValue) }.getOrDefault(defaultValue)
    }

    private fun ExifInterface.getAttributeLongCompat(tag: String, defaultValue: Long): Long {
        return runCatching {
            getAttribute(tag)?.trim()?.toLongOrNull() ?: defaultValue
        }.getOrDefault(defaultValue)
    }

    companion object {
        private data class CityCoord(val name: String, val lat: Double, val lon: Double)
        
        private val CHINESE_CITIES = listOf(
            CityCoord("北京", 39.9042, 116.4074),
            CityCoord("上海", 31.2304, 121.4737),
            CityCoord("广州", 23.1291, 113.2644),
            CityCoord("深圳", 22.5431, 114.0579),
            CityCoord("杭州", 30.2741, 120.1551),
            CityCoord("成都", 30.5728, 104.0668),
            CityCoord("武汉", 30.5928, 114.3055),
            CityCoord("西安", 34.3416, 108.9398),
            CityCoord("南京", 32.0603, 118.7969),
            CityCoord("重庆", 29.4316, 106.9123),
            CityCoord("天津", 39.3434, 117.3616),
            CityCoord("苏州", 31.2990, 120.5853),
            CityCoord("青岛", 36.0671, 120.3826),
            CityCoord("厦门", 24.4798, 118.0894),
            CityCoord("长沙", 28.2282, 112.9388),
            CityCoord("郑州", 34.7466, 113.6253),
            CityCoord("济南", 36.6512, 117.1209),
            CityCoord("沈阳", 41.8057, 123.4315),
            CityCoord("哈尔滨", 45.8038, 126.5340),
            CityCoord("长春", 43.8171, 125.3235),
            CityCoord("石家庄", 38.0428, 114.5149),
            CityCoord("太原", 37.8706, 112.5489),
            CityCoord("合肥", 31.8206, 117.2272),
            CityCoord("南昌", 28.6829, 115.8579),
            CityCoord("福州", 26.0745, 119.2965),
            CityCoord("贵阳", 26.6470, 106.6302),
            CityCoord("昆明", 25.0406, 102.7125),
            CityCoord("南宁", 22.8170, 108.3665),
            CityCoord("海口", 20.0444, 110.1999),
            CityCoord("兰州", 36.0611, 103.8343),
            CityCoord("西宁", 36.6171, 101.7782),
            CityCoord("银川", 38.4872, 106.2309),
            CityCoord("乌鲁木齐", 43.8256, 87.6168),
            CityCoord("拉萨", 29.6500, 91.1409),
            CityCoord("呼和浩特", 40.8414, 111.7519),
            CityCoord("大连", 38.9140, 121.6147),
            CityCoord("宁波", 29.8683, 121.5440),
            CityCoord("无锡", 31.4912, 120.3119),
            CityCoord("佛山", 23.0218, 113.1219),
            CityCoord("东莞", 23.0205, 113.7518),
            CityCoord("温州", 28.0006, 120.6719),
            CityCoord("珠海", 22.2719, 113.5767),
            CityCoord("中山", 22.5170, 113.3926),
            CityCoord("惠州", 23.1115, 114.4152),
            CityCoord("烟台", 37.4638, 121.4478),
            CityCoord("泉州", 24.8740, 118.6757),
            CityCoord("南通", 32.0085, 120.8943),
            CityCoord("常州", 31.8122, 119.9692),
            CityCoord("嘉兴", 30.7467, 120.7508),
            CityCoord("绍兴", 30.0003, 120.5820),
            CityCoord("台州", 28.6561, 121.4286),
            CityCoord("桂林", 25.2736, 110.2900),
            CityCoord("三亚", 18.2528, 109.5117),
            CityCoord("北海", 21.4733, 109.5117),
            CityCoord("绵阳", 31.4677, 104.6794),
            CityCoord("德阳", 31.1270, 104.3979),
            CityCoord("遵义", 27.7256, 106.9279),
            CityCoord("大理", 25.6065, 100.2679),
            CityCoord("丽江", 26.8721, 100.2299)
        )

        private val MOTION_PHOTO_FLAG_TAGS = listOf(
            "MotionPhoto",
            "GCamera:MotionPhoto",
            "MicroVideo"
        )

        private val MOTION_PHOTO_OFFSET_TAGS = listOf(
            "GCamera:VideoOffset",
            "GCamera:MicroVideoOffset",
            "MicroVideoOffset",
            "MotionPhotoVideoOffset"
        )

        fun getLogFile(context: Context): File? {
            val publicFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "music-frame-debug.log"
            )
            if (publicFile.exists() || publicFile.parentFile?.exists() == true || publicFile.parentFile?.mkdirs() == true) {
                return publicFile
            }
            return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                File(it, "music-frame-debug.log")
            }
        }

        fun readLogContent(context: Context): String? {
            return try {
                getLogFile(context)?.takeIf { it.exists() }?.readText()
            } catch (e: Exception) {
                null
            }
        }

        fun copyLogToDownloads(context: Context): File? {
            return try {
                val sourceFile = getLogFile(context) ?: return null
                if (!sourceFile.exists()) return null
                val destFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "music-frame-debug.log"
                )
                if (destFile.absolutePath == sourceFile.absolutePath) {
                    return destFile
                }
                destFile.parentFile?.mkdirs()
                sourceFile.copyTo(destFile, overwrite = true)
                destFile
            } catch (e: Exception) {
                null
            }
        }
    }
}
