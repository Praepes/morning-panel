# Morning Panel · 晨间面板

原生 Android 应用，面向横屏投影；最低 Android 8.0（API 26），首页按 **1366 × 768** 画布设计。

## 首页

- 只展示大字时钟、本周日期、下一次闹钟、天气、中文科技新闻、本机状态和用户选定的 Home Assistant 实体。本周日期用月份小标题、高亮今天及休息日/补班日标记展示，不再在时钟下方重复显示日期；下一次闹钟只在时间下方显示一行，没有启用的闹钟时隐藏。
- 闹钟清单、设备诊断、备份和配置收在设置菜单；入口位于底部设备栏右端，用浅灰色齿轮显示，不占用顶部空间。
- 天气展示未来时段和含今天在内的六天预报；设置入口移到底部后，天气卡片新增一天预报，保留原来的字号和行距。点击任意日期可查看当日详情。
- 地点搜索兼容“西安”“西安市”和西安的常见拼音写法，也支持省份限定（如“西安市，陕西”）；中文城市名称末尾的“市”会自动适配地点库写法，无结果时再尝试原词。
- 时钟支持时分上下排、表盘与数字和横排数字三种样式。长按时间可选择，也可在“显示与电源”设置中切换；普通点击时间不触发操作，选项只展示样式预览，选择立即生效并在重启后保留。上下排采用较轻的字体与加长的分隔线；横排的小时、分钟统一为较小字号，冒号使用两个小圆点。
- 简报保留连续上下滚动。按每条新闻的实际高度计算首屏容量，将剩余高度均匀分配到条目间距，首屏底部不会露出半条新闻；一行和两行标题自动适配，不显示页码或翻页按钮。
- 通过 Android 分享菜单发送文本，或启动 `com.morningpanel.app.SHOW_MESSAGE` 并附带 `message` 字符串参数，可切换到纯消息页。触摸消息即可返回首页。
- 闹钟由首页接管：响铃通知将首页带到前台，底部半透明滑杆向左贪睡 10 分钟，向右停止。

## Home Assistant 与本机设备

应用设置中由用户填写 HA 地址和长期访问令牌；令牌用 Android Keystore 加密保存。主页要显示的 HA 实体 ID 在同一设置页选择，每行一个。

配套的 Home Assistant 集成单独发布在 [Companion Link](https://github.com/Praepes/companion-link)，可从 HACS 添加外部仓库安装。每台客户端 app 会作为独立 HA 设备呈现。Morning Panel 上报本机电量、充电、网络、屏幕状态、Android/应用版本、下次闹钟和控制能力，并提供屏幕唤醒/休眠、亮度和闹钟控制实体，无需 MQTT：

1. 在 HACS 的“自定义存储库”中添加 `https://github.com/Praepes/companion-link`，类型选 **Integration**。
2. 安装 **Companion Link** 集成并重启 HA。
3. 在 app 的 Home Assistant 设置中复制 **Companion Link 配对 ID**，填写 HA 地址和长期访问令牌并保存。
4. 在“设置 → 设备与服务 → 添加集成”中添加 **Companion Link**，填入配对 ID。
5. 点击“测试本机状态上报”。

唤醒使用 Android 电源唤醒锁；休眠需要应用级 Root。HA 集成还会显示下次闹钟，并在闹钟响铃时提供贪睡 10 分钟和停止按钮。可在“设置 → 设备诊断”中请求或重新检测 Root，并测试屏幕休眠/唤醒；装有 Magisk 等 Root 管理器的设备会显示授权弹窗。默认 Android 8 AVD 只有 ADB shell Root，系统自带 `su` 不会授权普通应用；可按下文方式临时注入 Magisk 测试应用级 Root。系统亮度需要在应用设置中授权“修改系统设置”。系统亮度是否同时控制 Xperia Touch 投影光学引擎，需要实机核对。

## 科技新闻源

初始配置采用三家网站提供的 RSS 地址：

- 少数派：`https://sspai.com/feed`
- 爱范儿：`https://www.ifanr.com/feed/`
- IT之家：`https://www.ithome.com/rss/`

设置中可增删来源，每行一个。建议主要看深度内容时保留少数派和爱范儿；想增加快讯密度时保留 IT之家。少数派在其 RSS 指引页列出官方 feed；爱范儿官方订阅页列出全文和快讯 feed；IT之家网站提供 RSS 订阅入口。[少数派 RSS 指引](https://sspai.com/post/24194) · [爱范儿订阅页](https://www.ifanr.com/social-link) · [IT之家 RSS 页面](https://www.ithome.com/rss/)

## 后台与闹钟

应用使用常驻前台服务、开机重排闹钟、响铃前台服务与唤醒锁，设置中可申请忽略电池优化。Android 和设备厂商仍可能在强制停止、极端省电或内存压力下终止进程；任何普通应用都无法保证绝不被杀。实际 Xperia Touch 固件必须验证开机自启、后台限制和深度休眠后的闹钟唤醒。

## 构建

需要 Android SDK 36、Build Tools 36.1.0 与 JDK 17–23：

```powershell
./gradlew.bat assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。项目不依赖第三方 Android 库。

时钟渲染与简报布局检查：`./gradlew.bat connectedClockChecksAndroidTest`。检查使用独立的 `com.xperiatouch.clock.clockchecks` 测试应用；测试工具清理安装时不会卸载正式应用或删除其配置。

## 本地虚拟机

`xperia_Touch` AVD 为 Android 8.0 / API 26、1366 × 768、120 dpi；默认镜像的 ADB shell 有 Root，普通应用仍需要 Root 管理器授权。测试应用 Root 时，可按 Magisk 官方说明运行 `python build.py emulator <Magisk APK>` 临时注入 Magisk；授权和休眠/唤醒在当前模拟器会话中已验证。官方 AVD 注入不会跨模拟器重启保留，重启后需再次运行脚本。
