# LSAN-信息防护终端 · LSAN Security

> 一款**完全离线**的 Android 个人信息防护终端：拦截应用间跳转并要求你确认、在后端终止被跳转的目标应用、识别应用联合逃逸、监控应用层的信息外泄。

![platform](https://img.shields.io/badge/platform-Android%208.0%2B-0B0B0C)
![language](https://img.shields.io/badge/language-Java-35F0A0)
![network](https://img.shields.io/badge/network-none-D92D20)
![deps](https://img.shields.io/badge/dependencies-zero-1D6BFF)
![license](https://img.shields.io/badge/license-BSL%201.1-B54708)

---

## 它解决什么问题

Android 上真正的隐私风险往往不来自单个应用，而来自**应用之间的配合**：

- **跳转劫持**：A 应用在你不知情时唤起 B 应用，把你带到钓鱼页 / 广告页 / 支付页；
- **联合逃逸**：A 负责"读"（无障碍读屏、短信、通讯录、剪贴板），B 负责"传"（联网上行），单个都合规，合起来完成窃取；
- **应用层泄漏**：敏感数据出现在屏幕、剪贴板、通知栏，被其他应用顺手拿走。

LSAN 把这些行为**拉到应用层盯住**：谁跳到谁、谁读了什么、哪两个应用可能串通——全部本机判定、本机留痕。

## 三档拦截策略

| 档位 | 静默跳转 | 你手动触发的跳转 | 拒绝后的处置 |
|---|---|---|---|
| **宽松** | 必须确认 | 直接放行 | 仅撤回界面 |
| **中等**（默认） | 必须确认 | 首次确认，可记忆永久批准 | 撤回 + 终止目标后台进程 |
| **严格** | 必须确认 | **每次都要确认**（不记忆） | 撤回 + 终止；连续 2 次自动阻止 |

档位在「总览」与「设置」中随时切换，切换即时生效，无需重启。

## 双外观

| | 视觉 | 交互 |
|---|---|---|
| **极简白** | 大字、宽留白、1px 细线、无阴影、黑底白字主按钮 | 淡入上移、数字滚动、按压回弹等丝滑动效 |
| **终端黑** | 纯黑底、`#333` 1px 方角线框、灰白字、窗口标题栏 `○ ○ ○`、`//` 命令行前缀、极淡网格；绿色仅用于状态点 | 干净利落，无动画，状态即点即变 |

字体：深色外观全部使用内嵌的 **JetBrains Mono**（子集化，仅 10 KB），与官网 `whitemeow.top` 保持一致；浅色外观沿用系统无衬线。

切换外观时，**桌面图标同步切换**（白底黑字 / 黑底荧光绿两套 adaptive icon，通过 activity-alias 实现）。

## 核心能力（逐项可开关）

| 模块 | 说明 |
|---|---|
| **跳转守卫** | 跨应用唤起浮层确认；静默跳转默认拦截、立即撤回，**并在后端终止被跳转的目标应用** |
| **剪贴板守卫** | 身份证 / 银行卡（Luhn）/ 手机号 / 验证码 / 邮箱 / 口令 / 住址识别，含主动体检入口 |
| **屏幕内容守卫** | 扫描界面文本，敏感数据告警并关联潜在读屏通道 |
| **联合逃逸守卫** | 同签名读写合谋、读屏且联网、读屏 + 通知监听双通道 |
| **通知泄露守卫** | 通知栏验证码等敏感内容监控，统计可读取通知的应用数 |
| **应用行为审计** | 权限 / 签名 / 安装来源画像 + 0–100 风险评分 + 联合风险 |
| **设备适配** | 按 ROM（vivo/OPPO/小米/华为/荣耀/三星）给出包名白名单与保活引导 |
| **开屏跳过** | 应用启动时识别并点掉「跳过 / skip / 关闭广告」控件（对标李跳跳）；位置+尺寸+危险词三重防误点 |
| **功能导入** | `.mpk` 声明式功能包（只描述规则，不执行代码） |

## 跳转终止：三档能力

拦截不是"按返回键"就完事——被跳转的目标应用必须被真正终止：

| 档位 | 触发条件 | 手段 | 界面标注 |
|---|---|---|---|
| 强 | 设备有 root / su | `am force-stop <pkg>` | 已强制终止 |
| 中 | 普通设备（默认） | `killBackgroundProcesses` × 3 次重试（界面已通过 BACK 撤回） | 已终止后台进程 |
| 兜底 | 上述均不可用 | `GLOBAL_ACTION_BACK` 撤回 | 已撤回界面 |

> Android 平台限制：**非 root 应用无法 force-stop 前台进程**。LSAN 会在设置页显示当前设备的真实终止能力，不做夸大承诺。

## 安全加固（防逆向 / 防篡改 / 防植入）

- **R8 全量混淆**：`-repackageclasses ''` + 类/包/成员三级自定义混淆字典 + 日志与断言剥离；
- **签名校验**：运行时比对发布证书 SHA-256，二次打包必然签名不符 → 顶部告警并留痕；
- **反调试**：`Debug.isDebuggerConnected()` + `/proc/self/status` 的 `TracerPid`；
- **反 Hook**：扫描 `/proc/self/maps` 中的 frida / xposed / lsposed / substrate / riru 等注入特征；
- **反模拟器**：Build 指纹 + qemu 设备节点检测；
- **零第三方依赖**：无 AndroidX、无 Google Play Services、无任何 SDK，供应链面收敛到最小；
- **无 INTERNET 权限**：系统层面不具备建连能力，任何"上传/回传"路径不存在。

> 说明：任何客户端都可被足够有动机的攻击者分析，加固的目标是**显著抬高成本**并让篡改可被发现；我们如实标注检测结果，不做"绝对无法逆向"的承诺。

## 开发版调试通道（dev 变体）

正式版（prod）无 `INTERNET` 权限；**开发版（dev）**在 `127.0.0.1:18888` 暴露本地调试接口，供真机联调：

```bash
adb forward tcp:18888 tcp:18888
curl -s http://127.0.0.1:18888/state
```

| 接口 | 说明 |
|---|---|
| `GET /state` | 主题 / 档位 / 模块开关 / 服务连接 / 统计 / 完整性 |
| `GET /events?limit=50&type=jump` | 事件流 |
| `GET /apps?limit=30` | 应用画像与风险分 |
| `GET /windows` `/skiptrace` | 窗口切换轨迹、跳过判定轨迹（排障用） |
| `POST /module?id=skip_guard&on=0` | 开关模块 |
| `POST /theme?dark=1` | 切换外观 |
| `POST /policy?level=2` | 切换拦截档位 |
| `POST /setting?key=strict_jump&value=0` | 改策略参数 |
| `POST /plugin/import?name=x.mpk` | 导入功能包 |
| `POST /probe?text=...` | 敏感识别自测 |

> dev 与 prod 使用不同 applicationId（`.dev` 后缀），可同机共存；dev 的桌面名带 `[DEV]` 便于区分。

## 构建

```bash
# JDK 17 / Android SDK 34 / Build-Tools 35.0.0
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

发布签名请自行生成并**不要提交到仓库**（`.gitignore` 已排除 `keystore/`、`*.jks`）。
Release 构建已启用 R8 + 资源压缩 + 混淆字典（`proguard-rules.pro` / `proguard-dict.txt`）。

## 正式独立版

- 文件：`LSAN-信息防护终端-v2.2.0-正式独立版.apk`
- 包名 `com.lsan.sec` · versionCode 220 · versionName 2.2.0
- 签名：`CN=LSAN Security Terminal`（RSA 4096 / PKCS12），证书 SHA-256
  `CC2156DE35B9820A45F35F3D3DB02D7CEE4DA7D0DA884E6A177458A5DAE50A44`
- 运行期会校验该指纹：一旦被二次签名或重打包，应用顶部会立即告警并留痕
- 权限：**不含 `INTERNET`**，无任何联网能力

## 安装后的授权（系统强制，需手动开启）

1. **无障碍服务**：设置 → 无障碍 → 已下载的应用 → LSAN 信息防护 → 开启
2. **通知使用权**：设置 → 通知 → 通知使用权 → LSAN 信息防护 → 开启
3. **通知权限**：首次启动弹窗允许
4. 国产 ROM 还需：自启动、后台保活、应用列表权限（应用内「设置 → 设备适配」逐项引导）

## 目录结构

```
app/src/main/java/com/lsan/sec/
├── core/     GuardEngine · Modules · Rules · AppScanner
│            VendorAdapter（机型适配）· Security（自检）· JumpKiller（终止）
├── data/     Db（SQLite）· Event
├── service/  GuardAccessibilityService · JumpPrompt · GuardNotificationService
│            GuardEngineService · BootReceiver
├── plugin/   PluginLoader（.mpk 导入/导出）
├── ui/       Theme（双外观）· Anim（动效）· UiKit（组件）· MainActivity
│            HomePage / ModulesPage / EventsPage / AuditPage / SettingsPage
└── util/     Sensitive（识别）· Prefs · Text
```

## 功能包格式

见 [docs/FEATURE_PACK.md](docs/FEATURE_PACK.md)。摘要：`.mpk` = zip(`manifest.json` + `rules.json`)，
只允许声明式规则（scope/action/match），不加载 dex/so/脚本。

## 隐私

- 无 `INTERNET` 权限、无埋点、无统计、不上传；
- 事件存本机 `/data/data/com.lsan.sec/databases/`，默认保留 14 天，可一键清空；
- 落库内容已脱敏；导出文件只写本应用私有目录。

## 已知限制

- 跳转拦截是"检测 → 确认 → 撤回 + 终止"，不是内核级 Intent 拦截；毫秒级连续跳转可能有闪烁；
- 非 root 设备无法强制停止前台进程（见上"三档能力"）；
- 剪贴板在后台受 Android 10+ 限制，只能记录变更事实，另有主动体检入口；
- 风险评分是启发式，不代表应用一定恶意。

## 协议

**Business Source License 1.1（商业开源保护）**：源码公开，个人学习 / 研究 / 非生产免费；
**生产环境商业使用需获得授权**；发布 4 年后自动转为 Apache-2.0。详见 [LICENSE](LICENSE)。

## 仓库

GitHub：**https://github.com/LSAN-HK17745/lsan-security**

- 最新版本：`v2.1.0` — [下载 APK](https://github.com/LSAN-HK17745/lsan-security/releases/download/v2.1.0/LSAN-Security-v2.1.0-release.apk)
- 发布页：https://github.com/LSAN-HK17745/lsan-security/releases
- 官网参考：https://whitemeow.top/timetable/（深色外观的设计语言来源）
