import java.io.File

// ============================================================================
// SDK 源码模块接入 + 源码/AAR 开关判断（仅本地源码开发相关）。
//
// 通过 settings.gradle.kts 的 apply(from = ...) 引入；其隐式 receiver 为 Settings，
// 不提供 file()，故统一用 File(settingsDir, ...) 解析模块目录。
//
// 默认行为：
//   - atomic_engine 以源码 include，同时 include atomicEngineSource
//     （通过 gn/ninja 编译 libatomicengine.so）。
//     -PuseAtomicEngineAar=true -PatomicEngineAarPath=<aar> 时两者都不 include，
//     由 source-dependencies.gradle.kts 改用预编译 AAR。
//   - atomic_engine 默认取 imsdk 仓库下同级目录 atomic_engine，
//     可通过 -PatomicEngineRoot=<path> 覆盖。
//   - imsdk 仅在 -PuseImSdkSource=true 时 include。
// ============================================================================

val useImSdkSource = providers.gradleProperty("useImSdkSource")
    .map(String::toBoolean)
    .getOrElse(false)
val useAtomicEngineAar = providers.gradleProperty("useAtomicEngineAar")
    .map(String::toBoolean)
    .getOrElse(false)

val atomicEngineRoot = providers.gradleProperty("atomicEngineRoot").orNull?.trim().orEmpty()
    .takeIf { it.isNotEmpty() }
    ?.let { path -> File(path).let { if (it.isAbsolute) it else File(settingsDir, path) } }
    ?: File(settingsDir, "../../../../../atomic_engine")

if (!useAtomicEngineAar) {
    val atomicEngineModuleDir = File(atomicEngineRoot, "src/atomic/kotlin/atomicengine")
    if (!atomicEngineModuleDir.isDirectory) {
        throw GradleException(
            "[Chat Demo] AtomicEngine source not found: $atomicEngineModuleDir. " +
                "Pass -PatomicEngineRoot=/absolute/path/to/atomic_engine " +
                "(or use -PuseAtomicEngineAar=true -PatomicEngineAarPath=<aar>)."
        )
    }

    include(":atomic_engine")
    project(":atomic_engine").projectDir = atomicEngineModuleDir

    include(":atomicEngineSource")
    project(":atomicEngineSource").projectDir =
        File(settingsDir, "source-build/atomicEngineSource")
}

if (useImSdkSource) {
    include(":imsdk")
    project(":imsdk").projectDir =
        File(settingsDir, "../../../../../imsdk/src/platform/android/imsdk")
}
