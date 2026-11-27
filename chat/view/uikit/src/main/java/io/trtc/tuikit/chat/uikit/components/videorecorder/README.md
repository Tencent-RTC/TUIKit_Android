# Enable Aspect Ratio Settings

Aspect ratio settings are advanced features of VideoRecorder. To enable them, the following conditions must be met:

## 1. Depend on TXLiteAVSDK_Professional

Add the dependency to your project or any module's Gradle configuration:

```gradle
dependencies {
    api "com.tencent.liteav:LiteAVSDK_Professional:latest.release"
}
```

- If any module in your project already depends on TXLiteAVSDK_TRTC, replace it with TXLiteAVSDK_Professional (this will not affect other modules).
- After switching to TXLiteAVSDK_Professional, aspect ratio settings will be available, and overall compatibility and image quality will be improved.

## 2. Enable the "Advanced Multimedia Features" capability

The advanced multimedia features (video recording, audio recording, photo/video editing, etc.) require the corresponding capability to be enabled for your Tencent Cloud account. Enable it in the Tencent Cloud console, or contact Tencent Cloud support for activation.

## 3. Behavior in Different Build Configurations

- Release:
  - If the prerequisites are not met, the advanced features will not work even if enabled in the configuration (the related buttons are hidden automatically).
- Debug:
  - Tapping an unsupported feature shows a UI toast/dialog message.
  - To hide these features in Debug as well, disable the corresponding switches in the Config or the configuration file (see the configuration documentation).
