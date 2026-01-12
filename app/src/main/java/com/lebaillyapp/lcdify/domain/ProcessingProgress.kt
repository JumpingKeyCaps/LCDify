package com.lebaillyapp.lcdify.domain

/**
 *
 * # Represents the progress of a video processing task.
 *
 * @property currentFrame The index of the frame that has just been processed.
 * @property totalFrames Total number of frames in the video.
 * @property elapsedTimeMs Time elapsed since the start of processing in milliseconds.
 *
 * Provides computed properties for convenience:
 * - [percentage]: progress as a value between 0 and 100
 * - [eta]: estimated remaining time in milliseconds based on average frame processing time
 */
data class ProcessingProgress(
    val currentFrame: Int,
    val totalFrames: Int,
    val elapsedTimeMs: Long
) {
    /**
     * Returns the processing progress as a percentage (0-100).
     * Returns 0 if totalFrames is zero to avoid division by zero.
     */
    val percentage: Float
        get() = if (totalFrames > 0) (currentFrame.toFloat() / totalFrames) * 100f else 0f
    /**
     * Returns the estimated time remaining (in milliseconds) to finish processing.
     *
     * Calculated as average time per frame multiplied by remaining frames.
     * Returns 0 if no frames have been processed yet.
     */
    val eta: Long
        get() {
            if (currentFrame == 0) return 0L
            val avgTimePerFrame = elapsedTimeMs / currentFrame
            return avgTimePerFrame * (totalFrames - currentFrame)
        }
}