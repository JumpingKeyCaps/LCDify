package com.lebaillyapp.lcdify.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lebaillyapp.lcdify.R


@Composable
fun LCDifyFirstStep(drawableId: Int) {
    val context = LocalContext.current

    val shaderSource = remember { context.readRawResource(R.raw.retroboy_shader_opti_ds) }
    val shader = remember(shaderSource) { RuntimeShader(shaderSource) }
    val bitmap = remember(drawableId) { BitmapFactory.decodeResource(context.resources, drawableId) }
    val bitmapShader = remember(bitmap) {
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    // --- ÉTATS DES RÉGLAGES ---
    var scaleFactor by remember { mutableFloatStateOf(6f) }
    var ditheringStrength by remember { mutableFloatStateOf(0.05f) }
    var gridIntensity by remember { mutableFloatStateOf(0.80f) } // L'opacité de la grille
    var gridSize by remember { mutableFloatStateOf(2.0f) }      // La taille de la maille

    // --- ÉTATS DE LA PALETTE ---
    var palette by remember {
        mutableStateOf(listOf(
            Color(0xFF1E3D1F), // darkest (vert très sombre mais pas noir)
            Color(0xFF4E6B2F), // dark mid olive
            Color(0xFF9DBA3A), // light mid jaune-vert dominant
            Color(0xFFD6E86A)  // lightest jaune LCD
        ))
    }

    var selectedToneIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .drawWithCache {
                    shader.setFloatUniform("res", size.width, size.height)
                    shader.setFloatUniform("imgRes", bitmap.width.toFloat(), bitmap.height.toFloat())
                    shader.setInputBuffer("inputFrame", bitmapShader)

                    shader.setFloatUniform("scaleFactor", scaleFactor)
                    shader.setFloatUniform("ditheringStrength", ditheringStrength)
                    shader.setFloatUniform("gridIntensity", gridIntensity)
                    shader.setFloatUniform("gridSize", gridSize)

                    palette.forEachIndexed { index, color ->
                        shader.setFloatUniform("palette$index", color.red, color.green, color.blue, color.alpha)
                    }

                    onDrawWithContent { drawRect(ShaderBrush(shader)) }
                }
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            // Scrollable au cas où les sliders dépassent sur petit écran
            Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {

                Text("Tone Editor (Select a box):", style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    palette.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, RoundedCornerShape(8.dp))
                                .border(
                                    width = if (selectedToneIndex == index) 3.dp else 1.dp,
                                    color = if (selectedToneIndex == index) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedToneIndex = index }
                        )
                    }
                }

                // Slider de Teinte pour la couleur sélectionnée
                val activeColor = palette[selectedToneIndex]
                SettingSlider(
                    label = "Selected Tone Hue",
                    value = getHue(activeColor),
                    range = 0f..360f,
                    onValueChange = { newHue ->
                        val newColor = Color.hsv(newHue, 0.6f, getLuma(activeColor))
                        val newList = palette.toMutableList()
                        newList[selectedToneIndex] = newColor
                        palette = newList
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // --- RÉGLAGES DU SHADER ---
                SettingSlider(
                    label = "Pixel Size",
                    value = scaleFactor,
                    range = 2f..48f,
                    onValueChange = { scaleFactor = it }
                )

                SettingSlider(
                    label = "Dithering",
                    value = ditheringStrength,
                    range = 0f..1f,
                    isPercentage = true,
                    onValueChange = { ditheringStrength = it }
                )

                // LE VOICI : Slider pour l'opacité de la grille
                SettingSlider(
                    label = "Grid Opacity",
                    value = gridIntensity,
                    range = 0f..1f,
                    isPercentage = true,
                    onValueChange = { gridIntensity = it }
                )

                SettingSlider(
                    label = "Grid Mesh Size",
                    value = gridSize,
                    range = 0.90f..1.0f,
                    onValueChange = { gridSize = it }
                )
            }
        }
    }
}

@Composable
fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isPercentage: Boolean = false,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        val displayValue = if (isPercentage) "${(value * 100).toInt()}%" else value.toString()
        Text(text = "$label: $displayValue", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Helpers pour extraire la teinte et la luminosité (Approximation simple)
fun getHue(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
        hsv
    )
    return hsv[0]
}

fun getLuma(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
        hsv
    )
    return hsv[2]
}

fun Context.readRawResource(@RawRes resId: Int): String {
    return resources.openRawResource(resId).bufferedReader().use { it.readText() }
}