# LSPosed 发布与 API

核对日期：2026-10-14。官方流程：[提交说明](https://github.com/Xposed-Modules-Repo/submission)／[提交入口](https://modules.lsposed.org/submission/)。

## 上架步骤

1. 使用自己拥有的包名。当前 com.antigravity.pixelvcrbt 若无法证明域名所有权，不宜直接申请；无自有域名时可用 io.github.icassius.pixelvcrbt。更换 applicationId 是新应用安装，需迁移并禁用旧模块，避免重复 Hook；本次尚未改包名。
2. 在 Xposed-Modules-Repo/submission 创建 `[submission] 包名` 的 Issue，按官方要求验证身份，并接受维护邀请。自有域名的快速验证为根域名 TXT：lsposed-modules-repo-verification=iCassius。
3. 生成的组织仓库存放精简说明与发行 APK；源代码仍保留在 iCassius/PixelVcrbt。组织仓库发布标签格式为 `versionCode-versionName`，附 APK 和变更说明；完整仓库通常在 5–60 分钟内索引。
4. 取得组织仓库权限后可增加镜像发布任务。当前 GitHub Actions 已发布到个人仓库，但没有向第三方组织发送申请或配置写入凭据。

## Legacy 是否需要升级

当前模块使用 XposedBridge／XposedHelpers 与 assets/xposed_init，管理器显示 Legacy 是正确的接口标识，不等于手机框架过旧或模块未生效。

[Modern API 官方说明](https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API)要求改入口、META-INF/xposed 配置与 Hook 接口；新服务可提供框架名称、版本、偏好共享与作用域管理。仅改版本号或声明不会完成迁移。

本次保留已验证的 Legacy 实现。后续可单独迁移 Modern API，仍静态限定 Dialer，并回归 Surface、按键、语音／真实视频边界。API 升级本身不能修复系统通话记录分类。当前官方 Wiki 可读取，libxposed/api 与 libxposed/service 的 GitHub 仓库在本次访问时返回 404，迁移前还需确认可用依赖来源与目标框架接口。
