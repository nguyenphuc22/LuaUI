plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.ksp.symbol.processing.api)
}
