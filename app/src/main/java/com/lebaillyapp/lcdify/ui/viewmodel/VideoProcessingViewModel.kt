package com.lebaillyapp.lcdify.ui.viewmodel

import androidx.annotation.RawRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lebaillyapp.lcdify.data.repository.VideoRepository
import com.lebaillyapp.lcdify.domain.ProcessingState
import com.lebaillyapp.lcdify.domain.ShaderConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoProcessingViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _shaderConfig = MutableStateFlow(ShaderConfig())
    val shaderConfig: StateFlow<ShaderConfig> = _shaderConfig.asStateFlow()

    /**
     * Lance le traitement complet de la vidéo
     * Le repository retourne un Flow qui émet les états en temps réel
     */
    fun processVideo(@RawRes videoRes: Int) {
        viewModelScope.launch {
            repository.processVideo(
                videoRes = videoRes,
                config = _shaderConfig.value
            ).collect { state ->
                _processingState.value = state
            }
        }
    }

    /**
     * Met à jour le scale factor
     */
    fun updateScaleFactor(scaleFactor: Float) {
        _shaderConfig.value = _shaderConfig.value.copy(scaleFactor = scaleFactor)
    }

    /**
     * Met à jour le dithering strength
     */
    fun updateDitheringStrength(strength: Float) {
        _shaderConfig.value = _shaderConfig.value.copy(ditheringStrength = strength)
    }

    /**
     * Met à jour l'intensité de la grille
     */
    fun updateGridIntensity(intensity: Float) {
        _shaderConfig.value = _shaderConfig.value.copy(gridIntensity = intensity)
    }

    /**
     * Met à jour la taille de la grille
     */
    fun updateGridSize(size: Float) {
        _shaderConfig.value = _shaderConfig.value.copy(gridSize = size)
    }

    /**
     * Change la palette
     */
    fun updatePalette(palette: List<androidx.compose.ui.graphics.Color>) {
        _shaderConfig.value = _shaderConfig.value.copy(palette = palette)
    }

    /**
     * Reset vers idle
     */
    fun resetToIdle() {
        _processingState.value = ProcessingState.Idle
    }

    /**
     * Annule le traitement en cours
     */
    fun cancelProcessing() {
        repository.cancelProcessing()
        _processingState.value = ProcessingState.Idle
    }
}