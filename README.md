<div align="center">

# Rep

**为薄肌训练设计的极简渐进式负荷健身记录 App**

[English](README_EN.md) | [简体中文](README.md)

[![Release](https://img.shields.io/github/v/release/JY626/rep?color=00E676&label=Release&logo=github)](https://github.com/JY626/rep/releases/latest)
[![Live Demo](https://img.shields.io/badge/Live%20Demo-在线交互预览-00E676?logo=safari&logoColor=black)](https://jy626.github.io/rep/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.11-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Health Connect](https://img.shields.io/badge/Health%20Connect-Supported-00E676?logo=googlefit&logoColor=white)](https://developer.android.com/health-and-fitness/guides/health-connect)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

<br/>

一款基于 **Kotlin + Jetpack Compose (Material 3)** 构建的现代化、**100% 离线单机**健身记录应用。<br/>
专注于**零延迟极速记录、双重渐进负荷量化与身体指标追踪**。<br/>
界面遵循 **Apple Human Interface Guidelines (HIG)** 极简黑曜石设计，全界面采用 **Lucide 矢量线性图标**。

<br/>

### 📱 界面预览 (Screenshots)

> 💡 **在线交互体验**：无需安装，即可在网页端 1:1 体验全套交互！👉 **[点击打开 Live Web Preview 演示页](https://jy626.github.io/rep/)**（支持打勾记容量、组间倒计时、5 个页面自由切换）。

| 训练记录 (Workout) | 负荷与表现 (Overload) | 日历打卡 (Calendar) | 动作库 (Library) | 身体数据 (Body) |
| :---: | :---: | :---: | :---: | :---: |
| <a href="https://jy626.github.io/rep/"><img src="docs/screenshots/01_workout.png" width="160"/></a> | <a href="https://jy626.github.io/rep/"><img src="docs/screenshots/02_analytics.png" width="160"/></a> | <a href="https://jy626.github.io/rep/"><img src="docs/screenshots/03_calendar.png" width="160"/></a> | <a href="https://jy626.github.io/rep/"><img src="docs/screenshots/04_library.png" width="160"/></a> | <a href="https://jy626.github.io/rep/"><img src="docs/screenshots/05_body.png" width="160"/></a> |

</div>

---

## ⚡️ 快速上手 (Getting Started)

### 方式一：下载预编译 APK（推荐）
直接在 GitHub Release 页面获取已签名的正式安装包：
👉 **[下载最新版本 APK (v1.0.0)](https://github.com/JY626/rep/releases/latest)**

### 方式二：从源码编译安装
本项目无需配置任何外部 API Key 或自建后端，开箱即编：
1. 克隆代码仓库：
   ```bash
   git clone https://github.com/JY626/rep.git
   cd rep
   ```
2. 使用 Android Studio (Ladybug 2024.2+) 打开；
3. 执行 Gradle 构建并直接安装至设备：
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 💎 核心功能特色 (Features)

### 1. 零延迟极速记录 (Zero-Friction Logging)
- **零键盘流式记录**：开练自动回填上次该动作的重量与次数；打勾某组后，下一组自动继承刚完成的数据，手抖疲劳时无需反复唤起软键盘。
- **48dp+ 防误触大触控区**：针对力竭手抖场景深度优化，单手拇指即可极速完成打卡。
- **严谨容量口径**：实时累加有效容量（Volume kg），严格自动排除热身组（`WARMUP`），避免虚高脏数据。

### 2. Apple Watch 风格悬浮休息胶囊 (Rest Timer Capsule)
- 打勾完成组后，底部自动平滑浮起半透明高斯模糊胶囊倒计时。
- 环形进度条显示，支持一键 `+30s` 延长与 `跳过`，完全不遮挡继续浏览和编辑动作库。

### 3. 渐进式负荷与动作 PR 荣誉墙 (Progressive Overload & PR Wall)
- **E1RM 极限拟合**：采用经典 Epley 公式 $1\text{RM} = \text{Weight} \times (1 + \text{Reps} / 30)$，以 Canvas 动态绘制力量增长轨迹，自动标出破纪录的 **PR 突破徽章**。
- **动作 PR 荣誉墙**：动作库每个动作卡片直观外显最佳成绩标签，点击动作即可呼出专属荣誉弹层——一览历史极限估算 1RM、最大做组重量、最佳单组容量（重量×次数）以及完整的做组时间线。
- **RP 科学肌群容量监控**：按周统计胸、背、腿、肩、臂、核心各部位有效组数，实时对照 **MEV（维持量）**、**MAV（最佳适应量）** 与 **MRV（最大恢复量）** 阈值，提示部位训练是否欠缺或过度。
- **智能双重渐进建议**：基于前次首组数据智能给出本周冲重指导（如 60kg × 8次达标 → 建议冲击 62.5kg × 6次）。

### 4. 每日日历与连续训练追踪 (Calendar & Streak)
- 直观查看每月训练打卡分布（推日 / 拉日 / 腿日 颜色速辨）。
- 连续训练天数（Current Streak）与最长坚持天数统计，强化健身习惯。

### 5. 身体指标量化 (Body Metrics)
- 记录体重、体脂率、胸围、腰围、臂围等全维度形态指标。
- 自动计算 BMI 并与上一次记录对比计算差值，趋势一目了然。

### 6. Health Connect 闭环读写与 AI 教练生态 (Health Connect & Muse Sync)
- **系统底层双向闭环**：训练归档后无感将力量训练记录（含起止时间、有效容量、各组明细）写入 Android 系统 Health Connect 底座；同时支持从 Health Connect **一键读取**智能体脂秤或第三方手表记录的体重与体脂率，自动去重补齐本地数据。
- **赋能外部 AI 教练**：外部 AI 助手（如 Muse）可直接通过系统权限在后台读取训练成果；同时支持在 App 内一键生成结构化 Markdown 卡片，通过系统 Sharesheet 快速转发。

### 7. 数据导出与本地安全备份 (100% Offline Backup & Restore)
- **极简 JSON 导出**：设置页一键将全部训练历史、动作库、身体指标打包导出为带日期的标准 JSON 文件，通过系统 Sharesheet 直接保存至云盘、文件管理器或发送到微信/邮箱。
- **无损恢复与跨机迁移**：重装系统或更换手机时，从本地 JSON 文件即可秒级恢复；弹窗支持【合并导入（按训练时间与会话去重，重复导入不会产生重复组，推荐）】与【覆盖导入（导入前自动落一份当前数据快照）】两种模式。

---

## 📊 产品特性对比 (Comparison)

| 特性维度 | 传统纸笔记录 | 商业臃肿健身 App | **Rep** |
| :--- | :---: | :---: | :---: |
| **网络要求** | 100% 离线 | 强依赖网络，地下室转圈 | **100% 离线单机运行** |
| **隐私安全** | 容易丢失 | 用户数据上传云端服务器，有商业变现风险 | **零服务器，数据 100% 本地** |
| **数据备份** | 拍照翻找 | 账号绑定闭环，导出受限 | **一键 JSON 导出与合并恢复** |
| **记录摩擦力** | 需手写笔算 | 步骤繁琐，广告弹窗与社区干扰 | **自动继承 + 48dp+ 盲打勾** |
| **科学量化** | 无 | 多数仅做简单次数累加 | **E1RM 曲线 + PR 荣誉墙 + RP 阈值** |
| **系统生态** | 无 | 封闭孤岛，无法导出 | **Health Connect 闭环 + 结构化 Markdown** |

---

## ❓ 常见问题解答 (FAQ)

<details>
<summary><b>Q1: Rep 是否需要注册账号或联网使用？</b></summary>
<br/>
<b>答：完全不需要。</b> Rep 是一款纯粹的单机工具，没有注册登录界面，不设任何远程用户中心，100% 离线可用，在任何地下室或无网健身房中均能秒开使用。
</details>

<details>
<summary><b>Q2: 我的训练记录保存在哪里？更换手机或误删应用如何恢复？</b></summary>
<br/>
<b>答：</b> 数据默认保存在本机的 SQLite Room 安全沙盒与系统 Health Connect 中。同时，Rep 提供了强大的<b>「数据备份与恢复」</b>功能：在日历/设置页面点击「导出数据备份」，即可将全量记录保存为 JSON 文件；更换手机或重装后，点击「从文件恢复」并选择该文件，即可无损恢复所有历史与身体数据（推荐使用默认的合并导入，自动按时间去重）。
</details>

<details>
<summary><b>Q3: 热身组会影响我的总容量和 E1RM 计算吗？</b></summary>
<br/>
<b>答：不会。</b> 针对力量训练严谨度，Rep 的核心算法严格排除了标为热身组（WARMUP）的数据，仅正式组计入累计容量和肌群有效组数监控，保证训练数据 100% 具备科学参考价值。
</details>

<details>
<summary><b>Q4: 如何授权 Health Connect 自动同步功能？</b></summary>
<br/>
<b>答：</b> 进入「日历」页面底部，即可看到「Health Connect 自动同步」卡片。如果尚未授权，点击「立即授权」并勾选写入运动、体重与体脂权限即可。若你的设备未安装 Health Connect 或版本过低，应用会自动优雅降级，完全不影响核心记录功能。
</details>

<details>
<summary><b>Q5: 动作库支持自定义动作吗？</b></summary>
<br/>
<b>答：支持。</b> 进入「动作库」Tab，点击右上角加号即可快速创建个人自定义动作，支持选择目标肌群与器械类型，并在创建后自动纳入渐进负荷与 RP 容量统计。
</details>

---

## 🛠 工程目录结构 (Architecture)

<details>
<summary><b>点击展开查看源码组织架构</b></summary>

```
app/src/main/java/com/baoji/fitness/
├── core/
│   ├── theme/                 # Apple OLED 黑色系、字阶排版、等宽数字 (Tabular nums)
│   ├── designsystem/icons/    # 纯净 Lucide Compose 矢量线性图标 (Dumbbell, Activity, Calendar, Layers, User 等)
│   └── database/              # Room 数据库实体、DAO 与预置经典动作种子数据 (BaojiDatabase)
├── domain/
│   ├── model/                 # 核心领域模型 (WorkoutSession, WorkoutSet, WorkoutDayDetail)
│   └── algorithm/             # 渐进负荷、RP 周期容量与 Markdown 分享格式化器 (OverloadCalculator, PeriodReviewCalculator, AiShareTextFormatter)
├── data/
│   ├── repository/            # 数据仓库 (WorkoutRepository, BodyMetricRepository)
│   ├── healthconnect/         # Health Connect 写入器 (HealthConnectWriter)
│   ├── reminder/              # 训练日定时通知 (WorkManager)
│   └── settings/              # 提醒、Health Connect 同步与组间休息偏好设置
├── ui/
│   ├── workout/               # 训练记录主屏 & 悬浮休息计时胶囊
│   ├── analytics/             # 渐进负荷看板 & Canvas E1RM 折线图与周期容量对比
│   ├── calendar/              # 每日记录：训练打卡月历、习惯面板与 Health Connect 设置
│   ├── library/               # 动作库分类与自定义动作
│   ├── body/                  # 身体指标管理与趋势面板
│   ├── components/            # AI 分享预览弹层 (AiSharePreviewBottomSheet) 等通用组件
│   └── navigation/            # 5-Tab 磨砂底部导航与路由宿主 (MainScreen)
├── BaojiApplication.kt        # 应用级依赖注入、Room 初始化与 Health Connect 桥接
└── MainActivity.kt            # 入口 Activity、边缘延展 (Edge-to-Edge) 与权限 Rationale
```

</details>

---

## 🤝 参与贡献 (Contributing)

欢迎提交 Issue 反馈 Bug 或发起 Pull Request！请参阅 [贡献指南](CONTRIBUTING.md) 了解环境配置与开发规范。

---

## 📄 开源协议 (License)

本项目采用 [MIT License](LICENSE) 开源协议，欢迎自由学习与使用。
