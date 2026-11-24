// Reference Gradle build template demonstrating Google's official KMP Android Library plugin
// (`com.android.kotlin.multiplatform.library`) alongside the Abeta KMP Kit BOM (`0.6.0`) and
// official Google AndroidX KMP libraries (`androidx.lifecycle`, `androidx.savedstate`,
// `androidx.datastore`, `androidx.sqlite`).
//
// Usage:
//   Copy into your consumer Android/KMP library module with `ANDROID_HOME` configured
//   (AGP 8.10+ / 9.0+, Kotlin 2.4+, JDK 21). Replace literal plugin/dependency versions
//   with your project's `libs.versions.toml` version catalog entries.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    // Modern Google KMP Android Library target (replaces legacy `com.android.library` + `androidTarget()`).
    androidLibrary {
        namespace = "dev.abeta.kmp.android"
        compileSdk = 36
        minSdk = 24

        withHostTestBuilder {}.configure {}

        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Abeta KMP Kit BOM aligns all `io.github.abeta-dev:*` library versions.
            implementation(project.dependencies.platform("io.github.abeta-dev:bom:0.6.0"))
            implementation("io.github.abeta-dev:core")
            implementation("io.github.abeta-dev:state")
            implementation("io.github.abeta-dev:database")
            implementation("io.github.abeta-dev:sdui-layout")

            // Official Google AndroidX KMP libraries (Google Maven).
            api("androidx.lifecycle:lifecycle-viewmodel:2.11.1")
            api("androidx.savedstate:savedstate:1.5.0")
            implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
            implementation("androidx.datastore:datastore-preferences-core:1.3.0")
            implementation("androidx.sqlite:sqlite-bundled:2.7.1")
        }

        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("androidx.biometric:biometric:1.1.0")
            implementation("androidx.fragment:fragment-ktx:1.8.6")
        }
    }
}
