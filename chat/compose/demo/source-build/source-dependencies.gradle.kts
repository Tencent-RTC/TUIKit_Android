// ============================================================================
// Source/AAR dependency switches (local source development only).
//
// Applied from the root build script via apply(from = ...); the implicit
// receiver is the root project. App ABI filters are configured in the app
// module, while this script wires dependency selection.
//
// Default (aligned with view/demo): AtomicEngine comes from source
// (:atomic_engine + native :atomicEngineSource), while IMSDK comes from Maven.
// IMSDK can switch to source or one local AAR.
//
//   -PuseAtomicEngineAar=true -PatomicEngineAarPath=<aar>  prebuilt AtomicEngine AAR
//   -PuseImSdkSource=true      : build against the IMSDK source module.
//   -PuseImSdkLocalAar=true -PimsdkAarPath=<aar>
//   -PatomicEngineRoot=<path>  : override the atomic_engine checkout.
//   -Parm64Only=true           : build/package arm64-v8a only.
// ============================================================================

val useImSdkSource = providers.gradleProperty("useImSdkSource")
    .map(String::toBoolean)
    .getOrElse(false)
val useImSdkLocalAar = providers.gradleProperty("useImSdkLocalAar")
    .map(String::toBoolean)
    .getOrElse(false)
val useAtomicEngineAar = providers.gradleProperty("useAtomicEngineAar")
    .map(String::toBoolean)
    .getOrElse(false)

val atomicEngineAarPath = providers.gradleProperty("atomicEngineAarPath").orNull?.trim().orEmpty()
val atomicEngineAarFile = atomicEngineAarPath
    .takeIf { useAtomicEngineAar && it.isNotEmpty() }
    ?.let { path -> java.io.File(path).let { if (it.isAbsolute) it else java.io.File(rootDir, path) } }
if (useAtomicEngineAar &&
    atomicEngineAarFile?.let { it.isFile && it.extension.equals("aar", ignoreCase = true) } != true
) {
    throw GradleException(
        "[Chat Demo] useAtomicEngineAar=true requires atomicEngineAarPath to point to an existing .aar file: " +
            atomicEngineAarPath.ifEmpty { "(empty)" }
    )
}
val atomicEngineAarDependency = atomicEngineAarFile
    ?.nameWithoutExtension
    ?.let { name -> mapOf("name" to name, "ext" to "aar") }

val imsdkAarPath = providers.gradleProperty("imsdkAarPath").orNull?.trim().orEmpty()
val imsdkAarFile = imsdkAarPath
    .takeIf { useImSdkLocalAar && it.isNotEmpty() }
    ?.let { path -> java.io.File(path).let { if (it.isAbsolute) it else java.io.File(rootDir, path) } }
val localImSdkModule = imsdkAarFile
    ?.nameWithoutExtension
    ?.let { name -> "local.imsdk:$name:local" }

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

val arm64Only = providers.gradleProperty("arm64Only").map(String::toBoolean).getOrElse(false)
val targetAbis = if (arm64Only) listOf("arm64-v8a") else listOf("armeabi-v7a", "arm64-v8a")
val projectProperties = gradle.startParameter.projectProperties
val taskNames = gradle.startParameter.taskNames
val isCleanOnly = taskNames.isNotEmpty() && taskNames.all { it == "clean" || it.endsWith(":clean") }

// Consumed by atomic_x/build.gradle via rootProject.ext.* to choose the AtomicEngine
// source module vs a flatDir-backed AAR notation. Avoid files(...) here: AGP rejects
// direct local AAR dependencies from Android library projects such as :atomic_x.
extra["atomicxCoreSdk"] = if (useAtomicEngineAar) atomicEngineAarDependency!! else project(":atomic_engine")

// LiteAV AAR providing both Java classes and the runtime .so.
extra["liteavSdk"] = "com.tencent.liteav:LiteAVSDK_Professional:13.5.0.20670"
extra["chatTargetAbis"] = targetAbis

if (!isCleanOnly) {
    val imSdkMode = when {
        useImSdkSource -> "SOURCE (-PuseImSdkSource=true)"
        useImSdkLocalAar -> "LOCAL AAR ($imsdkAarFile)"
        else -> "MAVEN AAR (com.tencent.imsdk:imsdk-plus)"
    }
    logger.lifecycle(
        """
        |============================================================
        |  [Chat Demo Build Config]
        |    Root         : $rootDir
        |    Tasks        : ${taskNames.joinToString(" ").ifBlank { "(none)" }}
        |    -P           : useImSdkSource=${projectProperties["useImSdkSource"] ?: "(unset)"}, useImSdkLocalAar=${projectProperties["useImSdkLocalAar"] ?: "(unset)"}, useAtomicEngineAar=${projectProperties["useAtomicEngineAar"] ?: "(unset)"}, imsdkAarPath=${projectProperties["imsdkAarPath"] ?: "(unset)"}, atomicEngineAarPath=${projectProperties["atomicEngineAarPath"] ?: "(unset)"}, arm64Only=${projectProperties["arm64Only"] ?: "(unset)"}
        |    IMSDK        : $imSdkMode
        |    AtomicEngine : ${if (useAtomicEngineAar) "AAR ($atomicEngineAarFile)" else "SOURCE (:atomic_engine) + native (:atomicEngineSource)"}
        |    Source ABI   : ${if (arm64Only) "arm64Only=true" else "arm64Only=false"} [${targetAbis.joinToString(", ")}] (app: release=arm64-v8a, debug=armeabi-v7a+arm64-v8a)
        |============================================================
        """.trimMargin()
    )
}

if (useImSdkSource) {
    project(":imsdk") {
        plugins.withId("com.android.library") {
            afterEvaluate {
                extensions.getByName("android").withGroovyBuilder {
                    "defaultConfig" {
                        "externalNativeBuild" {
                            "cmake" {
                                @Suppress("UNCHECKED_CAST")
                                val abiFilters = getProperty("abiFilters") as MutableCollection<String>
                                abiFilters.clear()
                                abiFilters.addAll(targetAbis)
                            }
                        }
                    }
                }
            }
        }

        tasks.configureEach {
            if (name.endsWith("[x86]")) {
                enabled = false
            }
        }
    }
}

if (!useAtomicEngineAar) {
    project(":atomic_engine") {
        afterEvaluate {
            tasks.matching { it.name == "preBuild" }.configureEach {
                dependsOn(":atomicEngineSource:buildAtomicEngineNativeSo")
            }
        }
    }
}

subprojects {
    configurations.configureEach {
        if (useImSdkLocalAar) {
            resolutionStrategy.eachDependency {
                if (requested.group == "com.tencent.imsdk" && requested.name == "imsdk-plus") {
                    useTarget(localImSdkModule!!)
                    artifactSelection {
                        selectArtifact("aar", "aar", null)
                    }
                }
            }
        }
        resolutionStrategy.dependencySubstitution {
            if (useImSdkSource) {
                substitute(module("com.tencent.imsdk:imsdk-plus"))
                    .using(project(":imsdk"))
            }
        }
    }
}
