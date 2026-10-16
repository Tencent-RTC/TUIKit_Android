# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class com.tencent.** { *; }
-keep class com.tencent.beacon.** { *; }
-keep class com.tencent.cloud.iai.lib.** { *; }
-keep class com.tencent.qimei.** { *; }
-keep class com.tencent.xmagic.** { *; }
-keep class com.trtc.uikit.livekit.component.gift.store.model.** { *; }
-keep class com.trtc.uikit.livekit.livestreamcore.** { *; }
-keep class com.tcmediax.** { *; }

# Google 序列化/反序列化框架 Gson 库相关混淆
-keep class com.google.gson.** { *; }

# 美颜特效相关混淆
-keep class androidx.exifinterface.** { *; }
-keep class com.gyailib.** { *; }
-keep class org.extra.** { *; }
-keep class org.libpag.** { *; }
-keep class org.light.** { *; }

# 播放礼物 SVG 动画相关混淆
-keep class com.squareup.wire.** { *; }
-keep class com.trtc.uikit.livekit.common.gift.player.svgaplayer.proto.** { *; }
