package com.example.emotiondetection.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.emotiondetection.domain.model.EmotionDetectionResult
import com.example.emotiondetection.domain.repository.EmotionDetectionRepository
import kotlinx.coroutines.launch

class MainViewModel(
    private val emotionDetectionRepository: EmotionDetectionRepository = EmotionDetectionRepository()
) : ViewModel() {

    var state by mutableStateOf(MainScreenState())
        private set

    companion object {
        private const val TAG = "MainViewModel"
    }    // Helper methods to reduce redundancy
    private fun updateStateWithError(message: String, exception: Exception? = null) {
        if (exception != null) {
            Log.e(TAG, "ERROR: $message", exception)
        }
        state = state.copy(
            lastResult = if (exception != null) "ERROR: $message - ${exception.message}" else message,
            isLoading = false,
            lastConfidence = null
        )
    }

    private fun updateStateWithLoading(message: String) {
        state = state.copy(
            lastResult = message,
            isLoading = true,
            lastConfidence = null
        )
    }    private fun updateStateWithSuccess(result: String, confidence: Float? = null, detectedImage: Bitmap? = null) {
        state = state.copy(
            lastResult = result,
            lastConfidence = confidence,
            isLoading = false,
            detectedImage = detectedImage ?: state.detectedImage
        )
    }

    private fun safeBitmapRecycle(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }    fun onEvent(event: MainScreenEvent) {
        when (event) {
            is MainScreenEvent.ResetDetection -> resetDetection()
            is MainScreenEvent.CaptureImage,
            is MainScreenEvent.SelectFromGallery -> {} // Handled in Activity
        }
    }fun handleImageResult(bitmap: Bitmap?, context: Context) {
        // Recycle current bitmap
        safeBitmapRecycle(state.detectedImage)
        
        updateStateWithLoading("Processing image...")
        
        viewModelScope.launch {
            val result = emotionDetectionRepository.processImage(bitmap, context)
            
            when (result) {
                is EmotionDetectionResult.Success -> {
                    updateStateWithSuccess(
                        result = result.emotion,
                        confidence = result.confidence,
                        detectedImage = result.visualizationBitmap
                    )
                }
                is EmotionDetectionResult.Error -> {
                    updateStateWithError(result.message, result.exception)
                }
                is EmotionDetectionResult.Loading -> {
                    updateStateWithLoading(result.message)
                }
                is EmotionDetectionResult.NoFacesDetected -> {
                    updateStateWithSuccess("No faces detected", detectedImage = result.originalBitmap)
                }
            }
        }
    }    private fun resetDetection() {
        safeBitmapRecycle(state.detectedImage)
        state = state.copy(
            detectedImage = null,
            lastResult = "",
            lastConfidence = null,
            isLoading = false
        )
    }    override fun onCleared() {
        super.onCleared()
        safeBitmapRecycle(state.detectedImage)
        emotionDetectionRepository.cleanup()
    }
}