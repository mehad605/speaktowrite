import java.util.Properties
import java.io.FileInputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.mhm.speaktowrite"
    compileSdk = 36

    // ── Signing Configuration (Industry Standard) ──
    val keystorePropertiesFile = rootProject.file("app/keystore.properties")
    val keystoreProperties = Properties()
    var hasSigning = false

    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        hasSigning = true
    } else {
        // Fallback to environment variables (for GitHub Actions CI/CD)
        val envFile = System.getenv("ANDROID_SIGNING_KEY_FILE")
        val envStorePass = System.getenv("ANDROID_SIGNING_STORE_PASSWORD")
        val envAlias = System.getenv("ANDROID_SIGNING_KEY_ALIAS")
        val envKeyPass = System.getenv("ANDROID_SIGNING_KEY_PASSWORD")

        if (!envFile.isNullOrBlank() && !envStorePass.isNullOrBlank() && !envAlias.isNullOrBlank() && !envKeyPass.isNullOrBlank()) {
            keystoreProperties["storeFile"] = envFile
            keystoreProperties["storePassword"] = envStorePass
            keystoreProperties["keyAlias"] = envAlias
            keystoreProperties["keyPassword"] = envKeyPass
            hasSigning = true
        }
    }

    val envVersionName = System.getenv("APP_VERSION_NAME")
    val envVersionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull()

    defaultConfig {
        applicationId = "com.mhm.speaktowrite"
        minSdk = 24
        targetSdk = 36
        versionCode = envVersionCode ?: 2
        versionName = envVersionName ?: "1.0.1"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                val storePath = keystoreProperties["storeFile"] as String
                storeFile = file(storePath)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release") // Use release key for local debug for seamless updating
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-extended")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Network and Archiving
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("org.apache.commons:commons-compress:1.27.1")
  
  // Sherpa-onnx
  implementation(files("libs/sherpa-onnx-1.10.42.aar"))
}
