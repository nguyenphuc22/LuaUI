plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":luaui-core"))
    api(project(":luaui-proto"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.serialization.json)
}
