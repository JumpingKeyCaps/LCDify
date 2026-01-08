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
 * Service de traitement vidéo V2 basé sur GPU
 *
 * Cette version remplace l'ancien VideoProcessingService qui utilisait un Canvas offscreen CPU.
 * Problématique de l'ancien service :
 *  - Dessiner un RuntimeShader GPU sur un Canvas CPU force le rendu logiciel.
 *  - Certaines configurations échouent ou sont extrêmement lentes.
 *  - Le pipeline CPU → Bitmap → shader CPU n'est pas fiable pour un vrai rendu GPU.
 *
 * Nouvelle approche :
 *  - Utilisation de GpuVideoPipeline, qui applique le shader directement sur une Surface GPU.
 *  - Pas de Canvas CPU intermédiaire : le transfert CPU → GPU est géré correctement.
 *  - Compatible avec RuntimeShader et tous les shaders AGSL modernes.
 *  - Plus performant et fiable sur tous les appareils.
 *
 * Pipeline simplifié :
 * 1. Extraction métadonnées vidéo (MediaExtractor/MediaMetadataRetriever)
 * 2. Décodage frame par frame sur GPU
 * 3. Application du shader sur la Surface GPU via GpuVideoPipeline
 * 4. Encodage H.264 (MediaCodec) et muxing final (MediaMuxer)
 *
 * Les états de progression sont émis via Flow (ProcessingState) et cancelable à tout moment.
 */


class VideoProcessingServiceV2(private val context: Context) {

    @Volatile
    private var isCancelled = false

    /**
     * Point d'entrée principal - Retourne un Flow qui émet les états
     */
    fun processVideo(@RawRes videoRes: Int, config: ShaderConfig): Flow<ProcessingState> = callbackFlow {
        isCancelled = false
        val startTime = System.currentTimeMillis()

        try {
            // Crée le fichier de sortie
            val outputFile = createOutputFile()

            // Instancie le pipeline GPU
            val pipeline = GpuVideoPipeline(
                context = context,
                videoRes = videoRes,
                outputFile = outputFile,
                onProgress = { currentFrame, totalFrames ->
                    // Safe depuis n'importe quel thread
                    val progress = ProcessingProgress(
                        currentFrame = currentFrame,
                        totalFrames = totalFrames,
                        elapsedTimeMs = System.currentTimeMillis() - startTime
                    )
                    trySend(ProcessingState.Processing(progress))
                },
                isCancelled = { isCancelled }
            )

            // Lancer le pipeline sur Default dispatcher
            withContext(Dispatchers.Default) {
                pipeline.process()
            }

            if (isCancelled) {
                outputFile.delete()
                trySend(ProcessingState.Error("Processing cancelled by user"))
            } else {
                val totalTime = System.currentTimeMillis() - startTime
                trySend(ProcessingState.Success(Uri.fromFile(outputFile), totalTime))
            }

        } catch (e: Exception) {
            trySend(ProcessingState.Error(message = e.message ?: "Unknown error", exception = e))
        }

        awaitClose { /* rien à nettoyer ici, le pipeline gère son release */ }
    }

    /**
     * Annule le traitement en cours
     */
    fun cancel() {
        isCancelled = true
    }

    /**
     * Crée le fichier de sortie dans Movies/LCDify/
     */
    private fun createOutputFile(): File {
        val moviesDir = context.getExternalFilesDir("Movies") ?: context.filesDir
        val lcdifyDir = File(moviesDir, "LCDify")
        if (!lcdifyDir.exists()) lcdifyDir.mkdirs()
        val fileName = "lcdify_${System.currentTimeMillis()}.mp4"
        return File(lcdifyDir, fileName)
    }
}
