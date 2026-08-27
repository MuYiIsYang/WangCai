package com.ai.wangcai.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移仓库
 * 统一管理所有手动迁移逻辑 (Manual Migrations)
 * 
 * 注意：从版本 22 (UUID 分布式架构) 开始，旧的数字 ID 迁移逻辑已被移除。
 * 后续的版本演进请在此处添加新的 MIGRATION 对象。
 */
object DatabaseMigrations {

    /* 
    示例：
    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 执行 SQL 变更
        }
    }
    */

    // 所有的手动迁移逻辑汇总
    fun all(): Array<Migration> = arrayOf(
        // 在此处按顺序添加新的迁移对象，例如：MIGRATION_22_23
    )
}
