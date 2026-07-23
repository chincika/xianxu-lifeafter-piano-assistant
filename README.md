# 弦序——明日之后钢琴助手

专为《明日之后》Windows 版 23 键钢琴适配的乐器辅助工具。它可以读取 MIDI，也可以在本地把常见音乐文件转换为简化乐谱，并在目标游戏位于前台时按计划发送键盘输入。

> 当前为测试版。本仓库仅发布可执行程序，不公开源代码，也不隶属于或代表任何游戏开发商、发行商。

## 主要功能

- 支持 MIDI、MP3、WAV、FLAC、M4A 和 OGG；
- 原始 MIDI 与“游戏映射”双模式试听；
- 播放、暂停、停止和进度定位；
- 23 键映射与 G1–G6 音区切换；
- 自动推荐更合适的音区，并统计八度折叠；
- 可选的人声分离、旋律提取和伴奏转谱；
- 前台窗口保护与 F8 紧急停止。

## 下载与运行

1. 在仓库右侧的 **Releases** 页面选择一个版本：
   - `GamePianoAutoplay-*-lite-windows-x64.zip`：约 85 MiB，适合已经有 MIDI 的用户；
   - `GamePianoAutoplay-*-full-windows-x64.zip`：约 540 MiB，内置音频转谱、人声分离和 FFmpeg，解压即用（解压后约 1.41 GiB）。
2. 完整解压 ZIP；不要只把 EXE 单独复制出来。
3. 运行 `GamePianoAutoplay.exe`。程序已经包含 Java 运行环境，无需另装 JDK。
4. 导入 MIDI 或音乐文件，先使用“游戏映射”试听确认效果。
5. 填写目标游戏进程名。开始演奏后，在三秒倒计时内切回游戏。

支持 Windows 10/11 x64。程序目前没有数字签名，首次运行时 Windows 可能显示安全提醒；请只从本仓库的 Releases 下载，并核对 SHA-256。

## Lite 版可选：安装音频转谱组件

Full 版不需要安装 Python 或下载模型。Lite 版的 MIDI 导入和演奏无需额外安装；若后来需要 MP3/WAV 等音频转谱，可联网安装本地模型：

```powershell
PowerShell -ExecutionPolicy Bypass -File .\tools\audio-transcription\setup-audio-engine.ps1
```

若需要人声分离：

```powershell
PowerShell -ExecutionPolicy Bypass -File .\tools\audio-transcription\setup-audio-engine.ps1 -WithSeparation
```

安装器会把独立 Python 环境和模型放在 `%LOCALAPPDATA%\GamePianoAutoplay\audio-runtime`。Full 版则直接使用程序目录中的 `audio-runtime`。音频处理在本机完成，不会上传音乐文件。带人声分离的版本体积较大，请预留足够磁盘空间。

## 当前限制

- 仅支持 Windows x64；
- 键位固定为游戏当前的 23 键键盘布局；
- G3 最低音暂按 MIDI 54（F#3）校准，相邻 G1–G6 暂按一个八度推导；
- 不同地区或版本的游戏进程名可能不同，需要在界面中修改；
- 音频自动转谱不是人工扒谱，复杂混音可能需要调整或重新制作 MIDI；
- 自动输入是否允许取决于游戏当前规则，使用者需要自行确认并承担相关风险。

## 反馈问题

提交 Issue 时请提供：程序版本、Windows 版本、游戏版本/地区、所选音区、错误提示，以及能否用示例 MIDI 复现。请勿上传包含版权音乐、账号信息或其他个人数据的文件。

## 使用与第三方组件

程序的二进制使用条件见 [BINARY-LICENSE.txt](BINARY-LICENSE.txt)，第三方组件见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。
