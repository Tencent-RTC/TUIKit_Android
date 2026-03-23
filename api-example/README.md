# AtomicXCore API 示例 Demo — Android

[English](./README_EN.md) | 中文

## 项目简介

本项目是 **AtomicXCore SDK** 的 Android 端 API 示例 Demo，通过四个渐进式阶段完整展示了从基础推拉流到复杂互动直播的全部核心功能。项目采用 Kotlin 语言开发，使用传统 View 体系 + ViewBinding 构建 UI，适合开发人员快速了解和集成 AtomicXCore SDK。

## 功能概览

| 阶段 | 功能模块 | 说明 |
|:---:|:---|:---|
| 1 | **BasicStreaming** 基础推拉流 | 直播创建/加入、摄像头/麦克风管理、视频渲染 |
| 2 | **Interactive** 实时互动 | 弹幕消息、礼物系统（含 SVGA 动画）、点赞、美颜、音效 |
| 3 | **MultiConnect** 观众连线 | 观众申请上麦、主播邀请连线、麦位管理、多人视频 |
| 4 | **LivePK** 直播 PK 对战 | 跨房连线、PK 对战、实时积分、战斗结果展示 |

> 四个阶段层层递进，每个后续阶段都包含前一阶段的全部功能并增加新能力。

## 技术栈

| 类别 | 技术 | 版本 |
|:---:|:---|:---|
| 语言 | Kotlin | 2.0.21 |
| 构建工具 | Android Gradle Plugin | 8.5.2 |
| UI 框架 | Android View + ViewBinding | — |
| 核心 SDK | AtomicXCore (`io.trtc.uikit:atomicx-core`) | latest.release |
| IM SDK | 腾讯云 IM (`com.tencent.imsdk:imsdk-plus`) | 8.7.7201 |
| 设计系统 | Material Design 3 | 1.12.0 |
| 图片加载 | Coil | 2.7.0 |
| 动画引擎 | SVGAPlayer | 2.6.1 |
| 最低版本 | Android 8.0 (API 26) | — |
| 目标版本 | Android 15 (API 35) | — |

## 项目架构

### 架构模式

项目采用 **MVC + Store** 模式：

- **Store 模式**：AtomicXCore SDK 通过各种 Store 单例对象（如 `LoginStore`、`DeviceStore`、`BarrageStore` 等）暴露状态（`StateFlow`）和操作方法
- **Activity 层**：直接与 Store 交互，通过 `lifecycleScope` + `collectLatest` 订阅状态变化并更新 UI
- **组件化复用**：`components/` 目录下的可复用 UI 组件被多个 Activity 共享

### 目录结构

```
android/app/src/main/java/com/example/atomicxcore/
├── App.kt                          # Application 入口
├── SplashActivity.kt               # 启动页
├── MainActivity.kt                 # 备用主 Activity
├── debug/
│   └── GenerateTestUserSig.kt      # 调试用 UserSig 本地生成工具
├── components/                     # 可复用 UI 组件
│   ├── AudioEffectSettingView.kt   # 音效设置面板（变声/混响/耳返）
│   ├── BarrageView.kt              # 弹幕消息列表 + 输入框
│   ├── BeautySettingView.kt        # 美颜设置面板（磨皮/美白/红润）
│   ├── CoHostUserListView.kt       # 可连线主播列表
│   ├── DeviceSettingView.kt        # 设备管理面板（摄像头/麦克风/镜像/清晰度）
│   ├── GiftAnimationView.kt        # 礼物动画展示（SVGA 全屏 + 弹幕滑动）
│   ├── GiftPanelView.kt            # 礼物选择面板（网格展示 + 发送）
│   ├── LikeButton.kt               # 点赞按钮（爱心粒子动效）
│   ├── LocalizedManager.kt         # 本地化管理器（中英文切换）
│   ├── Role.kt                     # 角色枚举（ANCHOR/AUDIENCE）
│   ├── SettingPanelController.kt   # 通用 BottomSheet 面板容器
│   └── TabbedSettingView.kt        # Tab 切换容器（设备/美颜/音效）
├── utils/                          # 工具类
│   ├── CompletionHandlers.kt       # SDK CompletionHandler 的 Lambda 包装器
│   ├── PermissionHelper.kt         # 统一权限管理（相机/麦克风/蓝牙）
│   └── ViewExtensions.kt           # View 扩展函数（状态栏适配）
└── scenes/                         # 业务场景页面
    ├── login/
    │   ├── LoginActivity.kt        # 用户登录页
    │   └── ProfileSetupActivity.kt # 资料完善页（昵称 + 头像）
    ├── featurelist/
    │   └── FeatureListActivity.kt  # 功能列表首页（4 个功能卡片）
    ├── basicstreaming/
    │   └── BasicStreamingActivity.kt  # 阶段 1: 基础推拉流
    ├── interactive/
    │   └── InteractiveActivity.kt     # 阶段 2: 实时互动
    ├── multiconnect/
    │   └── MultiConnectActivity.kt    # 阶段 3: 观众连线
    └── livepk/
        └── LivePKActivity.kt          # 阶段 4: 直播 PK 对战
```

### 应用流程

```
SplashActivity (启动页，1 秒展示 Logo)
  │
  ▼
LoginActivity (输入 UserID → SDK 登录)
  │
  ├─ 昵称为空 ──→ ProfileSetupActivity (设置昵称 + 头像)
  │                    │
  │                    ▼
  └─ 昵称已设置 ──→ FeatureListActivity (4 个功能卡片)
                       │
                       ├─ 选择角色（主播 / 观众）+ 房间 ID
                       │
                       ├──→ BasicStreamingActivity  (阶段 1)
                       ├──→ InteractiveActivity     (阶段 2)
                       ├──→ MultiConnectActivity    (阶段 3)
                       └──→ LivePKActivity          (阶段 4)
```

## AtomicXCore SDK API 使用说明

### 阶段 1：BasicStreaming — 基础推拉流

| Store | 关键 API | 功能 |
|:---|:---|:---|
| `LoginStore` | `login()`, `setSelfInfo()`, `loginState` | 用户登录与状态管理 |
| `LiveListStore` | `createLive()`, `joinLive()`, `endLive()`, `leaveLive()` | 直播房间生命周期管理 |
| `DeviceStore` | `openLocalCamera()`, `openLocalMicrophone()`, `switchCamera()` | 本地设备控制 |
| `LiveCoreView` | `PUSH_VIEW` / `PLAY_VIEW` 模式 | 视频渲染组件 |

### 阶段 2：Interactive — 实时互动

| Store | 关键 API | 功能 |
|:---|:---|:---|
| `BarrageStore` | `sendTextMessage()`, `barrageState.messageList` | 弹幕消息收发 |
| `GiftStore` | `sendGift()`, `refreshUsableGifts()` | 礼物系统 |
| `LikeStore` | `sendLike()`, `addLikeListener()` | 点赞互动 |
| `BaseBeautyStore` | `setSmoothLevel()`, `setWhitenessLevel()`, `setRuddyLevel()` | 美颜调节 |
| `AudioEffectStore` | `setAudioChangerType()`, `setAudioReverbType()` | 音效控制 |

### 阶段 3：MultiConnect — 观众连线

| Store | 关键 API | 功能 |
|:---|:---|:---|
| `CoGuestStore` | `applyForSeat()`, `inviteToSeat()`, `acceptApplication()` | 连线请求管理 |
| `LiveSeatStore` | `openRemoteCamera()`, `kickUserOutOfSeat()` | 麦位与远端设备管理 |
| `LiveAudienceStore` | `fetchAudienceList()` | 观众列表 |
| `VideoViewAdapter` | `createCoGuestView()` | 视频覆盖层代理 |

### 阶段 4：LivePK — 直播 PK 对战

| Store | 关键 API | 功能 |
|:---|:---|:---|
| `CoHostStore` | `requestHostConnection()`, `acceptHostConnection()`, `exitHostConnection()` | 跨房连线管理 |
| `BattleStore` | `requestBattle()`, `acceptBattle()`, `exitBattle()`, `battleState` | PK 对战管理与实时积分 |

## 环境要求

- **Android Studio**: Ladybug 或更高版本
- **JDK**: 17
- **Gradle**: 8.x
- **最低系统版本**: Android 8.0 (API 26)
- **目标系统版本**: Android 15 (API 35)

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd atomic-api-example/android
```

### 2. 配置 SDK 凭证

编辑 `app/src/main/java/com/example/atomicxcore/debug/GenerateTestUserSig.kt`，填入你的腾讯云应用凭证：

```kotlin
const val SDKAPPID: Long = 0          // 替换为你的 SDKAPPID
const val SECRETKEY = ""               // 替换为你的 SECRETKEY
```

> ⚠️ **安全提示**: `SECRETKEY` 仅用于本地调试。生产环境中，UserSig 必须由后端服务生成，切勿将 SECRETKEY 嵌入客户端发布包中。

### 3. 构建运行

使用 Android Studio 打开 `android/` 目录，同步 Gradle 后即可编译运行。

## 权限说明

应用运行需要以下权限：

| 权限 | 用途 |
|:---|:---|
| `INTERNET` | 网络通信 |
| `CAMERA` | 摄像头采集 |
| `RECORD_AUDIO` | 麦克风采集 |
| `BLUETOOTH` / `BLUETOOTH_CONNECT` | 蓝牙耳机连接 |
| `FOREGROUND_SERVICE` | 后台保活 |

## 本地化支持

项目支持中英文双语切换，可在功能列表页面的设置菜单中切换语言：

- `res/values/strings.xml` — 英文（默认）
- `res/values-zh-rCN/strings.xml` — 简体中文
