# UniChat

> 聚合微信/抖音聊天 + 模块搜索 的 LSPosed 模块工具(Android)

基于 LSPosed 框架,需要 **root 权限**。当前版本为开发迭代中的 v0.2.0,以真机验证为准。

## ✅ 当前真实功能状态

| 模块 | 状态 | 说明 |
|---|---|---|
| **抖音聊天同步** | ✅ **可用** | 私信(Bot 会话)通过 root 读库;真实好友私信通过 Hook + 文件通道同步 |
| **微信聊天同步** | ⚠️ **受限** | 微信 8.0.x 消息走 native(WCDB C++)写库,Java Hook 拦不到;库又 SQLCipher 加密读不了。实时抓取需 native Hook,未实现 |
| **模块搜索** | ✅ 可用 | GitHub 仓库搜索(Magisk / LSPosed) |
| **新拟态 UI** | ✅ | HyperOS 风格(浮雕卡片/凹陷搜索/无涟漪触觉点击/前台服务保活) |

## ✨ 功能

### 💬 抖音聊天同步(核心功能)
- **私信同步**:Bot 会话(root 读未加密 im_database)+ 真实好友私信(Hook 捕获 msg 表 → 文件通道)
- 消息方向(发出/收到)、内容(JSON 提取)、时间、联系人归并
- 联系人头像同步(icon_image → 网络头像)
- **周期自动同步**:前台每 45 秒自动拉新,无需手动
- **前台服务保活**:退到后台不被 HyperOS 冻结,跨进程/文件通道持续工作

### 🔍 模块搜索
- Magisk / LSPosed 模块仓库搜索、分类、Star 排序、跳转仓库

### ℹ️ 关于页
- 开发者、GitHub、设备信息、打赏

## 🧩 技术原理

### 抖音:三层数据链路(真机验证)
```
抖音进程(LSPosed 注入)
  ├─ Hook 框架 SQLiteDatabase/SQLiteStatement(捕获 msg 表明文消息)
  │     └─ ① 文件通道:写入抖音私有目录 inbox(绕过 HyperOS 跨进程拦截)
  │        └─ UniChat 周期用 root 读取 → 入库
  │     └─ ② 跨进程 ContentResolver → UniChatProvider(在 HyperOS 放行时可用)
  └─ root 读未加密 im_database(Bot 会话 + 联系人资料)
        └─ 增量解析 → 入库
```

### 微信:现状与瓶颈
- 微信 8.0.x 消息写入 **native(WCDB C++)层**,Java 的 `SQLiteDatabase`/`SQLiteStatement`(含
  `com.tencent.wcdb.compat` 兼容层)都拦不到
- `EnMicroMsg.db` 为 **SQLCipher 加密**,root 直接读不可行
- 可行方向(未实施):native Hook(参考 WeKit 的 zygisk ArtHook 方案),数天级、版本敏感

### UI 层
- Jetpack Compose + Material 3 + **自研新拟态设计系统**
  (`ui/designsystem`:浮雕阴影引擎 / 无涟漪缩放点击 / 触觉反馈 / HyperOS 色板)
- 深色/浅色自适应

## 🛠️ 构建

环境:JDK 17+ / Android SDK 34

```bash
./gradlew assembleDebug
# 产物:app/build/outputs/apk/debug/app-debug.apk
```

> 本机内存紧张时可用:`./gradlew assembleDebug --no-daemon -Dorg.gradle.jvmargs="-Xmx640m -XX:MaxMetaspaceSize=160m"`

## 📦 使用

1. 安装 APK(同时是 LSPosed 模块)
2. LSPosed 启用模块,作用域勾选 **微信 + 抖音**
3. 重启微信/抖音
4. 打开 UniChat(前台服务自动启动,保持进程存活)

**建议(提升稳定性)**:
- 给 UniChat 开启 **自启动 + 电池无限制 + 后台锁定**(HyperOS 后台限制的根治方式)
- 允许通知(前台保活需要)

## 🗺️ 待办(Roadmap)

- [x] 抖音私信同步(读库 + Hook + 文件通道)
- [x] 新拟态 UI / 周期同步 / 前台服务保活
- [ ] 微信 native Hook(实时消息,攻坚项)
- [ ] 抖音真实联系人昵称(当前显示对端 ID,需接抖音用户接口)
- [ ] 图片/语音/视频消息预览
- [ ] 已读回执细化
- [ ] 模块一键下载/更新

## ⚠️ 声明

- 仅供个人学习与研究,请勿用于违法违规用途
- 微信、抖音为其各自公司商标,本工具与其无任何关联
- Hook 类工具有账号风控风险,请自行评估

## 📄 License

MIT
