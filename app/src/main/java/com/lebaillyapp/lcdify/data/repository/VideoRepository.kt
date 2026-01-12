package com.lebaillyapp.lcdify.data.repository

import android.content.Context
import androidx.annotation.RawRes
import com.lebaillyapp.lcdify.data.service.VideoProcessingServiceV2
import com.lebaillyapp.lcdify.domain.ProcessingState
import com.lebaillyapp.lcdify.domain.ShaderConfig
import kotlinx.coroutines.flow.Flow

/**
 * # Video Repository
 *
 * Acts as a simple pass-through layer between the ViewModel and
 * the GPU video processing service (`VideoProcessingServiceV2`).
 *
 * Responsibilities:
 * - Delegates video processing requests to the service
 * - Exposes a Flow of `ProcessingState` to the ViewModel
 * - Provides a cancellation method for ongoing processing
 *
 * Note:
 * - This class does not implement any business logic or transformations.
 * - It is solely responsible for orchestrating calls between the ViewModel and service layer.
 *
 * @param context Android context required to initialize the service
 */
class VideoRepository(
    context: Context
) {
    private val videoProcessingService = VideoProcessingServiceV2(context)

    /**
     * ## Process Video
     * Starts the full video processing workflow.
     *
     * Delegates to `VideoProcessingServiceV2.processVideo` and returns
     * a Flow emitting real-time processing states for the UI.
     *
     * @param videoRes Raw resource ID of the input video
     * @param config Shader configuration for the GPU pipeline
     * @return Flow of `ProcessingState` reflecting progress, success, or error
     */
    fun processVideo(
        @RawRes videoRes: Int,
        config: ShaderConfig
    ): Flow<ProcessingState> {
        return videoProcessingService.processVideo(videoRes, config)
    }

    /**
     * ## Cancel Processing
     * Cancels the ongoing video processing, if any.
     *
     * This method can be called from the ViewModel or UI layer
     * to stop the pipeline mid-execution.
     */
    fun cancelProcessing() {
        videoProcessingService.cancel()
    }
}