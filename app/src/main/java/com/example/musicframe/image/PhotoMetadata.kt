package com.example.musicframe.image

data class PhotoMetadata(
    val createdDateTime: String?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double? = null,
    val deviceModel: String?,
    val isMotionPhoto: Boolean = false,
    val motionVideoOffset: Long? = null,
    val locationText: String? = null
) {
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
        if (isMotionPhoto) {
            builder += "实况照片"
        }
        return builder.joinToString(" · ")
    }

    fun buildGeoText(): String? {
        if (latitude == null || longitude == null) {
            return null
        }
        
        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "E" else "W"
        
        val coordStr = String.format("%.4f°%s, %.4f°%s", 
            Math.abs(latitude), latDir,
            Math.abs(longitude), lonDir)
        
        val altStr = altitude?.let { 
            String.format(" 海拔%.0fm", it)
        } ?: ""
        
        return coordStr + altStr
    }

    fun hasGpsInfo(): Boolean = latitude != null && longitude != null

    private fun Double.format(pattern: String) = String.format(pattern, this)
}