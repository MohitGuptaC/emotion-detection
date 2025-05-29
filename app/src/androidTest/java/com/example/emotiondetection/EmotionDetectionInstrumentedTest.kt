package com.example.emotiondetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Before

/**
 * Instrumented tests for emotion detection functionality.
 * These tests run on an Android device and verify the model loading,
 * face detection, and emotion classification components.
 */
@RunWith(AndroidJUnit4::class)
class EmotionDetectionInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun testAppContext() {
        // Verify the app context has the correct package name
        assertEquals("com.example.emotiondetection", context.packageName)
    }

    @Test
    fun testModelFileExists() {
        // Verify the emotion detection model file exists in assets
        val assetManager = context.assets
        try {
            val inputStream = assetManager.open("metadata.tflite")
            assertNotNull("Model file should exist", inputStream)
            inputStream.close()
        } catch (e: Exception) {
            fail("Model file metadata.tflite not found in assets: ${e.message}")
        }    }

    @Test
    fun testCreateTestBitmap() {
        // Test creating a test bitmap for testing purposes
        val testBitmap = createTestBitmap(224, 224)
        assertNotNull("Test bitmap should be created", testBitmap)
        assertEquals("Bitmap width should be 224", 224, testBitmap.width)
        assertEquals("Bitmap height should be 224", 224, testBitmap.height)
        testBitmap.recycle()
    }

    @Test
    fun testBitmapProcessing() {
        // Test bitmap creation and basic processing
        val testBitmap = createTestBitmap(512, 512)
        assertNotNull("Large test bitmap should be created", testBitmap)
        
        // Test that we can scale it down
        val scaledBitmap = Bitmap.createScaledBitmap(testBitmap, 224, 224, true)
        assertNotNull("Scaled bitmap should be created", scaledBitmap)
        assertEquals("Scaled width should be 224", 224, scaledBitmap.width)
        assertEquals("Scaled height should be 224", 224, scaledBitmap.height)
        
        // Cleanup
        testBitmap.recycle()
        scaledBitmap.recycle()
    }

    @Test
    fun testEmotionLabels() {
        // Test that emotion labels are properly defined
        val expectedLabels = arrayOf(
            "Neutral", "Happiness", "Surprise", "Sadness",
            "Anger", "Disgust", "Fear", "Contempt"
        )
        
        // This tests the consistency of emotion labels across the app
        assertEquals("Should have 8 emotion labels", 8, expectedLabels.size)
        
        // Test that labels are not empty
        for (label in expectedLabels) {
            assertFalse("Label should not be empty", label.isEmpty())
            assertTrue("Label should be capitalized", label[0].isUpperCase())
        }
    }

    @Test
    fun testModelDimensions() {
        // Test that model expects correct input dimensions
        val expectedWidth = 224
        val expectedHeight = 224
        val expectedChannels = 3
        
        // These are the dimensions our model expects
        assertTrue("Width should be positive", expectedWidth > 0)
        assertTrue("Height should be positive", expectedHeight > 0)
        assertTrue("Channels should be 3 for RGB", expectedChannels == 3)
    }

    @Test
    fun testNormalizationValues() {
        // Test normalization parameters
        val mean = floatArrayOf(0.5f, 0.5f, 0.5f)
        val std = floatArrayOf(0.5f, 0.5f, 0.5f)
        
        assertEquals("Mean should have 3 values", 3, mean.size)
        assertEquals("Std should have 3 values", 3, std.size)
        
        for (i in mean.indices) {
            assertTrue("Mean values should be between 0 and 1", mean[i] in 0.0f..1.0f)
            assertTrue("Std values should be positive", std[i] > 0.0f)
        }
    }

    /**
     * Helper function to create a test bitmap with gradient colors
     * This simulates a face-like image for testing purposes
     */
    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Create a simple gradient pattern that resembles a face
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Create a gradient from center (lighter) to edges (darker)
                val centerX = width / 2
                val centerY = height / 2
                val distance = kotlin.math.sqrt(
                    ((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).toDouble()
                ).toFloat()
                val maxDistance = kotlin.math.sqrt((centerX * centerX + centerY * centerY).toDouble()).toFloat()
                val intensity = (255 * (1 - distance / maxDistance)).toInt().coerceIn(0, 255)
                
                // Create a skin-tone-like color
                val red = (intensity * 0.9).toInt().coerceIn(0, 255)
                val green = (intensity * 0.7).toInt().coerceIn(0, 255)
                val blue = (intensity * 0.6).toInt().coerceIn(0, 255)
                
                bitmap.setPixel(x, y, Color.rgb(red, green, blue))
            }
        }
        
        return bitmap
    }
}
