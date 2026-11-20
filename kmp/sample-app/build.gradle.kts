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
    implementation("dev.abeta.kmp:sdui-layout")
    implementation("dev.abeta.kmp:design-tokens")
    implementation("dev.abeta.kmp:sdui-contracts")
    implementation("dev.abeta.kmp:ui-atoms")
    implementation("dev.abeta.kmp:action-engine")
    implementation("dev.abeta.kmp:network")
    implementation("dev.abeta.kmp:analytics")
    implementation("dev.abeta.kmp:state")
    implementation("dev.abeta.kmp:core")

    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.ktor.bom))
    testImplementation(libs.ktor.client.mock)
}
