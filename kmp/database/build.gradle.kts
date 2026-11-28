import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidxRoom3)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.abeta-dev"
version = internal.versions.database.get()

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "dev.abeta.kmp.database"
        compileSdk = 37
        minSdk = 24
        withHostTestBuilder {}
    }

    jvm()
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    watchosArm64()
    watchosSimulatorArm64()
    watchosDeviceArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                enabled = false
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core)
            api(libs.androidx.room3.runtime)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            api(libs.androidx.sqlite.bundled)
        }
        jvmMain.dependencies {
            api(libs.androidx.sqlite.bundled)
        }
        appleMain.dependencies {
            api(libs.androidx.sqlite.bundled)
        }
        wasmJsMain.dependencies {
            api(libs.androidx.sqlite.web)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room3.compiler)
    add("kspJvm", libs.androidx.room3.compiler)
    add("kspIosArm64", libs.androidx.room3.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
    add("kspMacosArm64", libs.androidx.room3.compiler)
    add("kspWatchosArm64", libs.androidx.room3.compiler)
    add("kspWatchosSimulatorArm64", libs.androidx.room3.compiler)
    add("kspWatchosDeviceArm64", libs.androidx.room3.compiler)
    add("kspWasmJs", libs.androidx.room3.compiler)
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    exclude { element ->
        element.file.path
            .replace('\\', '/')
            .contains("/generated/")
    }
}

tasks.matching { it.name == "jvmApiCheck" }.configureEach {
    mustRunAfter(tasks.matching { it.name == "jvmApiDump" })
}

val extractAndroidHostSqliteJni =
    tasks.register<Copy>("extractAndroidHostSqliteJni") {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val osArch = System.getProperty("os.arch").orEmpty().lowercase()
        val nativeSubdir =
            when {
                osName.contains("mac") || osName.contains("darwin") -> "osx_arm64"
                osName.contains("win") -> "windows_x64"
                osArch.contains("aarch64") || osArch.contains("arm64") -> "linux_arm64"
                else -> "linux_x64"
            }
        from({
            configurations
                .named("jvmRuntimeClasspath")
                .get()
                .files
                .filter { it.name.startsWith("sqlite-bundled-jvm") && it.extension == "jar" }
                .map { zipTree(it) }
        }) {
            include("natives/$nativeSubdir/*")
            eachFile {
                path = name
            }
            includeEmptyDirs = false
        }
        into(layout.buildDirectory.dir("androidHostTestJniLibs"))
    }

tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    dependsOn(extractAndroidHostSqliteJni)
    doFirst {
        val jniDir =
            layout.buildDirectory
                .dir("androidHostTestJniLibs")
                .get()
                .asFile.absolutePath
        val existing = systemProperties["java.library.path"]?.toString().orEmpty()
        val combined =
            if (existing.isBlank()) {
                jniDir
            } else {
                "$jniDir${File.pathSeparator}$existing"
            }
        systemProperty("java.library.path", combined)
    }
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = true,
        ),
    )
    coordinates(
        artifactId = "database",
    )
}
