package com.lebaillyapp.lcdify.ui.screen

import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lebaillyapp.lcdify.domain.ProcessingProgress
import com.lebaillyapp.lcdify.domain.ProcessingState
import com.lebaillyapp.lcdify.domain.ShaderConfig
import com.lebaillyapp.lcdify.ui.viewmodel.VideoProcessingViewModel

@Composable
fun VideoProcessingScreen(
    viewModel: VideoProcessingViewModel,
    @RawRes videoRes: Int,
    modifier: Modifier = Modifier
) {
    val processingState by viewModel.processingState.collectAsState()
    val shaderConfig by viewModel.shaderConfig.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Header
        Text(
            text = "LCDify Video Processor",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // État principal
        when (val state = processingState) {
            is ProcessingState.Idle -> {
                IdleState(
                    onStartProcessing = { viewModel.processVideo(videoRes) }
                )
            }

            is ProcessingState.Decoding -> {
                ProcessingCard(
                    title = "Décodage vidéo",
                    progress = state.progress,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            is ProcessingState.Processing -> {
                ProcessingCard(
                    title = "Application du shader LCD",
                    progress = state.progress,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            is ProcessingState.Encoding -> {
                ProcessingCard(
                    title = "Encodage vidéo finale",
                    progress = state.progress,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            is ProcessingState.Success -> {
                SuccessState(
                    outputUri = state.outputUri.toString(),
                    duration = state.duration,
                    onReset = { viewModel.cancelProcessing() }
                )
            }

            is ProcessingState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = { viewModel.processVideo(videoRes) }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Configuration panel (visible uniquement en Idle)
        AnimatedVisibility(visible = processingState is ProcessingState.Idle) {
            ConfigurationPanel(
                config = shaderConfig,
                onScaleFactorChange = { viewModel.updateScaleFactor(it) },
                onDitheringChange = { viewModel.updateDitheringStrength(it) },
                onGridIntensityChange = { viewModel.updateGridIntensity(it) },
                onGridSizeChange = { viewModel.updateGridSize(it) }
            )
        }
    }
}

@Composable
private fun IdleState(
    onStartProcessing: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Prêt à traiter la vidéo",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Configure les paramètres ci-dessous puis appuie sur Démarrer",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onStartProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Démarrer le traitement")
            }
        }
    }
}

@Composable
private fun ProcessingCard(
    title: String,
    progress: ProcessingProgress,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = color
            )

            LinearProgressIndicator(
                progress = { progress.percentage / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = color
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${progress.currentFrame} / ${progress.totalFrames} frames",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "${progress.percentage.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }

            if (progress.eta > 0) {
                Text(
                    text = "Temps restant: ${formatTime(progress.eta)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SuccessState(
    outputUri: String,
    duration: Long,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Traitement terminé !",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "Durée: ${formatTime(duration)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Fichier sauvegardé:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = outputUri,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )

            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Nouveau traitement")
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Text(
                text = "Erreur",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Réessayer")
            }
        }
    }
}

@Composable
private fun ConfigurationPanel(
    config: ShaderConfig,
    onScaleFactorChange: (Float) -> Unit,
    onDitheringChange: (Float) -> Unit,
    onGridIntensityChange: (Float) -> Unit,
    onGridSizeChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.titleMedium
            )

            HorizontalDivider()

            ConfigSlider(
                label = "Pixel Size",
                value = config.scaleFactor,
                range = 4f..48f,
                onValueChange = onScaleFactorChange
            )

            ConfigSlider(
                label = "Dithering",
                value = config.ditheringStrength,
                range = 0f..1f,
                isPercentage = true,
                onValueChange = onDitheringChange
            )

            ConfigSlider(
                label = "Grid Opacity",
                value = config.gridIntensity,
                range = 0f..1f,
                isPercentage = true,
                onValueChange = onGridIntensityChange
            )

            ConfigSlider(
                label = "Grid Size",
                value = config.gridSize,
                range = 0.9f..3f,
                onValueChange = onGridSizeChange
            )
        }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isPercentage: Boolean = false,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = if (isPercentage) "${(value * 100).toInt()}%" else "%.1f".format(value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

private fun formatTime(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> String.format("%dh %dm", hours, minutes % 60)
        minutes > 0 -> String.format("%dm %ds", minutes, seconds % 60)
        else -> String.format("%ds", seconds)
    }
}