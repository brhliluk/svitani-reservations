import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(21)

    android {
        namespace = "cz.svitaninymburk.projects.reservations.shared"
        compileSdk = 36
        minSdk = 29
    }

    jvm()
    js(IR) {
        browser()
    }
    wasmJs {
        browser()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                implementation(project.dependencies.platform(libs.arrow.stack))
                implementation(libs.arrow.core)
                implementation(libs.arrow.serialization)
            }
        }
    }
}
