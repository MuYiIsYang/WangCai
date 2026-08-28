package com.ai.wangcai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Bowl::class, ConsumptionLog::class, WeightLog::class, Medication::class, MedicationLog::class, ExcretionLog::class, Snack::class, SnackLog::class, ActivityLog::class, PetProfile::class, SyncLog::class, PendingSyncTask::class],
    version = 23,
    exportSchema = true
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
                .addMigrations(*DatabaseMigrations.all()) // 加载所有手动迁移逻辑
                .fallbackToDestructiveMigration(dropAllTables = true) // 兜底策略：若找不到迁移路径则重建数据库
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
