# UniChat

> 聚合微信/抖音聊天 + 模块搜索 的 LSPosed 模块工具

一个集 **跨平台聊天聚合** 与 **模块仓库搜索** 于一体的 Android 工具,基于 LSPosed 框架,需要 root 权限。

## ✨ 功能

### 💬 聊天聚合
- 将**微信**与**抖音**中与同一个人的聊天聚合到统一会话
- 按联系人(姓名/手机号/平台 ID)自动归并,一个人一个会话
- 跨平台**已读同步**:在一个平台读了,另一个平台也标记已读
- 消息按时间线统一展示,来源平台有角标
- 联系人资料(备注、头像)聚合整理

### 🔍 模块搜索
- 聚合 **Magisk 模块** 与 **LSPosed/Xposed 模块** 仓库(GitHub)
- 关键词搜索 + 分类浏览
- 按 Star 数排序,一键跳转仓库

## 🧩 技术原理

### Hook 层(核心功能1)
不依赖微信/抖音内部类名,直接 Hook `android.database.sqlite.SQLiteDatabase` 的写入层:

```
微信/抖音进程
   │  (LSPosed 注入)
   ▼
SQLiteDatabase.insertWithOnConflict / updateWithOnConflict
   │  识别消息表/联系人表,字段语义映射
   ▼
ContentResolver.call → UniChatProvider(跨进程)
   │  联系人归并 / 消息去重 / 未读计数
   ▼
Room 数据库 → Compose UI 实时刷新
```

这样的好处:微信/抖音升级也不容易失效,因为表结构字段名长期稳定。

### UI 层
- Jetpack Compose + Material 3
- HyperOS 风格:纯白背景、大标题居中、圆角卡片、无分割线、底部悬浮操作栏

## 🛠️ 构建

环境要求:
- JDK 17+
- Android SDK 34

```bash
./gradlew assembleDebug
# 产物:app/build/outputs/apk/debug/app-debug.apk
```

## 📦 使用

1. 安装 APK(本应用同时是 LSPosed 模块)
2. LSPosed 中启用模块,勾选作用域:**微信** 和 **抖音**
3. 重启微信/抖音
4. 打开 UniChat,开始使用

## 🗺️ Roadmap

- [x] 项目骨架 + 双核心功能框架
- [ ] 微信/抖音消息表字段适配(需真机调试)
- [ ] 图片/语音/视频消息预览
- [ ] 已读回执细化(对方已读)
- [ ] 更多模块仓库接入(官方仓库、酷安等)
- [ ] 模块一键下载/更新
- [ ] 联系人资料页(跨平台档案)

## ⚠️ 声明

- 本工具仅用于个人学习与研究,请勿用于任何违法违规用途
- 微信、抖音均为其各自公司的商标,本工具与其无任何关联
- 使用 Hook 类工具存在账号风控风险,请自行评估

## 📄 License

MIT
