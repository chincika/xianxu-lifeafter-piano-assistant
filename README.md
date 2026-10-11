# 弦序——明日之后钢琴助手

一个面向游戏内乐器的学习型自动演奏项目。当前同时支持游戏的 88 键通用钢琴界面，以及带 `G1` 至 `G6` 六个音区的 23 键快捷键界面。

当前阶段已经实现桌面工作台、离线核心和受前台保护的 Windows 输入：

- Win11 Fluent/Mica 风格的 Compose Desktop 工作台，提供跟随系统、深色、浅色三种主题；
- 钢琴主题应用图标已覆盖窗口左上角、任务栏、程序文件与安装包；
- 本地音频转 MIDI，支持 88 键·人声、88 键·伴奏及三种游戏简化模式；
- 文件选择、乐谱统计、88 键原音高直映、23 键兼容映射和 G1–G6 切换；
- 本地 MIDI 音乐库：记住用户指定的文件夹、递归扫描子目录和搜索选曲；上一首、下一首、列表循环与可选的完成后自动下一首集中在“演奏”主界面的播放队列中；
- “获取乐谱”入口：开放曲库、外部网站与第三方脚本教程均在系统浏览器中打开；
- 可选的原始 MIDI 本机试听；试听不会向游戏发送输入；
- 可选诊断日志：默认关闭、重启复位；在“帮助与安全”开启、复现问题后导出 ZIP，并自行通过 GitHub 反馈；
- 88 键鼠标演奏将 MIDI 21–108 一一映射到 A0–C8，不做逐音符八度折叠；默认使用不移动系统光标的窗口消息模式，遇到游戏版本不接收时可切换到移动光标的 `SendInput` 兼容模式；
- 游戏演奏以可拖动进度条为主界面；支持暂停/继续、演奏中拖动定位、停止后保留断点；
- 进度区会依据当前 MIDI 的真实触发密度显示动态节奏灯，而不是固定装饰波形；
- 可按需打开迷你演奏窗口，并独立选择是否始终置顶；悬浮窗复用同一套进度、拖动、暂停和停止状态；
- 可在界面中校准 G3 的最低音，并自动推导 G1/G2/G4/G5/G6；
- 自动分析乐谱并推荐折叠较少、位置更居中的音区；
- 读取标准 MIDI 文件；
- 保留音符开始时间、时长、力度、轨道和通道；
- 把 MIDI 音符投影到 23 个连续半音键；
- 支持丢弃或八度折叠超出音域的音符；
- 将 2ms 内的音符合并成和弦批次；
- 使用单调时钟生成短触发播放计划；
- 桌面端必须先导入有效 MIDI，且目标游戏回到前台后才发送键盘或鼠标输入；
- 命令行默认只打印演奏计划，使用 `--play` 时才调用 Win32 `SendInput`；
- 每次触发前检查目标游戏仍是前台窗口；切到其他窗口时冻结时间轴并停止发送，切回游戏后自动从断点继续，F8 可紧急停止。
- 首次启动必须确认自动输入风险声明；可随时从“帮助 → 用户协议与隐私声明”重新阅读。
- 乐谱准备好后可在游戏前台按 F7 开始演奏；演奏期间按 F9 暂停或继续，按 F8 紧急停止。暂停时间不会计入乐谱时间轴。

## 诊断与反馈

遇到窗口识别、演奏或转谱问题时，可在“帮助与安全”中开启诊断日志，复现后导出 ZIP，再点击“GitHub 反馈”手动附加。日志仅本地记录，尽力脱敏，不自动上传；分享前请自行检查。详情见[诊断日志说明](docs/diagnostics.md)。

## 当前假设

88 键模式要求先在游戏中打开“通用模式”的完整钢琴键盘。点击位置按照游戏客户区实时缩放，基准画面为 2048×1152；为保证准确度，建议使用与截图一致的 16:9 全屏或无边框窗口。

23 键兼容模式从左到右为：

```text
1 Q 2 W 3 E R 5 T 6 Y U 7 I 8 O 9 P [ - ] = /
```

暂定中央 G3 的最低键为 MIDI 54（F#3），因此 `R` 对应 MIDI 60（中央 C）；相邻音区暂按一组八度推导。这是待实际录音校准的假设，不会写死在解析器中。

## 运行

```powershell
$env:JAVA_HOME='你的 JDK 21 目录'
.\gradlew.bat test
.\gradlew.bat run
```

不带参数会打开桌面工作台。导入 MIDI 后可选择“88 键鼠标（推荐）”或“23 键键盘”，按需试听原始 MIDI、拖动演奏起点并填写目标游戏进程；点击“开始演奏”会从进度条位置开始并倒计时 3 秒，用于切回游戏。演奏过程中拖动进度条会先安全暂停输入，松手后从新位置继续。

生成无需安装的 Windows 可运行目录：

```powershell
.\gradlew.bat createPortableAppImage
```

输出位于 `build\portable\GamePianoAutoplay\`。MSI 安装包仍可通过 Compose 的 `packageMsi` 任务生成，但需要本机具备 WiX 或能够下载 WiX。

命令行模式仍然保留：

```powershell
.\gradlew.bat run --args='--generate-example examples\twinkle-twinkle.mid'
.\gradlew.bat run --args='--transcribe-audio C:\Music\song.mp3 high-fidelity-vocals'
.\gradlew.bat run --args='--transcribe-audio C:\Music\song.mp3 high-fidelity-accompaniment'
.\gradlew.bat run --args='--transcribe-audio C:\Music\song.mp3 balanced'
.\gradlew.bat run --args='C:\path\song.mid G3'
.\gradlew.bat run --args='--inspect-window'
.\gradlew.bat run --args='C:\path\song.mid G3 --play --process=lifeafter.exe --countdown=3 --tap-ms=0'
.\gradlew.bat run --args='C:\path\song.mid G3 --play --process=lifeafter.exe --input=mouse88 --mouse-input=window-message'
```

## 本地音频转谱组件

音频模型使用独立 Python 3.10 运行时，不修改系统 Python。首次使用前执行：

```powershell
.\tools\audio-transcription\setup-audio-engine.ps1 -WithSeparation
```

基础音符识别使用 Basic Pitch；安装 `-WithSeparation` 后会先用 Demucs 分离人声和伴奏。`88 键·人声` 仅保留人声主线并抑制泛音重复，`88 键·伴奏` 仅识别去除人声后的伴奏多声部，并过滤弱高次泛音与局部音域中的孤立弱高音；二者均覆盖 A0–C8，且不会重新混合。其他三种模式继续生成更适合 23 键演奏的简化编配。生成的 MIDI 保存在原音频旁的 `game-midi` 目录。整套处理在本机完成，不上传音乐文件。

> 音频自动转谱目前是测试功能，只适合作为初步编配素材。复杂混音、现场录音和强混响音乐可能出现漏音或误音，效果通常不如人工制作或原生 MIDI；演奏前应先试听并自行确认。

命令行真实输入必须同时提供 `--play` 和 `--process=<游戏进程名>` 或
`--title=<窗口标题片段>`。桌面版在乐谱准备好后可按 F7 开始；一旦焦点切离游戏，程序会释放当前输入、冻结演奏进度并进入“切屏等待”，切回游戏后自动从原位置继续，不会追赶等待期间的音符。手动停止会保留当前断点，再次开始默认续弹；也可以在进度条上另选起点。按 F9 可暂停或继续，按 F8 可停止播放。

软件会向游戏发送自动输入，可能被游戏或反作弊系统识别为异常行为。首次打开桌面程序时必须确认协议，否则程序退出；完整文本见[用户协议与隐私声明](docs/risk-disclaimer.md)。

详细设计见 [docs/architecture.md](docs/architecture.md)，Windows 输入说明见
[docs/windows-input.md](docs/windows-input.md)，获取 MIDI 的入口和使用边界见
[docs/getting-midi.md](docs/getting-midi.md)。

## 开源许可

Copyright © 2026 chincika。

弦序从 `v0.1.0-beta.2` 起以 GNU GPLv3 发布。你可以查看、修改和再发布源码；发布修改版或对应二进制时，需要遵守 GPLv3 并提供相应源码。第三方组件仍采用各自的许可证。
