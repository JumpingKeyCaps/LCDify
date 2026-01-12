package com.lebaillyapp.lcdify.domain.legacy

import android.graphics.Bitmap

/**
 * ## **Legacy / CPU-side video frame** - DO NOT USE THIS !
 *
 * Represents a single video frame as a Bitmap.
 *
 * Previously used in CPU-based decoding workflows for:
 * - Previewing frames
 * - Offline processing
 * - Frame-by-frame analysis
 *
 * In the current GPU zero-copy pipeline, frames are never
 * materialized as Bitmaps on the CPU, so this class is now unused.
 *
 * @property bitmap Bitmap of the frame
 * @property timestampUs Presentation timestamp in microseconds
 * @property frameIndex Index of the frame in the sequence
 */
data class VideoFrame(
    val bitmap: Bitmap,
    val timestampUs: Long,
    val frameIndex: Int
)