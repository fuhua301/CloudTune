# Third-party notices

## KuGouMusicApi

Direct HTTPS protocol portions in KugouApi.java were adapted to Java from:
https://github.com/MakcRe/KuGouMusicApi

Referenced files: module/user_cloud_upload.js, module/user_cloud_match.js, module/search.js, module/images_audio.js, module/login_qr_key.js, module/login_qr_check.js, module/login_qr_create.js, module/user_cloud.js, module/user_cloud_url.js, util/crypto.js, util/helper.js, util/request.js, util/config.json, util/util.js, server.js, module/user_detail.js.
Retrieved: 2026-11-02. MIT License, Copyright (c) 2023 MakcRe.
The complete license is included as LICENSE-KuGouMusicApi.txt.
This app is independent and is not affiliated with or endorsed by Kugou.

## ZXing core 3.3.3

https://github.com/zxing/zxing
Copyright ZXing authors. Apache License 2.0.
Local dependency: app/libs/zxing-core.jar.
License: LICENSE-ZXing.txt.

The optional build script also downloads Eclipse ECJ, Android SDK stubs, R8, Apktool, uber-apk-signer and org.json for build/test use. These tools are not bundled in the source archive. The org.json library is not included in the Android application; Android's system org.json implementation is used at runtime.

## JNI 头文件与内置原版组件

`native/include/jni.h` 来自 AOSP libnativehelper（Apache-2.0），保留文件头版权声明。完整 Apache-2.0 文本见 `LICENSE-ZXing.txt`。来源：https://android.googlesource.com/platform/libnativehelper/+/refs/heads/main/include_jni/jni.h 。

本版按用户要求，将用户所供酷狗标准版 20.6.6 APK 的 ARM64 `libeffect0.so`、`libviper4android.so`、`libc++_shared.so` 和一份耳机 IRS、两份参考 VPF 内置在工程和安装包中。来源 APK 与每个文件的 SHA-256 见 `docs/bundled-components.json`。原版音效库及预设没有公开源码，保留其原权利，不因包含于本工程而变成开源或获得再分发授权。此包用于向提供 APK 的用户交付；未自动发布至公开 GitHub。

`native/cloudtune_fx.c`、`PartitionedConvolver.java` 为本应用接入代码。前者调用原版导出接口，后者加载原版 IRS 进行实时卷积。Livehouse 等配置是本地参数组合，不声称与酷狗会员官方预设相同。未加入 dts 库或绕过会员检查。

1.0.15 新增调用同一原包 libeffect0.so 中的 Virtualizer/HRTF 与 ViPER3DRender。旋转轨迹和流式采样率适配由本应用实现；不修改原库授权检查，不把原版二进制视为开源。
