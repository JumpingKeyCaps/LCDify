package com.lebaillyapp.lcdify.domain

import android.graphics.Bitmap

data class VideoFrame(
    val bitmap: Bitmap,
    val timestampUs: Long,
    val frameIndex: Int
)