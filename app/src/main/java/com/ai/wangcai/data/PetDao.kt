package com.ai.wangcai.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {
    @Query("SELECT * FROM bowls")
    fun getAllBowls(): Flow<List<Bowl>>

    @Query("SELECT * FROM bowls WHERE type = :type LIMIT 1")
    fun getBowlByType(type: BowlType): Flow<Bowl?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBowl(bowl: Bowl): Long

    @Query("SELECT * FROM consumption_logs WHERE bowlType = :bowlType ORDER BY timestamp DESC")
    fun getConsumptionLogs(bowlType: BowlType): Flow<List<ConsumptionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumptionLog(log: ConsumptionLog): Long

    @Query("SELECT COUNT(*) FROM consumption_logs WHERE timestamp = :ts")
    suspend fun countConsumptionLogAt(ts: Long): Int

    @Query("SELECT * FROM weight_logs ORDER BY timestamp DESC")
    fun getWeightLogs(): Flow<List<WeightLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLog(log: WeightLog): Long

    @Query("SELECT COUNT(*) FROM weight_logs WHERE timestamp = :ts")
    suspend fun countWeightLogAt(ts: Long): Int

    @Query("SELECT * FROM medications")
    fun getAllMedications(): Flow<List<Medication>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication): Long

    @Query("SELECT * FROM medications WHERE name = :name LIMIT 1")
    suspend fun getMedicationByName(name: String): Medication?

    @Query("SELECT * FROM medication_logs ORDER BY timestamp DESC")
    fun getMedicationLogs(): Flow<List<MedicationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicationLog(log: MedicationLog): Long

    @Query("SELECT COUNT(*) FROM medication_logs WHERE timestamp = :ts")
    suspend fun countMedicationLogAt(ts: Long): Int

    @Delete
    suspend fun deleteConsumptionLog(log: ConsumptionLog)

    @Delete
    suspend fun deleteWeightLog(log: WeightLog)

    @Delete
    suspend fun deleteMedicationLog(log: MedicationLog)

    @Update
    suspend fun updateConsumptionLog(log: ConsumptionLog)

    @Update
    suspend fun updateWeightLog(log: WeightLog)

    @Update
    suspend fun updateMedicationLog(log: MedicationLog)

    @Query("SELECT * FROM excretion_logs ORDER BY timestamp DESC")
    fun getAllExcretionLogs(): Flow<List<ExcretionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExcretionLog(log: ExcretionLog): Long

    @Query("SELECT COUNT(*) FROM excretion_logs WHERE timestamp = :ts")
    suspend fun countExcretionLogAt(ts: Long): Int

    @Delete
    suspend fun deleteExcretionLog(log: ExcretionLog)

    @Update
    suspend fun updateExcretionLog(log: ExcretionLog)

    @Query("SELECT * FROM snacks")
    fun getAllSnacks(): Flow<List<Snack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnack(snack: Snack): Long

    @Query("SELECT * FROM snacks WHERE name = :name LIMIT 1")
    suspend fun getSnackByName(name: String): Snack?

    @Query("SELECT * FROM snack_logs ORDER BY timestamp DESC")
    fun getSnackLogs(): Flow<List<SnackLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnackLog(log: SnackLog): Long

    @Query("SELECT COUNT(*) FROM snack_logs WHERE timestamp = :ts")
    suspend fun countSnackLogAt(ts: Long): Int

    @Delete
    suspend fun deleteSnackLog(log: SnackLog)

    @Update
    suspend fun updateSnackLog(log: SnackLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLog): Long

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAllActivityLogs(): Flow<List<ActivityLog>>

    @Query("SELECT (SELECT COUNT(*) FROM consumption_logs) + (SELECT COUNT(*) FROM weight_logs) + (SELECT COUNT(*) FROM medication_logs) + (SELECT COUNT(*) FROM excretion_logs) + (SELECT COUNT(*) FROM snack_logs)")
    suspend fun getTotalRecordCount(): Int

    // Batch marking as synced
    @Update
    suspend fun updateBowls(list: List<Bowl>)
    @Update
    suspend fun updateConsumptionLogs(list: List<ConsumptionLog>)
    @Update
    suspend fun updateWeightLogs(list: List<WeightLog>)
    @Update
    suspend fun updateMedications(list: List<Medication>)
    @Update
    suspend fun updateMedicationLogs(list: List<MedicationLog>)
    @Update
    suspend fun updateExcretionLogs(list: List<ExcretionLog>)
    @Update
    suspend fun updateSnacks(list: List<Snack>)
    @Update
    suspend fun updateSnackLogs(list: List<SnackLog>)
    @Update
    suspend fun updatePetProfile(profile: PetProfile)

    // Pet Profile
    @Query("SELECT * FROM pet_profiles LIMIT 1")
    fun getPetProfile(): Flow<PetProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPetProfile(profile: PetProfile): Long
}
