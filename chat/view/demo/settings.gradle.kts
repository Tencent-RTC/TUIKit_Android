import java.io.File
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
                "org.jetbrains.kotlin.android" -> useVersion("1.9.22")
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
        // Offline push (TIMPush) vendor repos: Honor MCS / Huawei AGConnect.
        maven("https://developer.hihonor.com/repo")
        maven("https://developer.huawei.com/repo/")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.tencent.com/repository/maven/liteavsdk")
        mavenCentral()
        gradlePluginPortal()
    }
}

val useImSdkSource = providers.gradleProperty("useImSdkSource")
    .map(String::toBoolean)
    .getOrElse(false)
val useImSdkLocalAar = providers.gradleProperty("useImSdkLocalAar")
    .map(String::toBoolean)
    .getOrElse(false)
val imsdkAarPath = providers.gradleProperty("imsdkAarPath").orNull?.trim().orEmpty()
val imsdkAarFile = imsdkAarPath
    .takeIf { useImSdkLocalAar && it.isNotEmpty() }
    ?.let { path -> File(path).let { if (it.isAbsolute) it else File(settingsDir, path) } }

if (useImSdkSource && useImSdkLocalAar) {
    throw GradleException(
        "[Chat Demo] useImSdkSource=true conflicts with useImSdkLocalAar=true. Choose one IMSDK mode."
    )
}
if (useImSdkLocalAar &&
    imsdkAarFile?.let { it.isFile && it.extension.equals("aar", ignoreCase = true) } != true
) {
    throw GradleException(
        "[Chat Demo] useImSdkLocalAar=true requires imsdkAarPath to point to an existing .aar file: " +
            imsdkAarPath.ifEmpty { "(empty)" }
    )
}

val useAtomicEngineAar = providers.gradleProperty("useAtomicEngineAar")
    .map(String::toBoolean)
    .getOrElse(false)
val atomicEngineAarPath = providers.gradleProperty("atomicEngineAarPath").orNull?.trim().orEmpty()
val atomicEngineAarFile = atomicEngineAarPath
    .takeIf { useAtomicEngineAar && it.isNotEmpty() }
    ?.let { path -> File(path).let { if (it.isAbsolute) it else File(settingsDir, path) } }
if (useAtomicEngineAar &&
    atomicEngineAarFile?.let { it.isFile && it.extension.equals("aar", ignoreCase = true) } != true
) {
    throw GradleException(
        "[Chat Demo] useAtomicEngineAar=true requires atomicEngineAarPath to point to an existing .aar file: " +
            atomicEngineAarPath.ifEmpty { "(empty)" }
    )
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        // Offline push (TIMPush) vendor repos: Honor MCS / Huawei AGConnect.
        maven("https://developer.hihonor.com/repo")
        maven("https://developer.huawei.com/repo/")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.tencent.com/repository/maven/liteavsdk")
        mavenCentral()
        if (useAtomicEngineAar || useImSdkLocalAar) {
            flatDir {
                if (useAtomicEngineAar) {
                    dirs(atomicEngineAarFile!!.parentFile)
                }
                if (useImSdkLocalAar) {
                    dirs(imsdkAarFile!!.parentFile)
                }
            }
        }
    }
}

rootProject.name = "Chat"

include(":app")

include(":uikit")
project(":uikit").projectDir = file("${settingsDir.path}/../uikit")

include(":atomic_x")
project(":atomic_x").projectDir = file("${settingsDir.path}/../../../atomic_x")

include(":tuicallkit-kt")
project(":tuicallkit-kt").projectDir = file("${settingsDir.path}/../../../call/tuicallkit-kt")

apply(from = "source-build/source-modules.gradle.kts")
