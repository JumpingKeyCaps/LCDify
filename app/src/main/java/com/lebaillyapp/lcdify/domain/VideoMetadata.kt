package com.lebaillyapp.lcdify.domain

data class VideoMetadata(
    val width: Int,
    val height: Int,
    val durationUs: Long,
    val frameRate: Int,
    val totalFrames: Int
) {
    val durationMs: Long
        get() = durationUs / 1000
}