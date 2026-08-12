# 手机配置

English: [phone-setup.en.md](phone-setup.en.md)

难的部分在这里. 这个项目里其余的东西, Hermes 插件, 传输层, watchdog, 都只是普通软件. 这一页要做的, 是把一台原厂安卓手机改造成一个程序能对话的微信宿主: 解锁 bootloader, root, 装一个 Xposed 框架, 把 WeKit 模块加载进微信, 再打开 WeKit 的 HTTP + MCP server.

预算一个晚上. 有好几步会清空设备或者需要重启, 还有一步 (第一次 DexKit 扫描) 纯粹就是干等几分钟.

---

## 买硬件或者装任何东西之前先读这一节

**必须用一个专用的微信小号. 这不是可选项.**

- 自动化一个微信账号违反微信服务条款.
- 微信封号会连带冻结微信支付以及账号里的余额. 不要把你的主身份, 你的支付方式, 或者你在乎的钱, 压在这套东西后面.
- **微信一个账号同时只允许一个活动的手机会话.** 如果你把 agent 的手机登进*你自己*的账号, 你自己的手机就会被踢下线. 用独立账号是结构上的硬性要求, 不只是一条安全建议.
- 微信确实会主动检测 Xposed. 有公开记录的, 专门针对 Xposed 模块的处罚基本都是历史事件 (最近一次证据确凿的大规模封号潮打的是*另一种*技术, 本地数据库解密, 时间是 2026 年 3 月), 但"最近没有一波"不等于保证. 就当这个账号随时可能消失.
- 这个项目是给研究和个人使用的. 绝对不要拿它做批量群发, 未经许可的骚扰, 或者商业消息.

**想清楚你在这台手机上放弃了什么:** 解锁 bootloader 会清空设备, 会让硬件 attestation 失败, 并且会让一部分银行 / DRM 应用直接拒绝运行. 用一台你愿意专门拿来干这件事的手机.

> ⚠️ **在往里投进一个晚上之前, 先知道这条通道最核心的限制.** 入站是**边沿触发**的: WeKit 的 `wait-for-new-message` 只在单次调用期间注册一个数据库监听器, 调用一返回就把它摘掉. 没有队列, 没有缓冲, 没有游标. 任何在 poll 循环*两次调用之间*到达的消息都会**永久丢失, 而且找不回来**, 这是上游的行为, 不是本插件能修的. 普通的一来一回对话是可靠的; 但连珠炮式的密集消息, 以及任何空档 (gateway 重启, 出错退避) 都可能丢消息. 在你打算依赖它做任何事情之前, 先读架构文档.

---

## 手机侧最终要搭出来的东西

```
┌─ Android phone (rooted) ───────────────────────────────┐
│                                                        │
│  Magisk (root) → Zygisk                                │
│      └── Vector / LSPosed (Xposed framework)           │
│              └── WeKit module, scoped to WeChat        │
│                                                        │
│                   ├─ hooks the WCDB insert layer:      │
│                   │  sees messages as the database     │
│                   │  receives them — no notifications, │
│                   │  no UI automation                  │
│                   │                                    │
│                   └─ HTTP REST + MCP server on :3001,  │
│                      static bearer-token auth          │
└────────────────────────────────────────────────────────┘
                          ▲
              agent host reaches :3001 over the LAN
         (see the transport docs — WiFi strongly preferred)
```

---

## 0. 前置条件

| 项目 | 要求 | 备注 |
|---|---|---|
| 手机 | Android 9+, **bootloader 必须能解锁** | 见 §1, 大多数尝试都死在这一步 |
| 参考设备 | Pixel 9 Pro (`caiman`), Android 16 | 下面这套流程就是在它上面验证过的 |
| 电脑 | Windows / macOS / Linux, 带 `adb` + `fastboot` | Android SDK platform-tools |
| Root | Magisk (实测 30.x), 并且**开启 Zygisk** | §3 |
| Xposed 框架 | 较新的安卓上用 Vector; LSPosed 只适合更老的版本 | §4 |
| 微信 | **官方** APK, 版本 **8.0.72** | §5 |
| 微信账号 | 一个专用小号 | 不要用你的主号 |
| 模块 | [github.com/Ujhhgtg/WeKit](https://github.com/Ujhhgtg/WeKit) | §6 |

WeKit 是一个第三方项目, 实际上是单人维护, 而且从来没打过 tag 的 release. 本插件只调用它的 HTTP API, 不内置也不再分发任何 WeKit 代码. 从 §5 开始的每一步, 都依赖那个上游项目继续存在, 并且继续支持你手上这个微信版本.

---

## 1. 设备前提: 这台手机到底能不能解锁

**这件事要排在所有事情之前确认.** 在运营商锁死的机器上, "OEM unlocking" 开关是灰的, 而且**在软件层面你什么都做不了**: 这个限制活在 bootloader 层, 重刷官方固件也清不掉.

在设备上:

1. 设置 → 关于手机 → 连点*版本号*七次, 打开开发者选项.
2. 设置 → 系统 → 开发者选项 → 看 **OEM unlocking** 这一项.

| 你看到的 | 含义 |
|---|---|
| 开关可点 (开着或关着都行) | 好, 继续 |
| 灰色, 副标题是*"Connect to the internet or contact your carrier"* (中文系统上显示为"请连接到互联网或与您的运营商联系") | **运营商锁.** 就是这句副标题, 它是专属判据. 这台设备到此为止 |
| 灰色, 但副标题是别的 | 可能是没网, 可能是 device-owner/MDM 配置, 也可能是被锁死的系统构建. 值得查一下, 但答案通常还是运营商锁 |

机器可读的权威判据要从 bootloader 里拿:

```bash
adb reboot bootloader
fastboot flashing get_unlock_ability     # 1 = this device can be unlocked
```

(在暴露了这个接口的系统构建上, `adb shell dumpsys oem_lock` 会报出底层的 `isOemUnlockAllowedByCarrier` 标志位; 那里是 `false` 就是运营商锁.)

有两件事被广泛相信, 但其实是错的:

- **SIM 解锁 ≠ OEM 解锁.** 一台已经解了 SIM 锁的设备, 也就是插任何运营商的卡都能打电话上网的设备, 照样可能永远 OEM 锁死. 2023 年及更早那种"先解 SIM 锁, OEM unlocking 就会点亮"的说法, 对美国几大运营商已经不成立了. 这一条是在一台运营商渠道机上用惨痛方式复验过的: 电话和数据都正常, OEM unlocking 依然是灰的.
- **重刷官方厂包并不能去掉运营商锁.**

**建议: 直接买 Google Store 的无锁版 Pixel.** 同一个型号的运营商渠道机可能能解也可能不能解, 而你只有等手机拿到手才知道.

---

## 2. 解锁 bootloader

> ⚠️ **这一步会清空设备.** 要在往手机里登任何账号之前就做掉.

1. 开发者选项 → 打开 **OEM unlocking** *以及* **USB debugging**.
2. 重启进 bootloader 并解锁:

```bash
adb reboot bootloader
fastboot flashing unlock          # confirm on the device with the volume/power keys
```

3. 设备会清空并重启. 重新走一遍开机引导, 再次打开开发者选项和 USB debugging.

之后任何时候想确认*真实*状态, 用这条:

```bash
fastboot getvar unlocked          # ground truth
```

先记一件后面会用到的事: 一旦你装了 Play Integrity 伪装之类的 Magisk 模块, `getprop ro.boot.flash.locked`, vbmeta 状态, 以及 `verifiedbootstate` 可能全都报成*locked / green*, 因为有模块把它们改写了. 这些属性从此不再能证明任何事. `fastboot getvar unlocked` 是唯一可信的检查.

---

## 3. Root: 补丁要打对分区

**在 Pixel 9 系列设备上 (以及一般来说, 所有出厂即 Android 13+ 的设备上), 你要打补丁的是 `init_boot.img`, 不是 `boot.img`.** 在这些设备上给 `boot.img` 打补丁, 结果要么是循环重启, 要么是能开机但没有 root, 而你会在发现之前先浪费掉一个小时.

快速判断: 厂包里如果有 `init_boot.img`, 那它就是 Magisk 要打补丁的那个分区.

1. **在手机上装 Magisk APK.**

2. **下载官方厂包**, 必须对应你这台设备和这个确切的构建号 (设置 → 关于手机 → 版本号). 拿下载页旁边公布的校验值核对它的 SHA-256. 别跳过这一步.

3. **解开里层嵌套的 image zip**, 取出 `init_boot.img`. 在 Pixel 9 系列上这个文件大约 8 MB, 如果你解出来的是几十 MB, 那你抓错了, 抓到的是 `boot.img`:

```bash
unzip <device>-<build>-factory-*.zip
unzip image-<device>-<build>.zip init_boot.img
```

4. **备份原厂的 `init_boot.img`.** 取消 root, 干净地接收 OTA, 或者救回设备, 都要用到它. 你跑过的每一个构建都各留一份.

5. **用 Magisk 应用给它打补丁:**

```bash
adb push init_boot.img /sdcard/Download/
```

打开 Magisk → **Install** → *Select and Patch a File* → 选 `/sdcard/Download/init_boot.img`. Magisk 会在同目录写出 `magisk_patched-XXXXX_yyyyy.img`.

6. **把它拉回来然后刷进去:**

```bash
adb pull /sdcard/Download/magisk_patched-XXXXX_yyyyy.img
adb reboot bootloader
fastboot flash init_boot magisk_patched-XXXXX_yyyyy.img
fastboot reboot
```

7. 打开 Magisk 应用, 它应该报出一个已安装的 Magisk 版本号, 而不是 "N/A".

8. **打开 Zygisk** (Magisk → Settings → Zygisk) 然后重启. §4 里的 Xposed 框架需要它.

**有两个坑现在就该知道:**

- **OTA 更新会覆盖 `init_boot`, 把 root 干掉.** 任何系统更新之后, 你都必须用*新*构建的 `init_boot.img` 重新打补丁, 重新刷. 在你做完之前, 这条通道一直是断的.
- **`adb shell su` 可能被秒拒**, 报 `su: request rejected (2000)`. 在较新的安卓上, 后台启动 activity 的限制会让 Magisk 根本弹不出授权框; 请求超时, 被记成 Shell UID 上的一条 *deny*, 从此这条已存策略永远优先, 哪怕你后来把默认响应改成 "Grant" 也没用. 正确的修法是 **Magisk 应用 → Superuser 标签页 → 打开 `[SharedUID] Shell` 那一项**, 而不是去改自动响应的设置. 这个项目严格来说不需要 `adb su` (Magisk 和 Vector 应用在各自的界面里就能做完所有事), 但排障的时候你会想要它.

---

## 4. 装一个 Xposed 框架

WeKit 是一个 Xposed 模块, 所以它需要一个框架来加载它, 而这个框架需要 Zygisk.

| 框架 | 状态 |
|---|---|
| **Vector** (`JingMatrix/Vector`) | 仍在维护的 LSPosed 后继者, 参考部署跑的就是它 (Android 16 上的 v2.2) |
| **LSPosed** (`LSPosed/LSPosed`) | 官方 release 停在 v1.9.2 (2023), 覆盖不到 Android 16. 只在更老的安卓上还能用 |
| **NPatch** (免 root 的 APK 修补) | **对 WeKit 不管用.** 修补版微信能装, 也能登录, 但模块从来没被加载: `libdexkit.so` 没有加载, 于是 WeKit 的 hook 一个都挂不上, :3001 也永远起不来. 一手实测. 如果你有 root, 别往这条路上走 |

把框架当成 Magisk 模块来装. 最省事的路子, 同时也能绕开上面那个 `su` 被拒的陷阱, 是走 **Magisk 应用 → Modules → Install from storage**, 指向框架的 release zip. 在 root shell 里的等价做法是:

```
magisk --install-module /sdcard/Download/Vector-<version>.zip
```

**重启.**

然后装框架的**管理器应用**. Vector 把自己的 APK 放在模块目录里 (`/data/adb/modules/zygisk_vector/manager.apk`). SELinux 禁止 `su` 域读 `/data/adb` 下的文件, 所以要把 SELinux 短暂切成 permissive 把它拷出来, 然后立刻切回去. 下面这些要**在设备上**的 root shell 里跑:

```bash
adb shell
su
setenforce 0
cp /data/adb/modules/zygisk_vector/manager.apk /data/local/tmp/
setenforce 1
pm install /data/local/tmp/manager.apk
exit
```

打开管理器 (Vector 的包名是 `org.matrix.vector.manager`). 它应该显示框架处于 **active**; 你还应该能看到 `vectord` 和 `zygiskd64` 在跑. 如果它说框架没安装, 那多半是 Zygisk 没开, 去 Magisk → Settings 看一眼然后重启.

在设备上折腾的这段时间, 别让屏幕在流程中间睡过去:

```bash
adb shell svc power stayon true
```

---

## 5. 装一个受支持的微信版本, 然后掐掉它的更新

WeKit hook 的是微信内部的具体实现, 对版本敏感.

- **目标是微信 8.0.72.** 这是 WeKit 版本表里最高的一个. 项目文档里提到过更新的版本, 但那些下载链接解析出来的版本号其实还是同一个, 所以 8.0.72 以上的一律当作未经验证.
- 装**官方**微信 APK, 不要装被修补或改造过的. (有了 root + Xposed, 你是注入进原版微信, 不需要重打包的 APK.)
- **现在就用那个专用小号登录**, 趁模块还没生效, 让第一次登录是干净的.
- **在你装微信的那个渠道里关掉它的自动更新.** 一旦更新到不受支持的版本, hook 会静默失效.
- 模块一跑起来, 就用 WeKit 自己的开关**关掉微信的热更新 (tinker) 机制** (§7). 这一条很重要: 被热补丁过的微信会让模块*静默加载失败*, 而目前有记录的唯一清理办法, 是用手机上的文件管理器手动删掉补丁目录, 这件事你没法用 adb 远程驱动.

---

## 6. 装 WeKit 模块并把作用域限定到微信

从 **[github.com/Ujhhgtg/WeKit](https://github.com/Ujhhgtg/WeKit)** 获取 WeKit.

注意它的分发方式: **这个仓库不发布 release.** 构建产物来自 CI artifact (需要 GitHub 登录, 而且会过期) 或者项目的社群渠道. 装之前先核实你装的到底是什么, 你马上要把自己的微信会话交到它手里.

不要用 `Johnny520/wcx` 这个 fork, 它就是 WeKit 改了个名, 最低安卓版本要求更高, 而且没人在维护.

然后:

1. 装 WeKit APK (`adb install -g wekit-standard.apk` 会一次性把权限全授上).
2. 打开 **Vector/LSPosed 管理器 → Modules → WeKit**.
3. 把模块**打开**, 并且**作用域只勾微信**. 不要勾任何其他应用. 应用设置.
4. **强制关闭微信再重新打开**, 让框架注入到一个全新的进程里. 在你做这一步之前, 模块的状态是已启用但没有加载.

确认注入真的发生了, 不要靠猜. 微信刚启动之后立刻:

```bash
adb logcat -d | grep -iE "vector|xposed|wekit|dexkit"
```

你要找的是类似下面这样的行 (具体措辞随版本变化):

```
Vector: Loading Vector/Xposed for com.tencent.mm
Loaded module dev.ujhhgtg.wekit successfully
WeKit: hooking Application.attachBaseContext
Load libdexkit.so ... ok
```

如果这些一行都没有, 去 §排障 → "模块已启用, 但什么都没被 hook".

> **这一次性的强制关闭做完之后, 就别再强制停止微信了.** 见排障里那条红色警告, 那是把一套能跑的环境搞坏的最简单方式.

---

## 7. 打开 WeKit 的 API + MCP server

WeKit 会在**微信内部**加一个属于它自己的设置入口 (具体标签取决于模块版本和语言, 找 WeKit 的设置 / 功能页面, 然后找 "API + MCP server" 那一项).

1. **设置 bearer token.** WeKit 内置的默认值是占位符字面量 `your_token`. **一定要改掉.** 任何能连到这个端口并且知道 token 的东西, 都能读你的消息, 并且以你的身份发消息.
2. **端口设成 `3001`** (这是默认值, 也是本项目的文档和脚本假定的值).
3. 把这个功能**打开**.

还在 WeKit 设置里的时候, 顺手把这两个选项也打开:

- **关闭微信的热更新 / tinker** (§5), 以及
- **对微信隐藏 Xposed** (反检测).

这三项, 一个真 token, 关热更新, 隐藏 Xposed, 是部署时永远不该跳过的开关.

**关于这个 server 的几条安全事实:**

| | |
|---|---|
| 鉴权 | 一个静态 bearer token. 这就是它全部的访问控制模型 |
| 传输 | 明文 HTTP. token 和每一条消息内容都以明文穿过你的局域网 |
| 监听地址 | server 绑定的是**所有网卡 (0.0.0.0)**, 而且这一点不可配置 |

把 :3001 当成一个只在局域网内提供的服务. 绝对不要把它端口转发到公网; 如果你用路由器 DNAT 那套传输方式, 要确认你暴露它的那个接口面向的是你自己掌控的私有网络, 绝不能是朝向公网的 WAN. token 用长的随机串.

这个功能开关**能扛过重启**, 而且只要微信带着模块启动, server 就会自动跟着起来. 对话框里**没有单独的"开机自启"选项** (它只暴露 token 和端口), 所以让这条通道保持在线, 等价于让*微信*一直跑着, 见 §10.

---

## 8. 第一次启动很慢, 别慌

启用模块之后 (以及每次微信更新或重装之后), WeKit 的第一次启动都会对微信这个 app 跑一次**完整的 DexKit 扫描**.

**这要好几分钟**, Pixel 9 Pro 上是两到三分钟, 更慢的硬件更久. 在这段时间里:

- :3001 不响应.
- `curl` 会拿到 connection-refused 或者干脆卡住.
- 界面上没有任何东西告诉你正在扫描.

它看上去跟装坏了一模一样. 先等它过去, 别急着动手改东西. 想盯着看就 `adb logcat | grep -i dexkit`.

---

## 9. 找到手机 IP, 并从 agent 主机上验证

拿到手机的 WiFi 地址:

```bash
adb shell ip -4 addr show wlan0        # or Settings → About phone → Status
```

现在有两件事值得做, 让这个地址稳定下来, 因为传输层依赖它:

- 在安卓设置里给手机起一个好认的设备名, 路由器 DNAT 脚本是按 **DHCP 租约名** (`WEKIT_PHONE_HOSTNAME`) 找它的, 不是按 IP.
- 在路由器上给它加一条 DHCP 静态保留.

在 OpenWrt 系的路由器上可以直接读租约表, 脚本干的就是这件事:

```sh
cat /var/dhcp.leases
```

现在, **从跑 Hermes 的那台机器上**验证 API, 不是从手机上, 也不是从你的笔记本上:

```bash
curl -sS http://192.168.1.50:3001/api/self/info \
     -H "Authorization: Bearer YOUR_TOKEN"
```

健康的响应会标明当前登录的账号:

```json
{"wxId":"wxid_xxxxxxxx","customWxId":"YourWeChatID"}
```

这正是插件启动时发出的那个请求 (它会重试几次才放弃), 所以这条命令能通, 插件的 `connect()` 也能通.

再来一个有用的探测, 通讯录列表:

```bash
curl -sS "http://192.168.1.50:3001/api/contacts?type=friends" \
     -H "Authorization: Bearer YOUR_TOKEN"
```

`type` 的合法取值是 `all`, `friends`, `groups`, `official_accounts`, 都是复数; 写 `friend` 会被拒绝.

**如果 agent 主机和手机不在同一网段**, 哪怕手机侧一切完美, 这条 curl 也会超时. 那是路由问题, 不是配置问题, 去看传输方式的文档和 `wekit-dnat.sh`. 如果两者同网段, 直接把 `WEKIT_BASE_URL` 指向手机, 那个脚本完全不用装.

> **白名单提示, 这条真的坑过人.** 配 `WEKIT_ALLOWED_USERS` 的时候, 要等对方真的给 agent 发过消息之后, 从 adapter 自己打的那行日志 (`wechat-wekit: inbound from <convId> type=…`) 里抄 id, **不要**从好友列表里抄. 一个联系人在你通讯录里的那条记录, 未必就是他给你发消息用的那个账号; 而白名单对不上的时候, 消息是被*静默*丢掉的: 丢弃只在 debug 级别记日志, 所以在默认日志级别下你什么都看不到. 另外注意 **插件的日志行进的是 Hermes 的 `agent.log`, 不是 `gateway.log`**, 翻错文件会让一个正常工作的 poll 循环看起来像死了.

---

## 10. 让它一直活着

这条通道活着的时间, 精确等于微信带着模块在跑的时间. 有帮助的做法:

- **把微信从电池优化 / Doze 里排除掉**, 免得系统在后台把它杀了.
- **手机一直插着电.**
- **在 agent 侧放一个 watchdog.** 本仓库的 `ops/wechat_watchdog.py` 就是参考实现: 它每隔几分钟走 **HTTP** 做一次健康检查, 走的是跟 agent 完全相同的那条路径, 只有在连续多次 HTTP 失败之后才会去碰 adb, 到那时它才检查微信是否在跑, 不在就用 `monkey` 把它拉起来. 健康状态下它一次 adb 调用都**不会**发. 它**永远不会**强制停止微信. 用 `WEKIT_BASE_URL` / `WEKIT_TOKEN` 配置它, adb 兜底那条路径另外需要 `WEKIT_ADB_SERIAL`, `WEKIT_ADB_PATH` 和 `WEKIT_LOG_PATH`.
- **手机重启之后**, 微信不一定会自己起来. 靠 watchdog 把它拉起来就够了, 而且这整条路径已经端到端验证过: watchdog 发现 API 挂了 → `monkey` 拉起微信 → 模块加载 → DexKit 扫描几分钟 (§8) → server 自己回来了, 因为那个功能开关扛过了重启. 全程不需要手动去点任何开关.

**不要**写一个短间隔用 `adb` 轮询的 watchdog, 也不要把传输本身架在 `adb forward` 上. 在参考主机上, adb server 完全自发地每 10 到 30 秒崩溃并重启一次, USB 设备本身一次都没掉过, 结果就是那条 forward 大约每 21 秒蒸发一次. 实测数据见传输方式的文档.

---

## 排障

### `curl` 报 connection refused / :3001 没有响应

按这个顺序排查:

1. **你是不是刚启用模块, 或者刚更新过微信?** 等 DexKit 扫描跑完 (§8).
2. **微信在跑吗?** `adb shell pidof com.tencent.mm`. 没有 pid 就没有 server.
3. **有东西在监听吗?** `adb shell netstat -tln | grep 3001` (在 root shell 里加 `-p`, 可以确认这个 socket 属于 `com.tencent.mm`).
4. **那个功能开关还开着吗?** 回微信里重新打开 WeKit 的设置看一眼.
5. **问题是出在网络而不是手机上吗?** 从一台跟*手机同网段*的机器上测. 如果那边通而 agent 主机不通, 那就是路由问题, 去看传输方式的文档, 别再回头折腾手机.
6. **微信是不是自动更新了?** 查版本号. 不受支持的构建会静默让 hook 失效 (§5).

### HTTP 401

你请求里的 token 跟 WeKit server 设置里的 token 不一致. 检查你写进 `.env` 的那个值末尾有没有多出来的换行或空格, 确认请求头是 `Authorization: Bearer YOUR_TOKEN`, 然后回手机上 WeKit 的对话框里重新读一遍 token. 插件是在启动时读 `WEKIT_TOKEN` 的, 所以改完之后要重启 gateway.

### 模块已启用, 但什么都没被 hook

微信刚启动之后立刻跑 `adb logcat -d | grep -iE "vector|xposed|wekit|dexkit"`. 输出为空, 说明框架根本没有注入进去.

| 原因 | 修法 |
|---|---|
| Zygisk 没开 | Magisk → Settings → 打开 Zygisk → 重启 |
| 框架不是 active | 打开 Vector/LSPosed 管理器, 看它的状态页 |
| 模块作用域里没有微信 | 管理器 → Modules → WeKit → 勾上微信 → Apply |
| 设完作用域后没有重启微信 | 关掉再打开 (§6 第 4 步) |
| 微信给自己打了热补丁 (tinker) | 会导致**静默**不加载. 在 WeKit 设置里关掉热更新; 要清掉已经打上的补丁, 得用手机上的文件管理器删掉它的目录 |
| 微信版本不受支持 | 退回 8.0.72 |
| 你用的是 NPatch 而不是 root + Xposed | 对 WeKit 不管用, `libdexkit.so` 永远加载不上 |

### 🔴 永远不要强制停止微信

一旦微信带着注入好的模块跑起来, **就不要强制停止它**: 不要走设置 → 应用 → 强行停止, 不要用 `am force-stop com.tencent.mm`, 更重要的是, 不要在任何脚本或 watchdog 里做这件事.

在参考部署里, 一次强制停止就把 Xposed 注入弄成了一个坏掉的状态, 只有**整机重启**才救得回来. 端口一直是关的, 而日志里没有任何东西解释为什么.

§6 里那一次性的强制关闭, 是为了在一个还没被注入的进程上让模块第一次加载进去. 在那之后, 如果你需要重启微信, 请**重启手机**, 让它干净地起回来. 你写的任何自动化都应该只能*拉起*微信 (例如 `monkey -p com.tencent.mm -c android.intent.category.LAUNCHER 1`), 绝不能具备停止它的能力.

### 系统更新之后 root 没了

意料之中, OTA 覆盖了 `init_boot`. 用*新*构建厂包里的 `init_boot.img` 把 §3 重跑一遍.

### `adb shell su` 秒返回 `su: request rejected (2000)`

这是 Shell UID 上存着一条 deny 策略, 既不是 SELinux 问题, 也不是设置问题. Magisk 应用 → **Superuser** 标签页 → 打开 **`[SharedUID] Shell`** 那一项. 改 "Automatic response" 没用, 因为已存的策略优先级更高.

### 任何牵扯到 `/data/adb` 的操作

SELinux 禁止 `su` 域读写 `/data/adb` 下的内容, 所以在那里 `cp` / `chmod` / `cat` 都会 Permission denied. 装模块请走 Magisk 应用或者 `magisk --install-module`, 不要手动把文件拷进去; 唯一确实需要读出来的那个文件 (Vector 管理器 APK), 用的就是 §4 里那套 `setenforce` 来回切的办法.

---

## 做完了? 逐条对一遍

| 检查项 | 预期 |
|---|---|
| `fastboot getvar unlocked` | `yes` |
| Magisk 应用 | 能报出版本号, Zygisk 已开 |
| 框架管理器 | 框架处于 **active** |
| 微信启动后跑 `adb logcat -d \| grep -i wekit` | 模块已加载, `libdexkit.so` ok |
| 微信里的 WeKit 设置 | API+MCP server 已开, 端口 3001, **token 已改**, 热更新已关, Xposed 已隐藏 |
| **从 agent 主机**跑 `curl …/api/self/info` | 200, 并带上你的 `wxId` |

最后那一行, 就是本插件所依赖的全部手机侧契约.

下一步: 选一种传输方式 (强烈建议用 WiFi 而不是 USB, 实测理由见传输方式的文档), 配好 `WEKIT_TOKEN` / `WEKIT_BASE_URL` / `WEKIT_ALLOWED_USERS`, 然后在 Hermes 里启用插件.

另外, 在你打算依赖它做任何事情之前, 请重读本页开头那段提示, 以及架构文档里关于**边沿触发入站**的那部分. 在 poll 循环两次 `wait-for-new-message` 调用之间到达的消息会永久丢失, 也没有游标能把它们找回来. 插件能做的缓解, 只是把回复作为后台任务派发出去, 好让监听器立刻重新挂上, 它没法把这个窗口关掉.
