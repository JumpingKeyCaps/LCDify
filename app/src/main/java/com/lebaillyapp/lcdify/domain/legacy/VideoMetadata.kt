package com.lebaillyapp.lcdify.domain.legacy

/**
 * ## **Legacy / CPU-side video metadata** - DO NOT USE THIS ! (Removed in favor of MediaExtractor in the new pipeline)
 *
 * This was used in a previous CPU-based pipeline for storing
 * video width, height, frame count, and duration.
 *
 * In the current GPU zero-copy pipeline, this class is no longer used,
 * as metadata is directly obtained via MediaExtractor/MediaFormat.
 *
 * @property width Video width in pixels
 * @property height Video height in pixels
 * @property durationUs Video duration in microseconds
 * @property frameRate Frames per second
 * @property totalFrames Total number of frames
 *
 * @property durationMs Duration in milliseconds (convenience)
 */
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