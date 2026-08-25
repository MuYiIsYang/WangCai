package com.ai.wangcai.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移仓库
 * 统一管理所有手动迁移逻辑 (Manual Migrations)
 */
object DatabaseMigrations {

    // 迁移逻辑：从 15 版本升级到 16 版本
    // 增加安全检查，防止重复添加列导致崩溃
    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(consumption_logs)")
            var hasGrossWeight = false
            try {
                while (cursor.moveToNext()) {
                    val nameColumnIndex = cursor.getColumnIndex("name")
                    if (nameColumnIndex != -1 && cursor.getString(nameColumnIndex) == "grossWeight") {
                        hasGrossWeight = true
                        break
                    }
                }
            } finally {
                cursor.close()
            }

            if (!hasGrossWeight) {
                db.execSQL("ALTER TABLE consumption_logs ADD COLUMN grossWeight REAL NOT NULL DEFAULT 0.0")
            }
        }
    }

    // 所有的手动迁移逻辑汇总
    fun all(): Array<Migration> = arrayOf(
        MIGRATION_15_16
    )
}
