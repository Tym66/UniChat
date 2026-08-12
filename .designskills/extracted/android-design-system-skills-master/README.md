# Android Design System Skills

三套完整的 Android Jetpack Compose 设计系统 Skill，用于 Claude Code / Codex。每个 Skill 包含主题色板、Typography、间距系统、交互引擎、触觉反馈规范和 40+ 常用 UI 控件。

## 安装

将 `.md` 文件放入项目的 `.claude/skills/<skill-name>/SKILL.md` 即可。

```
your-project/
└── .claude/
    └── skills/
        ├── hyper-neumorphic/
        │   └── SKILL.md
        ├── neumorphism/
        │   └── SKILL.md
        └── glassmorphism/
            └── SKILL.md
```

## 三个设计系统

### 1. hyper-neumorphic — HyperOS + 新拟态 + 玻璃态 三合一引擎
- 新拟态双色浮雕阴影（`BlurMaskFilter` 凸/凹）
- 玻璃态 Mesh 光斑 + 半透明叠加
- 一键皮肤切换（`LocalAppSkin`）
- 深浅色自适应 + 触觉反馈 + 无涟漪缩放动画
- 40+ 通用控件（Button/IconButton/SegmentedControl/Switch/Slider/Input/SearchBar/Dialog/Checkbox/Radio/Progress/Chip/Tabs/List/Avatar/Badge/Skeleton/FAB/BottomSheet/TopAppBar/NavigationBar/Stepper/Rating/Tooltip/Snackbar/DateTimePicker…）

### 2. neumorphism — 纯新拟态（Soft UI）
- 经典柔和浮雕美学
- 阴影色从背景自动推导
- 极简色板 + 纯组件库

### 3. glassmorphism — 纯玻璃态（毛玻璃）
- 多层径向渐变 Mesh 背景
- 半透叠加 + 渐变描边 + 顶部高光
- 弹窗独立 Mesh Overlay 方案

## 演示项目

完整展示 App 源码位于 [HyperNeumorphicShowcase](https://github.com/Yang-Ya-Chao/HyperNeumorphicShowcase)，可直接 `./gradlew installDebug` 在手机上预览所有组件效果。

## 触觉反馈覆盖

所有可交互组件均有触觉反馈：

| 组件 | 触觉类型 | 触发 |
|------|---------|------|
| Button / FAB / Checkbox / Radio / Chip / Tabs / ListItem | `TextHandleMove` | 点击 |
| Switch | `LongPress` | 切换 |
| Slider | `TextHandleMove` | 拖拽开始 + 点击 |

## 常用控件覆盖

三套 Skill 都应优先覆盖以下控件。`hyper-neumorphic` 是最完整的双引擎参考实现；`neumorphism` 和 `glassmorphism` 使用同名 API，只替换底层视觉 Modifier。

| 分类 | 控件 |
|------|------|
| 操作 | Button、IconButton、FAB、SplitButton、ToggleButton、SegmentedControl |
| 输入 | InputField、PasswordField、SearchBar、TextArea、Stepper、Slider、RangeSlider、RatingBar |
| 选择 | Checkbox、RadioButton、Switch、Chip、FilterChip、ChoiceChip、DropdownMenu、DatePicker、TimePicker |
| 导航 | TopAppBar、SearchTopBar、Tabs、NavigationBar、NavigationRail、Breadcrumb、PagerIndicator |
| 数据展示 | Card、ListItem、GridTile、StatisticCard、Timeline、Avatar、Badge、Tag、Tooltip |
| 反馈 | Dialog、BottomSheet、Snackbar、ToastHost、Progress、LoadingSpinner、Skeleton、EmptyState、ErrorState |
| 布局容器 | Section、SettingsGroup、ExpandablePanel、Carousel、PullRefreshContainer |

## 使用方式

在 Claude Code 对话中输入：

```
/hyper-neumorphic    用 HyperOS 风格做一个设置页面
/neumorphism         用纯新拟态做登录页
/glassmorphism       用玻璃态做 Dashboard
```
