package com.ai.wangcai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Bowl::class, ConsumptionLog::class, WeightLog::class, Medication::class, MedicationLog::class, ExcretionLog::class, Snack::class, SnackLog::class, ActivityLog::class, PetProfile::class],
    version = 16,
    exportSchema = true, // 启用 Schema 导出，AutoMigration 必须
    autoMigrations = [
        // 以后简单的加字段可以写在这里，例如：
        // AutoMigration(from = 16, to = 17)
    ]
)
@TypeConverters(Converters::class)
abstract class PetDatabase : RoomDatabase() {
    abstract fun petDao(): PetDao

    companion object {
        @Volatile
        private var INSTANCE: PetDatabase? = null

        fun getDatabase(context: Context): PetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PetDatabase::class.java,
                    "pet_database"
                )
                    .addMigrations(*DatabaseMigrations.all()) // 从仓库加载所有手动迁移逻辑
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
