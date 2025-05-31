# Emotion Detection Android App

An Android application that uses machine learning to detect emotions from facial expressions in real-time using camera capture or gallery images.

## Features

- **Real-time Camera Capture**: Take photos using the device camera for emotion analysis
- **Gallery Integration**: Select existing images from the device gallery
- **Face Detection**: Automatically detects faces in images using ML Kit
- **Emotion Recognition**: Classifies emotions into 8 categories using a TensorFlow Lite model
- **GPU Acceleration**: Utilizes GPU acceleration when available for faster inference
- **Modern UI**: Built with Jetpack Compose for a responsive and intuitive interface

## Supported Emotions

The app can detect the following emotions:
- Neutral
- Happiness
- Surprise
- Sadness
- Anger
- Disgust
- Fear
- Contempt

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **ML Framework**: LiteRT (TensorFlow Lite) with GPU acceleration
- **Face Detection**: ML Kit Vision
- **Architecture**: MVVM with ViewModels
- **Build System**: Gradle with Kotlin DSL

## Requirements

- Android API Level 28 (Android 9.0) or higher
- Device with camera (for image capture)
- Minimum 50MB free storage

## Model Information

The app uses a custom TensorFlow Lite model trained on the FER+ dataset:
- **Input**: 224x224x3 RGB images
- **Output**: 8-class emotion probabilities
- **Architecture**: MobileNetV3 optimized for mobile inference
- **Format**: NCHW with normalization (mean=0.5, std=0.5)
- **Acceleration**: GPU acceleration with automatic fallback to CPU when GPU is not available

## Installation

1. Clone this repository
2. Open the project in Android Studio
3. Sync the project with Gradle files
4. Run the app on a device or emulator

## Usage

1. **Grant Permissions**: Allow camera access when prompted
2. **Capture Image**: Tap "Capture Image" to take a photo with the camera
3. **Select from Gallery**: Tap "Select from Gallery" to choose an existing image
4. **View Results**: The detected emotion and confidence score will be displayed
5. **Reset**: Tap "Reset" to clear the current result and start over

## Permissions

The app requires the following permissions:
- `CAMERA`: For capturing images
- No storage permissions needed (uses scoped storage)

## Development

### Building the Project

```bash
./gradlew assembleDebug
```

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

### Code Quality

The project follows Android development best practices:
- MVVM architecture pattern
- Jetpack Compose for modern UI
- Proper separation of concerns
- Comprehensive error handling
- Memory management for bitmaps

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- TensorFlow Lite team for the mobile ML framework
- Google ML Kit for face detection capabilities
- FER+ dataset contributors for emotion recognition training data
- Android Jetpack Compose team for the modern UI toolkit
