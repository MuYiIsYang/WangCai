# 旺财 (WangCai) - 数据记录系统

> **最新动态 (2026-08-31)**: 增强了“拉屎撒尿”状况记录的视觉交互（图标 + 文字），并在时间选择弹窗中增加了“此刻”一键同步功能。统一了所有资源文件命名为英文，进一步提升系统稳定性。

可本地可云数据同步。

## 核心功能与技术实现

### 1. 饮食与饮水追踪
- **实现方案**：Room 数据库实现增量式记录, 自动识别“ refill (加粮/水)”与“ consumption (进食/饮水)”动作，支持多食具（食碗、水碗）基准总重独立持久化。
- **技术栈**：Room Database + Kotlin Flow.

### 2. 详细的数据记录
- **实现方案**：自定义日历组件,支持双卡片设计（日历区 + 详情区），横竖屏ui自适应。
- **技术栈**：Jetpack Compose (Custom Grid Layout) + `rememberSaveable` 状态保持。

### 3. 数据可视
- **实现方案**：对体重、用药、拉撒、零食等数据进行实时运算。提供周、月、年三个维度的动态图表展示。
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

## Supabase 仓库配置与创建 (UUID 架构)

为了实现云端数据同步，您需要按照以下步骤在 Supabase 上配置您的后台：

### 1. 创建 Supabase 项目
1. 登录 [Supabase](https://supabase.com/)。
2. 进入 **Project Settings** -> **API** 页面，获取 `Project URL` 和 `service_role` 密钥。

### 2. 初始化数据库表 (SQL 脚本)
在 Supabase 控制台进入 **SQL Editor**，运行以下代码（注意：主键已全部迁移至 UUID）：

```sql
-- 1. 宠物档案
CREATE TABLE "public"."宠物档案" (
    "编号" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "昵称" text NOT NULL,
    "品种" text,
    "生日" date,
    "记录时间" timestamp without time zone DEFAULT now()
);

-- 2. 体重记录
CREATE TABLE "public"."体重记录" (
    "编号" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "体重" numeric NOT NULL,
    "备注" text,
    "记录时间" timestamp without time zone NOT NULL
);

-- 3. 拉撒记录
CREATE TABLE "public"."拉撒记录" (
    "编号" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "拉撒类型" text NOT NULL,
    "拉撒形态" text,
    "记录时间" timestamp without time zone NOT NULL
);

-- 4. 药品库
CREATE TABLE "public"."药品库" (
    "编号" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "药品名称" text NOT NULL,
    "剂量单位" text NOT NULL
);

-- 5. 用药打卡记录
CREATE TABLE "public"."用药打卡记录" (
    "编号" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "药品名称" text NOT NULL,
    "用药剂量" numeric NOT NULL,
    "记录时间" timestamp without time zone NOT NULL,
    "药品编号" uuid REFERENCES "药品库"("编号") ON DELETE CASCADE
);

-- 6. 零食库
CREATE TABLE "public"."零食库" (
    "编号" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "零食名称" text NOT NULL,
    "计量单位" text NOT NULL
);

-- 7. 零食打卡记录
CREATE TABLE "public"."零食打卡记录" (
    "编号" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "零食名称" text NOT NULL,
    "喂食数量" numeric NOT NULL,
    "记录时间" timestamp without time zone NOT NULL,
    "零食编号" uuid REFERENCES "零食库"("编号") ON DELETE CASCADE
);

-- 8. 食具配置
CREATE TABLE "public"."食具配置" (
    "编号" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "吃喝碗" text NOT NULL,
    "净重" numeric DEFAULT 0
);

-- 9. 饮食饮水记录
CREATE TABLE "public"."饮食饮水记录" (
    "编号" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "吃喝方式" text NOT NULL,
    "动作" text NOT NULL,
    "变动数值" numeric NOT NULL,
    "上次总重" numeric DEFAULT 0,
    "记录时间" timestamp without time zone NOT NULL
);
```

### 3. 在 App 中配置连接
1. 在 App 首页点击右上角头像，选择 **配置仓库**。
2. 按照以下格式粘贴您获取到的配置信息并保存即可。

---
**取之于咪，用之于喵。**
