import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

// 배포용 서명 키는 환경 변수로만 받습니다. 없으면 디버그 키로 서명해서
// assembleRelease 결과물도 바로 설치해 볼 수 있게 합니다.
val releaseKeystore: String? = System.getenv("KEYSTORE_PATH")

// .env.example → .env → 환경 변수 순서로 읽습니다 (뒤에 있는 값이 우선).
// 키가 비어 있어도 빌드는 되고, 앱은 AI 없이 기기 안 규칙으로 판단합니다.
val envProps = Properties().apply {
  listOf(".env.example", ".env").map { rootProject.file(it) }.filter { it.exists() }
    .forEach { f -> f.reader(Charsets.UTF_8).use { load(it) } }
}
fun envValue(key: String, default: String = ""): String =
  (System.getenv(key) ?: envProps.getProperty(key) ?: "").trim().removeSurrounding("\"").ifBlank { default }
fun javaString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.falldetector.jkqpyw"
    minSdk = 24
    targetSdk = 36
    versionCode = 3
    versionName = "2.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "GEMINI_API_KEY", javaString(envValue("GEMINI_API_KEY")))
    buildConfigField("String", "GEMINI_MODEL", javaString(envValue("GEMINI_MODEL", "gemini-3.5-flash")))
  }

  signingConfigs {
    if (releaseKeystore != null) {
      create("release") {
        storeFile = file(releaseKeystore)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig =
        if (releaseKeystore != null) signingConfigs.getByName("release")
        else signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  implementation(libs.play.services.location)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
