package com.ai.wangcai.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ai.wangcai.data.PetDatabase
import kotlinx.coroutines.flow.first
import java.util.Calendar

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        
        // Window 1: 10:00 - 12:00
        // Window 2: 20:00 - 22:00
        val isMorningWindow = hour in 10..11
        val isEveningWindow = hour in 20..21

        if (!isMorningWindow && !isEveningWindow) {
            return androidx.work.ListenableWorker.Result.success()
        }

        val prefs = applicationContext.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
        val lastBackup = prefs.getLong("last_auto_backup", 0L)
        val lastCal = Calendar.getInstance().apply { timeInMillis = lastBackup }
        
        val isSameDay = now.get(Calendar.YEAR) == lastCal.get(Calendar.YEAR) && 
                        now.get(Calendar.DAY_OF_YEAR) == lastCal.get(Calendar.DAY_OF_YEAR)
        
        val lastHour = lastCal.get(Calendar.HOUR_OF_DAY)
        val alreadyDoneInThisWindow = isSameDay && ((isMorningWindow && lastHour in 10..11) || (isEveningWindow && lastHour in 20..21))

        if (alreadyDoneInThisWindow) {
            return androidx.work.ListenableWorker.Result.success()
        }

        val dao = PetDatabase.getDatabase(applicationContext).petDao()
        
        // Sanity Check: Don't back up if empty
        if (dao.getTotalRecordCount() == 0) {
            return androidx.work.ListenableWorker.Result.success()
        }

        return try {
            val bowls = dao.getAllBowls().first()
            val cLogs = dao.getConsumptionLogs(com.ai.wangcai.data.BowlType.FOOD).first() + 
                        dao.getConsumptionLogs(com.ai.wangcai.data.BowlType.WATER).first()
            val wLogs = dao.getWeightLogs().first()
            val meds = dao.getAllMedications().first()
            val mLogs = dao.getMedicationLogs().first()
            val eLogs = dao.getAllExcretionLogs().first()
            val snacks = dao.getAllSnacks().first()
            val sLogs = dao.getSnackLogs().first()

            ExcelManager.performAutoBackup(applicationContext, bowls, cLogs, wLogs, meds, mLogs, eLogs, snacks, sLogs)
            androidx.work.ListenableWorker.Result.success()
        } catch (e: Exception) {
            android.util.Log.e("BackupWorker", "Auto backup failed, retrying...", e)
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}
