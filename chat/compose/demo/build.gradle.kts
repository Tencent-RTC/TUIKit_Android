// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("com.android.library") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.dokka") version "1.9.10" apply false
}

buildscript {

    dependencies {
        val currentGradleVersion = GradleVersion.current()
        if (currentGradleVersion >= GradleVersion.version("8.9")) {
            classpath("com.android.tools.build:gradle:8.7.0")
        } else if (currentGradleVersion >= GradleVersion.version("8.6")) {
            classpath("com.android.tools.build:gradle:8.4.0")
        } else if (currentGradleVersion >= GradleVersion.version("8.0")) {
            classpath("com.android.tools.build:gradle:8.0.0")
        } else if (currentGradleVersion >= GradleVersion.version("7.2")) {
            classpath("com.android.tools.build:gradle:7.0.0")
        } else if (currentGradleVersion >= GradleVersion.version("6.7.1")) {
            classpath("com.android.tools.build:gradle:4.2.0")
        }
        classpath("com.google.gms:google-services:4.3.15")
        classpath("com.hihonor.mcs:asplugin:2.0.1.300")
        if (currentGradleVersion >= GradleVersion.version("7.0")) {
            classpath("com.huawei.agconnect:agcp:1.9.4.300")
        } else {
            classpath("com.huawei.agconnect:agcp:1.6.0.300")
        }
    }
}

apply(from = "source-build/source-dependencies.gradle.kts")
