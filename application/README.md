# RTCube

English | [简体中文](README.cn.md)

## Overview

RTCube is a powerful UI component library built on top of the Tencent Cloud `AtomicXCore` SDK. `AtomicXCore` consolidates the core capabilities of Tencent Cloud Real-Time Communication (TRTC), Instant Messaging (IM), Audio/Video Calling (TUICallEngine), and Room Management (TUIRoomEngine), offering a state-driven API design.

On top of the core capabilities provided by `AtomicXCore`, RTCube delivers a set of ready-to-use user interfaces (UI), allowing you to quickly integrate interactive video live streaming, voice chat rooms, audio/video calling, and more into your Android application—without worrying about complex backend logic or state management.

## Features

Based on `AtomicXCore`, RTCube provides complete UI implementations for the following core business scenarios:

  * **Video/Voice Live Streaming:**

      * **Live List Management:** Fetch the list of live streams.
      * **Go Live & Watch:** Create live rooms and join live streams.
      * **Seat Management:** Manage seats; audience members can take or leave the mic.
      * **Co-hosting:** Support cross-room co-hosting between hosts.
      * **Host Battle (PK):** Support interactive PK between hosts.
      * **Interactive Features:**
          * **Gifts:** Send and receive gifts.
          * **Likes:** Send likes in the live room.
          * **Barrage (Danmaku):** Send and receive barrage messages.

  * **Audio/Video Calling:**

      * **Basic Calls:** Support 1v1 and multi-party audio/video calls.
      * **Call Management:** Support answering, rejecting, and hanging up calls.
      * **Device Management:** Control the camera and microphone during calls.
      * **Call History:** Query and delete call records.

  * **Multi-party Meetings (Room):**

      * **Quick Meetings:** One-click creation and joining of multi-party meetings.
      * **Invite to Meeting:** Invite members to join the current meeting.
      * **In-meeting Controls:** Hosts can manage members' audio/video, seats, and member list.
      * **Screen Sharing:** Support screen sharing during meetings.

## Quick Start

### 1. Prerequisites

  * Android Studio Giraffe (2022.3.1) or later
  * JDK 17
  * Android Gradle Plugin 8.x
  * Android 7.0 (API 24) or later

### 2. Clone the Repository

```bash
git clone https://github.com/Tencent-RTC/TUIKit_Android.git
```

### 3. Open the Project

Open `TUIKit_Android/application` with Android Studio and let Gradle sync the dependencies. All required AtomicXCore component modules (`tuilivekit`, `tuicallkit-kt`, `tuiroomkit`, etc.) are wired in through the top-level `settings.gradle`.

### 4. Configure & Run

Open `application/app/src/main/kotlin/com/tencent/rtcube/v2/debug/GenerateTestUserSig.kt`, fill in your Tencent Cloud `SDKAPPID` and `SECRETKEY`, then run the `app` module.

## Architecture

The architecture of `RTCube` follows a layered design:

1.  **TUILiveKit / TUICallKit / TUIRoomKit (UI Layer):**

      * Provides prebuilt, reusable UI components.
      * Handles view presentation and user interaction.
      * Subscribes to `Store`s in `AtomicXCore` to retrieve state and update the UI.
      * Calls `Store` methods in `AtomicXCore` to respond to user actions.

2.  **AtomicXCore (Core Layer):**

      * **Stores:** (e.g., `LiveListStore`, `CallListStore`, `ConversationListStore`) Manage business logic and state.
      * **Core Views:** (e.g., `LiveCoreView`, `ParticipantView`) Provide UI-less view containers that drive video rendering.
      * **Engine Wrappers:** Wrap the underlying `RTCRoomEngine`, `TUICallEngine`, and `IMSDK`, providing a unified API.

3.  **Tencent Cloud SDK (Engine Layer):**

      * `RTCRoomEngine` & `TUICallEngine`: Provide low-level real-time audio/video capabilities.
      * `IMSDK`: Provides instant messaging capabilities.

## Documentation

* [AtomicXCore Documentation](https://tencent-rtc.github.io/TUIKit_Android/documentation/atomicxcore)
* [Official Documentation - Quick Integration Guide](https://cloud.tencent.com/document/product/647/106536)

## License

This project is licensed under the [MIT License](https://www.google.com/search?q=LICENSE).

-----
