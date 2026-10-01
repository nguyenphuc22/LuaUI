plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

kotlin {
    jvm()
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(project(":luaui-compose"))
            implementation(project(":luaui-annotations"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
    }
}

dependencies {
    add("kspJvm", project(":luaui-ksp"))
}

ksp {
    arg("luaui.generated.package", "io.github.nguyenphuc22.luaui.material3.generated")
    arg("luaui.generated.object", "Material3GeneratedRendererDispatcher")
}
