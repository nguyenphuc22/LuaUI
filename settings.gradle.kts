pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "luaui"

include(
    ":luaui-core",
    ":luaui-annotations",
    ":luaui-ksp",
    ":luaui-compose",
    ":luaui-material3",
    ":luaui-transport",
    ":luaui-server",
    ":sample",
)
