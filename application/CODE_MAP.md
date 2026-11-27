# Android application Code Map

综合 App 的 Gradle 根目录为本目录，模块为 `app`；`debug` 只提供开发签名配置。接入与构建见 [README](README.md)。

## 登录与五 Tab

`login/LoginActivity` 通过真实 AtomicXCore `LoginStore` 登录，缺失资料时进入 `RegisterActivity`，否则进入 `main/MainActivity`。登录成功时初始化已有配置中的礼物播放 License。App 不依赖独立接入 App 的 Application、登录或签名实现。

`MainActivity` 默认选中直播，五个 Tab 依次为会议、直播、通话、消息、我的。`home/HomeTabBar` 只负责选中态、未读与点击；每个 `HomeBranchFragment` 独立持有 child FragmentManager，Tab 切换保留各自导航栈。直播根页的 App 内 `LiveHomePlayerFragment` 使用 add 叠加时，底页保留视图并降至 STARTED，停用请求/订阅/预热；顶部房间页承接点击，底页隐藏无障碍节点。退出 pop 时底页恢复 RESUMED 和原滚动位置，淡出始终有真实列表承接。非 `voice_` 卡片点击与预览「进入」默认走 Kit `PlayerActivity` + `LiveListView`（`autoEnterRoom=true`），App 只传 liveID。根内容与底栏纵向布局，底栏 56dp 加实际 navigationBars inset；全屏子页隐藏底栏。主窗口使用 edge-to-edge 与 `adjustNothing`，页面各自处理系统栏和键盘。会议分支的页根、状态栏留白与底栏统一读取 Room Kit 的 `roomkit_bg_canvas`，避免 App 默认浅色与会议画布拼接。

`MainActivity` 持有真实 ConversationListStore 的总未读订阅、跨观众页进房请求屏障、会话级 `CallEntryState` 和 Call Kit 初始化；CallStore 的活跃通话状态经过去重后投影给直播 Feed，不复制通话业务事实。账号失效通常回登录，开播宿主内失效则延迟至宿主退出，使组件仍可展示结束/总结页。

## 通话

| 目录 / 类型 | 可达流程与职责 |
|---|---|
| `call/CallEntryFragment` | 通话分支根页，无独立返回；语音/视频、被叫 ID、好友选择、清晰度、字幕与真实 `TUICallKit.calls`；拨号防重并忽略离页后的界面更新 |
| `call/CallSettingsFragment` | 设置子页，通过当前 `HomeBranchFragment` push/pop，底栏常驻；资料订阅使用 `viewLifecycleOwner` |
| `call/entry/CallEntryState` | 由 MainActivity 持有，保存被叫 ID、房间号、超时、自定义信息和 UI 设置；不作为通话事实来源 |
| `call/entry/CallEntryView`、`FriendPickerSheet` | 入口控件与好友弹窗；显式共享 CallEntryState，系统返回优先关闭弹窗，离页取消 ContactStore 订阅 |
| `call/settings/CallSettingsView` | 复用 CallKitAccess 的资料、铃声、静音、来电横幅、浮窗、虚拟背景和高级美颜设置；直接委托 TUICallKit / LoginStore |
| `call/common/` | 通话入口与设置共用的 Sheet、Toast、Toggle、TapRow 和 inset 辅助；不包含独立登录或测试签名 |

Call Kit 的 `CallActivity`、前台服务和权限由 Library Manifest 合并；`MainActivity.onCreate` 即启动 CallLauncher，未访问通话 Tab 也能接电。通话 Activity 不随 Tab 切换挂断。Chat 内音视频入口仍保持隐藏。

## 会议、消息、我的

| 目录 / 类型 | 可达流程与职责 |
|---|---|
| `room/RoomHomeFragment` | 复用 Room Kit 首页 XML、头像与弹窗；快速会议、加入会议、预约占位、头像→设置。登录资料只读订阅随 RESUMED 生命周期运行 |
| `room/RoomQuickStartFragment` | 复用快速会议表单，真实 RoomStore 探测房号，防重与请求 token；创建前将名称、设备开关传给 TUIRoomKit MeetingActivity |
| `room/RoomJoinFragment` | 复用加入表单，将房号与设备开关传给 TUIRoomKit MeetingActivity；进入会议前清空会议分支子页 |
| `TUIRoomKit MeetingActivity` | Kit 公开薄宿主，组合 MeetingView；读取房间/设备参数、委托创建或加入，Kit Manifest 注册 singleTask/portrait/adjustNothing。会议状态与设备订阅仍由 Kit 持有 |
| `room/RoomSettingsFragment` | RoomSettingsView/RoomSettingsOptionsView 的导航宿主；设置业务仍由 Kit 持有 |
| `chat/HomeMessagesFragment` | ConversationsPageView、会话点击、搜索、ContactFlowLauncher 发起单聊/群聊；Kit 负责联系人选择和建群 |
| `chat/HomeChatFragment` | ChatPageView 的全屏宿主；支持定位消息、聊天设置与成员资料，隐藏音视频通话入口 |
| `chat/HomeChatSettingsFragment` | 真实单聊/群聊设置组件；删除回消息根页、成员资料发消息进入对应会话 |
| `chat/HomeSearchFragment` | SearchView 的全屏宿主；联系人、群、会话与消息结果进入聊天 |
| `mine/MineFragment` | 复用本 App 我的页面，根页无返回按钮；设置在本分支 push，日志分享使用本 App FileProvider，退出清除自动登录 |
| `mine/SettingsFragment`、`UserManager` | 复用本 App 头像/昵称编辑；资料修改统一委托 LoginStore.setSelfInfo，SDK 为事实来源。SettingsActivity 保留兼容壳 |

Room 接入页复用已获用户授权；只组合 `tuiroomkit` Library，不引入 `roomkitaccess` App。消息只组合 Chat View Kit 与 atomic_x，不引入 Chat 接入 App。现存旧 MainFragment/ComingActivity 不再由正式首页导航使用。

## 直播首页与观众

代码位于 `live/home/`，与 iOS App `Live/Home` 保持同等职责：

| 类型 | 状态、调用链与生命周期 |
|---|---|
| `LiveHomeListStore` | 每个列表独立持有游标、加载/错误、防重与请求 token；调用真实 LiveListStore.fetchLiveList，主线程应用快照。离页失效请求，直达页复制独立投影 |
| `LiveHomeFragment` | 根列表与预览模式、各自滚动位置、顶部栏事件、开播/直达路由；RESUMED 才订阅、请求与播放 |
| `LiveHomeHeaderView` | 列表/预览选择与开播入口；不调用 SDK |
| `LiveHomeListView` / `LiveHomeItemView` | RecyclerView 双列、刷新/分页/空错重试、卡片信息与图片；图片按 URL 去重，隐藏/移除清理请求 |
| `LiveHomePreviewItemView` | 封面、预览信息与进入按钮；完整 Window 高画布和实际分页视口分开布局；不持有播放器会话 |
| `LiveHomePreviewView` | ViewPager2 Feed，最多当前及相邻三个 PlayerView 会话；当前出声、相邻静音预热。进房防重与迟到清理，连麦禁止翻页，离页释放预热，后台保留已入房当前会话，detach 全部清理；CallStore 活跃时释放播放，结束后仅恢复仍可见 Feed |
| `LiveHomePlayerFragment` | App 内全屏独立 Feed；当前主要由 `voice_` 卡片 open 事件或语音房切换进入；布局后自动进房，220ms 封面展开、文字原尺寸淡出，退出用160ms淡出回原双列位置，当前播放器在淡出后随 detach 清理；首帧先设置卡片真实边界，展开末段才淡入目标 Feed |

App 通过 owner 隔离的 EventManager 事件传递操作，业务事实仍由 Kit/AtomicXCore 持有。结束状态保留原 Player END 页；返回预览按钮仅在停稳且失败/结束时出现，拖动、惯性和进房中间态均隐藏；未进房预览不增加进房加载阶段。刷新重排不打断加入/已加入的当前房间。默认非 `voice_` 卡片点击与预览「进入」先 `FloatingPlayerManager.releaseSession(false)`，再启动 Kit `PlayerActivity` 并只传 liveID；Kit `LiveListView(autoEnterRoom=true)` 负责 Feed、分页、进房、关闭和旧 PiP 释放，App 不维护播放器会话。预览页通过 `isFloating(liveID)` 跳过小窗同房间的当前及相邻预览播放，并通过 `addFloatingDismissListener` 在同 `liveID` 小窗结束且本地未播放时恢复，避免浮窗与预览为同一路流创建两个播放器。

## 开播

`live/PusherHostFragment` 同时只持有 PusherView 或 VoiceLoungeView 一个实例；`BroadcastTypeBarView` 展示视频/语音/游戏，默认视频，游戏仅提示。切换时先结束编辑、清理旧组件，只有标题跨类型共享，设备、封面与 Store 各自拥有。

Kit 新增的 `prepareAccessoryView` 与 `prepareTitle` 仅服务准备阶段；倒计时禁止标题写入，实际发布与结束仍归组件。插槽布局和关闭图标适配在原组件 Prepare 文件内，说明见 [Live README](../live/README.md) 与 [Live Code Map](../live/CODE_MAP.md)。

## 资源与边界

App 首页布局为 `app_home_*` / `app_live_*` / `app_broadcast_*`；颜色与文案集中于 `home_colors` / `home_strings`。通话接入资源使用 `callaccess_*` 与独立 `callaccess_colors/strings`，不包含 CallKitAccess 的登录、测试签名或 launcher 资源。新增开播及关闭图标分别来自 Ardot 5074:2435、83:598、5074:2790，由 Android Studio Svg2Vector 转换，不使用旧工程图标替代。

Android 原生 View 无法直接对 SDK Surface 实时背景模糊；顶部预览选择器和开播入口因此使用半透明底色，保留与 iOS 的材质差异。多房播放、连麦、会议、通话和消息业务由对应 Kit 负责，App 不复制 SDK 业务状态。
