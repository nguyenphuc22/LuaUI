plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(project(":luaui-core"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
    }
}
