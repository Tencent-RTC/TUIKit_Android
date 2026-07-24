# TUIKit_Android

English | [简体中文](README.cn.md)

## Overview

TUIKit_Android is a powerful UI component library built on top of Tencent Cloud's `AtomicXCore` SDK. `AtomicXCore` integrates the core capabilities of Tencent Cloud Real-Time Communication (TRTC), Instant Messaging (IM), Audio/Video Calling (TUICallEngine), and Room Management (TUIRoomEngine), providing a state-driven API design.

TUIKit_Android provides a set of pre-built user interfaces (UI) on top of the core capabilities offered by `AtomicXCore`, enabling you to quickly integrate video live streaming, voice chat rooms, audio/video calling, and other features into your Android applications without worrying about complex backend logic and state management.

## Features

TUIKit_Android provides complete UI implementations for the following core business scenarios based on `AtomicXCore`:

* **Video/Voice Live Streaming:**

    * **Live Room Management:** Fetch live room lists.
    * **Broadcasting & Watching:** Create live rooms, join live streams.
    * **Seat Management:** Support seat management, audience mic on/off.
    * **Host Co-hosting:** Support cross-room co-hosting between hosts.
    * **Host PK (Battle):** Support PK interactions between hosts.
    * **Interactive Features:**
        * **Gifts:** Support sending and receiving gifts.
        * **Likes:** Support live room likes.
        * **Barrage:** Support sending and receiving barrage messages.

* **Audio/Video Calling:**

    * **Basic Calling:** Support 1v1 and multi-party audio/video calls.
    * **Call Management:** Support answering, rejecting, and hanging up calls.
    * **Device Management:** Support camera and microphone control during calls.
    * **Call History:** Support querying and deleting call records.

* **Instant Messaging (Chat):**

    * **Conversation Management:** Support fetching and managing conversation lists.
    * **Message Sending/Receiving:** Support C2C (one-to-one) and Group chat scenarios, with multiple message types including text, images, voice, video, etc.
    * **Contact Management:** Support friend and blacklist management.
    * **Group Management:** Support group profile, group member, and group settings management.

* **Video Conference:**

    * **Cross-Platform Interoperability:** Supports seamless interconnection of TUIRoomKit components across multiple platforms including Android, iOS, Web, Flutter, HarmonyOS, and UniApp, ensuring barrier-free cross-end communication.
    * **Member Management:** Supports various room management functions such as mute all, disable video for all, mute/disable video for individual members, device requests & invitations, role management, and kicking out participants.
    * **Screen Sharing:** Powered by screen acquisition capabilities on all platforms polished with 3,000+ market applications, combined with exclusive AI encoding algorithms to deliver clearer images at a lower bitrate.
    * **Cloud Recording:** Enables users to record in scenarios such as video conferencing, online education, and live interactive broadcasting, meeting diverse needs like archiving and auditing important content.
    * **Barrage:** Supports sending and receiving barrage messages in Webinar rooms to enhance interactive experiences.

## Quick Start

### 1. Environment Setup

* Android 5.0 (API level 21) or higher
* Gradle 8.0 or higher

### 2. Clone Repository

```bash
git clone https://github.com/Tencent-RTC/TUIKit_Android.git
```

### 3. Install Dependencies

`TUIKit_Android` depends on `AtomicXCore`, which in turn depends on `RTCRoomEngine`. The dependencies are managed through Gradle.

```bash
cd TUIKit_Android/application
# Open in Android Studio and sync project
```

### 4. Run Project

Open the project in Android Studio, configure your Tencent Cloud SDKAppID, UserID, and UserSig (usually configured in the `GenerateTestUserSig` file), then build and run.

## Architecture

The architecture design of `TUIKit_Android` follows layered principles:

1. **TUIKit_Android (UI Layer):**

    * Provides pre-built, reusable UI components.
    * Responsible for view presentation and user interaction.
    * Subscribes to `Store` in `AtomicXCore` to get state and update UI.
    * Calls `Store` methods in `AtomicXCore` to respond to user operations.

2. **AtomicXCore (Core Layer):**

    * **Stores:** (such as `LiveListStore`, `CallListStore`, `ConversationListStore`) responsible for managing business logic and state.
    * **Core Views:** (such as `LiveCoreView`, `ParticipantView`) provide UI-less view containers that drive video rendering.
    * **Engine Wrapper:** Encapsulates underlying `RTCRoomEngine`, `TUICallEngine`, and `IMSDK`, providing unified APIs.

3. **Tencent Cloud SDK (Engine Layer):**

    * `RTCRoomEngine` & `TUICallEngine`: Provide underlying real-time audio/video capabilities.
    * `IMSDK`: Provides instant messaging capabilities.

## Documentation

* [AtomicXCore Documentation](https://tencent-rtc.github.io/TUIKit_Android/)
* [Official Documentation - Quick Integration Guide](https://trtc.io/document/60455?product=live&menulabel=uikit&platform=android)

## License

This project is licensed under the [MIT License](LICENSE).

---

## Project Structure

```
TUIKit_Android/
├── application/           # Demo application
│   ├── build.gradle
│   ├── settings.gradle
│   └── app/
├── atomic_x/             # AtomicX UI components
│   ├── src/              # Kotlin/Java source files
│   └── res/              # Android resources
├── call/                 # TUICallKit components
└── live/                 # Live streaming components
```

## Getting Started with Development

### Prerequisites

Before you begin development, ensure you have:

1. **Development Environment:**
   - Android 5.0+ (API level 21) deployment target
   - Gradle 8.0+ installed

2. **Tencent Cloud Account:**
   - SDKAppID from Tencent Cloud Console
   - Valid UserSig for testing

### Building from Source

1. **Clone and Setup:**

   ```bash
   git clone https://github.com/Tencent-RTC/TUIKit_Android.git
   cd TUIKit_Android/application
   # Open in Android Studio
   ```

2. **Configure Credentials:**
   - Open the project in Android Studio
   - Navigate to `GenerateTestUserSig.java` or `GenerateTestUserSig.kt`
   - Replace placeholder values with your actual SDKAppID and SecretKey

3. **Build and Run:**
   - Select your target device or emulator
   - Click the Run button or press Shift+F10

## Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details on how to submit pull requests, report issues, and contribute to the project.

### Development Workflow

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes following our [CONTRIBUTING](./CONTRIBUTING.md)
4. Add tests for your changes
5. Ensure all tests pass
6. Commit your changes (`git commit -m 'Add amazing feature'`)
7. Push to the branch (`git push origin feature/amazing-feature`)
8. Open a Pull Request

## Support

- **API Reference:** [AtomicXCore API](https://tencent-rtc.github.io/TUIKit_Android/)
- **Issues:** [GitHub Issues](https://github.com/Tencent-RTC/TUIKit_Android/issues)
- **Community:** [Tencent Cloud Developer Community](https://cloud.tencent.com/developer)

## Changelog

See [CHANGELOG.md](./CHANGELOG.md) for a detailed history of changes to this project.

## Acknowledgments

- Built with [Tencent Cloud TRTC](https://cloud.tencent.com/product/trtc)
- UI framework powered by [Android Jetpack](https://developer.android.com/jetpack)