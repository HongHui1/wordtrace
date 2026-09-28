# WordTrace

一个简单的中文安卓单词记录工具：在不背单词中学习，用小悬浮球开始和结束，得到每行一个单词的 TXT 词表。支持 Android 8+ 手机和平板。

[下载安装包](https://github.com/HongHui1/wordtrace/releases/latest) · [隐私说明](PRIVACY.md) · [验证记录](docs/TESTING.md)

## 1.2.0 的重点

- 默认只记录页面上的大字目标词，减少例句、音标和按钮干扰。
- 根据位置重组 OCR 拆开的字母，再用内置英文词表校验；不补猜缺失字母。
- 连续两帧确认后保存，每个单词只保留一次，按字母排序。
- 导出纯 TXT，每行一个单词；也可复制到剪贴板或分享文本文件。
- 保留 36dp 小悬浮球（48dp 触摸范围），可拖动，点击展开控制，操作后收起。
- 首页只保留开始入口、识别设置和词表。取消频次展示和采集模式切换。

## 使用

1. 安装 APK，打开 WordTrace，允许悬浮窗，点击 **打开悬浮球**。
2. 切换到不背单词，点小球 → **开始**，允许系统屏幕共享；系统支持时选择只共享不背单词。
3. 正常学习，让目标词稳定停留约两秒。点小球可以暂停或继续。
4. 点小球 → **结束并保存**，返回 **我的词表** → **导出 TXT**，选择保存文件、分享或复制。

1.2.0 不再使用无障碍服务，不保证与系统录屏同时工作。**开始记录前请结束系统录屏**，避免系统采集通道冲突。升级后旧辅助服务会从应用清单中移除。

TXT 不包含标题、频次或列名，例如：

```text
apple
banana
```

可直接覆盖此前正式版，不必卸载。旧记录不会删除，旧次数仍留在原始记录中，但新版只展示和导出去重单词。历史误识别不会被自动猜测或改写，可在导出的文本中整理。

## 识别设置与边界

默认选择大字目标词。设置入口可以切换全部英文、调整屏幕高度范围或设置忽略词，修改从下次记录生效。大字模式按字框大小筛选，不是对不背单词私有接口的接入。

识别使用 APK 内置的 ML Kit 拉丁文字模型和由 CMUdict 提取的 125,086 个英文词形。孤立单字母、不完整碎片和词表未收录的词会被跳过；因此 `a`、`I`、部分生僻词、新词或专有名称可能漏记。连续帧校验减少翻页动画噪声，不能保证所有 OCR 错误都消失。快速翻页、模糊字、复杂背景和受保护画面仍可能漏词。实际 vivo / OriginOS 和不背单词版本仍需真机确认。

所有处理离线完成，无账号、广告或联网权限，不保存截图。全屏共享可能包含其他应用内容，请离开学习页面前暂停。返回 WordTrace 时暂停取帧；锁屏结束并保存。词表先保存在应用私有目录，卸载会删除，请及时另存。

## 构建

需要 JDK 17 或 21、Android SDK Platform 35 / Build Tools 35.0.0。

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug
```

Windows 使用 `gradlew.bat`。配置 `ANDROID_HOME` 或 `local.properties` 的 `sdk.dir`。调试包位于 `app/build/outputs/apk/debug/app-debug.apk`。

正式构建读取以下环境变量：

```text
WORDTRACE_KEYSTORE=/absolute/path/to/wordtrace-release.jks
WORDTRACE_STORE_PASSWORD=...
WORDTRACE_KEY_ALIAS=wordtrace
WORDTRACE_KEY_PASSWORD=...
```

运行 `./gradlew assembleRelease`。Windows 也可以运行 `scripts/build-release.ps1`，在 `%LOCALAPPDATA%/WordTrace/signing` 保管本机发布密钥，并输出 `dist/wordtrace-1.2.0.apk`。密钥和密码不要上传 GitHub，后续覆盖安装必须使用同一签名。

## 代码结构

- `CaptureService`：屏幕共享取帧、裁剪、离线 OCR 和保存。
- `OcrWords` / `WordAssembler`：字框分组、字母重组与词表校验。
- `WordCollector`：连续帧确认、会话内去重。
- `OverlayService`：小球及开始、暂停、结束操作。
- `SessionStore` / `Exports`：原子检查点、恢复、TXT 导出和旧记录兼容。

仓库包含 GitHub Actions、MIT 许可证、贡献指南和问题模板。项目与不背单词没有隶属关系。模型、词表和其他依赖遵循各自条款，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
