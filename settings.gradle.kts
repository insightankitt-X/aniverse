pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Aniyomi extensions resolve their dependency-injection runtime from JitPack.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.null2264.injekt") }
        }
    }
}

rootProject.name = "AniVerse"

include(":app")
include(":source-api")
include(":source-runtime")
include(":aniyomi-api")
