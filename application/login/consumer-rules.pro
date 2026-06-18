# Login 模块 ProGuard 消费者规则
# 当其他模块依赖 login 模块时，自动应用这些规则

-keep class com.tencent.rtcube.v2.login.components.model.** { *; }
