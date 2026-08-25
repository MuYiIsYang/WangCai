# 旺财 (WangCai) - 数据记录系统

可本地可云数据同步。

## 核心功能与技术实现

### 1. 饮食与饮水追踪
- **实现方案**：Room 数据库实现增量式记录, 自动识别“ refill (加粮/水)”与“ consumption (进食/饮水)”动作，可追加可空碗添加。
- **技术栈**：Room Database + Kotlin Flow.

### 2. 详细的数据记录
- **实现方案**：自定义日历组件,支持双卡片设计（日历区 + 详情区），横竖屏ui自适应。
- **技术栈**：Jetpack Compose (Custom Grid Layout) + `rememberSaveable` 状态保持。

### 3. 数据可视
- **实现方案**：对体重、用药、排泄、零食等数据进行实时运算。提供周、月、年三个维度的动态图表展示。
- **技术栈**：Vico Charts 3.3.0.

### 4. 数据云同步
- **实现方案**：集成 Supabase 云端后台, 通过 Ktor 网络客户端直接与 Supabase 通信，支持手动全量上传/下载以及增量同步。
- **技术栈**：Ktor 3.5.2 + Supabase + kotlinx.serialization.

### 5. 本地备份
- **实现方案**：支持将所有健康数据导出为 Excel (.xlsx) 文件，并支持文件导入恢复, 通过 WorkManager 在后台静默执行定期备份任务。
- **技术栈**：Apache POI 5.5.1 + WorkManager 2.11.2.

## 核心依赖版本 

| 模块/库 | 版本 | 说明 |
| :--- | :--- | :--- |
| **Jetpack Compose** | 2026.08.00 | UI 框架 (BOM) |
| **Kotlin** | 2.4.10 | 编程语言 (K2 编译器) |
| **Room** | 2.8.4 | 本地数据库 |
| **Ktor** | 3.5.2 | 网络请求 |
| **Vico** | 3.3.0 | 图表引擎 |
| **POI** | 5.5.1 | Excel 处理 |
| **WorkManager** | 2.11.2 | 后台任务 |



## 项目文件架构与职责说明

| 文件名称 | 实现功能说明 |
| :--- | :--- |
| **MainActivity.kt** | 程序主入口，管理全局状态（如配置弹窗）、WorkManager 调度及横竖屏导航适配。 |
| **DashboardScreen.kt** | 核心仪表盘，包含自定义日历逻辑、数据录入入口（FAB）及每日详情响应式布局。 |
| **StatsScreen.kt** | 统计分析中心，利用 Vico Charts 渲染多维度图表，并提供原始数据审计表视图。 |
| **SupabaseManagerScreen.kt** | 云端实验室，提供直接操作 Supabase 数据库的 UI 环境，用于同步逻辑调试与数据干预。 |
| **PetViewModel.kt** | 业务逻辑指挥官，处理本地 Room 读写、云端同步算法、数据聚合以及操作日志录入。 |
| **Entities.kt** | 核心数据模型定义，结合 `SerialName` 与 `Transient` 兼顾数据库存储与 API 序列化。 |
| **PetDao.kt** | 数据库访问对象，定义了所有本地数据的增删改查 SQL 逻辑及流式数据发射。 |
| **PetDatabase.kt** | Room 数据库核心类，负责配置 TypeConverters、数据库版本控制及迁移策略。 |
| **SupabaseRepository.kt** | 网络抽象层，基于 Ktor 封装了与 Supabase REST API 通信的所有底层请求逻辑。 |
| **ExcelManager.kt** | 导出与导入核心，通过 Apache POI 处理 XLSX 文件的读写及 Android 上的适配方案。 |
| **BackupWorker.kt** | 定时备份实现类，定义了早晚两个固定窗口的静默数据导出逻辑。 |
| **TranslationHelper.kt** | 翻译与映射工具，负责数据库原生字段与 UI 展示中文名之间的双向转换。 |
| **Converters.kt** | 数据库转换器，处理 Kotlin Enum 类型与 SQLite 字符串之间的存取转换。 |
| **DatabaseMigrations.kt** | 手动迁移逻辑库，管理数据库各版本间的架构演进及复杂字段校验。 |


---
**取之于咪，用之于喵。**