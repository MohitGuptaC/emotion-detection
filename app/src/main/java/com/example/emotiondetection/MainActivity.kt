package com.example.emotiondetection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleCameraResult(result.data)
        } else {
            Toast.makeText(this, getString(R.string.image_capture_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleGalleryResult(result.data)
        } else {
            Toast.makeText(this, getString(R.string.image_capture_cancelled), Toast.LENGTH_SHORT).show()
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
    
    // Request permission without launching camera
    private fun requestCameraPermissionOnly() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            shouldLaunchCameraAfterPermission = false // Explicitly set to false to not launch camera
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // This function checks permission and launches camera when button is pressed
    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
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
            if (takePictureIntent.resolveActivity(packageManager) != null) {
                cameraLauncher.launch(takePictureIntent)
            } else {
                Toast.makeText(this, getString(R.string.no_camera_app_found), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching camera: ${e.message}")
            Toast.makeText(this, getString(R.string.error_accessing_camera), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchGallery() {
        try {
            val pickPhotoIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            if (pickPhotoIntent.resolveActivity(packageManager) != null) {
                galleryLauncher.launch(pickPhotoIntent)
            } else {
                Toast.makeText(this, getString(R.string.no_gallery_app_found), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching gallery: ${e.message}")
            Toast.makeText(this, getString(R.string.unable_to_select_image), Toast.LENGTH_SHORT).show()
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
            viewModel.handleImageResult(imageBitmap, this)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling camera result: ${e.message}")
            Toast.makeText(this, getString(R.string.error_processing_image), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGalleryResult(data: Intent?) {
        try {
            val imageUri = data?.data
            if (imageUri != null) {
                val imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                viewModel.handleImageResult(imageBitmap, this)
            } else {
                Toast.makeText(this, getString(R.string.unable_to_select_image), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling gallery result: ${e.message}")
            Toast.makeText(this, getString(R.string.error_processing_image), Toast.LENGTH_SHORT).show()
        }
    }
}
