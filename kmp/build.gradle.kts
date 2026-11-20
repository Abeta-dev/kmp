plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.binaryValidator) apply false
}

val publishableModules = setOf(
    "core",
    "state",
    "analytics",
    "design-tokens",
    "sdui-contracts",
    "ui-atoms",
    "action-engine",
    "network",
    "sdui-layout",
    "watch-companion",
    "kmp-bom",
    "dependencyManagement",
)

val isMacOsHost = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
val useNoSyncBuildDir = isMacOsHost && rootDir.absolutePath.contains("/Documents/")

if (useNoSyncBuildDir) {
    layout.buildDirectory.set(rootDir.resolve(".build.nosync/root"))
}

val availableSimulatorRuntimesOutput: String by lazy {
    if (!isMacOsHost) {
        ""
    } else {
        runCatching {
            val process = ProcessBuilder("xcrun", "simctl", "list", "runtimes", "available")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (process.waitFor() == 0) output else ""
        }.getOrDefault("")
    }
}

val hasIosSimulatorRuntime: Boolean by lazy {
    isMacOsHost && availableSimulatorRuntimesOutput.contains("iOS ")
}

val hasWatchOsSimulatorRuntime: Boolean by lazy {
    isMacOsHost && availableSimulatorRuntimesOutput.contains("watchOS ")
}

subprojects {
    group = "dev.abeta.kmp"
    if (useNoSyncBuildDir) {
        layout.buildDirectory.set(rootDir.resolve(".build.nosync/${project.name}"))
    }

    apply(plugin = "com.diffplug.spotless")

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        apply(plugin = "org.jetbrains.dokka")
        apply(plugin = "dev.detekt")
        if (name in publishableModules && name != "kmp-bom" && name != "dependencyManagement") {
            apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")
        }
        afterEvaluate {
            val kmpDetektTasks = tasks.matching {
                it.name.startsWith("detekt") &&
                    it.name.endsWith("SourceSet") &&
                    !it.name.contains("Baseline")
            }
            tasks.matching { it.name == "detekt" }.configureEach {
                dependsOn(kmpDetektTasks)
            }
            tasks.matching { it.name == "check" }.configureEach {
                dependsOn("detekt")
            }
        }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "org.jetbrains.dokka")
        apply(plugin = "dev.detekt")
        afterEvaluate {
            tasks.matching { it.name == "check" }.configureEach {
                dependsOn("detekt")
            }
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
        }
    }

    if (name in publishableModules) {
        plugins.withId("com.vanniktech.maven.publish") {
            configure<PublishingExtension> {
                repositories {
                    maven {
                        name = "GitHubPackages"
                        url = uri("https://maven.pkg.github.com/abeta-dev/kmp")
                        credentials {
                            username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USER")
                            password = findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
                        }
                    }
                }
            }

            configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
                pom {
                    name.set("abeta-kmp-${project.name}")
                    description.set("Abeta KMP Kit module: ${project.name}")
                    url.set("https://github.com/abeta-dev/kmp")
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("abeta-dev")
                            name.set("Abeta Engineering")
                            url.set("https://github.com/abeta-dev")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/abeta-dev/kmp.git")
                        developerConnection.set("scm:git:ssh://github.com/abeta-dev/kmp.git")
                        url.set("https://github.com/abeta-dev/kmp")
                    }
                }
            }
        }
    }
}

if (isMacOsHost && (!hasIosSimulatorRuntime || !hasWatchOsSimulatorRuntime)) {
    gradle.projectsEvaluated {
        allprojects {
            plugins.withId("org.jetbrains.kotlin.multiplatform") {
                tasks.matching { it.name == "check" }.configureEach {
                    setDependsOn(
                        dependsOn.filterNot { dep ->
                            val text = dep.toString()
                            text.contains("allTests") ||
                                (!hasIosSimulatorRuntime && text.contains("iosSimulatorArm64Test")) ||
                                (!hasWatchOsSimulatorRuntime && text.contains("watchosSimulatorArm64Test"))
                        },
                    )
                    val requiredCheckTasks = buildSet {
                        add("jvmTest")
                        add("macosArm64Test")
                        add(if (hasIosSimulatorRuntime) "iosSimulatorArm64Test" else "linkDebugTestIosSimulatorArm64")
                        add(
                            if (hasWatchOsSimulatorRuntime) {
                                "watchosSimulatorArm64Test"
                            } else {
                                "linkDebugTestWatchosSimulatorArm64"
                            },
                        )
                    }
                    dependsOn(tasks.matching { it.name in requiredCheckTasks })
                }
            }
        }
    }
}
