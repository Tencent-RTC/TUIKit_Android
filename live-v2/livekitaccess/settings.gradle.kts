pluginManagement {
    repositories {
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/gradle-plugins/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven {
            url = uri("https://mirrors.tencent.com/repository/maven/thirdparty")
            content { includeGroup("com.tencent.bugly_16kb") }
        }
        flatDir {
            dirs(File(settingsDir, "app/libs"))
        }

        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        google()
        mavenCentral()
    }
}

rootProject.name = "LiveKitAccess"
include(":app")
include(":tuilivekit")
project(":tuilivekit").projectDir = File(settingsDir, "../tuilivekit")
