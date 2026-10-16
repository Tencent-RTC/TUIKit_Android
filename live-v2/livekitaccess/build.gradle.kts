// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

extra["imSdk"] = "com.tencent.imsdk:imsdk-plus:8.7.7201"
extra["liteavSdk"] = "com.tencent.liteav:LiteAVSDK_Professional:13.5.0.20670"
extra["liteavScreenCaptureSdk"] = "com.tencent.liteav:LiteAVSDK_ScreenCapture:13.5.0.20670"
extra["atomicxCoreSdk"] = "io.trtc.uikit:atomicx-core:5.2.0.124"

