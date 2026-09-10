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
- v0.1 相册扫描 + UI 骨架
- v0.2 端侧 OCR + Room 数据库 + WorkManager
- v0.3 搜索（FTS）+ 详情查看
- v0.4 图片标签 + 智能分类
- v0.5 收藏 + 分享
- v0.6 云端 OCR 增强（混合策略）

## 构建
需 Android Studio Koala+ / JDK 17+。在 Android Studio 中打开本目录即可。
