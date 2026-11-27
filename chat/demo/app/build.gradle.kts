plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersionCode = (findProperty("VERSION_CODE") as String?
    ?: System.getenv("VERSION_CODE"))?.toIntOrNull() ?: 1
val appVersionName = (findProperty("VERSION_NAME") as String?
    ?: System.getenv("VERSION_NAME")) ?: "1.0"

// 打包 ABI：release 只打 arm64-v8a；
// debug 保留 armeabi-v7a + arm64-v8a（AtomicEngine 链接的 LiteAV C++ 库无 x86/x86_64，无法提供模拟器 ABI）。
// -Parm64Only=true 时 debug 也只打 arm64-v8a。
val arm64Only = providers.gradleProperty("arm64Only").map(String::toBoolean).getOrElse(false)

android {
    namespace = "io.trtc.tuikit.chat.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tencent.qcloud.tim.tuikit"
        minSdk = 23
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            ndk {
                abiFilters += if (arm64Only) listOf("arm64-v8a") else listOf("armeabi-v7a", "arm64-v8a")
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // so 在 APK 内 deflate 压缩存储（extractNativeLibs=true），减小 APK 下载体积
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":uikit"))
    implementation(project(":atomic_x"))
    implementation(project(":tuicallkit-kt"))
    implementation("com.tencent.imsdk:imsdk-plus:latest.release")
    implementation("com.tencent.imsdk:timquic-plugin:latest.release")
    implementation("com.tencent.liteav.tuikit:tuicore:9.0.+") {
        exclude("com.tencent.imsdk", "imsdk-plus")
    }

    implementation("com.tencentcloud.desk:aideskcustomer:latest.release")
    implementation("com.tencent:mmkv:2.4.0")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.+")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.google.code.gson:gson:2.9.1")
}

