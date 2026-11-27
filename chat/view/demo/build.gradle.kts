buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.8.20")
        val currentGradleVersion = GradleVersion.current()
        val androidGradlePluginVersion = when {
            currentGradleVersion >= GradleVersion.version("8.9") -> "8.7.0"
            currentGradleVersion >= GradleVersion.version("8.7") -> "8.6.1"
            currentGradleVersion >= GradleVersion.version("8.6") -> "8.4.2"
            currentGradleVersion >= GradleVersion.version("8.4") -> "8.3.2"
            currentGradleVersion >= GradleVersion.version("8.2") -> "8.2.2"
            currentGradleVersion >= GradleVersion.version("8.0") -> "8.1.4"
            currentGradleVersion >= GradleVersion.version("7.2") -> "7.0.0"
            else -> "4.2.0"
        }
        classpath("com.android.tools.build:gradle:$androidGradlePluginVersion")

        // ===== Offline push (TIMPush) classpath =====
        // Vendor push plugins applied in push-config/push-app.gradle.kts. These
        // classpath entries live in buildscript {} and cannot be externalized via
        // apply(from = ...), so they are kept here and tagged for clarity.
        classpath("com.google.gms:google-services:4.3.15")
        classpath("com.hihonor.mcs:asplugin:2.0.1.300")
        if (currentGradleVersion >= GradleVersion.version("7.0")) {
            classpath("com.huawei.agconnect:agcp:1.9.4.300")
        } else {
            classpath("com.huawei.agconnect:agcp:1.6.0.300")
        }
        // ===== End offline push classpath =====
    }
}

plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("com.android.library") apply false
}

apply(from = "source-build/source-dependencies.gradle.kts")
