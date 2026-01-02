package com.lebaillyapp.lcdify.models

sealed class ExportState {
    object Idle : ExportState()
    data class Processing(val progress: Float) : ExportState() // 0.0 to 1.0
    data class Success(val filePath: String) : ExportState()
    data class Error(val message: String) : ExportState()
}