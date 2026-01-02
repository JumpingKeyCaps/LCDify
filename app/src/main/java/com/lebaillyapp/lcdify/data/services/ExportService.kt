package com.lebaillyapp.lcdify.data.services

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.lebaillyapp.lcdify.ui.effects.RetroBoyEffect
import java.io.File

@OptIn(UnstableApi::class)
class ExportService(private val context: Context) {

    private var transformer: Transformer? = null

    fun startExport(
        inputUri: Uri,
        outputFile: File,
        effect: RetroBoyEffect,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onSuccess()
                }
                override fun onError(composition: Composition, exportResult: ExportResult, e: ExportException) {
                    onError(e)
                }
            }).build()

        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
            .setEffects(Effects(listOf(), listOf(effect)))
            .build()

        transformer?.start(editedMediaItem, outputFile.absolutePath)
    }
}