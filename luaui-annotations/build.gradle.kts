plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(project(":luaui-core"))
        }
    }
}
