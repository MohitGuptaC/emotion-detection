package com.example.emotiondetection.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emotiondetection.R
import com.example.emotiondetection.ui.theme.AppColors

@Composable
fun MainScreen(
    state: MainScreenState,
    onEvent: (MainScreenEvent) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    bottom = 16.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Content area - 60% of screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Image Preview
                ImagePreview(
                    bitmap = state.detectedImage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.7f)
                        .padding(vertical = 8.dp)
                )
                
                // Result Text
                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.3f)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.3f)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.lastResult.ifEmpty {
                                stringResource(R.string.detected_labels_will_appear_here)
                            },
                            color = AppColors.Blue,
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        state.lastConfidence?.let { confidence ->
                            Text(
                                text = "Confidence: ${String.format("%.1f%%", confidence * 100)}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
              // Button section - 40% of screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Capture Image Button
                CaptureButton(
                    enabled = !state.isLoading,
                    onClick = { onEvent(MainScreenEvent.CaptureImage) }
                )

                // Select from Gallery Button
                SelectFromGalleryButton(
                    enabled = !state.isLoading,
                    onClick = { onEvent(MainScreenEvent.SelectFromGallery) }
                )

                // Reset Button
                ResetButton(
                    enabled = state.detectedImage != null && !state.isLoading,
                    onClick = { onEvent(MainScreenEvent.ResetDetection) }
                )
            } // End of button section
        } // End of main Column
    } // End of Surface
}

@Composable
private fun ImagePreview(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    // Use a consistent content scale
    val imageContentScale = ContentScale.Fit
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            // Display captured image with fixed content scale
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.preview_of_the_captured_image),
                contentScale = imageContentScale,
                modifier = Modifier.fillMaxSize(0.95f) // Slightly smaller to avoid edge issues
            )
        } else {
            // For empty state, use a fixed placeholder icon size
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.baseline_image_24),
                    contentDescription = stringResource(R.string.preview_of_the_captured_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize(0.5f) // Use fixed 50% size of the container
                )
            }
        }
    }
}

@Composable
private fun CaptureButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = AppColors.Blue,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(48.dp)
    ) {
        Text(
            text = stringResource(R.string.capture_image),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ResetButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = AppColors.Blue,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(48.dp)
    ) {
        Text(
            text = stringResource(R.string.reset),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SelectFromGalleryButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = AppColors.Blue,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(48.dp)
    ) {
        Text(
            text = stringResource(R.string.select_from_gallery),
            fontSize = 14.sp
        )
    }
}
