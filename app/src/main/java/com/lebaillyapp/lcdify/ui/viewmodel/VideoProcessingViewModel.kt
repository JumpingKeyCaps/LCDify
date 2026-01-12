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

/**
 * # ViewModel for GPU Video Processing
 *
 * Responsibilities:
 * - Holds the current shader configuration and processing state
 * - Orchestrates video processing through the VideoRepository
 * - Exposes StateFlow to the UI for reactive updates
 * - Allows runtime updates to shader parameters (scale, dithering, grid, palette)
 * - Provides cancellation and reset mechanisms
 *
 * @param repository VideoRepository instance for delegating video processing tasks
 */
class VideoProcessingViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    // Current processing state exposed to the UI
    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    // Current shader configuration exposed to the UI
    private val _shaderConfig = MutableStateFlow(ShaderConfig())
    val shaderConfig: StateFlow<ShaderConfig> = _shaderConfig.asStateFlow()

    /**
     * ## Process Video
     * Starts the full video processing workflow.
     *
     * Delegates to the repository which returns a Flow emitting real-time
     * processing states. Updates the internal _processingState accordingly.
     *
     * @param videoRes Raw resource ID of the input video
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
     * ## Update Scale Factor
     * Updates the pixelation scale factor of the shader.
     */
    fun updateScaleFactor(scaleFactor: Float) {
        _shaderConfig.value = _shaderConfig.value.copy(scaleFactor = scaleFactor)
    }

    /**
     * ## Update Dithering Strength
     * Updates the dithering strength of the shader.
     */
    fun updateDitheringStrength(strength: Float) {
        _shaderConfig.value = _shaderConfig.value.copy(ditheringStrength = strength)
    }

    /**
     * ## Update Grid Intensity
     * Updates the intensity of the LCD grid effect.
     */
    fun updateGridIntensity(intensity: Float) {
        _shaderConfig.value = _shaderConfig.value.copy(gridIntensity = intensity)
    }

    /**
     * ## Update Grid Size
     * Updates the size of the LCD grid blocks.
     */
    fun updateGridSize(size: Float) {
        _shaderConfig.value = _shaderConfig.value.copy(gridSize = size)
    }

    /**
     * ## Update Palette
     * Updates the shader color palette.
     *
     * @param palette List of colors to be used in the shader palette
     */
    fun updatePalette(palette: List<androidx.compose.ui.graphics.Color>) {
        _shaderConfig.value = _shaderConfig.value.copy(palette = palette)
    }

    /**
     * ## Reset
     * Resets the processing state to Idle.
     */
    fun resetToIdle() {
        _processingState.value = ProcessingState.Idle
    }

    /**
     * ## Cancel Processing
     * Cancels any ongoing video processing and resets the state.
     */
    fun cancelProcessing() {
        repository.cancelProcessing()
        _processingState.value = ProcessingState.Idle
    }
}