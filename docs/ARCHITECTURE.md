# 架构说明

## 分层

```
┌──────────────────────── 界面层 (ui/) ────────────────────────┐
│ MainActivity ─ HomePage / ModulesPage / EventsPage /         │
│                AuditPage / SettingsPage ─ UiKit              │
└───────────────────────────────┬──────────────────────────────┘
                                │ 同进程静态监听（GuardEngine.Listener）
┌──────────────────────── 引擎层 (core/) ──────────────────────┐
│ GuardEngine：事件汇聚 · 运行时状态 · 统计与清理                │
│ Modules：模块注册表（内建 + 功能包）                            │
│ Rules：声明式规则引擎（jump / clip / screen / notif）           │
│ AppScanner：应用画像 · 风险评分 · 联合逃逸分析                  │
└───────────────────────────────┬──────────────────────────────┘
                                │
┌────────────────── 系统服务层 (service/) ─────────────────────┐
│ GuardAccessibilityService：窗口事件 / 跳转判定 / 屏幕扫描 / 剪贴板│
│ JumpPrompt：无障碍浮层确认框（TYPE_ACCESSIBILITY_OVERLAY）       │
│ GuardNotificationService：通知监听与敏感内容识别                 │
│ GuardEngineService：前台常驻与周期维护（无网络）                 │
│ BootReceiver：开机自启                                          │
└───────────────────────────────┬──────────────────────────────┘
                                │
┌──────────────────── 数据层 (data/ + util/) ───────────────────┐
│ Db（SQLite 事件表）· Event · Prefs（策略/白名单）               │
│ Sensitive（正则 + 校验位 + Luhn）· Text（脱敏/截断/时间）        │
└──────────────────────────────────────────────────────────────┘
```

## 跳转判定时序

```
AccessibilityEvent(TYPE_WINDOW_STATE_CHANGED)
        │
        ├─ 包名 == 自身 / 未变化 ──────────────► 忽略
        ├─ 系统白名单（SystemUI/设置/安装器/桌面/输入法/文件选择器）► 忽略
        ├─ 用户白名单 ────────────────────────► 忽略
        ├─ 模块「跳转守卫」关闭 ───────────────► 忽略
        │
        ├─ Prefs.jumpApproved(src>dst) ───────► 放行并记录
        ├─ 规则命中 allow ────────────────────► 放行并记录
        ├─ 规则命中 block ────────────────────► 阻止并记录
        │
        ├─ 非严格模式 且 (now - lastInteraction) < 阈值 ─► 放行并记录
        │
        └─ 其余 ──► JumpPrompt 浮层（倒计时 N 秒）
                      ├─ 终止跳转 ──► performGlobalAction(BACK) + 记录 block
                      ├─ 仅本次   ──► 记录 allow
                      └─ 永久允许 ──► Prefs.approveJump + 记录 allow
```

## 为什么没有用 Xposed / root

目标设备（realme GT Neo2 / Android 13）未 root，且普通用户设备通常也不会 root。
因此拦截能力建立在**系统公开能力**上：无障碍事件 + 无障碍浮层 + 全局动作。
代价是"事后立即撤回"而非"事前阻断 Intent"，这个取舍写进了 README 的已知限制。

## 数据流与隐私边界

- 所有事件写入本机 SQLite；敏感原文只用于判定，落库的是脱敏后的展示串。
- 无 `INTERNET` 权限 → 任何代码路径都不具备把数据送出去的系统能力。
- 功能包只支持声明式规则，从机制上排除"导入即执行代码"的风险。
