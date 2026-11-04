# 1.0.24 响度及菜单

动态响度：AppDefaults.initialize 新增一次性 loudness_default_v24 迁移标记。首次安装和从旧版进入时设 loudness=true；标记写入后不再覆盖用户手动关闭。旧有HIFI默认迁移保持独立。MainActivity、PlaybackService和EffectsActivity的既有初始化入口均会应用默认设置。

菜单：设置、本地导入及扫描文件夹管理均调用同一个 glassMenu 方法，共用 FrostedDrawable 圆角毛玻璃背景、半透明分组行、描边、间距及完成按钮；导入菜单按行数自适应高度。

验证：全项目Java 8编译、DEX、资源打包及APK签名通过。82项协议与数据、68项上传状态机、34项写真匹配检查通过。本次未在实体手机或AIDE运行，也未重新生成原生界面截图；样式通过共享实现与代码检查确认。
