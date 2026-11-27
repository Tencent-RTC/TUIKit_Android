# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html
#
# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
#
# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable
#
# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# 腾讯云 SDK（IMSDK / LiteAV / AtomicEngine / TUICore / AIDesk 等）含 JNI 与反射，整体保留
-keep class com.tencent.** { *; }
-keep class com.tencentcloud.** { *; }
-keep class io.trtc.** { *; }
-dontwarn com.tencent.**
-dontwarn com.tencentcloud.**
-dontwarn io.trtc.**

# Gson TypeToken：AtomicEngine 的 EngineBridge.parseJSON 等处用匿名子类 TypeToken<...>() {} 取泛型。
# gson 不自带混淆规则时，R8 会裁掉子类的泛型签名，构造时抛异常被吞成 null，
# 引擎事件全部解析失败（表现为登录后 loginStatus 一直 UNLOGIN，登录页与首页循环跳转）。
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
