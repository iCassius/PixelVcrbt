# Google 电话更新后的适配与加载确认方案

2026-10-18。以下根因记录对应研究开始时的正式 3.1.1 源码。后续按用户要求，已改为按特征决定是否启用，实施与验证见 [本轮迭代记录](FEATURE_COMPAT_IMPLEMENTATION_20261018.md)。本轮未安装、重启进程、拨号或修改通信配置。

## 结论与证据

仓库为 `PixelVcrbt`，`main@d153004`；开始研究时无已跟踪或未跟踪修改。`v3.1.1..HEAD` 的 `src`、`build.py` 无差异。既有约定：仅 Google 电话作用域，状态页无 Root，配置与实时连接分别表达。

- **设备只读取证**：当前安装模块 3.1.1/21 的 APK SHA-256 为 `38c224bf0688c0d8b0347c65107a2d05b1bb3a44cdc6c9f974c3d5b22b3520e9`，与本地正式发布 APK 完全一致；签名验证通过。不能用仓库根目录的旧 `PixelVcrbt.apk` 代替正式产物。
- **既有设备取证**：电话于 10 月 16 日 22:52:20 更新至 `242.0.998301074-publicbeta-pixel / 20149143`，此前受支持版本为 `242.0.995172309-publicbeta-pixel / 20148463`；退出记录为 PACKAGE UPDATED。电话进程已注册 QUERY_RUNTIME 接收器。状态应用记录四项参数读取成功、`fallback_available=false`。未取得状态页最终文字及 nonce 回复，现存日志未捕获 `version_mismatch`。
- **原 3.1.1 源码确定的阻断原因**：`PixelVcrbtBridge.initialize()` 先注册探测接收器，再检查版本；新电话版本必定在业务 Hook 安装前返回。`ParameterReader.read()` 和 `CarrierFallback.apply()` 也分别写死旧版本。因此，入口存在与功能未安装能够同时发生。`fallback_available=false` 本身还可能来自权限或开关，不能单独作为版本错误的证据。这三处限制已在后续迭代中移除。
- **证据限制**：这是版本检查造成初始化中止的确定性源码结论，并有安装包和设备版本支持；不是直接捕获错误分支的日志证明，也不证明新版电话其他行为完全兼容。未进行新版电话彩铃、接通后语音或原生视频业务验证。

私人取证保留于工作区 `evidence/adb_health_20261018_011205` 和 `evidence/vcrbt_compat_20261018`，不提交 APK、原始日志或设备身份。后者含可重复执行的 APK 检查脚本和摘要 JSON。

## 新旧电话 APK 静态检查

使用 SDK 37.0.0 的 aapt2、dexdump，并解析所有 DEX 的类、继承关系和方法描述符。

| 对象 | 本次结果 | 尚不能确认的行为 |
| --- | --- | --- |
| `com.android.incallui.InCallServiceImpl` | 两版都存在，经 `ypq` 继承 `android.telecom.InCallService` | 回调时序与其他模块干预 |
| `onCallAdded`、`onCallRemoved` | 两版均为实例 `public final (android.telecom.Call)void` | 正常执行后是否得到相同状态、接通与挂断时序 |
| `com.android.dialer.incall.activity.ui.InCallActivity` | 两版都存在，继承链终点为 Activity | 原生远端视图布局、替换时机、全屏与后台恢复 |
| Manifest | 新版声明上述 Activity/Service；Service 要求 BIND_INCALL_SERVICE；声明两项电话特权权限 | 权限是否实际授予仍须运行时检查 |
| `contactgrid_status_text` | 两版均为 `0x7f0b0210` | 是否仍为应修改的业务文本、恢复时机 |
| `videocall_fullscreen_background` | 两版均为 `0x7f0b0851` | 是否仍可安全隐藏、归属是否改变 |

新电话 APK SHA-256：`15f5efb148aca61200ac460a4e4cf5d6bd55d2066530ea9128b5732ea7d8cded`；旧电话 APK：`daec62e93012af88a1a5d66a8e2baf01aec2af5f488ed51c68e7cc69e8163ebb`。两版入口类的指令字节摘要不同，DEX 索引与混淆引用也改变；不能由此认定语义变化，也不能声称方法体相同。

平台调用须单独核验：`setAudioRoute(int)`、`requestBluetoothAudio(BluetoothDevice)`、`requestCallEndpointChange(CallEndpoint, Executor, OutcomeReceiver)`；本机 Android 36.1 SDK 已核对三者完整签名，不等于已验证手机 Android 17 实现。具体 VideoCall 实现的 `setDisplaySurface(Surface)`、`sendSessionModifyRequest(VideoProfile)` 以及 TextureView listener 只能在对象出现后检查，不应在无通话时谎报已核验。注意底层 `IVideoProvider` 的视频请求才是双参数；本轮首个候选曾混淆两层，已通过实机测试发现并修正，见 [实机记录](DEVICE_TEST_20261018.md)。

## 动态适配设计

采用“明确的适配器契约 + 进程内能力探测 + 按功能决定是否启用”。电话版本、机型和系统构建号均不作为放行条件；电话版本只用于诊断追溯。功能依赖未满足时关闭相应行为，不由版本新旧推断兼容。

1. **统一决定来源**：新增纯数据 `CompatibilityDecision`，由宿主探测生成。至少包括策略版本、adapterId、电话版本、模块运行版本、required/optional 能力、失败原因、验证等级。删除三处各自做版本判断的方式。状态页能直接读取包版本与配置，但不能用这些静态信息推导 Hook 成功；无电话回复时显示“尚未确认”。
2. **明确适配器契约**：首个适配器保留当前两类入口、精确回调签名、Activity 类型和资源角色。每个方法校验声明类/继承关系、参数、返回值、实例属性和候选唯一性。把 `hookNamed()` 按名字遍历全部重载改为完整描述符解析。候选不唯一时停止相关功能，不猜混淆类、不扫描所有方法后任选一个。
3. **分开能力**：通话观察、渲染、用户音频保护、装饰文字、配置兜底分别记录。文字资源缺失可停用文字装饰；不能证明远端 Surface 的角色、所有权或释放规则时，停用本模块渲染。音频选择保护不完整时禁止自动恢复音频路由。任何关键失败都保留原生通话与原生视频路径。
4. **按特征决定行为**：必要的类、完整签名和运行对象满足要求后启用对应功能。电话升级不触发版本封锁；必需特征缺失、候选歧义或 Hook 失败才停止相关行为。业务回归结果另记为测试证据，不变成版本白名单，也不把结构检查称为实际通话验收。
5. **本地可审计策略**：适配规则随签名模块发布，保存每次支持变更的 APK 摘要、签名关系、静态检查、实际验收范围和规则原因；首期不下载远程代码或任意反射表达式。后续如需要远程数据策略，须另做签名、防降级和故障回退设计。

建议组件：`CompatibilityPolicy`（设备/系统与候选范围）、`DialerAdapter`（固定契约）、`CapabilityProbe`（只读解析）、`HookInstaller`（安装/撤销）、`RuntimeState`（不可变快照）。状态页使用宿主回复中的决定；`ParameterReader` 仅负责可直接读取的设置、配置和权限提示。

### 安装与运行必须处理的边界

- 当前 initialize 在安装多个 Hook 后才置 `installed=true`，异常时没有撤销，重复进入可能重复安装。改为主线程串行状态机 `DISCOVERING → INSTALLING → READY / FAILED`，先解析全部必要对象，再保存每个 HookHandle；失败按逆序 unhook 并注销生命周期回调。已核对本地 libxposed API 102 HookHandle 提供 `unhook()`。所有回调还须检查同一代的 READY 状态，避免安装期间执行半套逻辑。失败后禁止自动无限重试。
- `installed=true` 目前早于兜底调度及广播注册；后续异常可能同时出现 `hook=true` 与 `init_failed`。必要 Hook 提交和可选兜底状态分离，不能让一个布尔值掩盖部分失败。PROTECTIVE 回调中的异常也必须更新故障状态，安装返回不代表回调业务成功。
- `trace()` 目前在视频请求保护安装前就把类记入 traced，后续失败将跳过重试。按具体实现类及 ClassLoader 保存完整安装结果；provider 变化时先验证，再允许绑定。捕获用户主动视频请求失败时不得继续接管渲染。
- 当前 `findRemote()` 以面积、可见性和排除 preview/camera/local 名称选视图，仍可能误认未知视图。新增正向角色判定、唯一性和本地预览排除；SurfaceView 的画面更新不可按 TextureView 回调推断。代际检查、绑定归属、接通/挂断/切后台释放仍须保留。
- 只在单个、符合运营商和语音起呼条件的早期媒体会话内工作；紧急、外部、会议、原生主动视频与非目标订阅不介入。不因更新关闭 ViLTE，不发送伪造的视频降级，不覆盖用户听筒/扬声器/蓝牙选择。
- 进程内全局 View/TextView Hook 会收到大量无关事件，须用会话存在与对象身份快速返回；禁止全类扫描和每帧日志。适配器解析只执行一次，不在状态轮询中反复解析。
- 配置兜底另设资格：特权、受支持系统、唯一目标卡、SIM 就绪、空闲、四项值类型、开关、一次尝试及有效配置回读。现有 `overrideConfig(..., true)` 会请求持久化覆盖，不应作为只读能力探测；本次不执行，也不借“动态适配”改变它的持久化策略。回读只证明配置值，不能证明来源或业务成功。

## 可靠的加载确认

| 层级 | 成立所需证据 | 不能替代 |
| --- | --- | --- |
| 模块入口 | 当前请求 nonce 的宿主回复，带进程实例标识与运行模块版本 | 必要 Hook 安装 |
| Hook 安装 | 同一进程全部必要 Hook 提交成功，列出每项结果与 adapterId | 后续 provider/视图可接管 |
| 前置配置 | 当前目标订阅四项有效 CarrierConfig 布尔值及读取结果 | IMS 注册、配置来源 |
| IMS 注册 | 独立的注册查询回调、订阅关联与超时结果 | 运营商视频授权、彩铃可用 |
| 单次媒体路径 | 匿名会话 token、语音起呼、拨出/RX-only、provider、Surface 代际与提交事件 | SIP/SDP 已协商或对端已发帧 |
| 实际画面 | Texture 更新仅记“内容有更新”；实际彩铃画面由授权测试人工确认 | 接通后语音/原生视频回归 |

不把接收器注册、PID 存在、功能开关、旧日志、IMS 注册或 Surface 提交写成“彩铃正常”。`setDisplaySurface()` 只设置远端视频显示 Surface；Texture 回调只说明 SurfaceTexture 更新，均不证明画面内容正确。[Android VideoCall 文档](https://developer.android.com/reference/android/telecom/InCallService.VideoCall)、[TextureView 文档](https://developer.android.com/reference/android/view/TextureView.SurfaceTextureListener)。

协议沿用显式包广播和 nonce，每次前台检测重新开始，暂停页面立即失效，不启动电话来制造响应。建议扩展字段：

```text
schemaVersion, nonce, processInstanceId, pid, replySeq, observedElapsedRealtime
runningModuleVersion/code, dialerVersionCode, adapterId, policyRevision
initPhase, hooksReady, requiredHooks[], optionalHooks[], reasonCode
configReadState, fallbackState, imsState, imsPending
```

- `processInstanceId` 在电话进程内随机生成，不能只用可能复用的 PID；运行模块版本来自已加载代码中的构建常量，不能从已更新的磁盘 APK 冒充。状态页比较本地安装版本，发现不一致提示“电话进程仍运行旧模块”。
- 回复只接受当前 nonce、支持的 schema、同一进程实例中递增的 replySeq；过期或乱序异步回调不能覆盖新状态。以状态页自己的单调时钟计算请求有效期，不依赖两端墙钟。超时清除本次实时结果，不持久化“已加载”。
- 入口快照与 IMS 查询分离。IMS 查询合并并发请求，设独立截止时间，例如 5 秒；页面可每 3 秒询问入口，但不每次再创建一个无限等待的 IMS 查询。超时显示“不可读取”，有回复但初始化中时显示“正在适配”，不能瞬间标为失败。现有 IMS 展示依赖 hook 成功，建议去掉这项非业务必要的绑定。
- 保留双向权限约束，并验证发送者身份。API 34+ 可通过发送端共享身份及接收端 UID/包名检查关联实际安装包；身份无法取得时不能把 payload 自报 PID/包名当作认证。先做目标系统验证，必要时另用显式受保护通道；不要贸然增加会改变现有特权语义的自定义签名权限。发送者 API 可能返回未知身份，见 [BroadcastReceiver 文档](https://developer.android.com/reference/android/content/BroadcastReceiver)。
- QUERY 仅返回有限状态，限频、校验 nonce 格式/长度；回复及按需导出的诊断不含号码、联系人、通话音视频、设备序列号、IMSI/ICCID 或原始运营商日志。

页面继续保留功能开关、四项依赖和简洁能力行。电话入口特征显示“必要项齐备 / 缺少必要项 / 尚未确认”；模块连接区分未连接、正在适配、已加载、适配失败、运行异常与需重新加载；IMS 单独展示。未响应不改写功能开关。详情按需展示版本、adapterId、必要步骤和简短原因，不恢复常驻的媒体会话/画面更新行。“已加载”只承诺必要 Hook 安装成功，可在详情说明等待实际通话验证。

## 验证与实施顺序

1. **补诊断与安装原子性**：实施运行代码标识、协议代际、状态机、失败撤销和 IMS 截止时间。针对任一 Hook 安装位置抛异常、重复 initialize、乱序回复、进程重建、旧模块进程、IMS 无回调/延迟回调、权限拒绝编写测试。确认失败后无活动业务回调、无重复 Hook，状态页不误报。
2. **再集中适配规则**：将三处限制改为统一决定，新增适配器与只读探测。使用两个已取证 APK 建立精确签名/资源检查；加入错误返回类型、静态同名方法、额外重载、候选歧义、资源缺失和非目标包的反例。配置补齐必须证明未知类型不覆盖、仅四项、空闲且一次尝试、回读失败可见；不得由探测触发写入。
3. **实机验收按证据推进**：本次已完成新旧电话包身份/摘要、Manifest、入口签名、继承链、资源存在核验；未完成运行时框架/具体 provider、远端视图角色与媒体行为验证。候选构建完成后先审阅差异、作用域、签名和包内容；安装与重启电话进程须另获授权。在不拨号阶段确认 nonce、运行代码标识、必要 Hook、配置与 IMS 状态；关闭/杀死电话等验收同样须授权。
4. **授权业务验收后再正式支持**：测试无彩铃的语音起呼、视频彩铃未接听/接通/拒接/本地主叫取消、用户主动视频与接通后升级视频、听筒/扬声器/蓝牙选择、退后台/全屏/旋转、provider/Surface 重建、结束后释放。验证多卡与非目标卡、并发/会议等不介入；紧急排除用离线策略测试，不拨真实紧急号码。逐次记录匿名会话事件与人工画面/声音结论，不用配置或 frame 回调替代业务验收。
5. **发布门槛**：候选完成回归后记录网络、系统与业务验收范围；不新增电话版本白名单。回退候选前应停用新增适配器并保留原生电话，不在活动通话中热撤销 Surface；恢复旧模块可能仍拒绝新电话，不可把旧版 APK 当作已验证修复方案。发布、安装、重启及配置清理须另获授权。

建议先完成第 1、2 项的可审阅实现，再开展第 3、4 项授权验收。当前根因足以解释“电话更新后模块失效”，但尚不足以声称“只放开版本检查即可恢复”。
