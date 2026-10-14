# 按键与通话记录检查

2026-10-14，2.3.2-experimental。

## 按键回归

2.0.5／2.0.6 的 APK DEX 无 presentEarlyMedia 及三个视频操作按钮名称；2.0.8 首次加入主动隐藏逻辑，之后保留至 2.3.1。模块每秒检查彩铃显示，原生 UI 切换全屏后恢复按钮，模块又将 second／third／fourth 按钮设为 GONE，造成短暂出现后消失。

2.3.2 移除按钮隐藏和恢复逻辑，仅保留彩铃状态文案、视频显示与原有音频策略；按钮的可见性、启用状态和全屏切换全部交给原生 Dialer。

本机一次授权未接听语音起呼验证：初始五个原生按钮均存在；点击视频进入全屏时全部隐藏，退出后恢复；等待两秒跨过模块巡检后仍为五个按钮。三个视频操作在振铃期为原生 disabled，音频路由和静音为 enabled；未强制启用摄像头。视频帧更新、退出清理正常。此结果不代表接通后按钮／相机完整验收。

## 为什么记录仍为视频

同一次呼叫模块记录 requested_video=0、initial_video=0、voice_origin=true。接收视频早期媒体出现后，通话记录 features=65，即 VIDEO(1) + VOLTE(64)，duration=0。这不是主动视频起呼的证明。

本机 service-telecom.jar 的 CallLogManager 读取 Call.getVideoStateHistory()，getCallFeatures 调用 VideoProfile.isVideo(history) 后置位 FEATURES_VIDEO。RX 也属于 isVideo；记录字段不包含视频源是否为彩铃的业务语义。旧版阶段证据也有相同标志，2.3.1 状态页改动没有修复或重新引入该问题。

- [VideoProfile.isVideo](https://developer.android.com/reference/android/telecom/VideoProfile#isVideo(int))：接收或发送视频均算视频。
- [CallLog.Calls.FEATURES_VIDEO](https://developer.android.com/reference/android/provider/CallLog.Calls#FEATURES_VIDEO)：标记呼叫曾有视频，不等于双向视频或相机开启。

## 严格区分的边界

Dialer 内可以依据语音起点、振铃 RX 和用户视频升级区分适配行为，但不能只凭最终 features 清除视频位，否则会误改真正的视频通话。系统通话记录的严格分类需要在记录生成处保留语音起点、视频发生阶段、接通后媒体和用户升级意图；身份或阶段不确定时保留原生分类。

当前仅 Dialer 作用域无法保证修改全系统记录生成。没有新增系统作用域、没有修改通话数据库，也没有伪造实时 AUDIO_ONLY。协议不要求把彩铃称为双向视频通话，但当前系统的历史视频位路径仍会产生视频标签。接通后是否回到纯语音尚未实机验收，因此不宣称已经严格修复分类。

原始截图、按钮 XML、日志与记录位抽查保留在私人证据目录，未上传联系人或厂商代码。
