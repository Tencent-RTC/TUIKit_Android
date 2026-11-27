import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

apply(plugin = "com.google.gms.google-services")
apply(plugin = "com.huawei.agconnect")
apply(plugin = "com.hihonor.mcs.asplugin")

// Packaging ABIs: release packages arm64-v8a only; debug keeps armeabi-v7a + arm64-v8a
// (the LiteAV 13.x C++ SDK ships no x86/x86_64 .so). Pass -Parm64Only=true for arm64-v8a only in debug.
val arm64Only = providers.gradleProperty("arm64Only").map(String::toBoolean).getOrElse(false)

val appVersionCode = (findProperty("VERSION_CODE") as String?
    ?: System.getenv("VERSION_CODE"))?.toIntOrNull() ?: 1
val appVersionName = (findProperty("VERSION_NAME") as String?
    ?: System.getenv("VERSION_NAME")) ?: "1.0"

android {
    namespace = "io.trtc.tuikit.chat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tencent.qcloud.tim.tuikit"
        minSdk = 23
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        manifestPlaceholders += mapOf(
            "VIVO_APPKEY" to "f91c42f5519e50fdad0bb88b2268f632",
            "VIVO_APPID" to "103904657",
            "HONOR_APPID" to "220765771"
        )
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
        }
        create("prod") {
            dimension = "env"
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf(
                "**/libatomicengine.so",
                "**/libImSDK.so",
                "**/libc++_shared.so",
                "**/libliteavsdk.so",
                "**/libtxffmpeg.so",
                "**/libtxsoundtouch.so",
                "**/libtcpcore-master.so",
                "**/libtcpdownloadproxy.so",
                "**/libtcpthirdparties-master.so",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(project(":uikit"))
    implementation(project(":atomic_x"))
    implementation(project(":tuicallkit-kt"))
    implementation("com.tencent.imsdk:imsdk-plus:latest.release")
    implementation("com.tencent.imsdk:timquic-plugin:latest.release")
    implementation("com.tencentcloud.desk:aideskcustomer:2.6.0")

    // TIMPush vendor artifacts are not published as 9.0.7653; 9.0.7652 is the
    // newest exact version available for the main package and all vendor packages.
    implementation("com.tencent.timpush:timpush:9.0.7652")
    implementation("com.tencent.liteav.tuikit:tuicore:9.0.7652") {
        exclude("com.tencent.imsdk", "imsdk-plus")
    }
    implementation("com.tencent.timpush:huawei:9.0.7652")
    implementation("com.tencent.timpush:xiaomi:9.0.7652")
    implementation("com.tencent.timpush:oppo:9.0.7652")
    implementation("com.tencent.timpush:vivo:9.0.7652")
    implementation("com.tencent.timpush:honor:9.0.7652")
    implementation("com.tencent.timpush:meizu:9.0.7652")
    implementation("com.tencent.timpush:fcm:9.0.7652")

    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.tencent:mmkv:1.3.14")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.foundation:foundation:1.8.0")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui:1.8.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.navigation:navigation-runtime-ktx:2.8.9")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("io.coil-kt.coil3:coil-gif:3.1.0")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
}