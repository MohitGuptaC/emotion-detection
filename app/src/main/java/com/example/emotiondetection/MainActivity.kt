package com.example.emotiondetection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.emotiondetection.ui.MainScreen
import com.example.emotiondetection.ui.MainScreenEvent
import com.example.emotiondetection.ui.MainViewModel
import com.example.emotiondetection.ui.theme.EmotionDetectionTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }
    
    private val viewModel: MainViewModel by viewModels()
    
    // Track if we should open camera after permission granted
    private var shouldLaunchCameraAfterPermission = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->        
        if (isGranted) {
            // Only launch camera if requested from Capture Image button
            if (shouldLaunchCameraAfterPermission) {
                openCamera()
                shouldLaunchCameraAfterPermission = false
            }
        } else {
            showToast(R.string.camera_permission_required, Toast.LENGTH_LONG)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleCameraResult(result.data)
        } else {
            showToast(R.string.image_capture_cancelled)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        
        // Check camera permission on first composition without launching camera
        LaunchedEffect(checkCameraPermissionOnStart) {
            if (checkCameraPermissionOnStart) {
                requestCameraPermissionOnly()
                checkCameraPermissionOnStart = false
            }
        }
        
        MainScreen(
            state = state,
            onEvent = { event ->
                when (event) {
                    is MainScreenEvent.CaptureImage -> launchCamera()
                    is MainScreenEvent.SelectFromGallery -> launchGallery()
                    else -> viewModel.onEvent(event)
                }
            }
        )
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

    // This function checks permission and launches camera when button is pressed
    private fun launchCamera() {
        if (!hasCameraPermission()) {
            shouldLaunchCameraAfterPermission = true // Set flag to launch camera after permission
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            openCamera()
        }
    }
      
    // Separate method to actually open the camera
    private fun openCamera() {
        try {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            // Add extra to use front camera for selfies
            takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", 1)
            launchIntentIfAvailable(
                takePictureIntent,
                { cameraLauncher.launch(it) },
                R.string.no_camera_app_found
            )        } catch (e: Exception) {
            handleError("launching camera", e, R.string.error_accessing_camera)
        }
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
      
    private fun handleCameraResult(data: Intent?) {
        try {
            val imageBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.extras?.getParcelable("data", Bitmap::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.extras?.getParcelable("data")
            }
            processImageResult(imageBitmap)        } catch (e: Exception) {
            handleError("handling camera result", e, R.string.error_processing_image)
        }
    }    private fun handleGalleryResult(data: Intent?) {
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
            processImageResult(imageBitmap)        } catch (e: Exception) {
            handleError("handling gallery result", e, R.string.error_processing_image)
        }
    }

    // Extract common image processing logic
    private fun processImageResult(imageBitmap: Bitmap?) {
        viewModel.handleImageResult(imageBitmap, this)
    }
}