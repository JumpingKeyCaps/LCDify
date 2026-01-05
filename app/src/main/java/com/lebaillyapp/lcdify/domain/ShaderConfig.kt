package com.lebaillyapp.lcdify.domain

import androidx.compose.ui.graphics.Color

data class ShaderConfig(
    val scaleFactor: Float = 16f,
    val ditheringStrength: Float = 0.05f,
    val gridIntensity: Float = 0.8f,
    val gridSize: Float = 2f,
    val palette: List<Color> = PALETTE_GAMEBOY_CLASSIC
) {
    companion object {
        val PALETTE_GAMEBOY_CLASSIC = listOf(
            Color(0xFF0F381F), // Darkest
            Color(0xFF306230), // Dark
            Color(0xFF7BAC7D), // Light
            Color(0xFFAED9AE)  // Lightest
        )

        val PALETTE_GAMEBOY_POCKET = listOf(
            Color(0xFF1E3D1F),
            Color(0xFF4E6B2F),
            Color(0xFF9DBA3A),
            Color(0xFFD6E86A)
        )
    }
}