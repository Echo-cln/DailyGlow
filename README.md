# DailyGlow · 每日训练

原生 Android（Kotlin + Jetpack Compose）训练清单应用。可在 Android Studio 打开 `DailyGlow/` 后直接运行或生成 APK。

## 已完成

- 按阶段展示今日训练；计时训练可开始、暂停、每次调节 5 秒。
- 计次数和组数可在卡片内编辑；完成状态按日期保存。
- “看姿势”跳转小红书搜索；“打开网易云”尝试唤起网易云音乐。
- 离线优先，内置 `app/src/main/assets/today_plan.json`；未联网也可训练。
- 已接入 GitHub 云端计划：App 读取仓库内 `app/src/main/assets/today_plan.json` 的 Raw 地址；App 点击右上角刷新即可同步，网络失败会保留现有计划。
- 可接收其他 Android 应用通过系统“分享”发送的纯文本 JSON；这不是 ChatGPT 的固定导入方式。

## 打包安装

1. Android Studio 打开本目录，按提示使用 JDK 17 和 Android SDK 35。
2. 等待 Gradle Sync 完成，连接手机后点击 Run；或 Build → Build APK(s)。
3. 安装 `app/build/outputs/apk/debug/app-debug.apk` 到 Android 8.0 及以上设备。

项目允许使用本机已有 JDK（包括 JDK 22）运行 Gradle，同时将 Java/Kotlin 产物统一编译为 JVM 17；因此**不需要安装 JDK 17**。若 Android Studio 提示 JDK 版本不支持，再将 **Settings → Build Tools → Gradle → Gradle JDK** 设为 Android Studio 自带的 Embedded JDK。

## 与 ChatGPT 定时任务配合（推荐）

ChatGPT 分享对话和定时任务不是可调用 API，不能被 App 自动抓取；ChatGPT 也不能保证把单条任务结果通过 Android 系统分享给 DailyGlow。唯一可靠闭环是：定时任务按固定 JSON 格式生成当天计划 → 将 JSON 提交到本仓库的 `app/src/main/assets/today_plan.json` → App 点击右上角刷新。聊天记录链接只作为你和 ChatGPT 制定训练规则的参考，不参与 App 网络请求。

把定时任务的输出要求设为：**不要写解释；只输出一个合法 JSON 对象，字段完全遵循本文件下方的接口契约。**

> 每天更新训练：在 GitHub 编辑并提交 `app/src/main/assets/today_plan.json`，随后打开 App 点击刷新。App 请求会自动附带时间戳，避免读取 CDN 缓存。

## 云端接口契约

`GET /api/v1/plans/today` 返回 HTTP 200 和 JSON；字段与内置 `today_plan.json` 完全一致。服务端应根据当天日期返回该日计划，App 不提交任何运动数据。

```json
{
  "date": "2026-09-24",
  "title": "腹部核心 · 第 2 天",
  "note": "动作质量优先。",
  "playlist": "轻快电子 / 100–120 BPM",
  "items": [
    {"id":"plank","phase":"训练阶段","name":"平板支撑","instruction":"肩在肘正上方，收腹。","kind":"TIME","value":25,"sets":3,"tutorialQuery":"平板支撑 正确姿势"}
  ]
}
```

字段 `kind` 只能是 `TIME`（秒）或 `REPS`（次数）；`id` 在同一天必须唯一。更新本地默认计划时也只需替换同格式的 `today_plan.json`。
