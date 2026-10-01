plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":luaui-compose"))
    implementation(project(":luaui-runtime"))
    implementation(project(":luaui-material3"))
    implementation(project(":luaui-server"))
    implementation(project(":luaui-transport"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.ktor.server.test.host)
}

compose.desktop {
    application {
        mainClass = "io.github.nguyenphuc22.luaui.sample.MainKt"
    }
}
