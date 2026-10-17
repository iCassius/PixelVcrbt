# 3.2.0 发布核验

2026-10-18。四批修复与验证见 [健壮性记录](RELIABILITY_20261018.md)。

- 源码提交：`f41b1f4077fc69f0b2a5bb37b16cc42e2d932997`，标签 `v3.2.0`；versionCode 22。
- [正式构建与发布](https://github.com/iCassius/PixelVcrbt/actions/runs/39060303085)成功；[源码分支验证](https://github.com/iCassius/PixelVcrbt/actions/runs/39060261507)成功。
- [源码仓库 Release](https://github.com/iCassius/PixelVcrbt/releases/tag/v3.2.0)与 [LSPosed 分发 Release](https://github.com/Xposed-Modules-Repo/io.github.icassius.pixelvcrbt/releases/tag/22-3.2.0)均已公开，各仅包含一个 `PixelVcrbt-v3.2.0.apk`。
- 两处公开下载 APK SHA256 均为 `5ffc208aed69318a0bdf210707b02faa370466828bd2545e30dad68da81dac60`，135792 字节；更新信息中的版本、文件名和摘要一致。
- APK 签名验证通过，证书 SHA256 为 `67514a1b7824cd9089b9fd161a58860e443f2d8801052d7bde7f4c56535c7a93`，与既有版本一致。
- 公开 APK 的 DEX、Manifest、资源表与手机上已验证的 3.2.0 候选逐字节相同；Xposed 元数据规范化行尾后一致。ZIP 封装时间不同，完整文件摘要不作为实机候选逐字节一致的声明。
- 手机已完成同签名升级、持久化和电话进程重建验证。未新增拨号，未执行实际 OTA 或接通后的通话质量验收。旧稳定版保留用于查验，不承诺 Android 直接覆盖降级。

本记录确认两处 Release 已发布，不代表 LSPosed 模块索引已刷新。
