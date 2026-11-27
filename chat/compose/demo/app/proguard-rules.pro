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
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,Exceptions,SourceFile,LineNumberTable

# Keep Tencent Cloud SDKs (IMSDK / LiteAV / AtomicEngine / TUICore / TIMPush / AIDesk, etc.) using JNI and reflection
-keep class com.tencent.** { *; }
-keep class com.tencentcloud.** { *; }
-keep class io.trtc.** { *; }
-dontwarn com.tencent.**
-dontwarn com.tencentcloud.**
-dontwarn io.trtc.**

# Keep Gson TypeToken generic signatures for EngineBridge.parseJSON and other TypeToken subclasses
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep annotations
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Enum fields
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# WebView JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Parcelable & Serializable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Custom Views inflated from XML
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Vendor push SDKs
-keep class com.huawei.hms.** { *; }
-keep class com.hihonor.push.** { *; }
-keep class com.xiaomi.** { *; }
-keep class com.vivo.push.** { *; }
-keep class com.vivo.vms.** { *; }
-keep class com.heytap.msp.** { *; }
-keep class com.meizu.cloud.** { *; }
-dontwarn com.huawei.**
-dontwarn com.hihonor.**
-dontwarn com.xiaomi.**
-dontwarn com.vivo.**
-dontwarn com.heytap.**
-dontwarn com.meizu.**