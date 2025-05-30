package com.example.emotiondetection.data.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer

/**
 * Manages TensorFlow Lite model loading and inference
 */
class TensorFlowModelManager {
    
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    
    companion object {
        private const val TAG = "TensorFlowModelManager"
        private const val MODEL_FILE = "metadata.tflite"
        private val EMOTION_LABELS = arrayOf(
            "Neutral", "Happiness", "Surprise", "Sadness",
            "Anger", "Disgust", "Fear", "Contempt"
        )
    }
    
    fun loadModel(context: Context): Boolean {
        return try {
            Log.d(TAG, "=== LOADING MODEL ===")

            // Load model
            val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
            val inputStream = java.io.FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY, 
                startOffset, 
                declaredLength
            )            
            // Create interpreter with GPU delegate if available
            val options = Interpreter.Options()
            options.setNumThreads(4)
              // Check if GPU delegate is available and compatible
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                Log.d(TAG, "GPU delegate is supported on this device")
                try {
                    val delegateOptions = compatList.bestOptionsForThisDevice
                    gpuDelegate = GpuDelegate(delegateOptions)
                    options.addDelegate(gpuDelegate)
                    Log.d(TAG, "GPU delegate added successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to add GPU delegate, falling back to CPU: ${e.message}")
                    try {
                        gpuDelegate?.close()
                    } catch (closeException: Exception) {
                        Log.w(TAG, "Error closing failed GPU delegate: ${closeException.message}")
                    }
                    gpuDelegate = null
                }
            } else {
                Log.d(TAG, "GPU delegate not supported on this device, using CPU")
            }
            
            interpreter = Interpreter(modelBuffer, options)            // Verify model configuration
            val inputTensor = interpreter!!.getInputTensor(0)
            val inputShape = inputTensor.shape()
            val inputDataType = inputTensor.dataType()
            Log.d(TAG, "Model loaded successfully:")
            Log.d(TAG, "  Input shape: ${inputShape.contentToString()}")
            Log.d(TAG, "  Input data type: $inputDataType")
            Log.d(TAG, "  Labels: ${EMOTION_LABELS.contentToString()}")

            // Get output tensor info
            val outputTensor = interpreter!!.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            val outputDataType = outputTensor.dataType()

            Log.d(TAG, "Output tensor:")
            Log.d(TAG, "  Shape: ${outputShape.contentToString()}")
            Log.d(TAG, "  Data type: $outputDataType")
            Log.d(TAG, "  Expected classes: ${EMOTION_LABELS.size}")            
            Log.d(TAG, "=== MODEL LOADING COMPLETE ===")

            // Warm up the model with a dummy inference to reduce first-run latency
            warmUpModel()

            true

        } catch (e: Exception) {
            Log.e(TAG, "ERROR loading model: ${e.message}", e)
            false
        }
    }    fun runInference(inputBuffer: ByteBuffer): FloatArray? {
        return try {
            if (interpreter == null) {
                Log.e(TAG, "ERROR: Interpreter not initialized")
                return null
            }
            
            // Additional health check for consecutive calls
            if (!isInterpreterHealthy()) {
                Log.e(TAG, "ERROR: Interpreter is not in healthy state")
                return null
            }

            // Ensure buffer is rewound and in correct position
            inputBuffer.rewind()

            // Prepare output
            val outputArray = Array(1) { FloatArray(EMOTION_LABELS.size) }

            Log.d(TAG, "Running emotion inference...")
            val startTime = System.currentTimeMillis()

            interpreter!!.run(inputBuffer, outputArray)

            val inferenceTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Emotion inference completed in ${inferenceTime}ms")

            outputArray[0]

        } catch (e: Exception) {
            Log.e(TAG, "ERROR during inference: ${e.message}", e)
              // If GPU delegate causes issues, try to recover by falling back to CPU
            if (gpuDelegate != null && e.message?.contains("GPU", ignoreCase = true) == true) {
                Log.w(TAG, "GPU inference failed, attempting to recover with CPU-only mode")
                try {
                    // Close current interpreter and delegate
                    interpreter?.close()
                    gpuDelegate?.close()
                    gpuDelegate = null
                    interpreter = null
                    
                    Log.w(TAG, "Interpreter reset due to GPU issues. Model needs to be reloaded.")
                } catch (recoveryException: Exception) {
                    Log.e(TAG, "Failed to recover from GPU error: ${recoveryException.message}")
                }
            }
            
            null
        }
    }    fun getEmotionLabels(): Array<String> = EMOTION_LABELS
    
    fun isModelLoaded(): Boolean = interpreter != null
    
    fun needsReload(): Boolean = interpreter == null
    
    /**
     * Force a clean reload of the model (useful for recovery from errors)
     */
    fun forceReload() {
        Log.i(TAG, "Forcing model reload...")
        close()
    }
      /**
     * Check if the interpreter is in a healthy state for inference
     */
    fun isInterpreterHealthy(): Boolean {
        return try {
            if (interpreter == null) {
                return false
            }
            
            // Try to access interpreter state - this will throw if interpreter is closed/corrupted
            val inputTensor = interpreter!!.getInputTensor(0)
            val outputTensor = interpreter!!.getOutputTensor(0)
            
            // Basic validation that tensors are accessible
            inputTensor.shape() != null && outputTensor.shape() != null
            
        } catch (e: Exception) {
            Log.w(TAG, "Interpreter health check failed: ${e.message}")
            false
        }
    }
    
    private fun warmUpModel() {
        try {
            Log.d(TAG, "Warming up model with dummy inference...")
            // Create dummy input buffer with correct size
            val dummyBuffer = java.nio.ByteBuffer.allocateDirect(4 * 224 * 224 * 3)
            dummyBuffer.order(java.nio.ByteOrder.nativeOrder())
            
            // Fill with dummy normalized data (0.0f)
            repeat(224 * 224 * 3) {
                dummyBuffer.putFloat(0.0f)
            }
            dummyBuffer.rewind()
            
            // Run dummy inference
            val startTime = System.currentTimeMillis()
            runInference(dummyBuffer)
            val warmupTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Model warm-up completed in ${warmupTime}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Model warm-up failed, but continuing: ${e.message}")
        }
    }
    fun close() {
        try {
            interpreter?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing interpreter: ${e.message}")
        } finally {
            interpreter = null
        }
        
        try {
            gpuDelegate?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GPU delegate: ${e.message}")
        } finally {
            gpuDelegate = null
        }
    }
}
