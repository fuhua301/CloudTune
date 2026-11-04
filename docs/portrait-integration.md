# 歌手写真接入说明

接口依据 MakcRe/KuGouMusicApi 的 module/images_audio.js。
实际请求为 HTTPS /v2/author_image/audio，按歌曲 hash、audio_id、album_audio_id、filename 获取对应歌手图片，无需传递登录 token 或 userid。图片优先级为高清竖图(type 6)、移动竖图(type 4)、横向写真(type 3)，无写真时尝试歌手头像。

2026-11-04 使用上游文档公开示例 hash 55603312694BF99AD6000C2D0D72D368 联网检查，返回张碧晨写真和图片地址；HTTPS 图片下载成功。fixture 只记录公开示例响应，不含账号或用户云盘数据。

切歌时递增请求代次并取消旧任务，界面立即清空旧图；旧响应仅在代次和歌曲标识均匹配时应用。图片采样解码最大边不超过1600像素，缓存上限约24 MB。写真请求失败不会影响音频播放。

界面已通过 Android API28/35 原生渲染与布局检查，见 player-portrait-test-results.txt；尚未在实体手机上进行播放与网络环境验证。

1.0.19：写真区域覆盖播放页全宽并延伸到顶部导航后方，上下均使用柔和渐变。验证照片宽度等于视口宽度、左侧和顶部偏移均为0；Android API28/35及短屏检查通过。

## 1.0.20 查询修正

歌曲图片为空、仅头像或接口报错时，按歌手名称调用 gateway.kugou.com/v1/search/author（x-router: complexsearch.kugou.com），选择准确名称对应的 AuthorId，再调用 openapicdnretry.kugou.com/kmr/v1/author/extend，fields_pack=allimages，authorimg_type=2,3,4,6。优先入驻歌手，在同等条件下取热度较高结果；不选仅模糊相似的歌手。合唱按顿号、逗号等拆分，各自查询。

兼容歌曲接口的 imgs 对象及歌手详情的 imgs 数组（file/type），高清竖版 type6 优先，然后 type4/type3；头像后备。歌手详情图片地址按 AuthorId 在内存最多缓存64组。

使用应用实际 Java HTTP 实现按公开歌手名完成联网查询及图片下载：程响4442、蔡旻佑432、顾峰983、斯琴高丽5837全部通过，见 portrait-live-results.txt。仅发送用户明确要求测试的歌手名及对应公开歌手ID，没有发送云盘歌曲标识、账号或登录凭据。

新增34项确定性检查覆盖空歌曲匹配、歌曲接口失败、合唱、同名及近似名称筛选、两种图片结构和竖版优先。播放器布局沿用1.0.19，既有布局检查结果保留；本版未在实体手机验证。
