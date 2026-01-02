package com.lebaillyapp.lcdify.models

import androidx.compose.ui.graphics.Color

data class RetroSettings(
    val scaleFactor: Float = 16f,
    val ditheringStrength: Float = 0.25f,
    val gridIntensity: Float = 0.20f,
    val gridSize: Float = 2.0f,
    val palette: List<Color> = listOf(
        Color(0xFF0F381F), Color(0xFF306230),
        Color(0xFF7BAC7D), Color(0xFFAED9AE)
    )
)