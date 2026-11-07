plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.babu.ytdownloader"
  compileSdk = 37
  defaultConfig {
    applicationId = "com.bistardigital.socialdownloader"
    minSdk = 24
    targetSdk = 34
    versionCode = 4
    versionName = "2.3.0"
  }

  signingConfigs {
    create("release") {
      storeFile = file("release-keystore.jks")
      storePassword = "SocialDL2026!"
      keyAlias = "socialdownloader"
      keyPassword = "SocialDL2026!"
      enableV1Signing = true
      enableV2Signing = true
      enableV3Signing = true
    }
  }

  buildTypes {
    debug {
      signingConfig = signingConfigs.getByName("release")
    }
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
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
    dex {
      useLegacyPackaging = true
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
  coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
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
  implementation(libs.androidx.compose.material.icons.core)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Pure-JVM YouTube & Multi-Platform Social Media extractors + HTTP client
  implementation(libs.newpipe.extractor)
  implementation(libs.okhttp)

  // Thumbnails
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)

  // JSON (metadata + download history storage)
  implementation(libs.kotlinx.serialization.json)

  // Local tests
  testImplementation(libs.junit)
}
