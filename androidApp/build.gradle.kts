plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "cz.svitaninymburk.projects.reservations.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.svitaninymburk.projects.reservations"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions.freeCompilerArgs.addAll(
        "-Xexplicit-backing-fields",
        "-opt-in=kotlin.uuid.ExperimentalUuidApi",
        "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
    )
}

dependencies {
    implementation(projects.shared)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Navigation 3
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Ktor client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.contentNegotiation.json)

    // Arrow
    implementation(platform(libs.arrow.stack))
    implementation(libs.arrow.core)

    // QR Code
    implementation(libs.qrCode)

    // EncryptedSharedPreferences
    implementation(libs.security.crypto)

    // Kotlinx DateTime (for LocalDateTime formatting in UI)
    implementation(libs.kotlinx.datetime)

    // Tests
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.datetime)
}
