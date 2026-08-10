import org.gradle.util.GradleVersion

pluginManagement {
    val currentGradleVersion = GradleVersion.current()
    val androidGradlePluginVersion = when {
        currentGradleVersion >= GradleVersion.version("8.9") -> "8.7.0"
        currentGradleVersion >= GradleVersion.version("8.7") -> "8.6.1"
        currentGradleVersion >= GradleVersion.version("8.6") -> "8.4.2"
        currentGradleVersion >= GradleVersion.version("8.4") -> "8.3.2"
        currentGradleVersion >= GradleVersion.version("8.2") -> "8.2.2"
        currentGradleVersion >= GradleVersion.version("8.0") -> "8.1.4"
        currentGradleVersion >= GradleVersion.version("7.2") -> "7.0.0"
        else -> "4.2.0"
    }

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application",
                "com.android.library" -> useVersion(androidGradlePluginVersion)
                "org.jetbrains.kotlin.android" -> useVersion("1.9.0")
            }
        }
    }

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.tencent.com/repository/maven/liteavsdk")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.tencent.com/repository/maven/liteavsdk")
        mavenCentral()
    }
}

rootProject.name = "Chat"

include(":app")

include(":uikit")
project(":uikit").projectDir = file("${settingsDir.path}/../uikit")

include(":atomic_x")
project(":atomic_x").projectDir = file("${settingsDir.path}/../../atomic_x")

include(":tuicallkit-kt")
project(":tuicallkit-kt").projectDir = file("${settingsDir.path}/../../call/tuicallkit-kt")
