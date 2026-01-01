package com.lebaillyapp.lcdify.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lebaillyapp.lcdify.ui.theme.LCDifyTheme


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.constraintlayout.compose.ConstraintLayout
import kotlin.math.roundToInt
import kotlin.math.sqrt


@Composable
fun ThemeDemoScreen() {


    var scale by remember { mutableStateOf(16f) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(0.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {

            // --- LCD AREA WRAPPER (Le cadre de la vitre) ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = Color(0xFFBCBCB4), // Gris légèrement plus foncé que le corps
                shape = RoundedCornerShape(24.dp), // Bords très arrondis pour le style Game Boy
                tonalElevation = 4.dp,
                shadowElevation = 0.8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp) // Padding entre la vitre et l'écran
                ) {
                    // L'écran proprement dit (où sera ton shader)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.1f), // Ratio proche du mockup
                        color = Color(0xFF9CA08F), // Vert LCD
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.Black.copy(0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "LCD PREVIEW",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Barre d'infos sous l'écran (Status + Timer)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "READY!",
                                color = Color(0xFF8B2E4E), // Le bordeaux
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "DEMO_VIDEO.MP4",
                                color = Color.DarkGray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Timer
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "00:00:",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.DarkGray
                            )
                            Text(
                                text = "00",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFF8B2E4E) // Millisecondes en bordeaux
                            )
                        }
                    }
                }
            }



            Spacer(modifier = Modifier.height(5.dp))

            //   Slider (scale)
            Box(modifier = Modifier.fillMaxWidth().padding(start = 55.dp, end = 55.dp)) {
                Slider(
                    value = scale,
                    onValueChange = {scale = it},
                    valueRange = 4f..48f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }


            Spacer(modifier = Modifier.height(1.dp))

            // --- CONTROLS SECTION (L'élément clé) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .align(Alignment.CenterHorizontally)
                ,
                horizontalArrangement = Arrangement.spacedBy(space = 66.dp, alignment = Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically

            ) {
                // 1. Joystick (Placeholder pour le moment)
                RetroJoystick(
                    onDirectionTriggered = { direction ->
                        println("Action déclenchée : $direction")
                    }
                )

                // 2. Play / Stop Buttons
                GameBoyButtonsRow(modifier = Modifier.size(width = 120.dp, height = 160.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))


            // --- Buttons (export / reset) ---
            Row(
                modifier = Modifier.padding(start = 16.dp,end = 16.dp).align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameBoySettingButton(
                    label = "IMPORT",
                    onClick = {}
                )

                GameBoySettingButton(
                    label = "RESET",
                    onClick = {}
                )

                GameBoySettingButton(
                    label = "EXPORT",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = {}
                )
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun DemoPreview() {
    LCDifyTheme {
        ThemeDemoScreen()
    }
}


//Button play
@Composable
fun GameBoyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .padding(2.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.secondary.copy(0.40f)),
            onClick = onClick
        ) {
            // AJOUT DU REFLET SEULEMENT
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(0.1f), Color.Transparent),
                            center = Offset(35f, 35f), // Reflet haut-gauche
                            radius = 60f
                        )
                    )
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF3D4D77)
        )
    }
}
//Button stop
@Composable
fun GameBoySecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.size(56.dp).padding(2.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondary,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(2.dp, Color.Black.copy(0.10f)),
            onClick = onClick
        ) {
            // AJOUT DU REFLET SEULEMENT
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(0.1f), Color.Transparent),
                            center = Offset(35f, 35f),
                            radius = 60f
                        )
                    )
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF3D4D77)
        )
    }
}



//Special positionement des boutons
@Composable
fun GameBoyButtonsRow(modifier: Modifier) {
    ConstraintLayout(
        modifier = modifier
    ) {
        val (play, stop) = createRefs()

        // Bouton PLAY (A)
        GameBoyButton(
            label = "PLAY",
            onClick = {},
            modifier = Modifier.constrainAs(play) {
                start.linkTo(parent.start, margin = 1.dp)
                bottom.linkTo(parent.bottom, margin = 10.dp)
            }
        )

        // Bouton STOP (B)
        GameBoySecondaryButton(
            label = "STOP",
            onClick = {},
            modifier = Modifier.constrainAs(stop) {
                start.linkTo(play.end, margin = 15.dp) // décalage horizontal
                bottom.linkTo(play.bottom, margin = 40.dp)   // décalage vertical subtil
            }
        )
    }
}







//Button settings (import/reset/export)
@Composable
fun GameBoySettingButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.secondary

) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .height(20.dp)
                .width(48.dp)
            ,
            shape = RoundedCornerShape(40.dp),
            color = color,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(2.dp, Color.Black.copy(0.10f)),
            onClick = onClick
        ) {}

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF3D4D77)
        )
    }
}


//Scale slider vertical
@Composable
fun VerticalSliderWrapper(
    content: @Composable () -> Unit
) {
    Layout(
        content = content
    ) { measurables, constraints ->
        // On mesure le Slider avec des contraintes inversées
        val placeable = measurables.first().measure(
            constraints.copy(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth
            )
        )

        // On définit la taille du composant (Inversée !)
        layout(placeable.height, placeable.width) {
            placeable.placeWithLayer(
                x = -(placeable.width / 2 - placeable.height / 2),
                y = -(placeable.height / 2 - placeable.width / 2),
                layerBlock = {
                    rotationZ = -90f
                }
            )
        }
    }
}


//joystick
@Composable
fun RetroJoystick(
    modifier: Modifier = Modifier,
    onDirectionTriggered: (String) -> Unit
) {
    val density = LocalDensity.current
    // --- RÉGLAGE DE LA DISTANCE MAX ICI ---
    val maxDistanceDp = 45.dp
    val radius = with(density) { maxDistanceDp.toPx() }

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val animatedX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "x"
    )
    val animatedY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "y"
    )

    Box(
        modifier = modifier.size(170.dp), // Légèrement plus grand pour les flèches
        contentAlignment = Alignment.Center
    ) {
        // --- LABELS + FLÈCHES ---
        // Haut (Palette)
        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PALETTE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3D4D77))
            JoystickArrow(direction = "UP")
        }
        // Bas (Restart)
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            JoystickArrow(direction = "DOWN")
            Text("RESTART", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3D4D77))
        }
        // Gauche (BWD)
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Text("BWD", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3D4D77))
            JoystickArrow(direction = "LEFT")
        }
        // Droite (FWD)
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            JoystickArrow(direction = "RIGHT")
            Text("FWD", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3D4D77))
        }

        // --- BACKRING ---
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(Brush.radialGradient(listOf(Color.Black.copy(0.2f), Color.Transparent)), CircleShape)
                .border(2.dp, Color.Black.copy(0.05f), CircleShape)
        )

        // --- THE STICK ---
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
                .size(95.dp)
                .shadow(elevation = 8.dp, shape = CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF444444), Color(0xFF111111))), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // Seuil de déclenchement (si on a poussé à plus de 40% de la distance max)
                            val triggerThreshold = radius * 0.4f
                            when {
                                offsetY < -triggerThreshold -> onDirectionTriggered("PALETTE")
                                offsetY > triggerThreshold -> onDirectionTriggered("RESTART")
                                offsetX < -triggerThreshold -> onDirectionTriggered("BWD")
                                offsetX > triggerThreshold -> onDirectionTriggered("FWD")
                            }
                            offsetX = 0f
                            offsetY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = offsetX + dragAmount.x
                            val newY = offsetY + dragAmount.y
                            val distance = sqrt(newX * newX + newY * newY)

                            if (distance <= radius) {
                                offsetX = newX
                                offsetY = newY
                            } else {
                                val ratio = radius / distance
                                offsetX = newX * ratio
                                offsetY = newY * ratio
                            }
                        }
                    )
                }
        ) {
            // Reflet
            Box(Modifier.fillMaxSize().padding(8.dp).background(
                Brush.radialGradient(listOf(Color.White.copy(0.15f), Color.Transparent), center = Offset(40f, 40f)), CircleShape
            ))
        }
    }
}

@Composable
fun JoystickArrow(direction: String) {
    val color = Color(0xFF8B2E4E) // Le bordeaux de tes boutons
    val rotation = when(direction) {
        "UP" -> 0f
        "DOWN" -> 180f
        "LEFT" -> -90f
        "RIGHT" -> 90f
        else -> 0f
    }

    // Un simple petit triangle dessiné en Canvas
    Canvas(modifier = Modifier.size(12.dp).padding(2.dp).graphicsLayer { rotationZ = rotation }) {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color = color)
    }
}


