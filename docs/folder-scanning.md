# 文件夹自动扫描

先在本地音乐“导入”中添加音乐文件夹，系统返回的读取权限通过 takePersistableUriPermission 保存；仅保存选择过的目录URI，不申请全部文件权限。

FolderScanner 通过 DocumentsContract 枚举子目录，按音频 MIME 或常见扩展名筛选，调用既有 LocalMusic 私有副本导入逻辑。文件修改时间、大小及名称与缓存一致，且对应本地副本仍在时跳过复制。默认保留已导入副本，不同步删除。

onCreate 及重新进入前台的 onResume 请求扫描，短时间连续进入会合并请求。首次选择立即扫描；扫描期间追加目录会排队重扫。后台任务在独立文件任务线程上串行执行，活动销毁时停止。

检查：使用Android API28/35及模拟文档提供器，验证子目录、非音频过滤、内容去重、未变更不重复读取、新增、修改、授权撤回、取消、移除目录保留副本、启动扫描入口和文件夹选择器权限标志。共8项检查通过，音频元数据采用测试替身。实体手机系统文件夹授权和真实音频格式需实测。

实现依据： https://developer.android.com/training/data-storage/shared/documents-files 和 https://developer.android.com/reference/android/provider/DocumentsContract 。
