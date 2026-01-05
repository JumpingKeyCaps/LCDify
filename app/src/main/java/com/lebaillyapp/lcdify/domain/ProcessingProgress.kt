package com.lebaillyapp.lcdify.domain

data class ProcessingProgress(
    val currentFrame: Int,
    val totalFrames: Int,
    val elapsedTimeMs: Long
) {
    val percentage: Float
        get() = if (totalFrames > 0) (currentFrame.toFloat() / totalFrames) * 100f else 0f

    val eta: Long
        get() {
            if (currentFrame == 0) return 0L
            val avgTimePerFrame = elapsedTimeMs / currentFrame
            return avgTimePerFrame * (totalFrames - currentFrame)
        }
}