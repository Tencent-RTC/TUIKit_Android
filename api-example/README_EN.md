# AtomicXCore API Example Demo — Android

English | [中文](./README.md)

## Introduction

This project is the Android API example demo for the **AtomicXCore SDK**, showcasing the full range of core capabilities through four progressive stages — from basic streaming to advanced interactive live broadcasting. Built with Kotlin using the traditional View system and ViewBinding, it serves as a comprehensive reference for developers integrating the AtomicXCore SDK.

## Feature Overview

| Stage | Module | Description |
|:---:|:---|:---|
| 1 | **BasicStreaming** | Live stream creation/joining, camera/microphone management, video rendering |
| 2 | **Interactive** | Barrage messages, gift system (with SVGA animations), likes, beauty filters, audio effects |
| 3 | **MultiConnect** | Audience co-guest requests, host invitations, seat management, multi-person video |
| 4 | **LivePK** | Cross-room connection, PK battles, real-time scoring, battle result display |

> Each stage builds upon the previous one, progressively adding new capabilities.

## Tech Stack

| Category | Technology | Version |
|:---:|:---|:---|
| Language | Kotlin | 2.0.21 |
| Build Tool | Android Gradle Plugin | 8.5.2 |
| UI Framework | Android View + ViewBinding | — |
| Core SDK | AtomicXCore (`io.trtc.uikit:atomicx-core`) | latest.release |
| IM SDK | Tencent IM (`com.tencent.imsdk:imsdk-plus`) | 8.7.7201 |
| Design System | Material Design 3 | 1.12.0 |
| Image Loading | Coil | 2.7.0 |
| Animation Engine | SVGAPlayer | 2.6.1 |
| Min SDK | Android 8.0 (API 26) | — |
| Target SDK | Android 15 (API 35) | — |

## Architecture

### Architecture Pattern

The project adopts the **MVC + Store** pattern:

- **Store Pattern**: The AtomicXCore SDK exposes state (`StateFlow`) and operation methods through various Store singletons (e.g., `LoginStore`, `DeviceStore`, `BarrageStore`)
- **Activity Layer**: Directly interacts with Stores, subscribing to state changes via `lifecycleScope` + `collectLatest` to update the UI
- **Component Reuse**: Reusable UI components under the `components/` directory are shared across multiple Activities

### Project Structure

```
android/app/src/main/java/com/example/atomicxcore/
├── App.kt                          # Application entry point
├── SplashActivity.kt               # Splash screen
├── MainActivity.kt                 # Reserved main Activity
├── debug/
│   └── GenerateTestUserSig.kt      # Debug utility for local UserSig generation
├── components/                     # Reusable UI components
│   ├── AudioEffectSettingView.kt   # Audio effect panel (voice changer/reverb/ear monitor)
│   ├── BarrageView.kt              # Barrage message list + input
│   ├── BeautySettingView.kt        # Beauty filter panel (smooth/whiten/ruddy)
│   ├── CoHostUserListView.kt       # Available co-host list
│   ├── DeviceSettingView.kt        # Device management panel (camera/mic/mirror/quality)
│   ├── GiftAnimationView.kt        # Gift animation display (SVGA fullscreen + sliding barrage)
│   ├── GiftPanelView.kt            # Gift selection panel (grid display + send)
│   ├── LikeButton.kt               # Like button (heart particle effect)
│   ├── LocalizedManager.kt         # Localization manager (Chinese/English toggle)
│   ├── Role.kt                     # Role enum (ANCHOR/AUDIENCE)
│   ├── SettingPanelController.kt   # Generic BottomSheet panel container
│   └── TabbedSettingView.kt        # Tab container (device/beauty/audio)
├── utils/                          # Utility classes
│   ├── CompletionHandlers.kt       # SDK CompletionHandler lambda wrappers
│   ├── PermissionHelper.kt         # Unified permission management (camera/mic/bluetooth)
│   └── ViewExtensions.kt           # View extensions (status bar adaptation)
└── scenes/                         # Business scene pages
    ├── login/
    │   ├── LoginActivity.kt        # User login page
    │   └── ProfileSetupActivity.kt # Profile setup page (nickname + avatar)
    ├── featurelist/
    │   └── FeatureListActivity.kt  # Feature list home page (4 feature cards)
    ├── basicstreaming/
    │   └── BasicStreamingActivity.kt  # Stage 1: Basic Streaming
    ├── interactive/
    │   └── InteractiveActivity.kt     # Stage 2: Interactive
    ├── multiconnect/
    │   └── MultiConnectActivity.kt    # Stage 3: Multi-Connect
    └── livepk/
        └── LivePKActivity.kt          # Stage 4: Live PK Battle
```

### App Flow

```
SplashActivity (splash screen, 1-second logo display)
  │
  ▼
LoginActivity (enter UserID → SDK login)
  │
  ├─ Nickname empty ──→ ProfileSetupActivity (set nickname + avatar)
  │                          │
  │                          ▼
  └─ Nickname set ─────→ FeatureListActivity (4 feature cards)
                             │
                             ├─ Select role (Anchor / Audience) + Room ID
                             │
                             ├──→ BasicStreamingActivity  (Stage 1)
                             ├──→ InteractiveActivity     (Stage 2)
                             ├──→ MultiConnectActivity    (Stage 3)
                             └──→ LivePKActivity          (Stage 4)
```

## AtomicXCore SDK API Reference

### Stage 1: BasicStreaming — Basic Live Streaming

| Store | Key APIs | Functionality |
|:---|:---|:---|
| `LoginStore` | `login()`, `setSelfInfo()`, `loginState` | User authentication & state management |
| `LiveListStore` | `createLive()`, `joinLive()`, `endLive()`, `leaveLive()` | Live room lifecycle management |
| `DeviceStore` | `openLocalCamera()`, `openLocalMicrophone()`, `switchCamera()` | Local device control |
| `LiveCoreView` | `PUSH_VIEW` / `PLAY_VIEW` mode | Video rendering component |

### Stage 2: Interactive — Real-time Interaction

| Store | Key APIs | Functionality |
|:---|:---|:---|
| `BarrageStore` | `sendTextMessage()`, `barrageState.messageList` | Barrage message sending & receiving |
| `GiftStore` | `sendGift()`, `refreshUsableGifts()` | Gift system |
| `LikeStore` | `sendLike()`, `addLikeListener()` | Like interaction |
| `BaseBeautyStore` | `setSmoothLevel()`, `setWhitenessLevel()`, `setRuddyLevel()` | Beauty filter adjustment |
| `AudioEffectStore` | `setAudioChangerType()`, `setAudioReverbType()` | Audio effect control |

### Stage 3: MultiConnect — Audience Co-Guest

| Store | Key APIs | Functionality |
|:---|:---|:---|
| `CoGuestStore` | `applyForSeat()`, `inviteToSeat()`, `acceptApplication()` | Co-guest request management |
| `LiveSeatStore` | `openRemoteCamera()`, `kickUserOutOfSeat()` | Seat & remote device management |
| `LiveAudienceStore` | `fetchAudienceList()` | Audience list |
| `VideoViewAdapter` | `createCoGuestView()` | Video overlay delegate |

### Stage 4: LivePK — Live PK Battle

| Store | Key APIs | Functionality |
|:---|:---|:---|
| `CoHostStore` | `requestHostConnection()`, `acceptHostConnection()`, `exitHostConnection()` | Cross-room connection management |
| `BattleStore` | `requestBattle()`, `acceptBattle()`, `exitBattle()`, `battleState` | PK battle management & real-time scoring |

## Prerequisites

- **Android Studio**: Ladybug or later
- **JDK**: 17
- **Gradle**: 8.x
- **Min OS Version**: Android 8.0 (API 26)
- **Target OS Version**: Android 15 (API 35)

## Getting Started

### 1. Clone the Repository

```bash
git clone <repository-url>
cd atomic-api-example/android
```

### 2. Configure SDK Credentials

Edit `app/src/main/java/com/example/atomicxcore/debug/GenerateTestUserSig.kt` and fill in your Tencent Cloud application credentials:

```kotlin
const val SDKAPPID: Long = 0          // Replace with your SDKAPPID
const val SECRETKEY = ""               // Replace with your SECRETKEY
```

> ⚠️ **Security Note**: `SECRETKEY` is for local debugging only. In production, UserSig must be generated by your backend server. Never embed SECRETKEY in client release builds.

### 3. Build and Run

Open the `android/` directory in Android Studio, sync Gradle, and build to run.

## Permissions

The app requires the following permissions:

| Permission | Purpose |
|:---|:---|
| `INTERNET` | Network communication |
| `CAMERA` | Camera capture |
| `RECORD_AUDIO` | Microphone capture |
| `BLUETOOTH` / `BLUETOOTH_CONNECT` | Bluetooth headset connection |
| `FOREGROUND_SERVICE` | Background service |

## Localization

The project supports Chinese and English. Language can be switched from the settings menu on the feature list page:

- `res/values/strings.xml` — English (default)
- `res/values-zh-rCN/strings.xml` — Simplified Chinese
