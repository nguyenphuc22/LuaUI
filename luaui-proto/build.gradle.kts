import com.google.protobuf.gradle.*
import org.gradle.api.artifacts.VersionCatalogsExtension

val protobufVersion = extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("protobuf")
    .get()
    .requiredVersion

plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    api(libs.protobuf.java)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }

    generateProtoTasks {
        all().configureEach {
            generateDescriptorSet = true
            descriptorSetOptions.includeImports = true
            descriptorSetOptions.includeSourceInfo = false
        }
    }
}
