# modules 模块 ProGuard 消费者规则
# 当其他模块依赖 modules 模块时，自动应用这些规则

-keep class com.tencent.rtcube.modules.call.model.** { *; }
-keep class com.tencent.rtcube.modules.aiconversation.model.** { *; }
-keep class com.tencent.rtcube.modules.live.** { *; }
