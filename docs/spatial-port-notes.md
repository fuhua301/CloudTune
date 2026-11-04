# 1.0.15 原版空间模块接入记录

本记录来自用户提供的酷狗标准版 20.6.6 APK 静态调用分析和 ARM64 CPU 数值执行，不是实机听感报告。

## HIFI现场

原版 Java 调用链：

`HiFiLiveSoundEffect.enable()` → `KGPlayerManager.ab(true)` → `bi3.b.P0(true)` → `Virtualizer.setEnabled(true)`。

`Virtualizer` 的效果类型为 16；原生 `KugouPlayer::VirtualizerEffect` 通过原包 `CreateVirtualizer` 工厂和 `Virtualizer::Active / ProcessFloat` 接口处理音频。其内部使用 HRTF。包装器默认模式为 1，原效果的 EQ 数组 C 全零；BV 数组 J 为 `[0,0,50]`。本应用接入模式 1 的原版处理模块，保留可选 EFFX 动态响度，不注入额外 EQ。

新增桥接符号：

- `CreateVirtualizer`
- `_ZN11Virtualizer6ActiveEiii`
- `_ZN11Virtualizer12ProcessFloatEPfi`
- `_ZN11Virtualizer7ReleaseEv`

原模块有内部缓冲，可返回 0 或实际输出帧数；播放器按返回长度写入 AudioTrack，并在歌曲结束追加有界零输入以排出缓存。没有把未处理的输入假装成效果输出。

## 3D旋转

原包含 `ViPER3DEffect::AdvanceSource` 和可设置双声源位置的 `ViPER3DRender`。新增桥接直接调用后者的原版 HRTF 渲染核心：

- `CreateViPER3DRender`
- `_ZN13ViPER3DRender14ConfigInstanceEii`
- `_ZN13ViPER3DRender17SetSourcePositionEffffff`
- `_ZN13ViPER3DRender16RenderBlockFloatEPfS0_`
- `_ZN13ViPER3DRender7ReleaseEv`

核心 `Audio3DSource::IsSampleRateMatch` 仅接受 44100 Hz；每次渲染必须使用配置的固定帧数。本应用配置 256 帧，保留跨解码块的左右声道样本，通过固定缓冲调用原版渲染器。

左右虚拟声源位于轨道中心角 ±17°、高度 0°、距离 1.4；每渲染 256 帧增加 `360×256/(44100×8)` 度，8 秒完成一圈，超过 360° 后回绕。该运动策略为本地实现，不宣称逐参数复刻官方轨道。

44.1/48 kHz 均可输入。48 kHz 在渲染前后使用保持相位位置的流式线性插值；队列初始延迟为 512 个源采样帧，处理时无分配和 Java 回调。暂停不推进角度；拖动进度和切换音效会重建状态，原声旁路不处理样本。暂未开放其他采样率，不能用采样率标签改写代替真正转换。

## 数值检查

- `builtin-bridge-test-results.json`：十种选择，44.1/48 kHz；检查实际编译的 JNI 桥接、有限输出、精确旁路、切换和输入范围拒绝。
- `spatial-port-test-results.json`：HIFI 在两种采样率和多种音频块大小下产生非零、有限输出；旋转模式在 2048 帧与不规则分块下的输出误差为零。原 HRTF 渲染器在 90°、180°、270° 产生与正前方不同的响应。
- CPU 测试补充执行原版 ELF 初始化函数；这些初始化在 Android 中由 dlopen 执行。测试替代了 JNI、libc 和动态加载器，因此不能代表安卓链接和硬件播放已测试。

卡片文案按用户指定展示，其中音质等级、采样技术、5–10 米听位及具体电子管型号未作独立验证。原版核心移植也不代表完整官方 EQ、设备适配和预设链已经复刻。
