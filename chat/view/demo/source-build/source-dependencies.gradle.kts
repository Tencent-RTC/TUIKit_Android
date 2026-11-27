// ============================================================================
// 源码 / AAR 依赖开关（仅供本地源码开发使用）。
//
// 对外发布的 Demo 默认走 AAR：客户无需关心本文件，imsdk 以 AAR 依赖，
// 下面所有 if (useXxxSource) 分支都不会进入。需要以源码方式调试时再开启对应开关：
//   -PuseImSdkSource=true       以源码方式依赖 IMSDK
//   -PuseImSdkLocalAar=true -PimsdkAarPath=<aar>  use one local IMSDK AAR
//   -PuseAtomicEngineAar=true -PatomicEngineAarPath=<aar>  使用预编译的 AtomicEngine AAR
//   源码依赖 :atomic_engine 时，:atomicEngineSource 会在 preBuild 前编译 libatomicengine.so
//   -PatomicEngineRoot=<path>   覆盖 atomic_engine 仓库位置
//
// 说明：本脚本通过 apply(from = ...) 引入，其编译类路径不含 AGP，因此 imsdk 的 ABI 配置
// 使用 withGroovyBuilder 反射方式，避免在编译期引用 LibraryExtension 等 AGP 类型。
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

// targetAbis 供 IMSDK CMake 源码编译使用（chatTargetAbis）。
// app 打包 ABI 在 app/build.gradle.kts 按 buildType 配置：release 仅 arm64-v8a，debug 为 armeabi-v7a + arm64-v8a。
// The link-time LiteAV libs only cover arm64-v8a / armeabi-v7a, so AtomicEngine native cannot be linked for x86.
val arm64Only = providers.gradleProperty("arm64Only").map(String::toBoolean).getOrElse(false)
val targetAbis = if (arm64Only) listOf("arm64-v8a") else listOf("armeabi-v7a", "arm64-v8a")
val projectProperties = gradle.startParameter.projectProperties
val taskNames = gradle.startParameter.taskNames
val isCleanOnly = taskNames.isNotEmpty() && taskNames.all { it == "clean" || it.endsWith(":clean") }

// Consumed by atomic_x/build.gradle via rootProject.ext.* to choose source module vs AAR.
// AtomicEngine uses a flatDir-backed module notation when useAtomicEngineAar is enabled.
// Avoid files(...) here because AGP rejects direct local AAR dependencies from Android
// library projects such as :atomic_x.
// liteav has no source module and stays on the AAR coordinate.
extra["atomicxCoreSdk"] = if (useAtomicEngineAar) atomicEngineAarDependency!! else project(":atomic_engine")
extra["liteavSdk"] = "com.tencent.liteav:LiteAVSDK_Professional:13.5.0.20670"
extra["chatTargetAbis"] = targetAbis

// Build banner: surface source/AAR mode for each SDK plus the resolved ABIs.
if (!isCleanOnly) {
    val imSdkMode = when {
        useImSdkSource -> "源码 SOURCE (-PuseImSdkSource=true)"
        useImSdkLocalAar -> "本地 AAR ($imsdkAarFile)"
        else -> "Maven AAR (com.tencent.imsdk:imsdk-plus)"
    }
    logger.lifecycle(
    """
    |
    |============================================================
    |  [Chat Demo 构建配置 / Build Config]
    |    Root         : $rootDir
    |    Tasks        : ${taskNames.joinToString(" ").ifBlank { "(none)" }}
    |    -P           : useImSdkSource=${projectProperties["useImSdkSource"] ?: "(unset)"}, useImSdkLocalAar=${projectProperties["useImSdkLocalAar"] ?: "(unset)"}, useAtomicEngineAar=${projectProperties["useAtomicEngineAar"] ?: "(unset)"}, imsdkAarPath=${projectProperties["imsdkAarPath"] ?: "(unset)"}, atomicEngineAarPath=${projectProperties["atomicEngineAarPath"] ?: "(unset)"}, arm64Only=${projectProperties["arm64Only"] ?: "(unset)"}
    |    IMSDK        : $imSdkMode
    |    AtomicEngine : ${if (useAtomicEngineAar) "AAR ($atomicEngineAarFile)" else "源码 SOURCE (:atomic_engine) + native (:atomicEngineSource)"}
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

// 源码模式下，AtomicEngine 的 native 由 :atomicEngineSource 封装（gn/ninja copy_engine_sdk）。
// 保证 :atomic_engine 打包 jniLibs 前先生成 libatomicengine.so。
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
