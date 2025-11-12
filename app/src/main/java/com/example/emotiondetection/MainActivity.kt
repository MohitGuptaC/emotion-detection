package com.example.emotiondetection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import com.example.emotiondetection.ui.MainScreen
import com.example.emotiondetection.ui.MainScreenEvent
import com.example.emotiondetection.ui.MainViewModel
import com.example.emotiondetection.ui.theme.EmotionDetectionTheme
import com.example.emotiondetection.audio.MusicManager
import com.example.emotiondetection.audio.EmotionAggregator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.graphics.BitmapFactory
import androidx.camera.view.PreviewView
import com.example.emotiondetection.ui.FaceOverlayView
import androidx.camera.core.ExperimentalGetImage

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }
    
    private val viewModel: MainViewModel by viewModels()
    private val supportedEmotionLabels = setOf(
        "Neutral",
        "Happiness",
        "Surprise",
        "Sadness",
        "Anger",
        "Disgust",
        "Fear",
        "Contempt"
    )
    private var lastRecognizedEmotion: String? = null
    
    // Track if we should open camera after permission granted
    private var shouldLaunchCameraAfterPermission = false
    
    // MediaPlayer for playing music
    private var mediaPlayer: MediaPlayer? = null
    
    // Default/generative music manager
    private lateinit var musicManager: MusicManager
    private val aggregatorConfidenceThreshold = 0.55f
    private lateinit var emotionAggregator: EmotionAggregator
    private val cadenceHandler by lazy { android.os.Handler(mainLooper) }
    private val detectionWindowMs = 10_000L
    private val playbackDurationMs = 30_000L
    private val detectionWindowRunnable = Runnable { completeDetectionWindow() }
    private var isDetectionWindowActive: Boolean = false
    private var pendingDetectionStart: Runnable? = null
    
    // CameraX monitoring
    private var cameraExecutor: ExecutorService? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var isMonitoring: Boolean = false
    private var previewView: PreviewView? = null
    private var faceOverlayView: FaceOverlayView? = null
    private val mainHandler by lazy { android.os.Handler(mainLooper) }
    private var lastAnalyzerFrameTimestamp = 0L
    private val faceDetector by lazy {
        com.google.mlkit.vision.face.FaceDetection.getClient(
            com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
                .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .build()
        )
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->          if (isGranted) {
            // Only launch camera if requested from Capture Image button
            // In new flow, permission gates monitoring
            if (shouldLaunchCameraAfterPermission) startMonitoring()
        } else {
            showToast(R.string.camera_permission_required, Toast.LENGTH_LONG)
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleGalleryResult(result.data)
        } else {
            showToast(R.string.image_capture_cancelled)
        }
    }

    private val audioFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleAudioFileResult(result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize model early to improve first detection performance
        viewModel.initializeModel(this)
        musicManager = MusicManager(
            context = this,
            onPlaybackStatusChanged = { isGenerated, emotion ->
                viewModel.updateMusicStatus(isGenerated, emotion)
            }
        )
        emotionAggregator = EmotionAggregator(confidenceThreshold = aggregatorConfidenceThreshold)
        
        setContent {
            EmotionDetectionTheme {
                MainScreenContent()
            }
        }
    }

    @Composable
    private fun MainScreenContent() {
        val state = viewModel.state
        var checkCameraPermissionOnStart by remember { mutableStateOf(true) }
        val noFaceMessage = stringResource(R.string.no_face_detected)
        
        // Check camera permission on first composition without launching camera
        LaunchedEffect(checkCameraPermissionOnStart) {
            if (checkCameraPermissionOnStart) {
                requestCameraPermissionOnly()
            }
        }
        
        MainScreen(
            state = state,
            onEvent = { event ->
                when (event) {
                    is MainScreenEvent.StartMonitoring -> launchMonitoring()
                    is MainScreenEvent.SelectFromGallery -> launchGallery()
                    is MainScreenEvent.SelectMusic -> launchAudioFilePicker()
                    is MainScreenEvent.ResetDetection -> {
                        stopMonitoring()
                        musicManager.stopPlayback()
                        viewModel.updateMusicStatus(null, "")
                        clearOverlay()
                        emotionAggregator.reset()
                        viewModel.onEvent(event)
                    }
                }
            }
        )
        
        // Aggregate detections continuously; cadence scheduler decides when to act
        LaunchedEffect(state.lastResult, state.lastConfidence, state.isLoading) {
            if (!state.isLoading && state.lastResult.isNotBlank()) {
                val label = state.lastResult
                val conf = state.lastConfidence

                if (label.equals(noFaceMessage, ignoreCase = true)) {
                    lastRecognizedEmotion = null
                    return@LaunchedEffect
                }

                if (conf != null && supportedEmotionLabels.contains(label)) {
                    if (conf >= aggregatorConfidenceThreshold) {
                        lastRecognizedEmotion = label
                        if (isDetectionWindowActive) {
                            emotionAggregator.add(label, conf)
                        }
                    }
                }
            }
        }
    }
    
    // Helper method to reduce toast redundancy
    private fun showToast(messageResId: Int, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, getString(messageResId), duration).show()
    }    
    
    // Helper method to reduce error handling redundancy
    private fun handleError(action: String, exception: Exception, messageResId: Int) {
        Log.e(TAG, "Error $action: ${exception.message}")
        showToast(messageResId)
    }

    // Helper method to check camera permission (reduces duplication)
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    // Helper method for intent resolution pattern
    private fun launchIntentIfAvailable(intent: Intent, launcher: (Intent) -> Unit, noAppMessageResId: Int) {
        if (intent.resolveActivity(packageManager) != null) {
            launcher(intent)
        } else {
            showToast(noAppMessageResId)
        }
    }

    // Request permission without launching camera
    private fun requestCameraPermissionOnly() {
        if (!hasCameraPermission()) {
            shouldLaunchCameraAfterPermission = false // Explicitly set to false to not launch camera
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // This function checks permission and launches monitoring when button is pressed
    private fun launchMonitoring() {
        if (!hasCameraPermission()) {
            shouldLaunchCameraAfterPermission = true // Set flag to launch monitoring after permission
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            viewModel.updateMonitoring(true)
            startMonitoring()
        }
    }

    @Suppress("OPT_IN_ARGUMENT_IS_NOT_MARKER")
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    @OptIn(ExperimentalGetImage::class)
    private fun startMonitoring() {
        if (isMonitoring) return
    isMonitoring = true
    cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                val previewUseCase = androidx.camera.core.Preview.Builder().build().also { preview ->
                    preview.surfaceProvider = previewView?.surfaceProvider
                }
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                            try {
                                // Throttle processing (process at most once per second)
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastAnalyzerFrameTimestamp < 1000) {
                                    try {
                                        imageProxy.close()
                                    } catch (_: Exception) { }
                                    return@setAnalyzer
                                }
                                lastAnalyzerFrameTimestamp = now

                                // Create a corrected bitmap for downstream usage
                                val bitmap = imageProxy.toBitmapCorrected()

                                // Prefer running a fast face detector first; only when faces are present
                                // do we forward the frame to the heavier emotion model. This prevents
                                // spurious emotion detections when no face is visible.
                                val mediaImage = imageProxy.image
                                if (bitmap != null && mediaImage != null) {
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    val inputImage = com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, rotation)
                                    faceDetector.process(inputImage)
                                        .addOnSuccessListener { faces ->
                                            if (faces.isNotEmpty()) {
                                                // Only run the emotion pipeline when we actually have a face
                                                viewModel.handleContinuousFrame(bitmap, this@MainActivity)
                                                updateOverlayBoxes(faces, imageProxy)
                                            } else {
                                                // No faces: clear overlay and skip model inference
                                                viewModel.handleNoFaceDetected(getString(R.string.no_face_detected))
                                                clearOverlay()
                                            }
                                        }
                                        .addOnFailureListener {
                                            clearOverlay()
                                        }
                                        .addOnCompleteListener {
                                            // Close the imageProxy after ML Kit finished with the frame
                                            try {
                                                imageProxy.close()
                                            } catch (_: Exception) { }
                                        }
                                    // End early after ML Kit schedules work and handles closing
                                    return@setAnalyzer
                                } else {
                                    // No usable media image or bitmap: close immediately
                                    try {
                                        imageProxy.close()
                                    } catch (_: Exception) { }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Analyzer error: ${e.message}")
                                try {
                                    imageProxy.close()
                                } catch (_: Exception) { }
                            }
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider.bindToLifecycle(this, cameraSelector, previewUseCase, imageAnalysis)
                startDetectionWindow()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting monitoring: ${e.message}")
                isMonitoring = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopMonitoring() {
        if (!isMonitoring) return
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            cameraProvider.unbindAll()
        } catch (_: Exception) { }
        imageAnalysis = null
        cameraExecutor?.shutdownNow()
        cameraExecutor = null
        isMonitoring = false
        cadenceHandler.removeCallbacksAndMessages(null)
        pendingDetectionStart = null
        isDetectionWindowActive = false
        musicManager.stopPlayback(triggerCallback = false)
        viewModel.updateMusicStatus(null, "")
        emotionAggregator.reset()
        viewModel.updateMonitoring(false)
    }

    private fun launchGallery() {
        try {
            val pickPhotoIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            launchIntentIfAvailable(
                pickPhotoIntent,
                { galleryLauncher.launch(it) },
                R.string.no_gallery_app_found
            )        } catch (e: Exception) {
            handleError("launching gallery", e, R.string.unable_to_select_image)
        }
    }

    private fun handleGalleryResult(data: Intent?) {
        try {
            val imageUri = data?.data
            val imageBitmap = if (imageUri != null) {
                // Use modern ImageDecoder (API 28+ required)
                val source = ImageDecoder.createSource(contentResolver, imageUri)
                ImageDecoder.decodeBitmap(source)
            } else {
                showToast(R.string.unable_to_select_image)
                return
            }
            processImageResult(imageBitmap)
        } catch (e: Exception) {
            handleError("handling gallery result", e, R.string.error_processing_image)
        }
    }

    // Extract common image processing logic
    private fun processImageResult(imageBitmap: Bitmap?) {
        viewModel.handleImageResult(imageBitmap, this)
    }

    // Convert ImageProxy to Bitmap with rotation/mirroring correction for front camera
    private fun ImageProxy.toBitmapCorrected(): Bitmap? {
        return try {
            val nv21 = yuv420888ToNv21()
            val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 95, out)
            val imageBytes = out.toByteArray()
            out.close()
            val raw = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
            val matrix = android.graphics.Matrix().apply {
                val rotation = imageInfo.rotationDegrees.toFloat()
                if (rotation != 0f) {
                    postRotate(rotation)
                }
                // Mirror horizontally because we use the front camera
                postScale(-1f, 1f, raw.width / 2f, raw.height / 2f)
            }
            val corrected = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (corrected != raw && !raw.isRecycled) {
                raw.recycle()
            }
            corrected
        } catch (e: Exception) {
            Log.e(TAG, "toBitmap error: ${e.message}")
            null
        }
    }

    private fun ImageProxy.yuv420888ToNv21(): ByteArray {
        val width = width
        val height = height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuffer = yPlane.buffer.duplicate().apply { rewind() }
        val uBuffer = uPlane.buffer.duplicate().apply { rewind() }
        val vBuffer = vPlane.buffer.duplicate().apply { rewind() }

        val yBytes = ByteArray(yBuffer.remaining())
        yBuffer.get(yBytes)

        val uBytes = ByteArray(uBuffer.remaining())
        uBuffer.get(uBytes)

        val vBytes = ByteArray(vBuffer.remaining())
        vBuffer.get(vBytes)

        var outputPos = 0
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until height) {
            var inputOffset = row * yRowStride
            for (col in 0 until width) {
                nv21[outputPos++] = yBytes[inputOffset]
                inputOffset += yPixelStride
            }
        }

        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        var uvPos = ySize
        for (row in 0 until height / 2) {
            var uOffset = row * uRowStride
            var vOffset = row * vRowStride
            for (col in 0 until width / 2) {
                nv21[uvPos++] = vBytes[vOffset]
                nv21[uvPos++] = uBytes[uOffset]
                uOffset += uPixelStride
                vOffset += vPixelStride
            }
        }

        return nv21
    }
    private fun launchAudioFilePicker() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "audio/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                // Filter for WAV and MP3 files
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("audio/wav", "audio/x-wav", "audio/wave", "audio/mpeg", "audio/mp3")
                )
            }
            launchIntentIfAvailable(
                intent,
                { audioFileLauncher.launch(it) },
                R.string.no_audio_app_found
            )
        } catch (e: Exception) {
            handleError("launching audio file picker", e, R.string.unable_to_select_music)
        }
    }

    private fun handleAudioFileResult(data: Intent?) {
        try {
            val audioUri = data?.data
            if (audioUri != null) {
                playAudio(audioUri)
            } else {
                showToast(R.string.unable_to_select_music)
            }
        } catch (e: Exception) {
            handleError("handling audio file result", e, R.string.error_playing_music)
        }
    }

    private fun playAudio(uri: Uri) {
        try {
            // Stop and release previous MediaPlayer if exists
            mediaPlayer?.release()
            mediaPlayer = null

            // Create and configure new MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, uri)
                prepare()
                start()
                
                // Release MediaPlayer when playback completes
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                }
                
                // Handle errors
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    release()
                    mediaPlayer = null
                    showToast(R.string.error_playing_music)
                    true
                }
            }
        } catch (e: Exception) {
            handleError("playing audio", e, R.string.error_playing_music)
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release MediaPlayer when activity is destroyed
        mediaPlayer?.release()
        mediaPlayer = null
        if (this::musicManager.isInitialized) {
            musicManager.release()
        }
        stopMonitoring()
    }

    private fun startDetectionWindow() {
        cancelPendingDetectionStart()
        cadenceHandler.removeCallbacks(detectionWindowRunnable)
        isDetectionWindowActive = true
        emotionAggregator.reset()
        cadenceHandler.postDelayed(detectionWindowRunnable, detectionWindowMs)
    }

    private fun completeDetectionWindow() {
        if (!isDetectionWindowActive) return
        isDetectionWindowActive = false

        val summary = emotionAggregator.windowSummary()
        val fallbackFromState = viewModel.state.lastResult.takeIf { supportedEmotionLabels.contains(it) }
        val primaryCandidate = summary?.topLabel?.takeIf { supportedEmotionLabels.contains(it) }
        val emotionToPlay = primaryCandidate
            ?: fallbackFromState
            ?: lastRecognizedEmotion?.takeIf { supportedEmotionLabels.contains(it) }

        if (emotionToPlay.isNullOrBlank()) {
            Log.d(TAG, "Detection window ended with no playable emotion; retrying detection")
            startDetectionWindow()
            return
        }

        val usePrimarySet = summary?.topShare?.let { it >= 0.5f } ?: false

        cancelPendingDetectionStart()

        val playbackStarted = musicManager.playPreGenerated(
            emotionLabel = emotionToPlay,
            usePrimarySet = usePrimarySet,
            playbackDurationMs = playbackDurationMs
        ) {
            cadenceHandler.post {
                cancelPendingDetectionStart()
                if (!isDetectionWindowActive) {
                    startDetectionWindow()
                }
            }
        }

        if (!playbackStarted) {
            Log.w(TAG, "Failed to start playback for $emotionToPlay; restarting detection window")
            cadenceHandler.post { startDetectionWindow() }
            return
        }

        scheduleDetectionWindowDuringPlayback()
    }

    private fun scheduleDetectionWindowDuringPlayback() {
        cancelPendingDetectionStart()
        val delayBeforeNextWindow = playbackDurationMs - detectionWindowMs
        if (delayBeforeNextWindow <= 0L) {
            cadenceHandler.post { startDetectionWindow() }
            return
        }
        val runnable = Runnable {
            pendingDetectionStart = null
            startDetectionWindow()
        }
        pendingDetectionStart = runnable
        cadenceHandler.postDelayed(runnable, delayBeforeNextWindow)
    }

    private fun cancelPendingDetectionStart() {
        pendingDetectionStart?.let {
            cadenceHandler.removeCallbacks(it)
            pendingDetectionStart = null
        }
    }

    fun bindPreview(preview: PreviewView, overlay: FaceOverlayView) {
        previewView = preview
        faceOverlayView = overlay
    }

    private fun updateOverlayBoxes(
        faces: List<com.google.mlkit.vision.face.Face>,
        imageProxy: ImageProxy
    ) {
        val overlay = faceOverlayView ?: return
        val preview = previewView ?: return
        if (faces.isEmpty()) {
            clearOverlay()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val imageWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val imageHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

        val viewWidth = preview.width.toFloat().coerceAtLeast(1f)
        val viewHeight = preview.height.toFloat().coerceAtLeast(1f)

        // FILL_CENTER mapping
        val scale = maxOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val dx = (viewWidth - imageWidth * scale) / 2f
        val dy = (viewHeight - imageHeight * scale) / 2f

        val isFrontCamera = true

        val rects = faces.map { face ->
            val b = face.boundingBox
            val x = b.left.toFloat()
            val y = b.top.toFloat()
            val w = b.width().toFloat()
            val h = b.height().toFloat()

            val mappedXImage = if (isFrontCamera) (imageWidth - (x + w)) else x

            val left = mappedXImage * scale + dx
            val top = y * scale + dy
            val right = left + w * scale
            val bottom = top + h * scale
            android.graphics.RectF(left, top, right, bottom)
        }
        mainHandler.post { overlay.setBoxes(rects) }
    }

    private fun clearOverlay() {
        faceOverlayView?.let { view ->
            mainHandler.post { view.clear() }
        }
    }
}
