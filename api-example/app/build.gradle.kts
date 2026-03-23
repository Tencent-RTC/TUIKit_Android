plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Support BlueShield pipeline injecting version numbers dynamically via -P parameter
val appVersionName: String = project.findProperty("appVersionName") as? String ?: "1.0.0"
val appVersionCode: Int = (project.findProperty("appVersionCode") as? String)?.toIntOrNull() ?: 1

android {
    namespace = "com.example.atomicxcore"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.atomicxcore"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Material Design 3
    implementation("com.google.android.material:material:1.12.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")

    // Coil (non-Compose version)
    implementation("io.coil-kt:coil:2.7.0")

    // AtomicXCore SDK
    implementation("io.trtc.uikit:atomicx-core:latest.release")

    // Tencent IM SDK
    implementation("com.tencent.imsdk:imsdk-plus:8.7.7201")

    // SVGAPlayer - for playing full-screen gift special effect animations
    implementation("com.github.yyued:SVGAPlayer-Android:2.6.1")
}
