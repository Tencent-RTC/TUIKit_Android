# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

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

-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}

-keep public class * extends com.qq.taf.jce.JceStruct{*;}

-keep public class com.qq.jce.*{
public * ;
protected * ;
}

-keepclasseswithmembernames class * {
    native <methods>;
}

-keep public interface com.tencent.feedback.eup.jni.NativeExceptionHandler{
*;
}
-keep public class com.tencent.feedback.eup.jni.NativeExceptionUpload{
*;
}
-keep public class com.tencent.bugly.crashreport.crash.jni.NativeExceptionHandler {
    *;
}

-keep class com.tencent.cloud.huiyan.** {*;}
-keep class com.tencent.youtu.** {*;}
-keep class com.tencent.turingcam.** {*;}
-keep class com.tencent.turingfd.** {*;}
-keep class com.tencent.turingface.** {*;}
-keep class com.tenpay.utils.**{*;}
-keep class com.tencent.cloud.aicamare.** {*;}
-keep class com.tencent.cloud.component.** {*;}
-keep class com.tencent.cloud.ai.network.** {*;}
-keep class trpc.engine.yishan_websocket.** {*;}

# mini program
-dontwarn com.tencent.tmfmini.sdk.**
-dontwarn org.brotli.dec.**
-dontwarn org.java_websocket.**