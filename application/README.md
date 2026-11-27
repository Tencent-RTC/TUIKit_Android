# Android 综合 App

以 `ios/application` 为产品基准的五 Tab 接入 App：会议、直播、通话、消息、我的。通话 Tab 已接入真实 Call Kit，并支持全局来电路由；Flutter 不在本轮同步范围。代码位置与状态边界见 [CODE_MAP](CODE_MAP.md)。

## 环境与依赖

- JDK 17 或 Android Studio JBR（本次使用 Studio JBR 21）。
- Android SDK 35，minSdk 23，targetSdk 35；Gradle Wrapper 8.14、AGP 8.3.1、Kotlin 2.0.21。
- AtomicXCore 5.3.2.0、IM 9.1.7824、LiteAV Professional / ScreenCapture 13.5.0.20670，根 Gradle 对组合库统一这些版本。
- 仓库源码 Library：`../live/tuilivekit`、`../room/tuiroomkit`、`../call/tuicallkit-kt`、`../chat/view/uikit`、`../chat/atomic_x`、`../tceffectplayerkit`。不组合各组件独立接入 App，不依赖仓库外本地 SDK 源码。

在 `debug` 模块原有 GenerateTestUserSig 中配置测试账号签名及礼物播放 License，保持密钥与本地配置不提交。该方式仅用于当前调试工程。

## 构建与安装

从仓库根目录执行：

```bash
cd android/application
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

首次构建需要网络拉取 Maven/Gradle 依赖；缓存完整后可加 `--offline`。本机 Android SDK 也可通过不提交的 `local.properties` 配置。

产物为 `app/build/outputs/apk/debug/app-debug.apk`。统一交付目录使用仓库忽略的 `build/android/`：

```bash
mkdir -p ../../build/android
cp app/build/outputs/apk/debug/app-debug.apk ../../build/android/app_kotlin_android_1.0.0.apk
adb install -r ../../build/android/app_kotlin_android_1.0.0.apk
adb shell am start -n com.trtc.uikit.livekit.example/com.tencent.uikit.app.login.LoginActivity
```

当前 applicationId 为既有 `com.trtc.uikit.livekit.example`，安装将更新同包名测试 App 并保留数据。根 `scripts/build_apk.sh application debug|release` 也支持综合 App：Debug 产出 `app_kotlin_android_<三段版本>.apk`（调试证书），Release 产出 `app_kotlin_android_<三段版本>_unsigned.apk`（需在流水线完成签名）。文件名三段版本由 `APP_VERSION` 或工程 `versionName` 提取，构建号仍保留在 App 元数据中。

## 验证边界

编译、lint、设备执行状态及逐单元模版依据见 [Android 执行计划](../../docs/task/android/application-sync-plan.md)。编译通过不等于真实播放、开播、会议、双向呼叫、来电或消息收发已验证。预览顶部材质与 iOS 的实时背景模糊存在平台差异，其他页面继续复用相应 Kit 的正式实现。
