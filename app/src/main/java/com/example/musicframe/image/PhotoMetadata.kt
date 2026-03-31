package com.example.musicframe.image

data class PhotoMetadata(
    val createdDateTime: String?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double? = null,
    val deviceModel: String?,
    val isMotionPhoto: Boolean = false,
    val motionVideoOffset: Long? = null,
    val locationText: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    // 动态照片相关字段
    val isAnimated: Boolean = false,
    val frameCount: Int = 1,
    val duration: Long = 0L, // 总时长（毫秒）
    val animationType: AnimationType? = null
) {
    enum class AnimationType {
        GIF,
        WEBP,
        MOTION_PHOTO
    }
    fun asReadableText(): String {
        val builder = mutableListOf<String>()
        createdDateTime?.let { builder += it }

        if (locationText != null) {
            builder += locationText
        } else {
            val geo = buildGeoText()
            if (geo != null) {
                builder += geo
            }
        }

        deviceModel?.let { builder += it }

        // 动态照片信息
        if (isMotionPhoto) {
            builder += "实况照片"
        } else if (isAnimated) {
            val typeStr = when (animationType) {
                AnimationType.GIF -> "GIF"
                AnimationType.WEBP -> "WebP"
                else -> "动态图片"
            }
            builder += "$typeStr · ${frameCount}帧 · ${duration / 1000.0}s"
        }

        return builder.joinToString(" · ")
    }

    fun buildGeoText(): String? {
        if (latitude == null || longitude == null) {
            return null
        }

        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "E" else "W"

        val coordStr = String.format(
            "%.4f°%s, %.4f°%s",
            Math.abs(latitude),
            latDir,
            Math.abs(longitude),
            lonDir
        )

        val altStr = altitude?.let {
            String.format(" 海拔%.0fm", it)
        } ?: ""

        return coordStr + altStr
    }

    fun hasGpsInfo(): Boolean = latitude != null && longitude != null

    fun hasCameraInfo(): Boolean = focalLength != null || aperture != null || exposureTime != null || iso != null

    fun getCameraInfoText(): String? {
        val parts = mutableListOf<String>()
        focalLength?.let { parts += it }
        aperture?.let { parts += it }
        exposureTime?.let { parts += it }
        iso?.let { parts += "ISO$it" }
        return parts.joinToString("  ").takeIf { it.isNotBlank() }
    }

    private fun Double.format(pattern: String) = String.format(pattern, this)
}
