package com.lebaillyapp.lcdify.data.repository

import android.content.Context
import android.net.Uri
import androidx.annotation.RawRes
import com.lebaillyapp.lcdify.data.service.VideoProcessingService
import com.lebaillyapp.lcdify.domain.ProcessingState
import com.lebaillyapp.lcdify.domain.ShaderConfig
import kotlinx.coroutines.flow.Flow

/**
 * Simple passe-plat entre ViewModel et Service
 * Pas d'interface, pas de logique métier, juste de l'orchestration
 */
class VideoRepository(
    context: Context
) {
    private val videoProcessingService = VideoProcessingService(context)

    /**
     * Lance le traitement complet de la vidéo
     * Retourne un Flow qui émet les états en temps réel
     */
    fun processVideo(
        @RawRes videoRes: Int,
        config: ShaderConfig
    ): Flow<ProcessingState> {
        return videoProcessingService.processVideo(videoRes, config)
    }

    /**
     * Annule le traitement en cours (si besoin)
     */
    fun cancelProcessing() {
        videoProcessingService.cancel()
    }
}