import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0"
}

android {
    namespace = "io.trtc.tuikit.chat.uikit.compose"
    compileSdk = 35

    defaultConfig {
        minSdk = 21

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
    }

    fun getResDirs(): List<String> {
        val basePath = "src/main"
        val baseDir = file("src/main")
        return listOf("$basePath/res") +
                (baseDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("res-") }
                    ?.map { "$basePath/${it.name}" } ?: emptyList())
    }

    sourceSets {
        named("main") {
            res.setSrcDirs(getResDirs())
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    // AtomicxCore is provided transitively by :atomic_x (api atomicxCoreSdk),
    // same as the view uikit; no direct atomicx-core declaration here.
    implementation(project(":atomic_x"))
    implementation("io.trtc.uikit:albumpicker:1.0.0.+")
    implementation("com.tencent.imsdk:imsdk-plus:latest.release")
    implementation("org.ahocorasick:ahocorasick:0.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.coil-kt.coil3:coil-gif:3.1.0")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
    implementation("com.github.bumptech.glide:glide:4.12.0")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.media3:media3-ui-compose:1.6.1")
    implementation("com.tencent.liteav.tuikit:tuicore:9.0.7652") {
        exclude("com.tencent.imsdk", "imsdk-plus")
    }
    implementation("com.tencent:mmkv:1.3.14")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.foundation:foundation:1.8.0")
    implementation("androidx.compose.ui:ui:1.8.0")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.google.android.material:material:1.12.0")
}