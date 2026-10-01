plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm()
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(project(":luaui-core"))
            implementation(libs.compose.runtime)
        }
    }
}
