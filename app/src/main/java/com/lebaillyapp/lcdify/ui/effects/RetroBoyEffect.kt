package com.lebaillyapp.lcdify.ui.effects

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.OptIn
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.lebaillyapp.lcdify.models.RetroSettings

@OptIn(UnstableApi::class)
class RetroBoyEffect(
    private val shaderSource: String,
    private val settings: RetroSettings
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return RetroBoyShaderProgram(useHdr, shaderSource, settings)
    }
}

@OptIn(UnstableApi::class)
private class RetroBoyShaderProgram(
    useHdr: Boolean,
    shaderSource: String,
    private val settings: RetroSettings
) : BaseGlShaderProgram(useHdr, 1) {

    private val runtimeShader = RuntimeShader(shaderSource)
    private val paint = Paint()

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        // Injection des réglages (fixés au début)
        runtimeShader.setFloatUniform("res", inputWidth.toFloat(), inputHeight.toFloat())
        runtimeShader.setFloatUniform("imgRes", inputWidth.toFloat(), inputHeight.toFloat())
        runtimeShader.setFloatUniform("scaleFactor", settings.scaleFactor)
        runtimeShader.setFloatUniform("ditheringStrength", settings.ditheringStrength)
        runtimeShader.setFloatUniform("gridIntensity", settings.gridIntensity)
        runtimeShader.setFloatUniform("gridSize", settings.gridSize)

        settings.palette.forEachIndexed { i, color ->
            runtimeShader.setFloatUniform("palette$i", color.red, color.green, color.blue, color.alpha)
        }

        paint.shader = runtimeShader
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        // Pour AGSL en manuel, on n'utilise pas drawFrame de cette façon
        // MAIS comme BaseGlShaderProgram demande une implémentation, on la laisse.
        // C'est l'étape 4 (le Transformer) qui va orchestrer l'application du shader.
    }
}