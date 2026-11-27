plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.trtc.tuikit.chat.uikit"
    compileSdk = 35

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    fun getResDirs(): List<String> {
        val basePath = "src/main"
        val baseDir = file("src/main")
        val dirs = listOf("$basePath/res") +
                (baseDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("res-") }
                    ?.map { "$basePath/${it.name}" } ?: emptyList())
        return dirs
    }

    sourceSets {
        named("main") {
            res.setSrcDirs(getResDirs())
        }
    }
}

dependencies {
    implementation(project(":atomic_x"))
    implementation("com.tencent.imsdk:imsdk-plus:latest.release")
    implementation("com.tencent.liteav:LiteAVSDK_VoiceAI:13.6.0.255")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("io.trtc.uikit:albumpicker:1.0.0.+")
    implementation("org.ahocorasick:ahocorasick:0.6.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.1")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("com.tencent:mmkv:2.4.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")

    testImplementation("junit:junit:4.13.2")
}
