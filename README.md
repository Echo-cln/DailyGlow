# DailyGlow Android

**一款面向个人日常的 Android 应用：把每日训练与轻量生活记录放在同一个入口里。**

DailyGlow 使用 Kotlin 与 Jetpack Compose 开发。训练区保留原有的计时训练体验；生活区采用独立的柔和手绘视觉方向，提供喝水、日记、穿搭和流水记录。两个区域属于同一个 App，但各自保留清楚的界面风格。

> **当前状态：A 期开发中。** 训练功能已有可运行版本；生活区正在接入。融合代码位于草稿 PR。GitHub Actions 已成功构建 debug APK（2026-10-06）；界面仍需在 Android Studio 模拟器或真机验收。

## 目录

- [功能](#功能)
- [项目结构](#项目结构)
- [技术栈](#技术栈)
- [运行项目](#运行项目)
- [训练计划格式](#训练计划格式)
- [数据与隐私](#数据与隐私)
- [当前限制](#当前限制)
- [开发路线](#开发路线)
- [开发与贡献](#开发与贡献)
- [许可证](#许可证)
- [English summary](#english-summary)

## 功能

### 训练

- 按阶段查看今日训练计划。
- 支持计时、次数、组数和完成状态记录。
- 训练动作可编辑，计时器支持开始、暂停和调整。
- 查看训练历史，并把历史计划导入今天。
- 训练计划可从内置 JSON 加载；网络可用时可刷新 GitHub 上的计划文件。
- 动作示范通过小红书搜索打开；音乐入口尝试打开网易云音乐。

### 生活记录（A 期接入中）

- 今日总览连接训练与生活记录入口。
- 在「拾光小队」和「朋友们陪伴」之间切换角色展示，共用同一份记录。
- 按日期浏览并记录喝水、三餐、日记、穿搭、生活习惯和手动流水。
- 衣橱支持编辑文字清单；流水可按农行、支付宝、微信和消费分类记录。
- 可从系统图片选择器选取账单截图，在设备本机运行中文 OCR；识别内容供核对，只有金额唯一时才预填金额，仍需手动确认并保存。
- 生活记录保存在设备本地，不会自动上传到 GitHub。

## 项目结构

```text
DailyGlow/
├── app/
│   └── src/main/
│       ├── assets/
│       │   └── today_plan.json       # 离线默认训练计划
│       └── java/com/echo/dailyglow/
│           ├── MainActivity.kt       # App 导航与训练主流程
│           ├── LifeHub.kt            # 生活区 A 期界面与本地记录
│           └── HistoryActivity.kt    # 训练历史
├── build.gradle.kts
└── settings.gradle.kts
```

目前项目使用单一 Android 应用模块。功能按页面与数据职责拆分；后续功能稳定后，再按需要提取独立 Gradle feature modules。

## 技术栈

- Kotlin
- Android SDK 35
- Jetpack Compose、Material 3
- Gradle Kotlin DSL
- 最低 Android 版本：Android 8.0（API 26）
- Java / Kotlin JVM target：17

## 运行项目

### 环境要求

- Android Studio
- JDK 17，或 Android Studio 自带的 Embedded JDK
- Android SDK Platform 35
- Android 8.0（API 26）或更高版本的模拟器/设备

### Android Studio

1. 克隆仓库并在 Android Studio 中打开项目根目录。
2. 等待 Gradle Sync 完成。
3. 连接设备或启动模拟器，点击 **Run**。

### 命令行

macOS / Linux：

```bash
bash ./gradlew assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 默认生成在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 训练计划格式

应用内置计划位于 `app/src/main/assets/today_plan.json`。每个动作的 `id` 在当天必须唯一；`kind` 使用 `TIME`（秒）或 `REPS`（次数）。

```json
{
  "date": "2026-10-06",
  "title": "臀腿训练",
  "note": "动作质量优先。",
  "playlist": "热身、主训练与拉伸曲目",
  "items": [
    {
      "id": "glute-bridge",
      "phase": "训练阶段",
      "name": "臀桥",
      "instruction": "肩背贴地，收紧臀部后抬起髋部。",
      "kind": "REPS",
      "value": 12,
      "sets": 3,
      "tutorialQuery": "臀桥 正确姿势"
    }
  ]
}
```

保持字段与示例一致。不要把个人训练历史或生活记录提交到公开仓库。

## 数据与隐私

- 训练计划与完成状态保存在应用本地；默认训练计划文件随代码提供。
- 生活区 A 期的喝水、日记、穿搭和支出记录保存在 Android 应用私有目录中的本地偏好存储。
- 当前版本不提供账号体系、跨设备云同步或云端 OCR。
- 公共仓库只应保存示例计划和代码。不要提交真实流水、日记、穿搭照片、账号凭据或密钥。
- 本地记录暂未做应用级加密；使用者应设置设备锁屏，并避免在共享设备上保存敏感内容。

## 当前限制

- 生活区仍处于 A 期接入阶段，视觉插画与已确认预览稿的细节还需继续落地。
- 自动喝水提醒和衣橱图片管理尚未接入。账单截图 OCR 已接入本机识别，识别准确性需由用户核对。
- 云同步与 AI 穿搭建议尚未接入。
- GitHub 计划刷新依赖网络；离线时应用继续使用本地计划。
- Android CI 的 `assembleDebug` 已通过，并上传了临时 debug APK 工件；构建通过不等于真机体验验收。PR 仍为草稿，尚未合并或正式发布。

## 开发路线

- [x] 保留现有训练主流程，并新增今日与生活区入口（A 期草稿）。
- [x] 加入喝水、日记、穿搭和手动支出的本地记录入口（A 期草稿）。
- [ ] 在 Android Studio / CI 完成构建、真机布局和功能验收。
- [ ] 对照已冻结的视觉稿精修手绘插画、布局与角色切换细节。
- [x] 增加按日记录与历史浏览、基础三餐/习惯、文字衣橱和支付渠道分类流水（A 期）。
- [ ] 增加喝水提醒、衣橱图片管理与更完整的穿搭历史分析。
- [ ] 设计私有云同步与备份；未经确认不上传私人记录。
- [x] 接入端侧中文账单截图识别与人工确认保存。
- [ ] 评估穿搭视觉建议。

## 开发与贡献

- 使用功能分支提交修改，并通过 Pull Request 合并。
- 提交前运行 `bash ./gradlew assembleDebug`，并在模拟器或真机检查页面尺寸、导航和数据保存。
- 不要提交 `local.properties`、密钥、真实账户信息、个人日记、流水或私密照片。
- 报告问题时请说明 Android 版本、设备/模拟器、复现步骤和预期结果。

## 许可证

仓库目前没有 `LICENSE` 文件。代码公开可见，但尚未声明可复用或分发的许可证；确定许可证前，请勿将项目标记为已正式开源。

## English summary

DailyGlow is a Kotlin and Jetpack Compose Android app for workouts and everyday personal records. The existing workout flow remains available alongside an in-progress Life area for hydration, journaling, outfit notes, and manual expenses. Life records are currently stored on-device. Receipt screenshots can be selected for on-device Chinese OCR; the user reviews and saves the extracted entry. Cloud sync and AI outfit advice are not available yet. GitHub Actions successfully built a debug APK for the draft integration branch on 2026-10-06; device-level UI validation is still pending.
