package com.example.emotiondetection.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import com.example.emotiondetection.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class MainViewModel : ViewModel() {

    var state by mutableStateOf(MainScreenState())
        private set

    companion object {
        private const val TAG = "MainViewModel"
        private const val MODEL_FILE = "metadata.tflite"

        // Model configuration matching Python code
        private const val INPUT_WIDTH = 224
        private const val INPUT_HEIGHT = 224
        private const val INPUT_CHANNELS = 3
        private const val IS_NCHW = true  // NCHW format as specified
        private val INPUT_MEAN = floatArrayOf(0.5f, 0.5f, 0.5f)
        private val INPUT_STD = floatArrayOf(0.5f, 0.5f, 0.5f)
        private val EMOTION_LABELS = arrayOf(
            "Neutral", "Happiness", "Surprise", "Sadness",
            "Anger", "Disgust", "Fear", "Contempt"
        )
    }    private var interpreter: Interpreter? = null
    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.1f)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    fun onEvent(event: MainScreenEvent) {
        when (event) {
            is MainScreenEvent.ResetDetection -> resetDetection()
            is MainScreenEvent.CaptureImage,
            is MainScreenEvent.SelectFromGallery -> {} // Handled in Activity
        }
    }

    private fun loadModel(context: Context): Boolean {
        return try {
            Log.d(TAG, "=== LOADING MODEL ===")

            // Load model
            val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
            val inputStream = java.io.FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            // Create interpreter
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(modelBuffer, options)

            // Verify model configuration
            val inputTensor = interpreter!!.getInputTensor(0)
            val inputShape = inputTensor.shape()
            val inputDataType = inputTensor.dataType()

            Log.d(TAG, "Model loaded successfully:")
            Log.d(TAG, "  Input shape: ${inputShape.contentToString()}")
            Log.d(TAG, "  Input data type: $inputDataType")
            Log.d(TAG, "  Expected: [1, $INPUT_CHANNELS, $INPUT_HEIGHT, $INPUT_WIDTH] (NCHW)")
            Log.d(TAG, "  Format: NCHW")
            Log.d(TAG, "  Normalization: mean=${INPUT_MEAN.contentToString()}, std=${INPUT_STD.contentToString()}")
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

    fun handleImageResult(bitmap: Bitmap?, context: Context) {
        try {
            if (bitmap == null) {
                state = state.copy(
                    lastResult = context.getString(R.string.unable_to_capture_image),
                    lastConfidence = null,
                    isLoading = false
                )
                return
            }

            // Load model if not done
            if (interpreter == null) {
                state = state.copy(
                    lastResult = "Loading model...",
                    isLoading = true,
                    lastConfidence = null
                )

                if (!loadModel(context)) {
                    state = state.copy(
                        lastResult = "ERROR: Cannot load model",
                        isLoading = false,
                        lastConfidence = null
                    )
                    return
                }
            }

            // Recycle current bitmap
            state.detectedImage?.recycle()

            state = state.copy(
                isLoading = true,
                lastResult = "Detecting faces...",
                lastConfidence = null
            )

            // Detect faces first (like Python code)
            detectFacesAndAnalyzeEmotion(bitmap, context)

        } catch (e: Exception) {
            Log.e(TAG, "ERROR in handleImageResult: ${e.message}", e)
            state = state.copy(
                lastResult = "ERROR: ${e.message}",
                isLoading = false,
                lastConfidence = null
            )
        }
    }

    private fun detectFacesAndAnalyzeEmotion(bitmap: Bitmap, context: Context) {
        val image = InputImage.fromBitmap(bitmap, 0)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    Log.w(TAG, "No faces detected in image")
                    state = state.copy(
                        lastResult = "No faces detected",
                        isLoading = false,
                        lastConfidence = null,
                        detectedImage = bitmap
                    )
                } else {
                    Log.d(TAG, "Found ${faces.size} face(s)")
                    processFacesForEmotion(bitmap, faces, context)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Face detection failed", e)
                state = state.copy(
                    lastResult = "Face detection failed: ${e.message}",
                    isLoading = false,
                    lastConfidence = null
                )
            }
    }

    private fun processFacesForEmotion(originalBitmap: Bitmap, faces: List<Face>, context: Context) {
        try {
            // Process the first (largest) face for emotion recognition
            val largestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

            if (largestFace == null) {
                state = state.copy(
                    lastResult = "No valid face found",
                    isLoading = false,
                    lastConfidence = null
                )
                return
            }

            Log.d(TAG, "Processing largest face: ${largestFace.boundingBox}")

            // Extract and preprocess face
            val faceBitmap = extractFaceFromBitmap(originalBitmap, largestFace.boundingBox)
            if (faceBitmap == null) {
                state = state.copy(
                    lastResult = "Failed to extract face",
                    isLoading = false,
                    lastConfidence = null
                )
                return
            }

            val processedFaceBitmap = preprocessImage(faceBitmap)
            if (processedFaceBitmap == null) {
                faceBitmap.recycle()
                state = state.copy(
                    lastResult = "Face preprocessing failed",
                    isLoading = false,
                    lastConfidence = null
                )
                return
            }

            // Create visualization bitmap with face bounding box
            val visualizationBitmap = createVisualizationBitmap(originalBitmap, faces)

            state = state.copy(
                detectedImage = visualizationBitmap,
                isLoading = true,
                lastResult = "Analyzing emotion...",
                lastConfidence = null
            )

            // Analyze emotion on the processed face
            detectEmotionOnFace(processedFaceBitmap, context)

            // Cleanup
            faceBitmap.recycle()
            if (processedFaceBitmap != faceBitmap) {
                processedFaceBitmap.recycle()
            }

        } catch (e: Exception) {
            Log.e(TAG, "ERROR processing faces: ${e.message}", e)
            state = state.copy(
                lastResult = "Error processing faces: ${e.message}",
                isLoading = false,
                lastConfidence = null
            )
        }
    }

    private fun extractFaceFromBitmap(bitmap: Bitmap, boundingBox: Rect): Bitmap? {
        return try {
            // Add some padding around the face (like Python's face detection)
            val padding = 20
            val left = max(0, boundingBox.left - padding)
            val top = max(0, boundingBox.top - padding)
            val right = min(bitmap.width, boundingBox.right + padding)
            val bottom = min(bitmap.height, boundingBox.bottom + padding)

            val width = right - left
            val height = bottom - top

            if (width <= 0 || height <= 0) {
                Log.e(TAG, "Invalid face dimensions: ${width}x${height}")
                return null
            }

            Log.d(TAG, "Extracting face: ${left},${top} ${width}x${height} from ${bitmap.width}x${bitmap.height}")

            Bitmap.createBitmap(bitmap, left, top, width, height)

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting face: ${e.message}", e)
            null
        }
    }

    private fun createVisualizationBitmap(originalBitmap: Bitmap, faces: List<Face>): Bitmap {
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = android.graphics.Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        val textPaint = Paint().apply {
            color = android.graphics.Color.GREEN
            textSize = 40f
            isAntiAlias = true
        }

        // Draw bounding boxes around detected faces
        faces.forEach { face ->
            canvas.drawRect(face.boundingBox, paint)
            canvas.drawText(
                "Face",
                face.boundingBox.left.toFloat(),
                face.boundingBox.top.toFloat() - 10,
                textPaint
            )
        }

        return mutableBitmap
    }

    private fun preprocessImage(bitmap: Bitmap): Bitmap? {
        return try {
            Log.d(TAG, "Preprocessing face image:")
            Log.d(TAG, "  Target: ${INPUT_WIDTH}x${INPUT_HEIGHT}x${INPUT_CHANNELS}")
            Log.d(TAG, "  Original face: ${bitmap.width}x${bitmap.height}")

            // Center crop to square (same as Python code)
            val minDimension = minOf(bitmap.width, bitmap.height)
            val xOffset = (bitmap.width - minDimension) / 2
            val yOffset = (bitmap.height - minDimension) / 2

            val squareBitmap = Bitmap.createBitmap(
                bitmap, xOffset, yOffset, minDimension, minDimension
            )

            // Scale to required size using LANCZOS (similar to Python's LANCZOS)
            val scaledBitmap = squareBitmap.scale(INPUT_WIDTH, INPUT_HEIGHT, true)

            // Convert to RGB format
            val finalBitmap = if (scaledBitmap.config != Bitmap.Config.ARGB_8888) {
                scaledBitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                scaledBitmap
            }

            // Cleanup
            if (squareBitmap != finalBitmap && !squareBitmap.isRecycled) {
                squareBitmap.recycle()
            }
            if (scaledBitmap != finalBitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }

            Log.d(TAG, "Face preprocessing complete: ${finalBitmap.width}x${finalBitmap.height}")
            finalBitmap

        } catch (e: Exception) {
            Log.e(TAG, "ERROR in face preprocessing: ${e.message}", e)
            null
        }
    }

    private fun detectEmotionOnFace(faceBitmap: Bitmap, context: Context) {
        try {
            val currentInterpreter = interpreter

            if (currentInterpreter == null) {
                Log.e(TAG, "ERROR: Interpreter not initialized")
                state = state.copy(
                    lastResult = "ERROR: Model not properly initialized",
                    isLoading = false,
                    lastConfidence = null
                )
                return
            }

            // Convert face bitmap to ByteBuffer with NCHW format
            val inputBuffer = bitmapToByteBuffer(faceBitmap)
            if (inputBuffer == null) {
                state = state.copy(
                    lastResult = "ERROR: Failed to convert face image to model input format",
                    isLoading = false,
                    lastConfidence = null
                )
                return
            }

            // Prepare output
            val outputArray = Array(1) { FloatArray(EMOTION_LABELS.size) }

            Log.d(TAG, "Running emotion inference on face...")
            val startTime = System.currentTimeMillis()

            currentInterpreter.run(inputBuffer, outputArray)

            val inferenceTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Emotion inference completed in ${inferenceTime}ms")            // Process results
            val output = outputArray[0]

            // Apply softmax (same as Python code)
            val softmaxOutput = applySoftmax(output)

            // Find best emotion
            val maxIndex = softmaxOutput.indices.maxByOrNull { softmaxOutput[it] } ?: 0
            val confidence = softmaxOutput[maxIndex].coerceIn(0f, 1f)
            val detectedEmotion = EMOTION_LABELS[maxIndex]

            state = state.copy(
                lastResult = detectedEmotion,
                lastConfidence = confidence,
                isLoading = false
            )

        } catch (e: Exception) {
            Log.e(TAG, "ERROR during emotion detection: ${e.message}", e)
            state = state.copy(
                lastResult = "ERROR: Emotion detection failed - ${e.message}",
                isLoading = false,
                lastConfidence = null
            )        }
    }

    private fun applySoftmax(input: FloatArray): FloatArray {
        // Apply softmax function (same as Python implementation)
        val maxVal = input.maxOrNull() ?: 0f
        val expValues = input.map { kotlin.math.exp((it - maxVal).toDouble()).toFloat() }
        val sumExp = expValues.sum()
        return expValues.map { it / sumExp }.toFloatArray()
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer? {
        return try {
            // Use FLOAT32 for normalized input (NCHW format)
            val bufferSize = 4 * INPUT_WIDTH * INPUT_HEIGHT * INPUT_CHANNELS
            val byteBuffer = ByteBuffer.allocateDirect(bufferSize)
            byteBuffer.order(ByteOrder.nativeOrder())

            val intValues = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
            bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            Log.d(TAG, "Converting face to ByteBuffer (NCHW format):")
            Log.d(TAG, "  Data type: FLOAT32")
            Log.d(TAG, "  Normalization: mean=${INPUT_MEAN.contentToString()}, std=${INPUT_STD.contentToString()}")
            Log.d(TAG, "  Format: NCHW [1, $INPUT_CHANNELS, $INPUT_HEIGHT, $INPUT_WIDTH]")

            // Process pixels with exact same normalization as Python code
            // Python: (image/255 - mean) / std
            // NCHW format: [batch, channels, height, width]

            // Extract and normalize all pixel values
            val normalizedPixels = Array(INPUT_CHANNELS) { Array(INPUT_HEIGHT) { FloatArray(INPUT_WIDTH) } }

            for (i in 0 until INPUT_HEIGHT) {
                for (j in 0 until INPUT_WIDTH) {
                    val pixelValue = intValues[i * INPUT_WIDTH + j]

                    // Extract RGB values
                    val r = (pixelValue shr 16) and 0xFF
                    val g = (pixelValue shr 8) and 0xFF
                    val b = pixelValue and 0xFF

                    // Normalize exactly like Python: (pixel/255 - mean) / std
                    normalizedPixels[0][i][j] = (r.toFloat() / 255.0f - INPUT_MEAN[0]) / INPUT_STD[0] // R channel
                    normalizedPixels[1][i][j] = (g.toFloat() / 255.0f - INPUT_MEAN[1]) / INPUT_STD[1] // G channel
                    normalizedPixels[2][i][j] = (b.toFloat() / 255.0f - INPUT_MEAN[2]) / INPUT_STD[2] // B channel
                }
            }

            // Write to buffer in NCHW order: all R values, then all G values, then all B values
            for (c in 0 until INPUT_CHANNELS) {
                for (i in 0 until INPUT_HEIGHT) {
                    for (j in 0 until INPUT_WIDTH) {
                        byteBuffer.putFloat(normalizedPixels[c][i][j])
                    }
                }
            }

            byteBuffer

        } catch (e: Exception) {
            Log.e(TAG, "ERROR converting face bitmap to ByteBuffer: ${e.message}", e)
            null        }
    }

    private fun resetDetection() {
        state.detectedImage?.recycle()
        state = state.copy(
            detectedImage = null,
            lastResult = "",
            lastConfidence = null,
            isLoading = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        state.detectedImage?.recycle()
        interpreter?.close()
        faceDetector.close()
    }
}