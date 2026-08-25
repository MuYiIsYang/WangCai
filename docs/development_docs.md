# 旺财 (WangCai) 开发者技术手册

本文档为“旺财”项目提供详尽的文件索引、架构说明及布局设计逻辑，旨在帮助开发者快速理解系统各模块的协作方式。

---

## 一、 文件系统索引与职责说明

### 1. 核心控制层 (Entry Points)
- [**MainActivity.kt**](file:///app/src/main/java/com/ai/wangcai/MainActivity.kt)
    - **职责**：应用入口，初始化边缘到边缘 (Edge-to-Edge) 布局，配置 WorkManager 自动备份。
    - **逻辑**：管理全局弹窗状态（如 Supabase 配置弹窗），通过 `Configuration` 监听设备旋转并决定导航模式（底部栏 vs 侧边栏）。

### 2. UI 展现层 (UI Layer)
- [**DashboardScreen.kt**](file:///app/src/main/java/com/ai/wangcai/ui/DashboardScreen.kt)
    - **职责**：核心交互 hub。包含自定义日历和每日详情视图。
    - **布局设计**：采用“双卡片”纵向堆叠（竖屏）或左右切分（横屏）。引入了 `DaySummaryPopover`，利用 `onGloballyPositioned` 获取坐标实现精准的悬浮气泡定位。
- [**StatsScreen.kt**](file:///app/src/main/java/com/ai/wangcai/ui/StatsScreen.kt)
    - **职责**：数据可视化中心。
    - **功能**：通过 Vico 框架渲染统计图表。内置“数据查阅表”，支持在大弹窗中以网格（横屏）或块状列表（竖屏）形式审计本地/云端原始数据。
- [**SupabaseManagerScreen.kt**](file:///app/src/main/java/com/ai/wangcai/ui/SupabaseManagerScreen.kt)
    - **职责**：高级调试页面。
    - **功能**：直接连接云端数据库，允许开发者手动增删改查所有同步表，用于调试同步逻辑。

### 3. 业务逻辑层 (ViewModel & Business Logic)
- [**PetViewModel.kt**](file:///app/src/main/java/com/ai/wangcai/viewmodel/PetViewModel.kt)
    - **职责**：数据“指挥官”。
    - **功能**：封装所有数据库操作。核心逻辑包括：摄入插值计算、云端增量同步算法、操作日志录入逻辑。

### 4. 数据持久化与网络 (Data Layer)
- [**Entities.kt**](file:///app/src/main/java/com/ai/wangcai/data/Entities.kt)
    - **职责**：定义所有数据库表模型，配置 `kotlinx.serialization` 的序列化别名（用于对齐 Supabase 表字段）。
- [**PetDao.kt**](file:///app/src/main/java/com/ai/wangcai/data/PetDao.kt)
    - **职责**：定义 Room SQL 查询，支持多表关联聚合。
- [**SupabaseRepository.kt**](file:///app/src/main/java/com/ai/wangcai/data/SupabaseRepository.kt)
    - **职责**：网络抽象层。基于 Ktor 实现 RESTful 请求，处理与 Supabase 的安全鉴权头（Bearer Auth）。

### 5. 工具类 (Utilities)
- [**ExcelManager.kt**](file:///app/src/main/java/com/ai/wangcai/util/ExcelManager.kt)
    - **职责**：Excel 处理中心。通过 Apache POI 读写 `.xlsx` 文件，处理复杂的 ClassLoader 切换逻辑以确保 POI 在 Android 上的稳定性。
- [**BackupWorker.kt**](file:///app/src/main/java/com/ai/wangcai/util/BackupWorker.kt)
    - **职责**：静默任务实现。在每日特定的时间窗口（早/晚）自动执行数据导出。
- [**TranslationHelper.kt**](file:///app/src/main/java/com/ai/wangcai/util/TranslationHelper.kt)
    - **职责**：名称映射器。负责将数据库原始表/列名翻译为用户友好的中文，或在 UI 展示时进行转换。

---

## 二、 关键布局与交互设计

### 1. 响应式布局策略
系统通过 `LocalConfiguration.current.orientation` 实现真正的响应式设计：
- **竖屏 (Portrait)**：顶部日历占 3/5 空间，下方详情占 2/5。操作按钮通过悬浮 FAB 菜单展示。
- **横屏 (Landscape)**：利用 `NavigationRail` 替换底部导航栏，主体内容采用左右分栏，提升平板或横屏手机的利用率。

### 2. 悬浮点触逻辑 (Popover)
在日历视图中，点击特定日期会触发 `DaySummaryPopover`。该组件不使用标准的 `AlertDialog`，而是基于 `Box` 和绝对坐标偏移实现的自定义气泡，能够完美跟随点击位置弹出，并自动处理屏幕边缘检测。

### 3. 操作记录 (Activity Logs)
每一笔数据的增删改都会在 `activity_logs` 表中生成流水，并在统计页的“操作记录”卡片中实时展示。这不仅是为了记录，也为未来的“撤回/回滚”功能奠定了基础。

---

## 三、 维护与扩展建议

1. **混淆配置**：项目依赖 Apache POI，发布版本必须严格执行 [**rules.keep**](file:///app/src/main/keepRules/rules.keep) 中的规则，否则 POI 核心库及序列化类会被过度混淆导致逻辑崩溃。
2. **环境连接**：Supabase 的 `service_role` 权限极高，建议在发布正式版前通过配置文件而非硬编码管理密钥。
3. **小米兼容性补丁**：UI 录入组件（`OutlinedTextField`）外层不可包裹 `SelectionContainer`，以规避某些 MIUI 版本在处理系统剪贴板时的致命崩溃。

---
*文档版本：V2.9 (2026-08-25)*
