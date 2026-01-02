package com.lebaillyapp.lcdify.data.repositories

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(private val context: Context) {

    fun createTempFile(): File {
        val storageDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        return File(storageDir, "export_${System.currentTimeMillis()}.mp4")
    }

    suspend fun saveToGallery(tempFile: File): Uri? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "LCDify_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LCDify")
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let { targetUri ->
            resolver.openOutputStream(targetUri)?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            tempFile.delete()
        }
        uri
    }
}