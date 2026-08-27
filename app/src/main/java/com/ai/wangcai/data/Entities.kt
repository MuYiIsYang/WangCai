package com.ai.wangcai.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.text.SimpleDateFormat
import java.util.*

fun Long.toDbTime(): String {
    // 强制使用 ISO 8601 格式 (yyyy-MM-ddTHH:mm:ss.SSS)，满足 Supabase 标准
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.CHINA)
    return sdf.format(Date(this))
}

fun String.toTimestamp(): Long {
    if (this.isBlank()) return 0L
    val raw = this.trim().replace(" ", "T")
    return try {
        if (raw.contains("T")) {
            if (raw.contains("+") || raw.endsWith("Z")) {
                java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            } else {
                java.time.LocalDateTime.parse(raw)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            sdf.parse(raw)?.time ?: 0L
        }
    } catch (e: Exception) {
        try {
            val normalized = raw.replace("T", " ")
            val sdfMs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
            sdfMs.parse(normalized)?.time ?: 0L
        } catch (e2: Exception) {
            0L
        }
    }
}

@Serializable
@Entity(tableName = "pet_profiles")
data class PetProfile(
    @SerialName("编号") @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("昵称") val nickname: String? = null,
    @SerialName("品种") val breed: String? = null,
    @SerialName("生日") val birthday: String? = null,
    val avatarPath: String? = null,
    @Transient val timestamp: Long = System.currentTimeMillis(),
    @SerialName("记录时间") val recordTime: String = timestamp.toDbTime(),
    @Transient val isSynced: Boolean = false 
)

@Serializable
@Entity(tableName = "bowls")
data class Bowl(
    @SerialName("编号") @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("吃喝碗") val name: String,
    @SerialName("净重") val tareWeight: Float = 0f,
    @Transient val type: BowlType = BowlType.FOOD,
    @Transient val isSynced: Boolean = false
)

@Serializable
enum class BowlType {
    FOOD, WATER
}

@Serializable
@Entity(tableName = "consumption_logs")
data class ConsumptionLog(
    @SerialName("编号") @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @Transient val timestamp: Long = 0,
    @SerialName("记录时间") val recordTime: String = if (timestamp == 0L) "" else timestamp.toDbTime(),
    @SerialName("变动数值") val amount: Float,
    @Transient val grossWeight: Float = 0f,
    @SerialName("动作") val action: String = "",       
    @SerialName("吃喝方式") val method: String = "",   
    @Transient val type: ConsumptionType = ConsumptionType.ADD,
    @Transient val bowlType: BowlType = BowlType.FOOD,
    @Transient val isSynced: Boolean = false
)

@Serializable
enum class ConsumptionType {
    ADD, EAT, CLEAR
}

@Serializable
@Entity(tableName = "weight_logs")
data class WeightLog(
    @SerialName("编号") @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @Transient val timestamp: Long = 0,
    @SerialName("记录时间") val recordTime: String = if (timestamp == 0L) "" else timestamp.toDbTime(),
    @SerialName("体重") val weight: Float,
    @SerialName("备注") val note: String? = null,
    @Transient val isSynced: Boolean = false
)

@Serializable
@Entity(tableName = "medications")
data class Medication(
    @SerialName("编号") @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("药品名称") val name: String,
    @SerialName("剂量单位") val unit: String = "粒",
    @Transient val isSynced: Boolean = false
)

@Serializable
@Entity(tableName = "medication_logs")
data class MedicationLog(
    @SerialName("编号") @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("药品编号") val medicationId: String,
    @SerialName("药品名称") val medicationName: String = "",
    @Transient val timestamp: Long = 0,
    @SerialName("记录时间") val recordTime: String = if (timestamp == 0L) "" else timestamp.toDbTime(),
    @SerialName("用药剂量") val dosage: Float,
    @Transient val isSynced: Boolean = false
)

@Serializable
@Entity(tableName = "excretion_logs")
data class ExcretionLog(
    @SerialName("编号") @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @Transient val timestamp: Long = 0,
    @SerialName("记录时间") val recordTime: String = if (timestamp == 0L) "" else timestamp.toDbTime(),
    @SerialName("拉撒类型") val type: ExcretionType,
    @SerialName("拉撒形态") val shape: String? = null,
    @Transient val isSynced: Boolean = false
)

@Serializable
enum class ExcretionType {
    @SerialName("拉屎") POOP,
    @SerialName("撒尿") PEE
}

@Serializable
@Entity(tableName = "snacks")
data class Snack(
    @SerialName("编号") @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("零食名称") val name: String,
    @SerialName("计量单位") val unit: String = "g",
    @Transient val isSynced: Boolean = false
)

@Serializable
@Entity(tableName = "snack_logs")
data class SnackLog(
    @SerialName("编号") @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("零食编号") val snackId: String,
    @SerialName("零食名称") val snackName: String = "",
    @Transient val timestamp: Long = 0,
    @SerialName("记录时间") val recordTime: String = if (timestamp == 0L) "" else timestamp.toDbTime(),
    @SerialName("喂食数量") val amount: Float,
    @Transient val isSynced: Boolean = false
)

@Serializable
@Entity(tableName = "activity_logs")
data class ActivityLog(
    @SerialName("编号") @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    @SerialName("记录时间") val recordTime: String = timestamp.toDbTime(),
    @SerialName("动作类型") val action: String,
    @SerialName("实体类型") val entityType: String,
    @SerialName("详情") val details: String
)

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val recordId: String = "", // 对应业务记录的 UUID
    val operation: String,
    val requestBody: String,
    val responseBody: String,
    val statusCode: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val recordTime: String = timestamp.toDbTime()
)

@Entity(tableName = "pending_tasks")
data class PendingSyncTask(
    @PrimaryKey(autoGenerate = true) val taskId: Long = 0,
    val tableName: String,
    val operation: String, 
    val recordId: String,   
    val timestamp: Long = System.currentTimeMillis()
)

data class DataSnapshot(
    val bowls: List<Bowl>,
    val consumptionLogs: List<ConsumptionLog>,
    val weightLogs: List<WeightLog>,
    val medications: List<Medication>,
    val medicationLogs: List<MedicationLog>,
    val excretionLogs: List<ExcretionLog>,
    val snacks: List<Snack>,
    val snackLogs: List<SnackLog>,
    val petProfile: PetProfile?
)

data class SupabaseConfig(
    val url: String = "",
    val publishableKey: String = "",
    val secretKey: String = "",
    val jwksUrl: String = ""
) {
    val isValid: Boolean get() = url.isNotBlank() && publishableKey.isNotBlank() && secretKey.isNotBlank()
}
