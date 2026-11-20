plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// ── Версионирование ──
// versionCode = минуты с 2024-01-01: растёт с каждой сборкой, поэтому любая новая
// сборка встаёт поверх старой, даже если репозиторий пересоздавали.
// versionName задаёт CI (APP_VERSION), локально — 0.0.0-dev.
val appVersionCode = ((System.currentTimeMillis() - 1704067200000L) / 60000L).toInt()
val appVersionName = System.getenv("APP_VERSION") ?: "0.0.0-dev"

android {
    namespace = "com.example.bookreader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.bookreader"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // Постоянный ключ: обновления устанавливаются поверх без удаления приложения.
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "../signing/release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "bookreader"
            keyAlias = System.getenv("KEY_ALIAS") ?: "bookreader"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "bookreader"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "/META-INF/DEPENDENCIES"
        resources.excludes += "/META-INF/LICENSE*"
        resources.excludes += "/META-INF/NOTICE*"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Архивы: 7z (commons-compress + xz для LZMA) и RAR4 (junrar). ZIP — стандартный java.util.zip.
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("org.tukaani:xz:1.9")
    implementation("com.github.junrar:junrar:7.5.5")
}
