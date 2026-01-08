package com.lebaillyapp.lcdify.data.service

import android.content.Context
import android.net.Uri
import androidx.annotation.RawRes
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
 * Service de traitement vidéo V2 basé sur GPU (Android 13+)
 * Orchestrateur du pipeline GpuVideoPipeline.
 */
class VideoProcessingServiceV2(private val context: Context) {

    @Volatile
    private var isCancelled = false

    /**
     * Lance le traitement et expose un Flow d'états pour l'UI
     */
    fun processVideo(
        @RawRes videoRes: Int,
        shaderSource: String,
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

            // On bascule sur Dispatchers.Default pour laisser le thread UI respirer
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
     * Permet une annulation manuelle via le bouton "Annuler" de ton UI
     */
    fun cancel() {
        isCancelled = true
    }

    private fun createOutputFile(): File {
        // Movies/LCDify/ est un bon choix pour la visibilité utilisateur
        val moviesDir = context.getExternalFilesDir("Movies") ?: context.filesDir
        val lcdifyDir = File(moviesDir, "LCDify")
        if (!lcdifyDir.exists()) lcdifyDir.mkdirs()

        val fileName = "lcdify_${System.currentTimeMillis()}.mp4"
        return File(lcdifyDir, fileName)
    }
}