import java.io.File

pluginManagement {
    repositories {
        google()
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        mavenCentral()
        gradlePluginPortal()
        maven("https://developer.hihonor.com/repo")
        maven("https://developer.huawei.com/repo/")
        maven("https://mirrors.tencent.com/repository/maven/liteavsdk")
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
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        mavenCentral()
        maven("https://developer.hihonor.com/repo")
        maven("https://developer.huawei.com/repo/")
        maven("https://mirrors.tencent.com/repository/maven/liteavsdk")
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

include(":tuicallkit-kt")
project(":tuicallkit-kt").projectDir = file("${settingsDir.path}/../../../call/tuicallkit-kt")

include(":atomic_x")
project(":atomic_x").projectDir = file("${settingsDir.path}/../../../atomic_x")

apply(from = "source-build/source-modules.gradle.kts")
