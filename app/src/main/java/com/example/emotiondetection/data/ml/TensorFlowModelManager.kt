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
            )            // Create interpreter with GPU delegate if available
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
                    gpuDelegate?.close()
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

            true

        } catch (e: Exception) {
            Log.e(TAG, "ERROR loading model: ${e.message}", e)
            false
        }
    }
    
    fun runInference(inputBuffer: ByteBuffer): FloatArray? {
        return try {
            if (interpreter == null) {
                Log.e(TAG, "ERROR: Interpreter not initialized")
                return null
            }

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
            null
        }
    }    fun getEmotionLabels(): Array<String> = EMOTION_LABELS
    
    fun isModelLoaded(): Boolean = interpreter != null
    
    fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }
}
