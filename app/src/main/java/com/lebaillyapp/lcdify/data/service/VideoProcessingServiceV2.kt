package com.lebaillyapp.lcdify.data.service

import android.content.Context
import android.net.Uri
import androidx.annotation.RawRes
import com.lebaillyapp.lcdify.R
import com.lebaillyapp.lcdify.domain.ProcessingProgress
import com.lebaillyapp.lcdify.domain.ProcessingState
import com.lebaillyapp.lcdify.domain.ShaderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * # High-Level Video Processing Service (V2)
 *
 * This service orchestrates the GPU video processing pipeline (`GpuVideoPipeline`) and
 * exposes the processing status as a Kotlin Flow to the UI layer.
 *
 * Key Features:
 * - Asynchronous video processing on a background thread (Dispatchers.Default)
 * - Real-time progress reporting through a Flow of `ProcessingState`
 * - Supports manual cancellation from the UI
 * - Automatically manages output file creation and cleanup in case of cancellation
 * - Integrates with `ShaderConfig` to pass dynamic shader parameters to the GPU pipeline
 *
 * @property context Android context used for resource access and file storage.
 */
class VideoProcessingServiceV2(private val context: Context) {

    @Volatile
    private var isCancelled = false

    /**
     * ## AGSL Shader Source
     * Loads the AGSL shader source lazily from raw resources.
     */
    private val shaderSource: String by lazy {
        context.resources.openRawResource(R.raw.retroboy_shader_video)
            .bufferedReader()
            .use { it.readText() }
    }

    /**
     * ## Process Video
     * Starts video processing and emits a Flow of `ProcessingState` for UI consumption.
     *
     * This function creates the GPU pipeline, executes it on a background thread,
     * and reports progress, success, or errors.
     *
     * @param videoRes Raw resource ID of the input video.
     * @param config Shader configuration (palette, scale factor, grid, dithering).
     * @return Flow of `ProcessingState` reflecting pipeline progress and completion.
     */
    fun processVideo(
        @RawRes videoRes: Int,
        config: ShaderConfig
    ): Flow<ProcessingState> = callbackFlow {
        isCancelled = false
        val startTime = System.currentTimeMillis()

        try {
            val outputFile = createOutputFile()

            // Initialisation du pipeline avec la config de l'utilisateur
            val pipeline = GpuVideoPipeline(
                context = context,
                videoRes = videoRes,
                outputFile = outputFile,
                shaderSource = shaderSource,
                config = config, // Injection de la config (palette, scale, etc.)
                onProgress = { currentFrame, totalFrames ->
                    val progress = ProcessingProgress(
                        currentFrame = currentFrame,
                        totalFrames = totalFrames,
                        elapsedTimeMs = System.currentTimeMillis() - startTime
                    )
                    // Utilisation de trySend pour ne pas bloquer le thread GPU
                    trySend(ProcessingState.Processing(progress))
                },
                isCancelled = { isCancelled }
            )

            // On bascule sur Dispatchers.Default pour laisser le thread UI respirer (Attention Dispatchers Default peu potentielement creer de la friction ! a revoir dans le futur)
            withContext(Dispatchers.Default) {
                pipeline.process()
            }

            if (isCancelled) {
                if (outputFile.exists()) outputFile.delete()
                trySend(ProcessingState.Error("Processing cancelled by user"))
            } else {
                val totalTime = System.currentTimeMillis() - startTime
                trySend(ProcessingState.Success(Uri.fromFile(outputFile), totalTime))
            }

        } catch (e: Exception) {
            // Log l'erreur ici si besoin
            trySend(ProcessingState.Error(
                message = e.message ?: "Unknown pipeline error",
                exception = e
            ))
        }

        // Si le Flow est collecté via collectAsState ou similaire en Compose,
        // awaitClose assure que l'annulation remonte bien jusqu'au pipeline.
        awaitClose {
            isCancelled = true
        }
    }

    /**
     * ## Cancel Processing
     * Cancels the ongoing video processing.
     *
     * This method can be called from the UI, e.g., when a user presses a cancel button.
     */
    fun cancel() {
        isCancelled = true
    }

    /**
     * ## Create Output File
     * Creates a unique output file for the processed video.
     *
     * Directory: Movies/LCDify for user visibility
     * File name: "lcdify_<timestamp>.mp4"
     *
     * @return File object representing the output video.
     */
    private fun createOutputFile(): File {
        // Movies/LCDify/ est un bon choix pour la visibilité utilisateur
        val moviesDir = context.getExternalFilesDir("Movies") ?: context.filesDir
        val lcdifyDir = File(moviesDir, "LCDify")
        if (!lcdifyDir.exists()) lcdifyDir.mkdirs()

        val fileName = "lcdify_${System.currentTimeMillis()}.mp4"
        return File(lcdifyDir, fileName)
    }
}