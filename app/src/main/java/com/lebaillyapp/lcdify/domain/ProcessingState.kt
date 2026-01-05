package com.lebaillyapp.lcdify.domain

import android.net.Uri

sealed interface ProcessingState {
    data object Idle : ProcessingState
    data class Decoding(val progress: ProcessingProgress) : ProcessingState
    data class Processing(val progress: ProcessingProgress) : ProcessingState
    data class Encoding(val progress: ProcessingProgress) : ProcessingState
    data class Success(val outputUri: Uri, val duration: Long) : ProcessingState
    data class Error(val message: String, val exception: Throwable? = null) : ProcessingState
}