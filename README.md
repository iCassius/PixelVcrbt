# Pixel彩铃激活模块 · PixelVcrbt

为 Google拨号补齐语音起呼时的视频彩铃显示

当前只在 Pixel 10 Pro XL 10月系统验证测试, 未作其他机型和版本验证

包名：`io.github.icassius.pixelvcrbt`。需要支持 Modern API 101+ 的 LSPosed。

## 安装与确认

1. 安装 APK，启用 LSPosed 模块，仅选择 Google 电话。
2. 允许普通“电话状态”权限。
3. 重启电话进程并打开电话一次，再查看模块状态页。
4. 发起普通语音呼叫，测试已开通视频彩铃的联系人。

页面显示模块版本，可检查 GitHub 更新。

## 功能边界

系统通话记录将彩铃记为视频的问题仍未修复，未增加系统作用域。

依赖参数：

- `carrier_vt_available_bool`
- `imsvt.video_on_default_bearer_supported_bool`
- `vendor.ims.carrier_video_over_lte_bool`
- `vendor.ims.carrier_video_over_wifi_bool`

每次开机最多尝试一次前置配置兜底。

需要 IMS 正常注册与媒体协商；页面仅检测注册状态。

本项目是 LSPosed 彩铃模块。PixelIMS 是独立的 KernelSU 通信模块，由其他项目维护。
