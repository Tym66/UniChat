# 故障排查

English: [troubleshooting.en.md](troubleshooting.en.md)

`hermes-wechat-wekit` 的症状导向 runbook. 每一条都是 **症状 → 如何确认 → 根因 → 修复** 四段式.

> **动手之前, 先看法律与服务条款.** 自动化个人微信账号违反微信服务条款. 封号的代价不止是丢掉这个账号: 挂在它名下的微信支付余额会一起被冻结. 请在一台专用设备上用**专门的小号**, 把流量压低到人类的节奏, 并且只把这个项目当研究 / 个人用途. 另外注意, 微信同一账号只允许一个活跃会话, 把这个账号登录到手机上, 就等于把它在其他所有地方挤下线. 本文档里没有任何内容能让这种自动化在政策层面变得安全.

> **安全.** WeKit 的 API server 监听在所有网卡上, 而它的 bearer token 是走**明文 HTTP** 传的. 任何能带着这个 token 摸到 3001 端口的东西, 都拥有这个微信账号的完整读写控制权, 而且 API 暴露的面比本插件用到的大得多. 把它放在可信的局域网里, 用一个足够长的随机 token, 永远不要把它端口转发到公网.

---

## 0. 先搞清楚这四件事

下面四件事, 能解释大部分人踩到的困惑:

1. **入站是边沿触发的.** WeKit 的 `wait-for-new-message` MCP 工具**只在这次调用期间**注册一个 WCDB 监听器, 并在 `finally` 里把它摘掉. 没有队列, 没有缓冲, 也没有游标. **只要一条微信消息到达时 poll 循环不在某次 wait 调用里面, 这条消息就永久丢失, 找不回来.** 这是上游 WeKit 的行为, 不是本插件的 bug, v0.1 里也没有任何补拉机制. 见 §6.
2. **插件打的日志进的是 `agent.log`, 不是 `gateway.log`.** 见 §8.
3. **传输层不稳定是 "有时候能用" 这类现象的最大来源.** 见 §4.
4. **`WEKIT_BASE_URL` 是必填的, 没有默认值.** 没配的话平台会拒绝连接, 并在日志里说明白, 它不会去猜一个地址. 见 §2.

先找到你的日志, 下面每一节都要 grep 它:

```bash
# Hermes home is usually ~/.hermes (i.e. /root/.hermes when the gateway runs as root)
ls -l ~/.hermes/logs/
# if you're not sure:
find / -name 'agent.log' -path '*hermes*' 2>/dev/null
```

设几个 shell 变量, 后面的命令就能直接粘贴:

```bash
export AGENT_LOG=~/.hermes/logs/agent.log
export WEKIT_BASE_URL=http://192.168.1.50:3001     # your phone / router DNAT address
export WEKIT_TOKEN=YOUR_TOKEN
```

---

## 1. 平台压根就没出现

### 症状

`hermes gateway status` 里没有 `wechat-wekit`, 或者 gateway 启动之后 `agent.log` 里一行 `wechat-wekit:` 都没有, 连一条失败信息都没有.

### 如何确认

```bash
grep -c 'wechat-wekit' "$AGENT_LOG"                      # 0 = never instantiated
grep -E '^WEKIT_TOKEN=' ~/.hermes/.env                   # is it set at all?
```

### 根因

`WEKIT_TOKEN` 是注册的总闸. 它没设置时, `check_requirements()`, `validate_config()` 和 env 启用钩子全都返回 false/None, 于是这个平台被当成 "没配置", 根本不会被拉起来. 这件事不会报错, 一个未配置的平台本来就是正常状态.

第二种更隐蔽的变体: token **确实**在你的 shell 里, 但**不在 gateway 进程的环境里**. 在交互式 shell 里 export 一下, 对 systemd 托管的 gateway 毫无作用.

### 修复

把它放到 gateway 真正读环境变量的地方 (通常是 `~/.hermes/.env`), 然后重启 gateway:

```bash
# ~/.hermes/.env
WEKIT_TOKEN=YOUR_TOKEN
WEKIT_BASE_URL=http://192.168.1.50:3001
WEKIT_ALLOWED_USERS=wxid_xxxxxxxx
WEKIT_ALLOW_ALL_USERS=false
```

同时确认插件本身在 `config.yaml` 的 `plugins.enabled` 下是以 **`wechat-wekit-platform`** 启用的: 插件的包名带 `-platform` 后缀, 而它注册出来的平台叫 `wechat-wekit`. 这是两个不同的字符串, 弄混了就是一次不报错的空操作.

---

## 2. `connect()` 失败, 报 "cannot reach WeKit API"

### 症状

gateway 启动时:

```
wechat-wekit: cannot reach WeKit API at http://192.168.1.50:3001 after retries: <reason>.
Is the phone forwarded and WeKit API server on?
```

`connect()` 会以 1.5 秒的间隔最多做 4 次 `GET /api/self/info`, 只有其中一次返回 200 才肯把平台拉起来. 这行里有两个判据: **它报出来的那个地址**, 以及 **`<reason>`**. 两个都要读.

**如果你看到的不是这行, 而是 `WEKIT_BASE_URL is not set`**, 那问题就全在这: 这个变量是必填的, 平台不会去猜. 配上它 (§0 第 4 条) 然后重启.

### 如何确认

```bash
curl -sS -v "$WEKIT_BASE_URL/api/self/info" -H "Authorization: Bearer $WEKIT_TOKEN"
```

| 结果 | 根因 | 修复 |
|---|---|---|
| `200` 加一小段 JSON, 里面写着已登录的账号 | API 没问题, 那次失败是启动瞬间的偶发 | 重启 gateway; 如果反复出现, 见 §4 |
| `401` (日志里写作 `http 401`) | `WEKIT_TOKEN` 跟手机上 WeKit "API + MCP server" 设置里的那个 token 对不上 | 从手机的 WeKit 设置对话框里重新读一遍 token, 更新 `.env`. 不要沿用 WeKit 自带的默认 token. |
| `Connection refused` | :3001 上没有任何东西在监听: 微信没在跑, WeKit 的 API server 开关是关的, 或者模块没加载 | 见 §7 |
| 卡住, 然后超时 / `000` (连 RST 都没有) | 包根本没到手机: `WEKIT_BASE_URL` 里的主机写错了, DNAT 缺失或者坏了, 手机在另一个网段, 或者中间有防火墙 | §4, 先修传输层, 再重跑这条 curl |
| `200`, 但报出来的账号不是你以为的那个 | 手机登的是另一个微信账号 | 把手机登录到那个专用小号上 (记住这么做会让该账号在其他所有地方掉线) |

**这个不是故障, 别追着查:** 在 USB `adb forward` 的部署下, 从 WSL 里面 `curl 127.0.0.1:3001` **永远**不会通. `adb forward` 绑的是 Windows 的 loopback, 而 WSL2 有自己独立的网络命名空间和自己的 loopback. 要测就从 Windows 那侧测, 或者更好的办法是别再用 USB (§4).

---

## 3. agent 一条消息都收不到

**先查白名单.** wxid 里错一个字符, 就会把每一条入站消息静默丢掉, 而这个丢弃动作打的是 `DEBUG` 日志, 在默认日志级别下完全看不见. 在参考部署里这件事烧掉了好几个小时: 运维从 API 读了好友列表, 把它返回的 wxid 填进白名单, 而真人实际**用来发消息的**是另一个账号. poll 把每一条消息都正确抓到了, adapter 把它们全丢了, 没有留下任何一行看得见的日志.

### 如何确认

真正到达过 adapter 的那些 id, 权威清单在日志里. poll 循环会在 `_dispatch` 之前 (也就是在白名单之前) 就把每一条抓到的入站打出来:

```bash
grep 'wechat-wekit: inbound from' "$AGENT_LOG" | tail -20
# just the distinct ids:
grep -o 'wechat-wekit: inbound from [^ ]*' "$AGENT_LOG" | awk '{print $NF}' | sort -u
```

跟你的配置对一下:

```bash
grep -E '^WEKIT_(ALLOWED_USERS|ALLOW_ALL_USERS)=' ~/.hermes/.env
```

三种结果:

| 你看到的 | 含义 | 去哪 |
|---|---|---|
| 有 `inbound from wxid_xxxxxxxx` 这样的行, 但这个 id **不在** `WEKIT_ALLOWED_USERS` 里 | 被白名单丢了, 就是本节 |
| 有 `inbound from …` 的行, 而且那个 id **在**白名单里 | 消息已经到 agent 了, 故障在下游 (LLM, 发送). 去 `agent.log` 里找 `dispatch failed:`, 以及 gateway 自己那行 `inbound message: platform=wechat-wekit` |
| **完全没有** `inbound from …` 的行 | 什么都没抓到. 去 §2 (连不上 WeKit) 和 §4 (poll 一直在死) |

### 根因

`_dispatch` 会算出 `who = {conv_id, sender}`, 只要这个集合跟 `WEKIT_ALLOWED_USERS` 没有交集就把消息丢掉, 但**只有在白名单非空且 `WEKIT_ALLOW_ALL_USERS` 关闭时才会过滤**. 丢弃那行是 `logger.debug("wechat-wekit: drop msg from unlisted %s", conv_id)`, 低于 `INFO`, 所以在默认级别的部署上, 你得到的是彻底的沉默, 零错误提示.

注意它的反面, 因为那是方向相反的故障: **`WEKIT_ALLOWED_USERS` 为空或者没设置, 就等于完全不过滤.** 这个白名单不是 fail-closed 的.

### 修复

把你在 `inbound from` 里看到的那个 id 填进白名单. **不要**从通讯录里推: 通讯录告诉你的是谁是好友, 不是哪个账号在跟 bot 说话, 而一个人完全可以用一个不在你看的那份列表里的账号给你发消息.

```bash
# ~/.hermes/.env  — comma separated; surrounding whitespace is stripped
WEKIT_ALLOWED_USERS=wxid_xxxxxxxx,wxid_yyyyyyyy
WEKIT_ALLOW_ALL_USERS=false
```

重启 gateway, 然后确认消息这回过了过滤器 (gateway 那行 `inbound message: platform=wechat-wekit`, 加上一次回复尝试).

**同一片区域里的其他坑:**

- **群聊.** 群的 `conv_id` 以 `@chatroom` (或 `@im.chatroom`) 结尾. 过滤器匹配的是 `{conv_id, sender}`, 所以把**群 id** 加进白名单等于放行整个群, 而把**某个成员的 wxid** 加进白名单只放行那一个人. 但要注意: `inbound from` 那行**只打印 `conv_id`**, 发送者的 wxid 不进日志, 所以对群聊而言, 实际可行的做法是把你能看到的那个群 id 加白名单.
- **用 agent 自己的账号做测试.** `wait-for-new-message` 不上报自己发出去的消息. 从 agent 所在的那台手机上给 bot 发消息, 什么都不会发生, 这是预期行为, 也正是这条通道不会形成回声循环的原因. 请用第二个账号来测.
- **`WEKIT_ALLOW_ALL_USERS`** 接受 `1`, `true`, `yes` (不区分大小写), 会把过滤彻底关掉. 它是调试用的临时手段, 不是一种配置: 一个开着自动化又不过滤入站的账号, 会回复任何知道它 wxid 的人, 包括群和垃圾消息. 别让它开着.
- 白名单的值也可以来自插件配置里的 `extra.allowed_contacts`; 非空的 `WEKIT_ALLOWED_USERS` 是**替换**那份列表, 而不是追加到它上面.

---

## 4. 消息时灵时不灵, "有时候能用"

### 如何确认

按小时数一下 poll 错误:

```bash
grep 'wechat-wekit: poll error' "$AGENT_LOG" \
  | awk '{print substr($0,1,13)}' | sort | uniq -c
```

(`substr($0,1,13)` 是按 `YYYY-MM-DD HH` 分桶, 你的日志时间戳格式不一样的话要相应调整.)

健康的通道是**零** poll 错误, 外加一个周期性心跳: 每 5 次成功且没收到消息的 poll 打一次, 所以在默认 30 秒 poll 下, 空闲时大约每 2.5 分钟一行:

```bash
grep 'wechat-wekit: poll alive' "$AGENT_LOG" | tail -5
```

> ⚠️ 对比 "修之前 vs 修之后" 的时候, 一定要用**带日期**的比较来过滤, 也就是 `awk '$0 >= "2026-01-01 23:45:51"'`, 而不是 `sed -n '/23:45:51/,$p'`. 光一个时间子串会匹配到历史行, 把你的计数放大一个数量级. 这个错误已经实实在在地制造过一次离谱的统计.

### 根因

几乎总是传输层. 在参考部署里手机是 USB 接的, 通过 `adb forward` 访问, 而 **Windows 的 adb server 每 10 到 30 秒就自己崩一次再重启**. 被动观察 (我们自己一次 adb 调用都不发) 抓到 server 的 PID 在持续循环, 而 `adb devices` 全程报的都是 `device`: USB 链路从来没断过, 死的是 **server 进程**. forward 规则活在 adb server 的内存里, 所以每崩一次 forward 就蒸发一次, 正在飞的长轮询也跟着被打断.

实测影响: **大约每小时 170 次 poll 错误, 也就是差不多每 21 秒断一次.** 一条消息要能被收到, 就得正好落进监听器恰好挂着的那个不到 21 秒的窗口里. 所以才会 "有时候会回".

同一台主机, 换到 WiFi/DNAT 路径之后: **55 秒的长轮询完整跑完, 0 次 poll 错误.**

还有一个二阶效应, 会让实际情况比这个错误率本身更糟: 一次 poll 出错之后, 循环会丢掉 MCP session, 按指数退避睡一会 (1 秒 → 2 秒 → 4 秒 → …, 上限 30 秒) 再重新挂监听器. 整个退避期间这条通道是聋的, 而边沿触发意味着这期间的消息就没了.

### 修复

把传输从 USB 上挪走.

| 拓扑 | 怎么做 |
|---|---|
| agent 主机和手机在**同一网段** | `WEKIT_BASE_URL` 直接指手机: `http://192.168.1.50:3001`. 不用脚本, 不用 adb, 什么都不用. |
| agent 主机和手机在**不同网段** (典型情况: PC 挂在上层路由器, 手机在下层路由器的 LAN 里) | 在手机所在 LAN 的那台路由器上跑 `wekit-dnat.sh`, 并把 `WEKIT_BASE_URL` 指向那台路由器的 WAN 地址. |
| USB `adb forward` | **不推荐.** 只留作应急兜底. |

从 agent 主机上验证:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' "$WEKIT_BASE_URL/api/self/info" \
  -H "Authorization: Bearer $WEKIT_TOKEN"     # expect 200
```

在跑 DNAT 脚本的 OpenWrt 系路由器上:

```sh
sh /etc/wekit-dnat.sh                              # idempotent; safe to re-run
iptables -t nat -S PREROUTING  | grep wekit-dnat   # DNAT to the phone's current IP
iptables -S FORWARD            | grep wekit-dnat
iptables -t nat -S POSTROUTING | grep wekit-dnat
logread | grep wekit-dnat | tail
```

这个脚本的行为, 以及它为什么这么做:

- 它按主机名 (`WEKIT_PHONE_HOSTNAME`) 从 DHCP 租约文件 (`/var/dhcp.leases`) 里解析出手机当前的 IP, 所以租约变了能自愈.
- 如果规则已经指向正确的 IP, 它会**什么都不碰直接退出**, 这是故意的, 免得周期性重跑把一个正在进行的长轮询拆掉.
- 它装的每一条规则都带 `wekit-dnat` 注释, 而且它只会删带这个注释的规则, 所以不会干扰同机共存的透明代理的 iptables 规则.
- 用 `rc.local` 加一个短周期的 cron 做持久化 (2 分钟就够).

> 🔴 **这个脚本特有的失败模式: 如果在租约文件里找不到那个主机名, 它会静默 exit 0.** 没有规则, 没有日志, 没有任何可 grep 的东西. 症状是: 从 agent 主机 `curl` 超时, 而 `logread` 里什么都没有. 用 `cat /var/dhcp.leases` 确认, 并把 `WEKIT_PHONE_HOSTNAME` 设成手机注册的那个精确的主机名字段; 做一条 DHCP 保留能让它稳定下来. 如果手机用的是静态 IP, 它永远不会出现在租约文件里, 那就要么改成给它做保留, 要么把地址写死.

> **规则顺序:** 脚本插在 `PREROUTING` 的第 1 位. 如果你的路由器上还跑着一个会劫持你选的这个端口的透明代理, 检查一下有没有东西插到了它前面.

> 如果你只能留在 USB 上: 那就做好会遇到这个失败模式的准备, 另外注意 `adb shell pidof …` 会短暂扰动 forward, 所以紧跟在一次 adb 调用后面发出的探测可能返回 HTTP 000, 而那纯粹是假象. 探测就用干净的 `curl`, 别在循环里掺 adb.

---

## 5. 长轮询提前死掉, poll 错误落在一个可疑的固定间隔上

### 症状

`wechat-wekit: poll error: ...` 以固定节奏出现, 比如每 60 秒左右一次, 跟消息流量无关, 而且这个间隔跟网络上任何东西都对不上.

### 如何确认

对比两个超时. 长轮询的时长是你自己设的; HTTP 读超时不是:

```bash
grep -E '^WEKIT_POLL_TIMEOUT_MS=' ~/.hermes/.env      # your long-poll duration, ms
```

`connect()` 里 HTTP 客户端是这么构造的: `httpx.Timeout(15.0, read=poll_timeout_ms/1000 + 15)`, 所以读超时永远比 poll 多活 15 秒, 不可能是这里的原因. (在更早的版本里它有可能是, 那时候写死了 `read=60.0`.) 然后手动给一次 poll 计时:

```bash
# 1) open an MCP session and capture the session id from the response headers
curl -sS -D - -o /dev/null -X POST "$WEKIT_BASE_URL/mcp" \
  -H "Authorization: Bearer $WEKIT_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"probe","version":"1"}}}' \
  | grep -i '^mcp-session-id:'

SID=<paste>

# 2) complete the handshake, exactly as the adapter does
curl -sS -o /dev/null -X POST "$WEKIT_BASE_URL/mcp" \
  -H "Authorization: Bearer $WEKIT_TOKEN" -H "mcp-session-id: $SID" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

# 3) run one wait
time curl -sS -X POST "$WEKIT_BASE_URL/mcp" \
  -H "Authorization: Bearer $WEKIT_TOKEN" -H "mcp-session-id: $SID" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"wait-for-new-message","arguments":{"timeout-ms":30000}}}'
```

没有流量的话, 这一下应该阻塞大约 30 秒, 然后返回一个 "No new message" 的文本结果.

### 根因

两个完全不同的原因, 靠时间间隔区分:

- **错误间隔在 60 秒左右:** `WEKIT_POLL_TIMEOUT_MS` 等于或者超过了客户端 60 秒的读超时, 于是 HTTP 读在 WeKit 返回它正常的空结果之前就放弃了. 每次 poll 都以异常收尾, MCP session id 被丢掉, 退避随之启动.
- **错误间隔是另一个固定值, 而且明显小于你的 poll 超时:** 路径上有东西在掐断空闲的 TCP 连接, 可能是 NAT/conntrack 的空闲超时, 防火墙, 或者 §4 里那个 adb server 崩溃循环.

### 修复

- `WEKIT_POLL_TIMEOUT_MS` 可以放心调大: HTTP 读超时是从它推导出来的 (poll + 15 秒), 所以不会再被更长的 poll 跑赢. 默认是 30000, 参考部署跑的就是这个值; 传输层测试期间跑过 55000, 完整跑完没出问题. 低于 5000 的值会被抬到 5000, 解析不了的值回落到 30000.
- 如果是连接被掐断造成的固定间隔, 去修那条路径 (§4), 而不是把 poll 调短: poll 越短, 两次 wait 之间的空隙越多, 而每一个空隙都是聋的.

---

## 6. agent 正在回复时发过来的消息不见了

### 症状

用户连着发三条消息. agent 回了第一条, 表现得像另外两条从来没发过一样. 或者: gateway 重启过, 重启期间发的东西全没了.

### 如何确认

```bash
grep 'wechat-wekit: inbound from' "$AGENT_LOG" | tail -20
```

如果用户赌咒发誓发过的某条消息**没有**对应的 `inbound from` 行, 那插件就是压根没见过它. 这条消息找不回来了, 别去队列里翻, 因为根本没有队列.

### 根因

这就是边沿触发这个性质本身, 是预期行为, 不是你这套部署出了毛病.

`wait-for-new-message` 在调用开始时挂上一个 WCDB 监听器, 并在调用返回时于 `finally` 里把它摘掉. 两次调用之间这条通道是聋的, 而且没有队列, 缓冲或者游标可以回放, 落在空隙里的消息就永久没了.

插件能做的是把这个空隙压到最小, 而不是消除它. 每条抓到的消息都作为一个**后台 asyncio task** 派发出去, 这样 poll 循环可以立刻重新挂上监听器, 而不用阻塞在那里等. 这一点极其关键: `_dispatch` 会一路 await 到 LLM 把回复生成完, 那是几秒到几十秒. 如果内联 await 它, 那么每一次回复的整个过程中通道都是聋的, 而那恰恰是用户最可能追发一条的窗口.

v0.1 里仍然存在的空隙:

| 空隙 | 时长 | 说明 |
|---|---|---|
| 两次 `wait-for-new-message` 调用之间 | 一个 HTTP 往返 | 下限. 上游不改就绕不开. |
| MCP session 掉了之后重新 `initialize` | 多一个往返 | 每次 poll 出错之后都有. |
| poll 错误退避 | 1 秒 → 30 秒, 翻倍 | 这就是 §4 为什么这么要紧. |
| gateway 重启 / 重新部署 | 几秒到几分钟 | 提前打招呼, 或者挑没人发消息的时候做. |
| 解析不了的 wait 结果 | 一条消息 | 见下文. |

### 修复

没有任何修复能把丢掉的消息找回来. 你能做的是:

- 把 poll 错误压到零 (§4). 传输层稳定时, 丢失窗口就是一个往返, 正常的一问一答是可靠的; 连珠炮式的连发仍然可能掉一条.
- 重启 gateway 要有意为之, 别随手就重启.
- 老老实实告诉这条通道的用户: 一串连发的消息可能会丢一条. 不要把这条通道说成是不丢消息的.

**基于历史记录的补拉没有实现**. WeKit 确实提供按会话的历史, 但它返回的数据是 `sender: content` 这样的组合, 既没有时间戳也没有消息 id, 所以要跟已经投递过的内容对账就得做位置比对, 而这直接跟 "用户合情合理地把同样的话说了两遍" 撞车. adapter 故意**不**按内容去重, 原因正在于此. (`wait-for-new-message` 每次 DB 插入触发一次, 所以它自己不会重复投递; 唯一的重复风险来自那个还不存在的补拉.)

**还有一条值得 grep 的静默丢失路径:** 如果 WeKit 的 wait 结果跟 adapter 的解析器对不上, 这条消息会被丢弃, 只留一行 `DEBUG`:

```bash
grep 'wechat-wekit: unparsed wait result' "$AGENT_LOG"
```

解析器锚定的格式正是 `ConvId='…',Sender='…',Type=N,Content='…'`. 现实中能打败它的, 是某个改了这个输出格式的 WeKit 版本. 如果你看到这些行, 请把 (脱敏后的) 形态反馈给上游, 也反馈到这里.

---

## 7. 手机重启之后 API 再也没回来

### 症状

手机重启了. `curl /api/self/info` 一直返回 `Connection refused`. gateway 记下了 §2 那个连接失败, 通道保持不可用.

### 如何确认

```bash
adb -s YOUR_SERIAL shell pidof com.tencent.mm       # empty = WeChat isn't running
adb -s YOUR_SERIAL shell 'ss -ltn | grep 3001'      # nothing listening = server not up yet
```

如果你是从 WSL 通过 ssh 调 Windows 上的 `adb.exe`, 要显式重定向, 否则这条调用看上去会一直卡着: adb server 守护进程继承了 ssh 的 stdout 管道, ssh 永远等不到 EOF:

```bash
adb -s YOUR_SERIAL shell pidof com.tencent.mm </dev/null >/tmp/adbout 2>&1; cat /tmp/adbout
```

### 根因

两件事, 按顺序:

1. **微信自己在重启后不一定会自动启动.** WeKit 的 "API + MCP server" 开关**确实**跨重启保留, 它会一直开着, 但这个 server 是寄生在微信进程里的. 没有微信进程, 就没有 server. 设置对话框里只给了 token 和端口, 它没有 "开机启动" 这个选项.
2. **就算微信起来了, server 也要过几分钟才出现.** 冷启动时模块要先跑一遍完整的 DexKit 扫描, 在参考部署里, 启动之后紧接着两三分钟的 `Connection refused` 是正常的. 别就此断定模块坏了, 然后开始重装一堆东西.

### 修复

用 `monkey` 把微信重新拉起来, 然后等:

```bash
adb -s YOUR_SERIAL shell monkey -p com.tencent.mm -c android.intent.category.LAUNCHER 1
# then poll for a few minutes:
for i in $(seq 1 30); do
  curl -sS -o /dev/null -w "$i %{http_code}\n" "$WEKIT_BASE_URL/api/self/info" \
    -H "Authorization: Bearer $WEKIT_TOKEN"
  sleep 20
done
```

> 🔴 **恢复流程里永远不要 `am force-stop com.tencent.mm`.** 在参考部署里, 一次 force-stop 让 Xposed 模块处于未加载状态, 最后是靠完整重启手机才把通道弄回来的. 这正是 watchdog 被设计成只会启动微信, 绝不停止微信的原因. 要拉起来就用 `monkey`, 仅此而已.

要把这件事自动化, 就跑 `wechat_watchdog.py` 这个示例 (在 `ops/` 里). 它的设计是刻意为之的, 你要自己写一个的话也值得照抄:

- 健康探测走的是**跟 Hermes 同一条路径上的普通 HTTP** (`GET /api/self/info`), 间隔放得很慢 (默认 120 秒). 健康的情况下它**一次 adb 都不调**: 早期版本每 60 秒用 `adb shell pidof` 探一次, 而那些 adb 流量本身就是不稳定的来源之一.
- 只有在连续几次 HTTP 失败之后 (默认 3 次) 才会碰 adb, 而且也只是去看微信还活着没, 不在就 `monkey` 一下.
- 如果 HTTP 在失败, 但微信**是**活着的, 它会把这件事记下来然后**什么都不改**: 这个组合指向的是网络/DNAT/WeKit server 那一层, 不是 app, 盲目重启微信只会让事情更糟.
- 它会先等过一段开机宽限期, 再做第一次探测.

用 `WEKIT_BASE_URL` / `WEKIT_TOKEN` / `WEKIT_ADB_SERIAL` / `WEKIT_ADB_PATH` / `WEKIT_LOG_PATH` 来配置它. **只要接了不止一台设备, 就一定要把 `WEKIT_ADB_SERIAL` 钉死**: 不带序列号的 adb 调用会在设备之间漂, 而参考部署里同一台主机上插着两台外观相近的手机, 只有其中一台是 root 过的那台.

---

## 8. 插件日志哪儿都找不到

### 症状

你 `grep wechat-wekit gateway.log`, 一行都没有, 连启动的那几行都没有, 于是你断定插件根本没加载, 或者 poll 循环从来没启动过. 这个结论是错的, 而且很容易白搭进去一个小时.

### 如何确认

```bash
grep -c 'wechat-wekit' ~/.hermes/logs/gateway.log     # typically 0
grep -c 'wechat-wekit' "$AGENT_LOG"                   # this is where they are
```

### 根因

`gateway.log` 是按 logger 命名空间过滤的: 一个组件过滤器只放行来自 `gateway.*` 这棵 logger 树的记录. Hermes 插件的模块是在插件命名空间下导入的 (`hermes_plugins.*`), 所以 `adapter.py` 里的 `logging.getLogger(__name__)` 产出的记录**匹配不上 gateway 那个过滤器**, 永远不会写进 `gateway.log`. 它们落在 `agent.log` 里.

没有任何东西配错. 是你在读错误的文件.

### 修复

插件这一侧的所有东西都去 grep `agent.log`:

```bash
grep 'wechat-wekit' "$AGENT_LOG" | tail -50

# the lines that matter most:
grep 'wechat-wekit: connected to'          "$AGENT_LOG" | tail -3   # connect() succeeded
grep 'wechat-wekit: inbound poll loop'     "$AGENT_LOG" | tail -3   # started / stopped
grep 'wechat-wekit: poll alive'            "$AGENT_LOG" | tail -3   # heartbeat
grep 'wechat-wekit: poll error'            "$AGENT_LOG" | tail -20  # should be empty
grep 'wechat-wekit: dispatch failed'       "$AGENT_LOG" | tail -20  # background reply blew up
```

`gateway.log` 对这条路径的**另外**半段仍然有用: gateway 自己那行 `inbound message: platform=wechat-wekit …`, 它证明消息过了白名单, 进到了 agent 里. 两个文件都要用: `agent.log` 看抓取, `gateway.log` 看投递.

`dispatch failed` 之所以存在, 恰恰因为 dispatch 是作为一个游离的后台 task 在跑: 没有那个负责记录它的 done-callback, 一次失败的回复会在任何地方都不留痕迹地消失. 看到这些行, 说明通道把消息好好地抓到了, 是下游 (LLM, 发送) 出了问题.

---

## 9. 出站问题

| 症状 | 确认 | 原因 / 修复 |
|---|---|---|
| `send` 返回 `http <code>: <body>` | `grep 'wechat-wekit' "$AGENT_LOG"` | 出站文本走的是 `POST /api/messages/text`, body 为 `{"type":"text","convId":…,"content":…}`. 这里的 401 跟 §2 是同一个 token 问题. 用附录里的 curl 复现一遍, 把 "插件坏了" 和 "API 在拒绝" 区分开. |
| `send` 返回 `not connected` | — | adapter 的 HTTP 客户端没了: `connect()` 失败了, 或者 `disconnect()` 已经跑过. 见 §2. |
| `send_image` 返回非 200 | 在 `agent.log` 里找那条 `http <code>: <body>` 错误 | 发图走的是 **multipart** `POST /api/messages/image`: 表单字段 `convId`, 加上一个名字必须正好是 `file` 的文件部分, 上传的是字节内容. 不要手工设 `Content-Type`, multipart 的 boundary 由 HTTP 客户端来设. 另一种 JSON `{convId, path}` 模式吃的是**手机本地**的路径, 从 agent 主机上用不了. 文件名要带一个真正的图片扩展名, 微信认这个. |
| 图片到了, 说明文字没到 (或者单独到) | — | 预期行为. 说明文字是随后作为一条独立的纯文本消息发出去的, 图片本身不带说明. 如果图片发成功而说明文字发失败, 整体结果仍然报成功. |
| `send_image` 报 `image load failed:` | — | 那个引用解析不了. `_load_image` 接受本地文件系统路径, `http(s)` URL (由 agent 主机下载), `file://` URI, 或者 `data:` URI. 注意 http(s) 这条分支会让 agent 主机去抓一个任意 URL, 别把不可信的输入喂给它. |
| 回复里的 markdown 原样渲染成 `**asterisks**` | — | 预期行为. 微信不渲染任何 markdown; 平台提示已经告诉 agent 用纯文本回复, 但模型会失手. |
| 长回复被切掉 | — | 平台声明了 `max_message_length: 2000`, gateway 会据此拆分或截断. |
| 对没加白名单的联系人也能发出去 | — | 预期行为. `WEKIT_ALLOWED_USERS` **只过滤入站**, 出站完全不受限. 请把这当成一项安全考量, 而不是一个特性. |
| 定时 / cron 消息发不出去 | `grep -E '^WEKIT_HOME_CHANNEL=' ~/.hermes/.env` | 定时投递需要把 `WEKIT_HOME_CHANNEL` 设成一个 convId. 不设就没有默认目的地. |
| 一条回复在 gateway 重启时生成到一半被切断 | — | 预期行为. `disconnect()` 给还在飞的 dispatch 10 秒时间把消息发完, 然后取消它们, 所以在一次慢速 LLM 调用期间重启会丢掉那条回复 (以及按 §6, 重启期间到达的一切). |

---

## 10. 零碎小问题

| 症状 | 确认 | 原因 / 修复 |
|---|---|---|
| agent 看到的是 `[image message]`, `[voice message]`, `[file/link/app message]` | 跟原始那行 `inbound from … type=N` 对一下 | 非文本负载会被解码成一行可读的描述 (文件名和大小, 语音时长, 链接的 URL). 如果到达的负载是**空的**, 就回落到按微信类型码生成的占位文本 (未知的类型码渲染成 `[typeN message]`). 只有开启了媒体取回, 真正的字节才会被附上, 这需要设了 `WEKIT_MEDIA_ADB_PATH` **并且**装了配套的手机脚本. 两者不齐, agent 拿到的就只有这段描述. |
| 日志里的文本比用户发的少 | — | `inbound from` 那行把预览截断到 60 个字符. 完整文本仍然会被派发. |
| 联系人显示成裸 wxid 而不是昵称 | `curl -sS "$WEKIT_BASE_URL/api/contacts/wxid_xxxxxxxx" -H "Authorization: Bearer $WEKIT_TOKEN"` | 查询失败了 (失败是 `DEBUG` 级别), 于是回落到 id. adapter 优先取 `remarkName`, 其次 `nickname`. 查询成功的结果按进程缓存, 所以在手机上改了名字, 要等 gateway 重启才会刷新; 查询失败的结果**不**缓存, 每条消息都会重试. |
| 群消息被忽略, 私聊却正常 | 看 `inbound from` 给这个群打印的是什么 | 群的 `conv_id` 是以 `@chatroom` / `@im.chatroom` 结尾的群 id. 把这个 id 加白名单就能放行整个群. 私聊里 `conv_id` 和 `sender` 都是对方的 wxid, 所以在私聊里填一个裸 wxid 就够了. |
| 重复回复 / agent 在自言自语 | 看启用了哪些平台插件 | 两个插件绑在同一个微信账号上会双发, 而且可能形成循环. 只启用一条微信通道, 参考部署里那个更老的手机 UI 版插件就必须被显式关掉. |
| agent 照着别人微信消息里夹带的指令行事 | — | 消息内容是不可信输入, 不是指令. 平台提示是这么说的, 但真正的控制手段是白名单. 让 `WEKIT_ALLOW_ALL_USERS` 保持关闭. |

---

## 11. 动作工具

### agent 好像没有这些工具

先确认它们到底注册上了没有. 下面这段加载插件的方式跟 gateway 完全一样:

```bash
cd /usr/local/lib/hermes-agent && ./venv/bin/python - <<'PY'
from hermes_cli.plugins import PluginManager
PluginManager().discover_and_load()
from tools.registry import registry
d = getattr(registry, "_tools", None) or registry.tools
for n in sorted(n for n in d if n.startswith("wechat_")):
    print(n, d[n].toolset)
from toolsets import resolve_toolset
print(sorted(resolve_toolset("hermes-wechat-wekit")))
PY
```

七个工具都应该打印出来, toolset 是 `hermes-wechat-wekit`. 如果它们都在, 但 agent 还是调不了, 那就是 toolset 注册了却没被**启用**: 对插件平台, Hermes 默认把启用的 toolset 定为 `hermes-{platform}`, 这也是名字能对上的原因; 但 `~/.hermes/config.yaml` 里针对 `wechat-wekit` 的 `platform_toolsets:` 段会覆盖这个默认值, 一旦覆盖就必须显式列出 `hermes-wechat-wekit`, 否则这些工具会只在这个平台上消失.

如果什么都没打印, 说明插件导入失败了. 把同一段加载再跑一遍, 让 traceback 显示出来: `actions.py` 里一个语法错误或者一个坏 import 会把整个平台一起拖下水, 所以通道在同一时刻变哑, 是同一个故障.

### 写操作报的错里提到 `WEKIT_ENABLE_WRITE_ACTIONS`

设计如此. 通过好友, 改群成员, 打标签和发朋友圈, 在这个变量为真之前都会拒绝执行. 在 `~/.hermes/.env` 里设上它然后重启 gateway, 同时也要明白你打开的是什么: 设上之后, 一个白名单内的联系人发来 "把我拉进你的群", agent 是真的能照做的.

### `wechat_labels action=set` 报 "no such WeChat label"

同样是设计如此, 而且错误信息里会列出确实存在的那些标签. 微信只接受已经存在的标签; WeKit 会把每个名字解析成一个 id, 解析不出来的名字它会**跳过**, 只留一行日志, 而且仍然返回 `200`. 所以这件事以前看上去像是成功了, 实际什么都没做. 先在微信 app 里把标签建出来 (我 → 通讯录 → 标签), 再去打.

如果名字**确实**是对的却还是失败, 重新读一次 `GET /api/labels`: 建标签是走一个 CGI 发出去的, 只有服务器答复之后那一行才会出现.

### 标签打上去 "成功了", 但成员列表没变

先读回来再相信它, 这就是上面那个 bug 的全部教训. 成员查询读的是 `rcontact.contactLabelIds`, 它反映的是微信已经持久化下来的状态, 所以一次还在飞的写入是看不见的. 等几秒再读一遍. 如果始终落不下来, 检查手机有没有网: 这个写入是一次服务器往返, 不是本地改一下.

### `WEKIT_ALLOWED_LABEL` 一个人都没放进来

去 `agent.log` 里 (不是 `gateway.log`, 见 §8) 找连接前后的这三行之一:

```
wechat-wekit: allow-list label 'NAME' added N contact(s)
wechat-wekit: allow-list label 'NAME' resolved to no contacts
wechat-wekit: could not resolve allow-list label 'NAME': …
```

第二行的意思是标签存在但没人挂着它, 或者赋值还没落地. 第三行的意思是查询本身失败了, 这时这个标签会被整个忽略, `WEKIT_ALLOWED_USERS` 就成了唯一的闸门, 所以你应该始终把那个变量填好, 而不是只靠标签.

成员关系在连接时读一次, 之后大约每 10 分钟刷新一次, 有变化会以 `allow-list changed on refresh: +[...] -[...]` 记录下来. 需要立刻生效就重启 gateway.

### `wechat_send_voice` 卡在 `edge-tts` 上

只有 `text` 那种形式需要它; 用 `audio_path` 给一个现成的 mp3 就不需要. 把它装进 gateway 实际在跑的那个 venv (`/usr/local/lib/hermes-agent/venv/bin/pip install edge-tts`), 不是系统 Python. mp3 → SILK 的转换发生在手机上, 所以本地不涉及任何编解码器.

### 工具报成功, 但微信里什么都没发生

把这个 API 返回的每一个 `ok: true` 都当成 "请求被接受了", 而不是 "效果已经存在了". WeKit 有好几个端点是把一个 CGI 发出去就立刻应答. 用一次独立的读取来验证: `wechat_pull_history` 会把发出去的语音消息显示成 `<type:voice>`, 标签相关的端点会显示成员关系. 信这个, 别信写操作自己的返回值.

---

## 附录: 诊断命令

**可达性与鉴权** (从 agent 主机上跑, 永远从那台跑, 从你自己笔记本上探测什么都证明不了):

```bash
curl -sS -o /dev/null -w '%{http_code}\n' "$WEKIT_BASE_URL/api/self/info" \
  -H "Authorization: Bearer $WEKIT_TOKEN"
```

**手机登录的是谁:**

```bash
curl -sS "$WEKIT_BASE_URL/api/self/info" -H "Authorization: Bearer $WEKIT_TOKEN"
```

**联系人 / 群 id** (合法的 `type` 值: `all`, `friends`, `groups`, `official_accounts`, 全都是复数; 单数形式会被拒绝):

```bash
curl -sS "$WEKIT_BASE_URL/api/contacts?type=friends" -H "Authorization: Bearer $WEKIT_TOKEN"
```

**不经过 Hermes 发一条测试消息** (`filehelper` 是微信自带的文件传输助手, 安全的目标, 不牵扯任何第三方):

```bash
curl -sS -X POST "$WEKIT_BASE_URL/api/messages/text" \
  -H "Authorization: Bearer $WEKIT_TOKEN" -H 'Content-Type: application/json' \
  -d '{"type":"text","convId":"filehelper","content":"probe"}'
```

**通道健康度速览:**

```bash
grep 'wechat-wekit' "$AGENT_LOG" | tail -50
grep -c 'wechat-wekit: poll error' "$AGENT_LOG"                        # target: 0
grep 'wechat-wekit: poll error' "$AGENT_LOG" \
  | awk '{print substr($0,1,13)}' | sort | uniq -c                     # per-hour buckets
grep -o 'wechat-wekit: inbound from [^ ]*' "$AGENT_LOG" \
  | awk '{print $NF}' | sort | uniq -c                                 # who actually talks to it
```

**路由器 DNAT 路径:**

```sh
sh /etc/wekit-dnat.sh
cat /var/dhcp.leases | grep -i <your phone hostname>    # the script silently exits if this is empty
iptables -t nat -S PREROUTING  | grep wekit-dnat
iptables -S FORWARD            | grep wekit-dnat
iptables -t nat -S POSTROUTING | grep wekit-dnat
logread | grep wekit-dnat | tail
```

**手机:**

```bash
adb -s YOUR_SERIAL shell pidof com.tencent.mm
adb -s YOUR_SERIAL shell 'ss -ltn | grep 3001'
adb -s YOUR_SERIAL shell monkey -p com.tencent.mm -c android.intent.category.LAUNCHER 1
# NEVER as a recovery step: adb shell am force-stop com.tencent.mm
```

**adb server 是不是在自己崩?** (Windows, 被动观察, 这段绝对不能调用 adb, 否则你测到的是自己造成的扰动):

```powershell
while ($true) {
  "{0}  {1}" -f (Get-Date -f HH:mm:ss),
    ((Get-Process adb -ErrorAction SilentlyContinue | Select-Object -Expand Id) -join ',')
  Start-Sleep 5
}
```

一个 PID 每 10 到 30 秒就变一次, 而 `adb devices` 仍然报 `device`, 那就是 §4 那个故障. 别再调试插件了, 把传输换到 WiFi.
