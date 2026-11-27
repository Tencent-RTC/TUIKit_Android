import java.util.Properties
import org.gradle.api.tasks.Exec

plugins {
    id("com.android.library")
}

android {
    namespace = "io.trtc.tuikit.chat.atomicengine.source"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }
}

// This module wraps the AtomicEngine native source build. When :atomic_engine is
// included from source, the task below compiles libatomicengine.so into
// <atomicEngineRoot>/src/atomic/kotlin/atomicengine/src/main/jniLibs/<abi>/ so
// :atomic_engine packages it via its jniLibs.srcDirs. The runtime libliteavsdk.so
// comes from the LiteAVSDK_Professional Maven AAR (it exports the C API that
// libatomicengine.so needs); <atomicEngineRoot>/sdk/android/{im,trtc} only
// provides compile/link inputs and must already be populated.
//
// -PatomicEngineRoot=<path> overrides the AtomicEngine checkout; the default
// resolves the monorepo sibling layout relative to the demo root.
val atomicEngineRootProp = providers.gradleProperty("atomicEngineRoot").orNull?.trim().orEmpty()
val engineDir = if (atomicEngineRootProp.isNotEmpty()) {
    val given = file(atomicEngineRootProp)
    (if (given.isAbsolute) given else file("$rootDir/$atomicEngineRootProp")).canonicalFile
} else {
    file("$rootDir/../../../../../atomic_engine").canonicalFile
}
val androidSdkDir: String? = run {
    val props = Properties()
    val localProps = file("$rootDir/local.properties")
    if (localProps.exists()) {
        localProps.inputStream().use { props.load(it) }
    }
    props.getProperty("sdk.dir")
        ?: System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
}
// The link-time LiteAV libs under sdk/android/trtc/libs only cover arm64-v8a /
// armeabi-v7a; x86 / x86_64 would fail at link time.
val arm64Only = providers.gradleProperty("arm64Only").map(String::toBoolean).getOrElse(false)
val atomicEngineAbis = if (arm64Only) "arm64-v8a" else "armeabi-v7a,arm64-v8a"
val atomicEngineNdkVersion = providers.gradleProperty("atomicEngineNdkVersion").getOrElse("")
val buildSoScript = file("$projectDir/build_atomic_engine_so.sh")

tasks.register<Exec>("buildAtomicEngineNativeSo") {
    group = "build"
    description = "Compile libatomicengine.so from AtomicEngine source."
    workingDir = engineDir
    val sdkDir = androidSdkDir
        ?: throw GradleException("Android SDK not found: set sdk.dir in demo/local.properties or export ANDROID_HOME.")
    commandLine(
        "bash",
        buildSoScript.absolutePath,
        engineDir.absolutePath,
        sdkDir,
        atomicEngineAbis,
        atomicEngineNdkVersion
    )
}

afterEvaluate {
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn("buildAtomicEngineNativeSo")
    }
}
