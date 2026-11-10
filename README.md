<p align="center"><img src="assets/project-logo.svg" alt="DeepSeek Visualize 项目标志" width="108" height="108" /> <img src="assets/v-divider.svg" alt="|" width="18" height="108" /> <a href="https://space.bilibili.com/404891612"><img src="assets/developer-logo.svg" alt="炽阳001 的 Bilibili 主页" width="108" height="108" /></a></p>

<h1 align="center">DeepSeek Visualize</h1>

<p align="center">在 DeepSeek Android 对话中展示图表、练习和可操作的卡片。</p>

DeepSeek Visualize 是适配 DeepSeek Android 2.7.5（版本号 295）的非官方补丁，由炽阳001开发。客户端识别助手回复中的 `visualize` Markdown 代码块，并根据其中的 JSON 渲染原生组件。

## 主要功能

- **数据展示** · 折线图、柱状图、散点图、热力图、表格、地图和甘特图。
- **交互练习** · 支持选择、填空和判断题，以及可翻面的记忆卡片。
- **动态计算** · 用输入框、滑块、下拉选择和开关联动更新指标或图表。
- **内容组织** · 支持对比卡、日历、流程图、看板和文本差异查看。

## 快速开始

1. 按下方步骤在 Windows 上构建本地 APK，并安装到 Android 设备。补丁包使用 `com.deepseek.chat.visualize`，可与原版并存。
2. 长按桌面上的 **DeepSeek Visualize** 图标，选择 **可视化示例** 查看内置组件。
3. 在对话中发送「用折线图展示周一到周五的步数，分别是 4200、5700、5100、6800、7300」，体验回复中的图表。

## 构建

构建依赖 Python 3、JDK 21、[Apktool 3.0.3](https://github.com/iBotPeaches/Apktool/releases/tag/v3.0.3)，以及 Android SDK Platform 36 和 Build Tools 37.0.0。请自行准备 DeepSeek Android 2.7.5（版本号 295）的原版 APK，将它命名为 `DeepSeek-2.7.5-295.apk` 并放到仓库根目录。脚本会校验 APK 的 SHA-256。

```powershell
git clone https://github.com/Chiyang001/DeepSeek-Visualize.git
cd DeepSeek-Visualize

# 将原版 APK 放在当前目录，按实际安装路径调整下面两项。
$env:APKTOOL_JAR = 'C:\tools\apktool.jar'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
python .\visualize\build.py

# 可选。启用 USB 调试并连接设备后，通过 ADB 安装。
adb install -r .\DeepSeek-Visualize-2.7.5-local.apk
```

脚本解包原版 APK、应用补丁、编译可视化组件，并使用本地测试密钥签名。构建产物为根目录下的 `DeepSeek-Visualize-2.7.5-local.apk`。发布版本如需自行签名，可运行 `python .\visualize\build.py --help` 查看参数。

## 源码导航

| 路径 | 内容 |
| --- | --- |
| [`visualize/src/`](visualize/src/) | Java 组件、协议解析和交互逻辑 |
| [`visualize/build.py`](visualize/build.py) | 解包、补丁、编译、打包和签名 |
| [`visualize/protocol.txt`](visualize/protocol.txt) | 组件协议和 JSON 字段说明 |
| [`visualize/examples/`](visualize/examples/) | 可视化示例 |

地图底图使用 [Natural Earth 公共领域数据](https://www.naturalearthdata.com/about/terms-of-use/)。
