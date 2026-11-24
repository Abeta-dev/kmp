plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.abeta.kmp.sample.MainKt")
}

dependencies {
    implementation(platform(projects.kmpBom))
    implementation("io.github.abeta-dev:sdui-layout")
    implementation("io.github.abeta-dev:design-tokens")
    implementation("io.github.abeta-dev:sdui-contracts")
    implementation("io.github.abeta-dev:ui-atoms")
    implementation("io.github.abeta-dev:action-engine")
    implementation("io.github.abeta-dev:network")
    implementation("io.github.abeta-dev:analytics")
    implementation("io.github.abeta-dev:state")
    implementation("io.github.abeta-dev:core")

    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.ktor.bom))
    testImplementation(libs.ktor.client.mock)
}
