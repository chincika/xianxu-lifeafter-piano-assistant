# 弦序——明日之后钢琴助手 v0.1.0-beta.1

首个 Windows x64 公开测试版。

## 功能

- MIDI 导入与本机试听；
- MP3/WAV/FLAC/M4A/OGG 本地转谱；
- 原始 MIDI、游戏映射两种试听效果；
- G1–G6 音区、23 键映射与推荐音区；
- 拖动进度，并从当前位置开始游戏演奏；
- 人声主旋律、主旋律＋和弦、纯伴奏三种音频转谱模式；
- 前台游戏窗口检查和 F8 紧急停止。

## 安装

根据需要下载：

- `GamePianoAutoplay-v0.1.0-beta.1-lite-windows-x64.zip`：MIDI、试听和游戏演奏；
- `GamePianoAutoplay-v0.1.0-beta.1-full-windows-x64.zip`：额外内置音频转谱、人声分离、模型和 FFmpeg，无需另装 Python。

完整解压后运行 `GamePianoAutoplay.exe`。请勿只复制 EXE。Lite 版后续也可以按仓库 README 安装音频组件。

## 已知问题

- 当前程序未进行数字签名；
- G1–G6 的绝对音高仍需更多游戏版本实测；
- 音频转谱结果取决于原曲混音质量；
- 仅测试 Windows x64 和当前 23 键布局。

两个 ZIP 的 SHA-256 请以同一 Release 中的 `.sha256.txt` 文件为准。
