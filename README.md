# Pixel彩铃激活模块 · PixelVcrbt

作者：[iCassius](https://github.com/iCassius)。当前为 2.3.0-experimental，MIT 许可。

为 Google Dialer 补齐语音起呼时的视频彩铃显示，并展示安装后未生效的定位提示。LSPosed **仅勾选 Google 电话（com.google.android.dialer）**，不勾选系统框架、Phone 或 IMS。

当前适配严格限定 Pixel 10 Pro XL（mustang）、Android CP3A.260905.009、Google Dialer versionCode 20148463、中国电信 carrierId 2237 / MCC 460 / MNC 11。其他版本不执行通话适配，状态页提示适配不符。这不是所有 Pixel／运营商通用解锁。

## 安装与确认

1. 从 Releases 安装 APK，启用 LSPosed 模块，仅选择 Google 电话。
2. 允许普通“电话状态”权限。无需给本 APK 授权 su。
3. 重启电话进程并打开电话一次，再查看模块状态页。
4. 使用目标卡发起普通语音呼叫，测试已开通视频彩铃的联系人。显式视频通话不属于本模块彩铃检测范围。

页面显示本模块提供能力、外部前置配置、实际运行检测。模块加载采用当前进程实时响应；前置配置通过公开 SDK 直接读取，不需要启动电话。IMS 注册通过已加载的 Dialer 宿主权限读取，无法读取时显示未检测到。

| 提示 | 检查方向 |
|---|---|
| 未收到模块响应 | 电话未加载，或 LSPosed 未启用／未选电话／未重启进程；不能仅凭无响应断言未安装 |
| 模块适配失败 | 设备、系统或电话版本不匹配；不要扩大作用域 |
| 前置配置未齐 | 可先空闲时加载电话触发兜底；仍缺失时由其他配置方案补齐 |
| IMS 未注册 | SIM、VoLTE／VoWiFi、运营商开通与上游配置 |
| 接收视频会话未出现 | 对端彩铃、运营商网络或视频策略；待机未检测到是正常情况 |
| 会话已出现、显示未绑定 | 将通话界面保持前台 |
| 已绑定、无画面更新 | 进一步检查媒体接收、解码与显示链路 |
| 已检测到画面更新 | 当前呼叫曾观察到纹理更新，不等于画面内容／接通质量完整验收 |

不显示手机号、联系人、通话 ID、时间／过期信息。不把 IMS 注册或 RX 状态称为已抓取 SIP／SDP 握手，也不把 Texture 更新当作可见内容证明。实时响应按查询 nonce 匹配，无响应会清除；呼叫结束后不保留成功快照。

## 功能边界

模块只介入单路普通语音呼出的 DIALING + RX_ONLY 早期媒体，显式视频、升级视频、紧急／会议／外部呼叫不介入；ACTIVE 后交回原生处理。系统通话记录将彩铃记为视频的问题仍未修复，未增加系统作用域。

四个差量兜底项为 carrier_vt_available_bool、imsvt.video_on_default_bearer_supported_bool、vendor.ims.carrier_video_over_lte_bool、vendor.ims.carrier_video_over_wifi_bool。仅当宿主有 MODIFY_PHONE_STATE、目标卡加载、配置有效、通话空闲时补齐缺失／false。全部已有则不写；每次开机／订阅最多尝试一次，不清空其他覆盖。

这四项不是已逐项证明的完整最小必要集。上游还可能需要视频 QoS／SDP／防降级策略，当前模块不覆盖。5G-A、VoNR、CDMA 不是本模块彩铃前置条件。已有可用 IMS 视频早期媒体时，不要求安装特定 KSU 包。厂商文件修改是否必要取决于实际注册与媒体结果，不能仅凭缺少映射断定必需。

可选关闭：persist.sys.vcrbt.disabled=1；关闭音频保留：persist.sys.vcrbt.audio_preserve=0；关闭配置兜底：persist.sys.vcrbt.carrier_fallback=0。本模块不修改厂商文件、SELinux 或基带，不保证开机／OTA 自愈，不制造网络视频流。

## 构建与发布

JDK 21、Python 3、Android SDK platform android-36.1 / build-tools 35.0.0。设置 JAVA_HOME、ANDROID_HOME，然后运行 python build.py 与 python test.py。构建输出 PixelVcrbtSurfaceBridge.apk。未配置 RELEASE_KEYSTORE 时使用临时本地调试签名；该签名不保证与官方 Release 相同。

GitHub Actions 在 push／PR 构建和测试，在 v* tag 推送时使用仓库 Secrets 的稳定签名生成 APK、SHA256SUMS，并发布预发布 Release。推送标签必须与 Manifest versionName 一致。Release 签名密钥不进入源码仓库；来自不可信 PR 的 CI 不读取签名 Secrets。

本仓库只包含模块源代码、编译接口桩、测试和构建配置；不包含 Google Dialer、厂商数据库／APK、私人通话日志、联系人或签名私钥。Xposed／LSPosed 框架由其原作者提供；编译接口桩不打入 APK，Android／Google 的代码与资产不在本项目授权范围。
