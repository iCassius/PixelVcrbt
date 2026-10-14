# 验证范围

2026-10-14，3.1.1-experimental。本项目为 LSPosed 彩铃模块；本轮未修改独立 KernelSU PixelIMS 项目。

## 构建与接口

采用 Maven Central 正式 libxposed API 102.0.0，编译依赖 SHA256 固定校验，API 类不打入 APK。入口与静态作用域使用 META-INF/xposed，移除全部 Legacy 入口、元数据与手写桩。最低 API 101，目标 API 102，作用域仅 Google Dialer；持有活动 Surface 会话时不启用热重载，更新后重启电话进程。

70 项 Java 策略断言、7 项更新元数据测试和 3 项 APK 封装测试通过。本地签名构建成功，沿用既有签名。完整源码另以正式 API 101.0.1 编译通过；API 101 框架运行行为未实机验证。

## 实机

Pixel 10 Pro XL，CP3A.260905.009，Dialer 20148463，LSPosed 2.2.0-it (7881)，框架 API 102。

- 管理器识别 Type: modern，min=101、target=102、Static scope: yes；仅 Google Dialer。
- 日志记录 modern_api_loaded api=102 与 hooks_ready。
- 一次授权未接听语音呼叫：requested_video=0、initial_video=0，收到视频彩铃，原生远端视图 1344×2792，截图确认画面位于按键下方。
- 进入全屏隐藏原生按钮，退出恢复五个按钮，跨过巡检仍持续显示；原生 disabled 状态保留。
- 挂断进入 idle，视频会话清理。

状态页显示功能开启、四项依赖已启用、模块已加载、IMS 已注册；更新服务可访问。原始日志与截图仅留私人工作目录。

## 限制

本次为未接听呼叫回归，接通后的语音／主动视频质量尚未验收。系统通话记录按 RX 视频历史分类的问题未修复；没有扩大 LSPosed 作用域。未新增紧急呼叫、OTA 或 SIM 热拔插验证。
