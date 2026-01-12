package com.lebaillyapp.lcdify.domain

import android.net.Uri

/**
 * # Represents the different states of a video processing workflow.
 *
 * This sealed interface allows the UI or other layers to react
 * to the current stage of the pipeline in real-time.
 */
sealed interface ProcessingState {
    /**
     * Initial idle state before any processing has started.
     */
    data object Idle : ProcessingState

    /**
     * Video decoding is in progress.
     *
     * @property progress Current progress information (frame count, percentage, ETA)
     */
    data class Decoding(val progress: ProcessingProgress) : ProcessingState

    /**
     * Shader processing / GPU rendering is in progress.
     *
     * @property progress Current progress information (frame count, percentage, ETA)
     */
    data class Processing(val progress: ProcessingProgress) : ProcessingState

    /**
     * Video encoding is in progress (writing frames to H.264 / MP4).
     *
     * @property progress Current progress information (frame count, percentage, ETA)
     */
    data class Encoding(val progress: ProcessingProgress) : ProcessingState

    /**
     * Video processing completed successfully.
     *
     * @property outputUri Uri pointing to the output video file
     * @property duration Total processing time in milliseconds
     */
    data class Success(val outputUri: Uri, val duration: Long) : ProcessingState

    /**
     * An error occurred during processing.
     *
     * @property message Description of the error
     * @property exception Optional exception object for debugging
     */
    data class Error(val message: String, val exception: Throwable? = null) : ProcessingState
}