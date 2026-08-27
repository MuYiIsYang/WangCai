package com.ai.wangcai.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {

    // Bowl
    @Query("SELECT * FROM bowls")
    fun getAllBowls(): Flow<List<Bowl>>

    @Query("SELECT * FROM bowls WHERE type = :type LIMIT 1")
    fun getBowlByType(type: BowlType): Flow<Bowl?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBowl(bowl: Bowl)

    // ConsumptionLog
    @Query("SELECT * FROM consumption_logs WHERE bowlType = :bowlType ORDER BY recordTime DESC")
    fun getConsumptionLogs(bowlType: BowlType): Flow<List<ConsumptionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumptionLog(log: ConsumptionLog)

    @Update
    suspend fun updateConsumptionLog(log: ConsumptionLog)

    @Delete
    suspend fun deleteConsumptionLog(log: ConsumptionLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateConsumptionLogs(logs: List<ConsumptionLog>)

    @Query("SELECT COUNT(*) FROM consumption_logs WHERE recordTime = :recordTime")
    suspend fun countConsumptionLogAt(recordTime: String): Int

    // WeightLog
    @Query("SELECT * FROM weight_logs ORDER BY recordTime DESC")
    fun getWeightLogs(): Flow<List<WeightLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLog(log: WeightLog)

    @Update
    suspend fun updateWeightLog(log: WeightLog)

    @Delete
    suspend fun deleteWeightLog(log: WeightLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateWeightLogs(logs: List<WeightLog>)

    @Query("SELECT COUNT(*) FROM weight_logs WHERE recordTime = :recordTime")
    suspend fun countWeightLogAt(recordTime: String): Int

    // Medication
    @Query("SELECT * FROM medications")
    fun getAllMedications(): Flow<List<Medication>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication)

    @Query("SELECT * FROM medications WHERE name = :name LIMIT 1")
    suspend fun getMedicationByName(name: String): Medication?

    // MedicationLog
    @Query("SELECT * FROM medication_logs ORDER BY recordTime DESC")
    fun getMedicationLogs(): Flow<List<MedicationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicationLog(log: MedicationLog)

    @Update
    suspend fun updateMedicationLog(log: MedicationLog)

    @Delete
    suspend fun deleteMedicationLog(log: MedicationLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateMedicationLogs(logs: List<MedicationLog>)

    @Query("SELECT COUNT(*) FROM medication_logs WHERE recordTime = :recordTime")
    suspend fun countMedicationLogAt(recordTime: String): Int

    // ExcretionLog
    @Query("SELECT * FROM excretion_logs ORDER BY recordTime DESC")
    fun getAllExcretionLogs(): Flow<List<ExcretionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExcretionLog(log: ExcretionLog)

    @Update
    suspend fun updateExcretionLog(log: ExcretionLog)

    @Delete
    suspend fun deleteExcretionLog(log: ExcretionLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateExcretionLogs(logs: List<ExcretionLog>)

    @Query("SELECT COUNT(*) FROM excretion_logs WHERE recordTime = :recordTime")
    suspend fun countExcretionLogAt(recordTime: String): Int

    // Snack
    @Query("SELECT * FROM snacks")
    fun getAllSnacks(): Flow<List<Snack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnack(snack: Snack)

    @Query("SELECT * FROM snacks WHERE name = :name LIMIT 1")
    suspend fun getSnackByName(name: String): Snack?

    // SnackLog
    @Query("SELECT * FROM snack_logs ORDER BY recordTime DESC")
    fun getSnackLogs(): Flow<List<SnackLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnackLog(log: SnackLog)

    @Update
    suspend fun updateSnackLog(log: SnackLog)

    @Delete
    suspend fun deleteSnackLog(log: SnackLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSnackLogs(logs: List<SnackLog>)

    @Query("SELECT COUNT(*) FROM snack_logs WHERE recordTime = :recordTime")
    suspend fun countSnackLogAt(recordTime: String): Int

    // ActivityLog
    @Query("SELECT * FROM activity_logs ORDER BY recordTime DESC LIMIT 50")
    fun getAllActivityLogs(): Flow<List<ActivityLog>>

    @Insert
    suspend fun insertActivityLog(log: ActivityLog)

    // PetProfile
    @Query("SELECT * FROM pet_profiles LIMIT 1")
    fun getPetProfile(): Flow<PetProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPetProfile(profile: PetProfile)

    // SyncLog
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: SyncLog)

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentSyncLogs(): Flow<List<SyncLog>>

    @Query("SELECT * FROM sync_logs WHERE tableName = :tableName AND recordId = :recordId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSyncLogForRecord(tableName: String, recordId: String): SyncLog?

    @Query("SELECT * FROM bowls WHERE id = :id")
    suspend fun getBowlById(id: String): Bowl?

    @Query("SELECT * FROM consumption_logs WHERE id = :id")
    suspend fun getConsumptionLogById(id: String): ConsumptionLog?

    @Query("SELECT * FROM weight_logs WHERE id = :id")
    suspend fun getWeightLogById(id: String): WeightLog?

    @Query("SELECT * FROM medication_logs WHERE id = :id")
    suspend fun getMedicationLogById(id: String): MedicationLog?

    @Query("SELECT * FROM excretion_logs WHERE id = :id")
    suspend fun getExcretionLogById(id: String): ExcretionLog?

    @Query("SELECT * FROM snack_logs WHERE id = :id")
    suspend fun getSnackLogById(id: String): SnackLog?

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationById(id: String): Medication?

    @Query("SELECT * FROM snacks WHERE id = :id")
    suspend fun getSnackById(id: String): Snack?

    @Query("DELETE FROM sync_logs WHERE statusCode >= 200 AND statusCode < 300")
    suspend fun clearSuccessfulSyncLogs()

    @Update
    suspend fun updateSyncLog(log: SyncLog)

    @Query("SELECT * FROM bowls")
    suspend fun getAllBowlsDirect(): List<Bowl>

    @Query("DELETE FROM bowls WHERE id = :id")
    suspend fun deleteBowlById(id: String)

    // Pending Tasks tracking
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingTask(task: PendingSyncTask)

    @Query("SELECT * FROM pending_tasks")
    fun getAllPendingTasksFlow(): Flow<List<PendingSyncTask>>

    @Query("SELECT * FROM pending_tasks")
    suspend fun getAllPendingTasks(): List<PendingSyncTask>

    @Query("DELETE FROM pending_tasks WHERE tableName = :tableName AND recordId = :recordId AND operation = :op")
    suspend fun removePendingTask(tableName: String, recordId: String, op: String)

    @Query("SELECT COUNT(*) FROM pending_tasks")
    fun getPendingTasksCount(): Flow<Int>

    @Query("SELECT (SELECT COUNT(*) FROM consumption_logs) + (SELECT COUNT(*) FROM weight_logs) + (SELECT COUNT(*) FROM medication_logs) + (SELECT COUNT(*) FROM excretion_logs) + (SELECT COUNT(*) FROM snack_logs)")
    suspend fun getTotalRecordCount(): Int
}
