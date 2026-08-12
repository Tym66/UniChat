# hermes-wechat-wekit

English: [README.en.md](README.en.md)

一个 [Hermes Agent](https://github.com/NousResearch) 平台插件, 给 agent 一个**真实的微信账号**: 私聊, 群聊, 图片, 通讯录. 它通过 [WeKit](https://github.com/Ujhhgtg/WeKit) 工作, 那是一个跑在已 root 安卓手机的微信进程内部的 Xposed 模块. 不做 UI 自动化, 也不刮通知栏.

> **动手之前先读这两条.**
> 1. **自动化个人微信账号违反微信服务条款, 而且封号会连带冻结微信支付.** 请用专门的小号. 见 [法律与服务条款](#法律与服务条款).
> 2. **入站是边沿触发的, 消息可能永久丢失.** 这是上游 WeKit 的行为, 任何插件都补不全. 见 [已知限制](#已知限制).

---

## 为什么会有这个项目

"让 agent 用上微信" 的其他每一条路, 要么慢, 要么已经死了, 要么很危险:

| 方案 | 状态 |
|---|---|
| UI 自动化 (截图 + 点击, 无障碍树) | 能用, 但慢, 脆, 而且结构性丢消息: 微信对当前在前台打开的那个会话不发通知, 消息会被静默丢掉 |
| WeChatFerry / 桌面客户端 hook | 上游已归档; 较新的微信桌面版直接拒绝被 hook 的客户端登录 |
| iPad / 第三方协议实现 | 所有路线里封号率最高; 本项目前期调研直接判定为不可用 |
| **WeKit (本项目)** | 在安卓侧 hook 微信自己的 **WCDB 数据库插入层**. 消息是在微信自己写库的地方读到的, 所以完全不依赖通知, 也不依赖屏幕上正显示着什么 |

WeKit 对外暴露一套 HTTP REST API 加一个原生 MCP server (默认端口 `3001`). 本插件把 `wechat-wekit` 注册成 Hermes 的一等公民平台, 与 Telegram, Discord 平级, 且不需要改 Hermes 核心的任何一行代码.

## 架构

```
┌────────────────────────────────────────────────┐
│ Hermes Agent (gateway)                         │
│   ~/.hermes/plugins/wechat-wekit/              │
│     outbound → REST  POST /api/messages/text   │
│                      POST /api/messages/image  │
│     inbound  ← MCP   POST /mcp                 │
│                      tools/call                │
│                        wait-for-new-message    │  (long poll)
└───────────────────┬────────────────────────────┘
                    │  plaintext HTTP + Bearer token, over your LAN
                    │  (different subnets? router DNAT — see Transport)
                    v
┌────────────────────────────────────────────────┐
│ Rooted Android phone                           │
│                                                │
│   WeChat (com.tencent.mm)                      │
│     └── WeKit Xposed module                    │
│           ├── REST  :3001/api/...              │
│           ├── MCP   :3001/mcp                  │
│           └── hooks WCDB insertWithOnConflict  │
│                        ▲                       │
│                        └── every inbound message
│                            lands here first    │
└────────────────────────────────────────────────┘
```

入站流程: poll 循环调用 `wait-for-new-message`, 解析 WeKit 返回的格式化字符串 (`ConvId='…',Sender='…',Type=N,Content='…'`), 然后把消息作为一个**后台 asyncio task** 交给 Hermes, 这样循环可以立刻重新挂上监听器. 出站流程: 就是普通的 REST 调用.

## 仓库结构

| 路径 | 是什么 |
|---|---|
| `plugin/` | `__init__.py`, `adapter.py`, `actions.py`, `plugin.yaml`, 插件本体; 复制到 `~/.hermes/plugins/wechat-wekit/`. `adapter.py` 是消息流, `actions.py` 是[动作工具](#动作工具) |
| `phone-script/hermes-media-bridge.js` | 跑在手机上的 WeKit JS 引擎脚本; 想真正拿到文件/图片/语音的字节就必须装它 (见下文) |
| `transport/router-dnat/wekit-dnat.sh` | 推荐的 WiFi 传输方式所需的路由器侧 DNAT 脚本 |
| `ops/wechat_watchdog.py` | 可选的手机端保活 (HTTP 探测, 只有失败时才碰 adb) |
| `.env.example` | 所有环境变量, 带注释 |
| `docs/` | 手机配置, 架构, 排障 |

## 环境要求

| 组件 | 要求 |
|---|---|
| 手机 | 已 **root** 的安卓设备 (参考部署: Pixel 9 Pro, Android 16, Magisk 30.7 开 Zygisk) |
| Xposed 框架 | LSPosed 或仍在维护的后继者. 在 Android 16 上参考部署用的是 **Vector** (`JingMatrix/Vector`), 因为上游 LSPosed 的最后一个 release 早于 Android 16 |
| 模块 | [WeKit](https://github.com/Ujhhgtg/WeKit), 启用并把微信勾进作用域, 并打开它的 **API + MCP server** (端口 `3001`, 换成你自己的 bearer token) |
| 微信 | WeKit 支持的版本, 对照上游 `WeChatVersions.kt` 确认. 参考部署跑的是 **8.0.72** (当时该文件里最高的那个常量) |
| Agent | 一个装好的 Hermes Agent, 带插件系统 (`gateway.platforms.base` 必须能 import) |
| Python | Hermes venv 里装了 `httpx` |
| 账号 | **一个专用小号.** 微信一个账号同时只允许一个活动会话, 所以 agent 用哪个账号, 你自己的手机就会被那个账号踢下线 |

WeKit 没有发布过打了 tag 的 release (只有 CI 产物), 而且实际上是单人维护的项目. 微信一次更新就可能随时把它打挂. 心里有数.

## 安装

### 1. 配置手机

给设备 root, 装 Xposed 框架, 装 WeKit 模块, 启用并把微信勾进作用域, 应用, 然后重启微信. 从 `logcat` 确认模块**真的**加载进了 `com.tencent.mm` (应该能看到框架为该包加载 Xposed 的日志, 以及 WeKit 自己的 hook 行). 模块在管理器里显示"已启用"但实际没加载, 从外面看长得一模一样.

有三个设置很关键, 而且很容易漏:

- **关掉微信的热更新 (tinker).** 被热补丁过的微信会让模块**静默**加载失败.
- **打开 WeKit 的反 Xposed 检测选项**, 如果你这个构建有的话.
- **改掉默认 API token.** WeKit 出厂带一个占位符字面量, 不改等于没有鉴权.

然后在微信里打开 WeKit 的设置, 启用 **API + MCP server**: 设好端口 (`3001`) 和你的 token.

> 启用模块后的第一次启动会跑一次完整的 DexKit 扫描, `:3001` 可能要**好几分钟**才开始监听. 这是正常的, 别急着当成故障.

另外把微信从这台设备的电池优化 / doze 里排除掉, 免得系统把持有 server 的进程杀了.

### 2. 让 agent 主机能访问到 `:3001`

| 传输方式 | 建议 |
|---|---|
| **同一网段** | 最简单. `WEKIT_BASE_URL` 直接指手机 IP (`http://192.168.1.60:3001`). 什么都不用额外装 |
| **不同网段 → 路由器 DNAT** | **需要跨网段时推荐这个.** 见 [传输方式](#传输方式) |
| **USB (`adb forward`)** | **不推荐.** 实测数据见 [传输方式](#传输方式) |

### 3. 安装插件

```bash
mkdir -p ~/.hermes/plugins/wechat-wekit
cp plugin/__init__.py plugin/adapter.py plugin/actions.py plugin/plugin.yaml \
   ~/.hermes/plugins/wechat-wekit/
```

在 `~/.hermes/config.yaml` 里启用:

```yaml
plugins:
  enabled:
    - wechat-wekit-platform      # ← the plugin.yaml `name`
```

注意这里有两个标识符: **插件**叫 `wechat-wekit-platform`; 它注册出来的**平台**叫 `wechat-wekit` (日志里出现的是后者, cron 的 `deliver=` 目标写的也是后者). 如果你之前用同一个账号跑过手机 UI 版的微信 adapter, 请把它关掉, 同一个账号上挂两个 adapter 会导致重复发送和回声循环.

### 4. 配置

把这些写进 Hermes 的 `.env` (参考 `.env.example`):

```bash
WEKIT_TOKEN=YOUR_TOKEN
WEKIT_BASE_URL=http://192.168.1.50:3001
WEKIT_ALLOWED_USERS=wxid_xxxxxxxx
WEKIT_ALLOW_ALL_USERS=false
```

### 5. 验证

重启 gateway 之前, 先**从 agent 主机上**证明链路是通的 (不是从你的笔记本, 只有 agent 主机那台算数):

```bash
curl -s http://192.168.1.50:3001/api/self/info \
     -H "Authorization: Bearer YOUR_TOKEN"
# → {"wxId":"wxid_xxxxxxxx","customWxId":"..."}
```

返回 `200` 并带上已登录账号的 id, 说明整条路径都没问题: 路由器, 手机, 微信进程, WeKit server, token. adapter 在连接时探测的就是这个端点 (试 4 次, 间隔 1.5 秒, 然后平台放弃并报 `cannot reach WeKit API at …`).

然后重启 gateway, 观察 `wechat-wekit: connected to …`, 接着是 `wechat-wekit: poll alive (N rounds, no new msg)`, 这行每 5 轮 poll 打一次. 健康的标志是 `poll alive` 持续出现, 并且**没有** `poll error`.

> **插件日志不一定在 `gateway.log` 里.** 在参考的这套 Hermes 构建里, gateway 的日志过滤器只收 `gateway.*` 这些 logger, 而插件 adapter 的 logger 名是 `hermes_plugins.*`, 于是本 adapter 的所有输出都跑到了 `agent.log`. 两个都 grep 一遍.

## 环境变量

| 变量 | 必填 | 含义 |
|---|---|---|
| `WEKIT_TOKEN` | **是** | 在 WeKit 的 API + MCP server 设置里配的 bearer token. `hermes gateway status` 也靠它来判断本平台是否存在 |
| `WEKIT_BASE_URL` | **是** | **从 agent 主机**看过去 WeKit API 的地址, 例如 `http://192.168.1.50:3001`. 这里**故意不设默认值**: 没配就直接拒绝连接并明说原因, 而不是猜一个地址, 然后把你扔进一个莫名其妙的重试循环里 |
| `WEKIT_ALLOWED_USERS` | 建议配 | 允许跟 agent 对话的 wxid, 逗号分隔. **只过滤入站, 出站完全不受限制** |
| `WEKIT_ALLOWED_LABEL` | 否 | 一个微信联系人标签名, 该标签下的成员可以跟 agent 对话; 连接时并入 `WEKIT_ALLOWED_USERS`. 见 [用标签管理白名单](#用标签管理白名单) |
| `WEKIT_ALLOW_ALL_USERS` | 否 | `1` / `true` / `yes` (不区分大小写) 会完全关掉白名单. 不安全: 任何能给这个账号发消息的人都能驱动你的 agent |
| `WEKIT_ENABLE_WRITE_ACTIONS` | 否 | `1` / `true` / `yes` 放行那些会改动账号或被别人看见的动作工具 (加好友, 群成员变更, 联系人标签, 朋友圈). 默认关闭 |
| `WEKIT_PLAIN_TEXT` | 否 | 默认开启: 出站文本在发送前把 markdown 拆成人话 (见 [出站文本](#出站文本)). `false` / `0` / `no` / `off` 关掉, 发原文 |
| `WEKIT_POLL_TIMEOUT_MS` | 否 | 长轮询时长, 毫秒. 默认 `30000`, 低于 `5000` 会被抬到下限 |
| `WEKIT_HOME_CHANNEL` | 否 | 定时 / cron 投递的目标 convId |
| `WEKIT_MEDIA_ADB_PATH` | 否 | `adb` 的路径. 配上它就开启"把收到的文件和图片从手机上取回来"的功能 (见下文). 不配 = 关闭 |
| `WEKIT_MEDIA_DIR` | 否 | 取回的媒体写到 agent 主机的哪里. 默认 `/tmp/wekit-media` |
| `WEKIT_CAPTURE_ARTICLES` | 否 | `true` 表示链接消息可以在手机上打开文章, 并从 WebView 的磁盘缓存里读出正文 (读不到就退化为截图). 会短暂占用屏幕, 见 [公众号文章](#公众号文章) |
| `WEKIT_ADB_SERIAL` / `WEKIT_ADB_PATH` / `WEKIT_LOG_PATH` | 否 | 取媒体用的设备序列号; 后两个是可选的保活 watchdog 用的 |
| `WEKIT_ROUTER_WAN` / `WEKIT_PHONE_HOSTNAME` | 否 | 只给路由器 DNAT 脚本用 (它的 WAN 侧地址, 以及手机的 DHCP 主机名) |

**`WEKIT_ALLOWED_USERS` 要填你在日志里真正见过的 wxid**, 也就是 `wechat-wekit: inbound from <id> …` 里的那个 id, **而不是**从通讯录里抄. 参考部署里白名单是照着好友列表填的, 结果把用户发的每一条消息都静默丢掉了, 因为他实际是用另一个账号在发. 而丢弃这个动作只在 `debug` 级别打日志, 所以在默认日志级别下, 这条通道看上去就是死的.

关于那行日志有两点要注意: 它打印的是**会话 id**, 私聊时它等于对方的 wxid, 群聊时是 `@chatroom` 的 id; 另外白名单是拿会话 id **或者**发送者 id 去匹配的.

## 传输方式

### USB (`adb forward`), 不推荐

在参考主机上, Windows 的 adb server **每 10 到 30 秒就自己崩一次再重启**. 设备本身从没掉过 (`adb devices` 全程报 `device`, 机器上只有一个 `adb.exe`, 而且当时没有任何命令在跑). 因为 `adb forward` 的规则活在 adb server 的内存里, 每崩一次 forward 就蒸发一次, 正在进行的长轮询也跟着被打断.

那台主机上实测每小时的 `poll error` 数: `163, 175, 169, 168, 169`, **大约每 21 秒断一次, 整夜如此**. 每一次断裂期间到达的消息都丢了, 在用户那边表现为"它有时候会回, 有时候不回". 同一台主机改走路由器 DNAT 的 WiFi 之后: 55 秒的长轮询完整跑完, **poll error 为 0**.

如果你的 adb 恰好很稳, USB 也能用. 只是别默认它稳, 而且永远不要在一个你没连续观察过一小时的 `adb forward` 上搭长期服务.

### WiFi 加路由器 DNAT, 推荐

当 agent 主机和手机不在同一网段时用这个, 比如 agent 主机在上层网络, 手机在第二台路由器的 LAN 里. `transport/router-dnat/wekit-dnat.sh` 跑**在路由器上** (OpenWrt 系, BusyBox `sh`, `iptables`), 把 `<路由器 WAN 侧地址>:3001` 转发到手机的 LAN IP:

```
Agent host (192.168.1.0/24) → 192.168.1.50:3001   (router WAN side)
                            → DNAT → 192.168.20.60:3001 (phone WiFi, WeKit API)
```

然后设 `WEKIT_BASE_URL=http://192.168.1.50:3001`.

这个脚本做了什么, 以及你需要知道的:

- 从 `/var/dhcp.leases` 里**按 DHCP 主机名** (`NAME` / `WEKIT_PHONE_HOSTNAME`) 解析手机当前 IP. 租约变了下次运行会自愈.
- **如果主机名匹配不到租约, 它会静默退出, 什么都不装.** 如果跑了没反应, 先去查租约文件.
- 它创建的每条规则都打了 iptables comment 标记, 而且只删带这个标记的规则, 绝不会碰别的工具的规则 (比如透明代理).
- 幂等: 如果 DNAT 已经指向当前 IP 就直接退出, 不重建, 所以它不会掐断正在进行的长轮询.
- 装三条规则: `nat/PREROUTING` DNAT, `FORWARD` ACCEPT, `nat/POSTROUTING` MASQUERADE, 每条都插在位置 1.
- 日志走 `logger -t wekit-dnat`.

改脚本顶部的配置块 (`PORT`, `WAN`, `NAME`), 或者用 `WEKIT_ROUTER_WAN` / `WEKIT_PHONE_HOSTNAME` 传进去. iptables 规则不持久, 所以要在 `/etc/rc.local` 里开机跑一次 (前面加一小段 sleep, 等 DHCP 拿到租约), **并且**用 cron 每隔几分钟再跑一次. 部署前先确认这台路由器上已有的透明代理规则没有把 `3001` 端口劫走.

只支持 nftables 的路由器, 或者 dnsmasq 租约不在 `/var/dhcp.leases` 的路由器, 需要自行改写脚本.

### 其他方式

任何能让 agent 主机访问到手机 `:3001` 的路子都行: WireGuard 隧道, SSH 隧道, `netsh portproxy`. 插件只认 `WEKIT_BASE_URL`.

## 保持手机端存活

`ops/wechat_watchdog.py` 是一个**示例**, 不是必需组件. 它跑在一台能 adb 到手机的机器上, 做三件事:

1. 按固定间隔用 Hermes 走的同一条路径探测 `GET /api/self/info`, **纯 HTTP, 不碰 adb**.
2. 只有连续失败若干次之后才动 adb, 去看微信是不是还在跑.
3. 如果微信没在跑, 用 `monkey -p com.tencent.mm -c android.intent.category.LAUNCHER 1` 把它拉起来.

里面固化了两条硬规矩:

- **永远不要用 `force-stop` 微信来做恢复动作.** 参考部署里这么干直接破坏了 Xposed 的注入状态, 只能整机重启才救得回来. watchdog 只会启动, 从不停止.
- **不要用 adb 做轮询.** 哪怕只是周期性地 `adb shell pidof`, 也会扰动 adb server, 有可能把 forward 搞掉; 而 HTTP 探测本来就更直接地回答了你真正关心的问题.

这套设计背后的几个事实: WeKit 的 API+MCP server 开关**重启后仍然保留**, 而且只要微信起来, 模块加载了, server 就会自动跟着起来. 但它没有"开机自启"选项, 而手机重启后微信自己也不一定会起来. 还要记得第一次启动前那次好几分钟的 DexKit 扫描, 那段时间 `:3001` 是不应答的.

## 能做什么, 不能做什么

| 能力 | 状态 | 说明 |
|---|---|---|
| 收私聊文本 | ✅ | MCP `wait-for-new-message`, WCDB 层 hook |
| 收群聊文本 | ✅ | 群会话 id 以 `@chatroom` 或 `@im.chatroom` 结尾; 发言成员和会话是分开上报的 |
| 发文本 | ✅ | `POST /api/messages/text`, `{type, convId, content}` |
| 发图片 | ✅ | `POST /api/messages/image`, multipart: 表单字段 `convId` 加一个文件部分, 名字必须正好是 `file`. 字节从 agent 主机上传; 接受本地路径, `http(s)://` URL, `file://` 或 `data:` URI. 如果带了说明文字, 会**另发一条文本消息** |
| 联系人显示名 | ✅ | `GET /api/contacts/{wxid}`, 进程内缓存 (优先备注名, 其次昵称); 查不到就退回原始 id |
| 定时 / cron 投递 | ✅ | 目标由 `WEKIT_HOME_CHANNEL` 指定 |
| 入站白名单 | ✅ | 拿会话 id **或**发送者 id 匹配 |
| 不会回声循环 | ✅ | WeKit 不上报 agent 自己发出去的消息 |
| 收非文本消息 (图片/语音/文件/链接/表情/位置/引用) | ✅ | 每种载荷都被解析成一行简短可操作的描述: 文件给文件名和大小, 语音给时长, 链接给真实 URL, 引用给被引用的原文. 原始 XML 绝不会进到模型面前 |
| 收文件 (任意扩展名) | ✅ | 会被取到本地成为 `media_urls` 里的真实文件. WeKit 的下载与格式无关, 它拉的是原始字节, 所以 xlsx / docx / pdf / zip 什么都行. 需要配套的 WeKit 脚本, 见 [真正拿到文件本身](#真正拿到文件本身) |
| 收图片 | ✅ | 同一条路径; 会从微信的存储形态还原成真正的 JPEG/PNG |
| 收语音 | ✅ | WeKit 解码成 mp3; 作为真实文件附上 |
| 收表情 | ⚠️ | 标准表情会转成 GIF 附上; 自定义表情可能解码失败 (但一定会给一个 `[Sticker]` 文本标签) |
| 收视频 | ⚠️ | 元数据和文本标签一定有; 文件本身只有在微信已经下载过的情况下才拿得到 (WeKit 没有暴露视频下载端点) |
| 收公众号文章 | ✅ | 配上 `WEKIT_CAPTURE_ARTICLES=true` 后, 链接会在手机上打开, 全文从 WebView 磁盘缓存里读出来 (是结构化正文, 不只是摘要); 读不到就退化为截图. 见 [公众号文章](#公众号文章) |
| 按需读聊天记录 | ✅ | `wechat_pull_history` 工具, 分页, 从旧到新, 群里的发送者会解析成名字. 不用于入站补漏 (见 [入站边沿触发导致消息永久丢失](#1-入站边沿触发导致消息永久丢失)) |
| 从 CDN 重新下载媒体 | ✅ | 配套脚本会先让微信把图片/文件从 CDN 缓存下来再去取, 所以手机没有自动下载过的媒体也能送到 agent 手上 |
| 发语音消息 | ✅ | `wechat_send_voice` 工具, 文本用 edge-tts 合成, 在手机上转成 SILK, 以真正的语音气泡发出去 |
| 发视频 | ✅ | `wechat_send_video` 工具, multipart 打到 `POST /api/messages/video` |
| 群成员管理 | ✅ | `wechat_group_members` 工具, 列出 / 添加 / 移除 / 邀请. 除了 `list` 之外都需要 `WEKIT_ENABLE_WRITE_ACTIONS` |
| 接受好友申请 | ✅ | `wechat_accept_friend` 工具, 需要 userId + ticket + scene. 受 `WEKIT_ENABLE_WRITE_ACTIONS` 管控 |
| 发朋友圈 | ✅ | `wechat_post_moment` 工具, 纯文本或文本加图片. 受 `WEKIT_ENABLE_WRITE_ACTIONS` 管控 |
| 联系人标签 | ✅ | `wechat_labels` 工具, 列标签, 读某个标签的成员, 设置某个联系人的标签. 标签还能通过 `WEKIT_ALLOWED_LABEL` 直接充当入站白名单 |
| 引用 / 回复串 | ⚠️ | 入站的引用会被解析出来 (被引用的原文会呈现给 agent); 但**出站**的 `reply_to` 是被忽略的, 回复就是普通消息, 不是微信的引用 |
| 正在输入提示 | ❌ | `send_typing` 是空操作 |
| 设置 / 取消群管理员 | ❌ | WeKit 的 REST API 没有这个端点 (加人/移除/邀请有, 提升管理员没有) |
| 用文件 / 位置 / 表情作为回复 | ❌ | WeKit 有对应端点, 但回复这条路径只接了文本和图片. 语音和视频可以通过上面的动作工具走 |

注册的 `max_message_length` 是 2000.

## 出站文本

**微信没有 markdown 渲染器.** 模型写的 `**重要**` 到用户手机上就是字面的六个字符, `### 标题` 就是三个井号加标题. 注册时的 platform hint 已经明说了"这里不渲染 markdown, 按人在聊天里打字的方式写", 但那是一句请求, 不是一道保证 —— 模型是被 markdown 喂出来的, 迟早会漏一个 `#` 出来. 所以 `send()` 里还有一层确定性的转换兜底 (`WEKIT_PLAIN_TEXT`, 默认开):

| 模型写的 | 用户看到的 |
|---|---|
| `**重要**` / `*斜体*` / `__粗__` | 重要 / 斜体 / 粗 |
| `### 部署结果` | 部署结果 (井号没了, 后面空一行) |
| `` `docker restart` `` / ```` ```代码块``` ```` | 代码本身, 没有反引号和围栏 |
| `- 先备份` | `· 先备份` (中文读者预期的项目符号; `1.` 编号原样保留) |
| `[订阅入口](https://s.starq.me/…)` | 订阅入口 https://s.starq.me/… (链接文字只是把 URL 重念一遍时, 只留 URL) |
| 竖线画的表格 | 两列摊成 `键: 值`, 三列以上每行摊成一小段 (手机屏幕上等宽网格必然折行折烂) |
| `> 引用` / `---` / 连续三个以上空行 | 引用只留正文, 分隔线丢掉, 空行压成一个 |

难的一半不是拆 markdown, 是**别把不是 markdown 的东西拆坏**. 下面这些一个字都不会动: `wxid_xxxxxxxx` 里的下划线, `/root/.hermes/plugins/wechat_wekit/my_file.py` 这样的路径, `__init__.py`, `2*3` 和 `2**3`, 行首的 `#1`, `C#`, 散文里落单的 `*`, `rm *.log`, 带括号的 URL (`…/wiki/Foo_(bar)#history`), 以及没有分隔行的、只是碰巧带竖线的正文. 每条规则都往保守里写: 拿不准就不动 —— 屏幕上多一个星号只是难看, 而改坏一个 wxid 是内容错误, 收到的人根本判断不出来.

转换器本身不会抛异常: 它出了 bug, 最坏也只是消息带着 markdown 发出去, 不会让消息发不出去.

## 动作工具

除了消息流之外, 插件还注册了七个 agent 可以在对话中调用的工具. 它们归入 `hermes-wechat-wekit` 这个 toolset, 这个名字是特意取的: Hermes 会把插件平台的默认 toolset 推导成 `hermes-{平台名}`, 所以微信会话里这些工具是自动启用的, 不用改任何配置.

| 工具 | 作用 | 写权限门禁 |
|---|---|---|
| `wechat_pull_history` | 读某个会话最近的消息, 从旧到新 | 无 |
| `wechat_send_voice` | 发一条原生语音气泡 (文本经 edge-tts 合成, 或直接给现成 mp3) | 无 |
| `wechat_send_video` | 发送本地视频文件 | 无 |
| `wechat_group_members` | `list` 成员, 或 `add` / `remove` / `invite` | 仅写操作 |
| `wechat_accept_friend` | 接受一个待处理的好友申请 | 是 |
| `wechat_post_moment` | 发文本或文本加图片到朋友圈 | 是 |
| `wechat_labels` | `list` 标签, 读标签 `members`, 或 `set` 某个联系人的标签 | 仅 `set` |

发消息这件事, 无论文本, 语音还是视频, 都不比 agent 本来就会发的那条回复更危险, 所以这几个工具永远可用. 而任何会改变账号社交关系, 或者会被别人看到的操作, 在 `WEKIT_ENABLE_WRITE_ACTIONS=1` 之前都保持惰性: 工具照样注册, 照样在模型面前有描述, 但调用时会拒绝并告诉你该设什么. 这样一来, 账号就不会被塞进消息里的一句注入悄悄改掉.

`wechat_send_voice` 在用 `text` 参数调用时需要 agent 主机上装了 `edge-tts`; 用 `audio_path` 则没有额外依赖. mp3 转 SILK 是在手机上, 由 WeKit 完成的.

**这些工具并不局限在微信会话里.** Hermes 对一个它不认识的插件 toolset 的默认处理是**对所有平台开启**, 所以 CLI 或 Telegram 会话同样能拿到这七个工具, 这是实测过的, 不是推测. 通常这是好事 (在 CLI 里让它拉一段微信对话是很合理的需求), 但这意味着边界是写权限门禁和你自己的 prompt, 而不是你当前所在的通道: `wechat_send_voice` 和 `wechat_send_video` 接受自由填写的 `conv_id`, 而且**不受**门禁约束, 所以在任何平台上模型都能给任意联系人发音频或视频. `WEKIT_ALLOWED_USERS` 管不住这个, 它只过滤入站. 如果你在意, 跑 `hermes tools`, 在不该拥有它的平台上把这个 toolset 关掉.

### 用标签管理白名单

`WEKIT_ALLOWED_USERS` 是写在文件里的一串 wxid. `WEKIT_ALLOWED_LABEL` 是同一件事, 但改成在手机上管理: 把所有该被 agent 回应的联系人都归到一个微信标签下, 在这里写上标签名, 连接时就会解析出成员并并入白名单. 之后加人只需要在微信里点两下, 不用改 `.env` 再重启.

有两个性质值得知道: 合并是叠加的, `WEKIT_ALLOWED_USERS` 里原有的内容仍然生效; 以及标签解析失败会被记录并忽略, 绝不会被当成"放行所有人", 因为一次查询失败绝不能把账号悄悄敞开. 成员在连接时读一次, 之后大约每十分钟重读一次, 所以在手机上授权或收回会自己生效, 不用重启, 而且不会在入站消息经过的路径上插入 HTTP 调用.

**标签必须先在微信里建好** (我 → 通讯录 → 标签). WeKit 能读标签, 也能把已存在的标签打给联系人, 但没有创建标签的端点, 微信是通过另一个不在 REST 面上的 CGI 干这件事的. 以前设置一个不存在的标签名看上去是成功的 (微信会跳过这个未知名字, 但照样返回 200); 现在 `wechat_labels` 会先拿标签列表核对名字, 不匹配就直接失败并列出真实存在的标签, 而不是报告一次什么都没做的写入.

## 接收文件和图片

每一种入站消息都会被解析成一行 agent 能直接据以行动的描述, 而不是微信实际发过来的那坨 XML:

| 对方发的 | agent 看到的 |
|---|---|
| 图片 | `[Image] — 871.2 KB` |
| 语音 | `[Voice message] — 2.8s` |
| 文件 | `[File] report.xlsx — XLSX, 11.0 KB` |
| 文章 / 链接 | `[Link] <title>` 加描述, 再加**真实 URL**, 这样 agent 能自己去读 |
| 引用回复 | `[Reply to Alice] <the reply>`, 被引用的原文放在 `reply_to_text` |
| 表情 / 位置 / 小程序 / 转账 / … | 对应的一行摘要 |

拿到**真实字节**要难得多, 原因在上游: WeKit 的每一个下载端点都要 `msgSvrId`, 而 WeKit 的任何 API 都不会返回这个东西, `wait-for-new-message` 只给 ConvId, Sender, Type 和 Content. 所以媒体没法通过 WeKit 自己拉下来.

设置 `WEKIT_MEDIA_ADB_PATH`, 插件就改走另一条路: 用 adb 把微信已经写进自己存储的那个文件复制出来.

```bash
WEKIT_MEDIA_ADB_PATH=/path/to/adb
WEKIT_ADB_SERIAL=YOURSERIAL          # optional when only one device is attached
WEKIT_MEDIA_DIR=/var/lib/hermes/wechat-media
```

取到的媒体写到那个目录, 并通过 `media_urls` 传给 agent, 于是视觉工具能打开图片, 文件工具能读文档. 依赖它之前先了解边界:

- **媒体必须已经在手机上.** 微信只在有人点开时才下载文件, 除非开了自动下载 (微信 → 我 → 设置 → 通用 → 照片, 视频, 文件和通话). 没开的话, 一条文件消息就只有元数据, 根本没有东西可复制.
- **文件是按精确文件名匹配的**, 不会有歧义. 但**图片在载荷里没有可用的文件名**, 所以取的是消息到达时间点附近最新写入的那张图, 这是个启发式做法, 限制在一个很短的时间窗内.
- 微信存成自有 `wxgf` 容器的图片会被**丢弃而不是附上**, 因为下游没有任何东西能打开它. 普通的 JPEG/PNG/GIF, 包括 XOR 混淆过的变体, 都能正常还原.
- adb 不够可靠, 所以调用带重试; 失败永远不是致命的, agent 至少还留着那行文字描述.

如果你希望这条路是可靠的而不是尽力而为的, 那么修复点在上游: `wait-for-new-message` 读到的那一行里本来就有 `msgSvrId`, 只要把它放进响应里, 后面用文档里写着的 `download-file` / `download-image` 端点就能干干净净地把事办了.

## 真正拿到文件本身

开箱状态下这个插件只能*描述*一个进来的文件, 打不开它, 原因值得直说: **WeKit 的下载端点全部以 `msgSvrId` 为键, 而 WeKit 的任何 API 都不会返回这个 id.** `wait-for-new-message` 给的是 ConvId/Sender/Type/Content; `get-chat-history` 给的是 sender/content. 取附件所需要的那个 id, 就是从来没有被暴露出来过.

`phone-script/hermes-media-bridge.js` 在微信内部把这个缺口补上了, 而且不用改 WeKit 的源码. WeKit 自带一个能 hook 任意方法的 JavaScript 引擎, 所以这个脚本 hook 了 WeKit 自己也在 hook 的那个 WCDB 插入调用, 直接从 `ContentValues` 里读出 `msgSvrId`, 再调用 WeKit 自己的本地 API 把附件下载到 `/sdcard/Download/WeKit/`. 之后插件通过 adb 把它拉到 agent 主机, 并把本地路径放进 `media_urls`.

### 安装脚本

```bash
adb push phone-script/hermes-media-bridge.js \
  /sdcard/Android/data/com.tencent.mm/WeKit/scripts_js/
```

把脚本顶部的 `TOKEN` 改成你的 WeKit API token, 然后在 WeKit 里启用**脚本引擎 (JS)** (功能 → 搜 `javascript`). 一启用就会立刻加载脚本, 不用重启微信. 最后, 把插件指向 adb:

```bash
WEKIT_MEDIA_ADB_PATH=/path/to/adb
WEKIT_ADB_SERIAL=<serial>        # optional with a single device
WEKIT_MEDIA_DIR=/var/lib/hermes/wechat-media
```

### 会坑到你的地方

- **改了脚本不会自动重新加载.** 要把 JS 功能关掉再打开; 日志里出现 `loaded script, name=...` 才说明新版本生效了.
- **`msgSvrId` 必须当字符串读.** 它超过 2^53, 用 JavaScript number 读会被静默四舍五入, 之后下载请求要的就是一个根本不存在的 id 了. 脚本用的是 `getAsString` / `Cursor.getString`.
- **绝对不要在 hook 里做网络 I/O.** 它跑在微信的数据库线程上, 在那里下载一个大附件会把 app 冻住. 脚本的做法是把 id 入队, 交给工作线程去取.
- **你自己发出去的消息在插入时没有 `msgSvrId`** (服务端在发送时才分配), 所以不能拿自己发的消息来测这条链路.
- 脚本还会补历史: 启动时会把安装之前收到的近期媒体排进队列.

### 公众号文章

文章链接 agent 是抓不到的, 而且这不是代理或网络的问题: `mp.weixin.qq.com` 只把页面给真正的微信客户端, 其他一律重定向到验证码 ("环境异常"). 但文章会在微信自带的系统 Chromium WebView 里渲染, 一旦渲染完成, 它的 HTML 就躺在那个 WebView 的磁盘 HTTP 缓存里.

配上 `WEKIT_CAPTURE_ARTICLES=true` 之后, 一条链接消息会让手机打开这篇文章; 插件随后直接从 WebView 的 **Chromium 磁盘缓存** (Simple Cache 格式) 里把渲染好的文档读出来, 解压, 一次性抽出干净的结构化正文, 标题加内容. 设备上什么都没有被改动: 缓存文件只读不写, 不滚屏, 不往微信里注入, 也不动无障碍设置.

如果文档不在缓存里 (少见的 `no-store` 响应), 就退化为打开文章, 滚屏, 把截图附给 agent, 让它用视觉能力去读.

这两条路都会占用手机屏幕几秒钟, 所以这个功能默认关闭. 只在专门给 agent 用的手机上开它.

## 已知限制

### 1. 入站边沿触发导致消息永久丢失

这是关于本项目你最需要理解的一件事.

WeKit 的 `wait-for-new-message` 这个 MCP 工具**只在调用期间**注册 WCDB 监听器, 并在 `finally` 里把它摘掉. 没有队列, 没有缓冲, 没有游标. **任何在 poll 循环不处于 wait 调用里的时刻到达的微信消息就是没了, 不可能找回来, 本插件不行, 别的任何东西也不行.** 这是上游的行为, 不是这里引入的 bug.

插件能把这个窗口压到最小, 但关不掉:

- 每条入站消息都作为**后台 asyncio task** 派发, 这样 poll 循环立刻重新挂上监听器, 而不是干等 LLM. 如果卡在等回复上, 那么每一轮对话都会有几十秒的通道失聪期, 而那恰恰是用户最可能追发一条的时候.
- 长轮询是背靠背重新发起的, 中间没有空闲间隙.
- 后台派发内部的失败按 `error` 级别记录, 所以丢一条消息至少会留下痕迹, 而不是凭空消失.
- 关停时, 正在进行的派发有最多 10 秒把消息发完, 然后才被取消.

仍然会丢消息的窗口:

- gateway 启动, 关停, 重启或 reload
- poll 出错后的指数退避 (1 秒起, 翻倍到 30 秒封顶), 这就是传输稳定性如此重要的原因
- MCP 会话过期与重新初始化
- 任何没能通过入站正则的结果 (按 `debug` 记录, 然后丢弃)

实际使用中: 一问一答式的对话是可靠的; 连珠炮式的连发可能丢消息, 而且**你和 agent 都不会收到"有消息丢了"的信号**. 用历史 diff 做补漏没有实现, 因为 WeKit 的历史端点返回的是 `sender: content` 这样的键值对, 既没有时间戳也没有消息 id, 要做出正确的 diff 是真的难, 尤其是面对一个正当地把同一句话发两遍的用户.

### 2. 其他

- **入站解析是基于正则的**, 匹配的是 WeKit 那个格式化字符串 `ConvId='…',Sender='…',Type=N,Content='…'`. 不寻常的内容可能解析失败并被丢弃.
- **消息 id 是本地时间戳**, 不是微信的消息 id. 它们不是稳定标识, 而且可能撞车.
- **没有基于内容的去重**, 这是故意的: `wait-for-new-message` 每次数据库插入触发一次, 而按文本去重会吞掉一个正当地重复自己的用户.
- **白名单匹配的是会话 id *或* 发送者 id.** 在群里, 把某一个成员加进白名单, 会让这个成员在该群里的消息全部放行. 加白名单要想清楚, 并且记住出站从来不过滤.
- **单账号, 单设备.** 不支持多账号或多设备.
- **微信的单会话规则**意味着跑 agent 账号的那台手机会把你自己的手机从那个账号上踢下线.
- **上游风险.** WeKit 没有稳定 release, 只有一个维护者.

## 排障

| 现象 | 可能原因 |
|---|---|
| 启动时报 `cannot reach WeKit API at … after retries` | `WEKIT_BASE_URL` 错, token 错, DNAT 没装, 或者微信/WeKit server 没在跑. 用 agent 主机上的 `curl /api/self/info` 那步复现 |
| 刚启用模块后 `:3001` 拒绝连接 | 首次启动的 DexKit 扫描, 等几分钟 |
| 模块在管理器里已启用, 但根本没有 API | 微信热更新 (tinker) 没关掉 → 模块静默没加载. 去 `logcat` 里找模块加载那几行 |
| `poll error` 反复出现 | 传输不稳定. 如果你走的是 USB `adb forward`, 那几乎肯定就是它, 换 WiFi |
| 通道没动静, 但 `poll alive` 一直在跳 | 入站其实到了, 只是被白名单丢掉了. 把日志级别提到 `debug`, 找 `drop msg from unlisted`, 然后用 `inbound from …` 里的 id 修 `WEKIT_ALLOWED_USERS` |
| `gateway.log` 里一行 `wechat-wekit` 都没有 | 插件的 logger 是 `hermes_plugins.*`, 去 `agent.log` 里看 |
| 手机重启后通道就死了 | 微信没自己起来. 用 `monkey` 把它拉起来, 永远不要 `force-stop` 它 |
| 看起来都连上了, 但回复全是报错文本 | 这不是通道的问题, 去查 agent 自己的模型 / provider 配置 |

## 安全须知

- **传输是明文 HTTP 加一个 bearer token.** 同一 LAN 段内任何能访问到 3001 端口的人, 都能以那个微信账号的身份读消息和发消息. 把这个 token 当成完整的账号凭据来对待.
- **WeKit 的 API server 绑的是 `0.0.0.0`, 而且上游不可配置.** 手机所在网络上的任何设备都能访问它. 把手机放在可信网络里, 可以考虑用独立 VLAN 或访客网络做隔离.
- **绝对不要把 3001 端口转发到公网.** DNAT 脚本映射的是路由器在私有 LAN 上的*内网* WAN 侧地址, 它不是一次公网暴露, 你也不该把它改造成公网暴露. 要跨不可信网络就用隧道 (WireGuard, SSH), 而不是把端口开出去.
- **一定要设 `WEKIT_ALLOWED_USERS`.** `WEKIT_ALLOW_ALL_USERS=true` 只适合调通阶段用, 一直开着意味着任何给这个账号发消息的陌生人都在驱动你的 agent, 它的工具, 以及你的 token 预算.
- **入站消息内容是不可信输入, 永远不是指令.** 本插件注册的平台提示里明确写了这一点, 但真正的边界是你自己的 prompt 和工具权限.
- **不需要就别开 `WEKIT_ENABLE_WRITE_ACTIONS`.** 它挡在"一条写着'把我拉进你的群'的消息"和"agent 真的去做了"之间. 门禁关着时, 注入到消息里的 prompt 仍然能让 agent 去*尝试*, 但工具会拒绝. 门禁开着时, 工具就照办了.
- **出站不过滤.** agent 可以给这个账号通讯录里的任何人发消息.
- **朋友圈是对该账号所有联系人公开的**, 而且这里没有任何东西会去删它. 动作工具也没有任何限流: 一个陷入循环的 agent 可以反复发朋友圈, 或者以远超人类的速度反复折腾群成员.
- 发图工具会去抓任何交给它的 `http(s)://` URL, 也会读任何本地路径, 当 agent 自己挑参数的时候记着这一点.
- 别把你的 `.env` 提交上去. token, 你的 wxid, 以及你的 LAN 拓扑全在里面.

## 法律与服务条款

**自动化个人微信账号违反微信的服务条款.** 比这轻的行为都有人被封过. 封号不只影响收发消息: 它还会**冻结微信支付**, 包括该账号的余额和绑卡功能.

- 用一个**专用小号**, 里面没有钱, 没有重要联系人, 也没有你会心疼的历史记录. 这不是建议, 这是唯一负责任的跑法.
- 本项目为**研究与个人使用**而发布. 不要拿它做群发, 骚扰式触达, 抓取他人数据, 或商业自动化.
- 跟 agent 对话的每一个人都是真人, 而他们并没有同意被一个 LLM 处理. 请据此行事.
- 跑不跑是你的决定, 风险由你承担. 作者不提供任何担保, 也不对封号, 资金冻结, 消息丢失或其他任何后果承担责任.

## 许可证与致谢

MIT 许可证, 见 `LICENSE`.

本项目依赖但不再分发 Ujhhgtg 的 **[WeKit](https://github.com/Ujhhgtg/WeKit)**, 那个 Xposed 模块才是真正 hook 微信的部分, 也是它暴露出本插件所使用的 REST + MCP API. 所有跟微信打交道的硬骨头都在那边. 上游署名见 `NOTICE`, 再分发它的任何部分之前请先看 WeKit 自己的许可证.

作为 [Hermes Agent](https://github.com/NousResearch) (Nous Research) 的平台插件而构建.
