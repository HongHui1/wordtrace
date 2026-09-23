# WordTrace 1.1.0 验证记录

验证日期：2026-09-23。

## 自动检查

- JDK 21、Gradle 8.9、AGP 8.7.3、SDK / Build Tools 35。
- `testDebugUnitTest`：8 项通过，覆盖连续停留、消失重现、短暂漏字、忽略词、暂停恢复、频次排序、慢帧与导出。
- `lintDebug`、`lintRelease`：无问题报告；正式构建通过。
- Android 15 MuMu：2 项设备测试通过，覆盖内置模型识别和检查点 / CSV / TXT 读写。
- APK v2 签名验证通过，RSA 3072；签名证书 SHA-256 与 1.0.1 相同，versionCode 从 2 增至 3。
- 合并清单没有联网、麦克风或存储读取权限。新增辅助服务由系统 BIND_ACCESSIBILITY_SERVICE 权限保护，只请求截图能力，不读取控件树、不执行手势、不订阅事件。

## 本次录屏冲突回归

模拟器未提供可用的系统录屏快捷入口，因此采用 debug 专用 ProjectionFixtureActivity / ProjectionFixtureService：通过真实系统授权，建立并持续持有一个 MediaProjection 和 VirtualDisplay，模拟已有录屏的投影会话。它不是视频编码器，不代表 vivo 自带录屏的全部实现。

1. 先启动投影测试服务，持久状态为 running。
2. 开启截图辅助服务，从小球启动兼容识别。
3. 执行 apple → banana（停留超过 2 秒）→ apple，再暂停、切换 cherry、回到 apple 后继续，最后结束并保存。
4. 测试服务状态始终为 running，没有收到 MediaProjection.onStop；CaptureService 的前台服务类型为 specialUse。
5. 目标词统计 apple 2、banana 1，暂停期间 cherry 未进入词表。

初轮发现首次开始可能识别到尚未消失的授权提示。现已跳过授权界面可见阶段，并延迟一秒启动采样。随后重跑截图识别及导出，词表仅含 apple 2 和 banana 1。首次授权提示的全部厂商动画仍需真机检查。

服务未开启时显示辅助服务引导，没有自动发起屏幕共享。截图失败或服务断开时，代码结束并保存，禁止回退到 MediaProjection。旧共享模式在授权之前明确警告可能中断录屏。

## 界面

在 Android 15 模拟器 1280×720 横屏、720×1280 竖屏 / 240dpi 下检查小球、展开菜单和拖动。可见圆直径 36dp，触摸窗口 48dp。拖到右下角后菜单能在屏内展开；操作后自动收起，闲置八秒收起。

1.0.0 的手机 / 平板、深色模式和 1.3 字体检查作为历史证据保留于早期截图，不能视作所有新控件已重新覆盖。测试页面和投影服务只在 debug 构建中，正式 APK 不包含它们。

## 复现投影测试

安装 debug APK，在模拟器上允许悬浮窗并开启截图辅助服务，然后运行：

```sh
adb shell am start -n io.github.wordtrace/.ProjectionFixtureActivity
```

在系统提示中选择共享整个屏幕。打开 WordTrace 小球，再运行：

```sh
adb shell am start -n io.github.wordtrace/.OcrFixtureActivity --es word apple
adb shell run-as io.github.wordtrace cat shared_prefs/projection_test.xml
```

用小球开始、暂停、继续、结束；通过 --es word banana 等切换测试词。在每个阶段检查投影状态仍为 running，检查应用历史和导出词表。测试后停止投影测试服务或强制停止 debug 应用。

## 未验证范围

- 未连接用户 vivo X100s 或平板；确切 OriginOS / Android 构建号未知。没有在其自带录屏和实际不背单词版本中实测。
- Android 8–14 真机、厂商后台限制、16KB 页大小设备尚未实测。
- 旧 MediaProjection 流程曾在 1.0.0 候选版本完成端到端验证；本次重点验证新的兼容截图路径。
- 文件选择器「另存为」和第三方分享接收器需真机确认；本次验证了应用生成的文件内容。
- OCR、大字筛选和区域裁剪不能保证识别准确率；快速翻页和受保护页面可能漏词。
