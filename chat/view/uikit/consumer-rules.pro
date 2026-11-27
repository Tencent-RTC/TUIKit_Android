-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,Exceptions

# Tencent SDKs & AtomicX / AtomicEngine / Chat UIKit (JNI & reflection)
-keep class io.trtc.tuikit.chat.uikit.** { *; }
-keep class io.trtc.tuikit.atomicx.** { *; }
-keep class io.trtc.tuikit.atomicxcore.** { *; }
-keep class com.tencent.cloud.tuikit.engine.** { *; }
-keep class com.tencent.imsdk.** { *; }
-keep class com.tencent.liteav.** { *; }
-keep class com.tencent.trtc.** { *; }
-keep class com.tencent.rtmp.** { *; }
-keep class com.tencent.live2.** { *; }
-keep class com.tencent.ugc.** { *; }
-keep class com.tencent.qcloud.** { *; }
-keep class com.tencent.mmkv.** { *; }

-dontwarn io.trtc.**
-dontwarn com.tencent.cloud.tuikit.engine.**
-dontwarn com.tencent.imsdk.**
-dontwarn com.tencent.liteav.**
-dontwarn com.tencent.trtc.**
-dontwarn com.tencent.rtmp.**
-dontwarn com.tencent.live2.**
-dontwarn com.tencent.ugc.**
-dontwarn com.tencent.qcloud.**
-dontwarn com.tencent.mmkv.**

# Gson & TypeToken (used by EngineBridge, CallStore, RecentEmojiManager, etc.)
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep annotations
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Enum fields (used by EngineBridge IntEnumTypeAdapterFactory and Gson)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
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
