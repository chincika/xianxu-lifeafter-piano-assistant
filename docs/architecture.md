# 架构设计

## 数据管线

```text
MIDI 文件
  -> MidiFileReader
  -> MidiScore / ScoreNote
  -> ScoreProjector
  -> TapEvent
  -> ChordBatcher
  -> KeyBatch
  -> GamePlaybackCoordinator
  -> PlaybackEngine
  -> InputSink
```

`SongPlanService` 负责把文件准备成可预览、可播放的 `SongPlan`，桌面端和命令行共用这条管线。`GamePlaybackCoordinator` 管理倒计时、前台窗口检查、F8 停止和资源释放。核心模型与投影层不知道具体游戏窗口，也不直接调用 Windows API；游戏差异集中在 `GameProfile`、键位布局和最终的 `InputSink`。

## 音频转谱管线

```text
MP3 / WAV / FLAC / M4A / OGG
  -> Demucs（人声 / 伴奏分离）
  -> Basic Pitch（音符事件）
  -> 88 键·人声：只保留人声主线与原始时序
     或 88 键·伴奏：只保留伴奏多声部与原始时序
     或
  -> 游戏简化：半拍量化、单旋律、每拍最多两个伴奏音
  -> Standard MIDI File
  -> SongPlanService / 88 键直映或 23 键投影
```

音频模型运行在 `%LOCALAPPDATA%\GamePianoAutoplay\audio-runtime` 的独立 Python 3.10 环境中，不修改系统 Python。桌面端通过受控子进程读取进度和结果。两个 88 键模式都要求 Demucs 分离成功，并且不会把人声与伴奏重新混合：人声模式从 vocal stem 中为每组起音只选择一个基频；伴奏模式只处理 no-vocals stem，在保留 A0–C8 多声部的同时，过滤与更强低音同起的弱八度、十二度和双八度泛音，以及局部声部中明显偏高且力度较弱的孤立音。其他三种游戏简化模式在人声分离失败时仍可回退为整曲智能简化。生成文件保存在原音频旁的 `game-midi` 目录，随后进入与普通 MIDI 完全相同的预览和安全播放流程。

## 88 键与 23 键布局

88 键模式覆盖标准钢琴 MIDI 21（A0）到 108（C8）。白键位置按客户区宽度等分为 52 份，黑键位置由标准音级结构计算；Y 坐标按游戏 88 键界面在客户区中的比例缩放。默认路由使用鼠标 `SendInput`；实验路由向同一前台 HWND 投递客户区坐标形式的鼠标消息。两者都不依赖固定屏幕像素坐标。

23 键兼容模式保留以下布局：

键位从左到右编号 `0..22`。编号只表达相对半音位置，因此在绝对音高尚未确认时仍然可以完成乐谱编辑、和弦合并与调度测试。

暂定校准如下：

| 游戏音区 | 最低 MIDI 音符 | 最高 MIDI 音符 |
|---|---:|---:|
| G1 | 30（F#1） | 52（E3） |
| G2 | 42（F#2） | 64（E4） |
| G3 | 54（F#3） | 76（E5） |
| G4 | 66（F#4） | 88（E6） |
| G5 | 78（F#5） | 100（E7） |
| G6 | 90（F#6） | 112（E8） |

实际开发时应录制同一个键在 G1–G6 下的声音，确认相邻音区是否正好相差 12 个半音。校准结果通过 `RangeCalibration` 注入，不修改演奏引擎。

## 目标游戏规则

- 长按不会延音，因此执行层忽略 MIDI 持续时间，统一使用固定短按。
- MIDI 时长仍保留在模型中，便于编辑、预览和未来适配其他游戏。
- 2ms 内开始的音符视为一个和弦批次。
- 同一和弦映射到同一个游戏键时自动去重。
- 所有时间调度使用单调时钟，避免系统时间校准影响节奏。
- 停止、异常或输入失败时必须调用 `releaseAll()`。

## Windows 输入路由

真实输入采用前台 `SendInput`：

1. `WindowsForegroundWindowInspector` 读取当前 HWND、PID、进程名和标题；
2. `WindowsForegroundGuard` 同时校验用户提供的进程名和标题条件；
3. `ScanCodeInputSink` 把一个和弦变成一组扫描码 DOWN 和一组 UP；
4. 默认把 DOWN+UP 用单次 Win32 `SendInput` 提交；需要兼容性停留时才拆成两次；
5. F8 监控调用 `PlaybackEngine.stop()`，等待循环最多约 10ms 重新检查停止状态；
6. 部分发送、窗口切换、异常、Ctrl+C 和正常结束都会尝试释放按键。

真实输入默认关闭。只有 `--play` 与至少一个目标窗口约束同时存在时才会创建 Windows 输入网关。

## 后续模块

1. 音区校准工具，通过 WASAPI loopback 或人工调音器确认 G1–G6。
2. 根据实测结果决定是否保留窗口消息实验模式，或继续评估 Windows 触摸注入；
3. 在现有 Compose Desktop 工作台上增加轨道选择、移调和钢琴卷帘编辑。
4. 输入延迟、触发时长和高密度连音校准。

不会实现隐藏注入、反作弊绕过或自动规避检测。
