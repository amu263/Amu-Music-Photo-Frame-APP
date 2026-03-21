package com.example.musicframe.image

data class PhotoMetadata(
    val createdDateTime: String?,
    val latitude: Double?,
    val longitude: Double?,
    val deviceModel: String?,
    val isMotionPhoto: Boolean = false,
    val motionVideoOffset: Long? = null
) {
    fun asReadableText(): String {
        val builder = mutableListOf<String>()
        createdDateTime?.let { builder += it }
        val geo = listOfNotNull(latitude?.format("%.4f"), longitude?.format("%.4f"))
        if (geo.isNotEmpty()) {
            builder += "坐标: ${geo.joinToString(", ")}"
        }
        deviceModel?.let { builder += it }
        if (isMotionPhoto) {
            builder += "实况照片"
        }
        return builder.joinToString(" · ")
    }

    private fun Double.format(pattern: String) = String.format(pattern, this)
}
