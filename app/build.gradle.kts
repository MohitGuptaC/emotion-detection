plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.emotiondetection"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.emotiondetection"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    /*signingConfigs {
        // For CI/CD or environment-based signing (e.g., GitHub Actions), use this block:
        create("release") {
            storeFile = file(System.getenv("ORG_GRADLE_PROJECT_storeFile"))
            storePassword = System.getenv("ORG_GRADLE_PROJECT_storePassword")
            keyAlias = System.getenv("ORG_GRADLE_PROJECT_keyAlias")
            keyPassword = System.getenv("ORG_GRADLE_PROJECT_keyPassword")
        }
        // For local Android Studio builds, you can comment out the above block entirely.
        // Android Studio's "Generate Signed Bundle / APK" wizard lets you pick the keystore and credentials interactively.
    }*/

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            //signingConfig = signingConfigs.getByName("release") //comment this line if you want to use Android Studio's interactive signing wizard
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packaging {
        resources {
            excludes += listOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.metadata)
    
    // Compose dependencies
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)   
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    
    // Compose debug tools
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)    
    implementation(libs.face.detection)

    // ML Kit Vision Common - required for InputImage
    implementation(libs.vision.common)

}
