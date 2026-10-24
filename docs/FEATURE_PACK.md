# 功能包（.mpk）开发指南

功能包是墨菲安全的扩展机制：**只描述规则，不携带代码**。

## 1. 结构

```
my-pack.mpk   (本质是 zip)
├── manifest.json   必需
└── rules.json      可选
```

## 2. manifest.json

```json
{
  "id": "com.example.otp_shield",
  "name": "验证码防外泄",
  "version": "1.0",
  "author": "你的名字",
  "desc": "剪贴板与通知中的验证码保护规则"
}
```

- `id`：`[a-z0-9_.]{3,64}`，全局唯一，不能与内置模块冲突（如 `jump_guard`）。
- `name` / `version` / `author` / `desc`：纯展示信息。

## 3. rules.json

```json
[
  {
    "id": "rule-1",
    "scope": "jump",
    "action": "block",
    "match": { "src": "com.unknown*", "dst": "com.android.chrome" },
    "note": "未知应用不得直接唤起浏览器"
  }
]
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | 否 | 规则标识，用于展示 |
| `scope` | 是 | `jump` / `clip` / `screen` / `notif` |
| `action` | 是 | `block` 阻止 / `alert` 告警 / `allow` 放行 |
| `match.src` | 否 | 源包名，`前缀*` 通配 |
| `match.dst` | 否 | 目标包名，`前缀*` 通配 |
| `match.keyword` | 否 | 文本包含 |
| `match.regex` | 否 | 正则，≤200 字符 |
| `match.sensitive` | 否 | 敏感类型标记数组 |
| `note` | 否 | 命中时展示给你的原因 |

至少要有 `src` / `dst` / `keyword` / `regex` / `sensitive` 之一，否则规则被丢弃。

## 4. 校验规则

导入时逐条校验，任一条不合法则**整包拒绝**：

- 规则数 ≤ 200
- `scope` 必须合法，`action` 必须合法
- `keyword` / `regex` 长度 ≤ 200
- `regex` 必须能编译

## 5. 打包

```bash
zip my-pack.mpk manifest.json rules.json
```

在应用内「模块 → 功能导入 → 导入功能包」选择该文件。

## 6. 安全边界

功能包无法执行代码、无法访问网络、无法修改应用设置，只能声明匹配条件。
这是为了让"导入一个功能包"永远不会变成"安装一个后门"。
