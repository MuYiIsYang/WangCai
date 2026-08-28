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

    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 防御性处理：检查列是否已经存在，避免重复添加导致闪退
            val cursor = db.query("PRAGMA table_info(consumption_logs)")
            var exists = false
            while (cursor.moveToNext()) {
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1 && cursor.getString(nameIndex) == "grossWeight") {
                    exists = true
                    break
                }
            }
            cursor.close()
            
            if (!exists) {
                db.execSQL("ALTER TABLE consumption_logs ADD COLUMN grossWeight REAL NOT NULL DEFAULT 0")
            }
        }
    }

    // 所有的手动迁移逻辑汇总
    fun all(): Array<Migration> = arrayOf(
        MIGRATION_22_23
    )
}
