# TUIKit\_Android

[English](README.md) | 简体中文

## 概述

TUIKit\_Android 是一款功能强大的 UI 组件库，它基于腾讯云 `AtomicXCore` SDK 构建。`AtomicXCore` 整合了腾讯云实时音视频（TRTC）、即时通信（IM）、音视频通话（TUICallEngine） 和房间管理（TUIRoomEngine） 的核心能力，提供了状态驱动的（State-driven）API 设计。

TUIKit\_Android 在 `AtomicXCore` 提供的核心能力之上，为您提供了一套预制的用户界面（UI），使您无需关心复杂的后端逻辑和状态管理，即可快速为您的 Android 
应用集成视频互动直播、语音聊天室、音视频通话等功能。

## 功能特性

TUIKit\_Android 基于 `AtomicXCore` 提供了以下核心业务场景的完整 UI 实现：

  * **视频/语音直播 (Live Streaming):**

      * **直播列表管理:** 拉取直播列表。
      * **开播与观看:** 创建直播间、加入直播。
      * **麦位管理:** 支持麦位管理，观众上麦/下麦。
      * **主播连麦 (Co-hosting):** 支持主播与主播（跨房）连麦。
      * **主播 PK (Battle):** 支持主播间 PK 互动。
      * **互动功能:**
          * **礼物:** 支持发送和接收礼物。
          * **点赞:** 支持直播间点赞。
          * **弹幕:** 支持发送和接收弹幕消息。

  * **音视频通话 (Calling):**

      * **基础通话:** 支持 1v1 及多人音视频通话。
      * **通话管理:** 支持接听、拒绝、挂断。
      * **设备管理:** 支持通话中的摄像头和麦克风控制。
      * **通话记录:** 支持查询和删除通话记录。

  * **即时通讯 (Chat):**

      * **会话管理:** 支持会话列表的拉取和管理。
      * **消息收发:** 支持 C2C（单聊） 和 Group（群聊） 场景，支持文本、图片、语音、视频等多种消息类型。
      * **联系人管理:** 支持好友和黑名单管理。
      * **群组管理:** 支持群资料、群成员和群设置管理。

  * **视频会议 (Room):**

      * **平台互通:** 支持 Android、iOS、Web、Flutter、Harmony、UniApp 等多平台 TUIRoomKit 组件互联互通，跨端沟通无障碍。
      * **成员管理:** 支持全体静音、全体禁画、单一成员禁言禁画、设备申请与邀请、角色管理、踢出房间等多个房间管理功能。
      * **屏幕分享:** 基于 3000+ 家市场应用共同打磨的各平台屏幕采集能力，搭配专属 AI 编码算法，更低码率更清晰的画面。
      * **云端录制:** 支持用户在视频会议、在线教育、直播互动等场景中进行录制，满足对重要内容进行存档、审核等多样化需求。
      * **弹幕:** Webinar 房间支持发送和接收弹幕消息，增强互动体验。

## 快速开始

### 1\. 环境准备

  * Android 5.0 (API level 21) 或更高版本
  * Gradle 8.0 或更高版本

### 2\. 克隆仓库

```bash
git clone https://github.com/Tencent-RTC/TUIKit_Android.git
```

### 3\. 安装依赖

`TUIKit_Android` 依赖 `AtomicXCore`，而 `AtomicXCore` 依赖于 `RTCRoomEngine`。依赖项通过 Gradle 进行管理。

```bash
cd TUIKit_Android/application
# 在 Android Studio 中打开并同步项目
```

### 4\. 运行项目

在 Android Studio 中打开项目，配置您的腾讯云 SDKAppID、UserID 和 UserSig（通常在 `GenerateTestUserSig` 文件中配置），然后构建并运行。

## 架构

`TUIKit_Android` 的架构设计遵循分层原则：

1.  **TUIKit\_Android (UI 层):**

      * 提供预制的、可复用的 UI 组件。
      * 负责视图（View）的展示和用户交互。
      * 订阅 `AtomicXCore` 中的 `Store` 来获取状态并更新 UI。
      * 调用 `AtomicXCore` 中的 `Store` 方法来响应用户操作。

2.  **AtomicXCore (核心层):**

      * **Stores:** (如 `LiveListStore`, `CallListStore`, `ConversationListStore`) 负责管理业务逻辑和状态（State）。
      * **Core Views:** (如 `LiveCoreView`, `ParticipantView`) 提供了驱动视频渲染的无 UI 视图容器。
      * **Engine 封装:** 封装了底层的 `RTCRoomEngine`, `TUICallEngine` 和 `IMSDK`，提供统一的 API。

3.  **Tencent Cloud SDK (引擎层):**

      * `RTCRoomEngine` & `TUICallEngine`: 提供底层的实时音视频能力。
      * `IMSDK`: 提供即时通讯能力。
  
## 文档

* [AtomicXCore 文档](https://tencent-rtc.github.io/TUIKit_Android/)
* [官方文档 - 快速集成指南](https://cloud.tencent.com/document/product/647/106537)

## 许可证

本项目遵循 [MIT 许可证](https://www.google.com/search?q=LICENSE)。

-----