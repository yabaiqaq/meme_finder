# MemeFinder 表情包检索器

扫描手机相册中的表情包与图片，自动 OCR 提取文字，按关键词搜索即可定位相关表情包和图片。

## 技术栈
- 平台：Android (Kotlin 2.0 + Jetpack Compose)
- 架构：MVVM + Clean Architecture + 单 module
- 数据库：Room (FTS 全文搜索)
- OCR：端侧 ML Kit (中英文) + 云端增强（可插拔）
- 图片标签：ML Kit Image Labeling
- 后台任务：WorkManager
- 依赖注入：Hilt
- 图片加载：Coil

## 模块规划
- v0.1 相册扫描 + UI 骨架（MediaStore + Jetpack Compose + Hilt 导航）
- v0.2 端侧 OCR + Room 数据库 + WorkManager 后台扫描
- v0.3 搜索（FTS4 全文索引）+ 详情页
- v0.4 图片标签识别（ML Kit Image Labeling）+ 智能二次分类
- v0.5 收藏切换 + 系统分享（FileProvider 临时文件）
- v0.6 云端 OCR 增强（混合策略：端侧 ML Kit 优先 + 百度云端兜底）

## 云端 OCR 配置
端侧 ML Kit 失败或返回空时，自动回退到云端。当前仅接入百度智能云：
1. 在 https://console.bce.baidu.com/ai 开通"文字识别"，创建应用拿到 API Key/Secret Key
2. App 内 Settings → 选择服务提供商"百度智能云 OCR" → 填入 API Key/Secret Key → 保存
3. 下次 OCR 会自动按需调用云端

无 GMS 的设备（华为等）端侧 ML Kit 不可用，云端 OCR 可作为主路径。

## 构建
需 Android Studio Koala+ / JDK 17+。在 Android Studio 中打开本目录即可。
