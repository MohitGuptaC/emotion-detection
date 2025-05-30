package com.example.emotiondetection.domain.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.emotiondetection.data.ml.TensorFlowModelManager
import com.example.emotiondetection.data.processing.ImageProcessor
import com.example.emotiondetection.data.vision.FaceProcessor
import com.example.emotiondetection.domain.model.EmotionDetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository that orchestrates the emotion detection pipeline
 */
class EmotionDetectionRepository(
    private val modelManager: TensorFlowModelManager = TensorFlowModelManager(),
    private val faceProcessor: FaceProcessor = FaceProcessor(),
    private val imageProcessor: ImageProcessor = ImageProcessor()
) {
    
    companion object {
        private const val TAG = "EmotionDetectionRepository"
    }
    
    suspend fun processImage(bitmap: Bitmap?, context: Context): EmotionDetectionResult {
        return withContext(Dispatchers.Default) {
            try {
                if (bitmap == null) {
                    return@withContext EmotionDetectionResult.Error("Unable to capture image")
                }

                // Load model if not done
                if (!modelManager.isModelLoaded()) {
                    if (!modelManager.loadModel(context)) {
                        return@withContext EmotionDetectionResult.Error("Cannot load model")
                    }
                }

                // Detect faces
                val faces = faceProcessor.detectFaces(bitmap)
                if (faces.isEmpty()) {
                    Log.w(TAG, "No faces detected in image")
                    return@withContext EmotionDetectionResult.NoFacesDetected(bitmap)
                }

                Log.d(TAG, "Found ${faces.size} face(s)")

                // Extract largest face
                val faceBitmap = faceProcessor.extractLargestFace(bitmap, faces)
                if (faceBitmap == null) {
                    return@withContext EmotionDetectionResult.Error("Failed to extract face")
                }

                // Preprocess face for model
                val processedFaceBitmap = imageProcessor.preprocessForModel(faceBitmap)
                if (processedFaceBitmap == null) {
                    safeBitmapRecycle(faceBitmap)
                    return@withContext EmotionDetectionResult.Error("Face preprocessing failed")
                }

                // Create visualization bitmap
                val visualizationBitmap = faceProcessor.createVisualizationBitmap(bitmap, faces)

                // Convert to model input format
                val inputBuffer = imageProcessor.bitmapToByteBuffer(processedFaceBitmap)
                if (inputBuffer == null) {
                    safeBitmapRecycle(faceBitmap)
                    safeBitmapRecycle(processedFaceBitmap)
                    return@withContext EmotionDetectionResult.Error("Failed to convert face image to model input format")
                }

                // Run inference
                val rawOutput = modelManager.runInference(inputBuffer)
                if (rawOutput == null) {
                    safeBitmapRecycle(faceBitmap)
                    safeBitmapRecycle(processedFaceBitmap)
                    return@withContext EmotionDetectionResult.Error("Model inference failed")
                }

                // Process results
                val softmaxOutput = imageProcessor.applySoftmax(rawOutput)
                val maxIndex = softmaxOutput.indices.maxByOrNull { softmaxOutput[it] } ?: 0
                val confidence = softmaxOutput[maxIndex].coerceIn(0f, 1f)
                val detectedEmotion = modelManager.getEmotionLabels()[maxIndex]

                // Cleanup
                safeBitmapRecycle(faceBitmap)
                if (processedFaceBitmap != faceBitmap) {
                    safeBitmapRecycle(processedFaceBitmap)
                }

                EmotionDetectionResult.Success(detectedEmotion, confidence, visualizationBitmap)

            } catch (e: Exception) {
                Log.e(TAG, "Error in processImage", e)
                EmotionDetectionResult.Error("Error processing image", e)
            }
        }
    }
      private fun safeBitmapRecycle(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
    
    fun cleanup() {
        modelManager.close()
        faceProcessor.close()
    }
}
