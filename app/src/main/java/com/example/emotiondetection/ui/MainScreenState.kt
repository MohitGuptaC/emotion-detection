package com.example.emotiondetection.ui

import android.graphics.Bitmap



/**
 * Data class representing the UI state of the main screen
 */
data class MainScreenState(
    val detectedImage: Bitmap? = null,
    val lastResult: String = "",
    val lastConfidence: Float? = null,
    val isLoading: Boolean = false
)

/**
 * Events that can be triggered from the UI
 */
sealed class MainScreenEvent {
    data object CaptureImage : MainScreenEvent()
    data object SelectFromGallery : MainScreenEvent()
    data object ResetDetection : MainScreenEvent()
}
