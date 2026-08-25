package com.ai.wangcai.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.text.SimpleDateFormat
import java.util.*

fun Long.toDbTime(): String {
    // 使用 ISO 8601 格式并包含时区偏移 (例如: 2026-08-22T15:30:00+08:00)
    // 这样 Supabase 就能正确识别这是本地时间而非 UTC 时间
    return java.time.OffsetDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(this),
        java.time.ZoneId.systemDefault()
    ).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

fun String.toTimestamp(): Long {
    if (this.isBlank()) return 0L
    return try {
        // 1. 尝试 ISO 8601 格式 (Supabase 默认, 如 2026-08-22T13:25:40.998+00)
        if (this.contains("T")) {
            java.time.OffsetDateTime.parse(this).toInstant().toEpochMilli()
        } else {
            // 2. 尝试本地数据库格式 yyyy-MM-dd HH:mm:ss
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            sdf.parse(this)?.time ?: 0L
        }
    } catch (e: Exception) {
        try {
            // 3. 后备方案：移除时区偏移重试 (针对某些环境返回的特殊 ISO)
            val clean = this.substringBefore("+").substringBefore("Z").replace("T", " ")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            sdf.parse(clean)?.time ?: 0L
        } catch (e2: Exception) {
            0L
        }
    }
}

@Serializable
@Entity(tableName = "pet_profiles")
data class PetProfile(
    @SerialName("编号") @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @SerialName("昵称") val nickname: String? = null,
    @SerialName("品种") val breed: String? = null,
    @SerialName("生日") val birthday: String? = null,
    @Transient val avatarPath: String? = null,
    @Transient val timestamp: Long = System.currentTimeMillis(),
    @SerialName("创建时间") val createdAt: String = timestamp.toDbTime(),
    @Transient val isSynced: Boolean = false // 本地标记，不上传
)

@Serializable
@Entity(tableName = "bowls")
data class Bowl(
    @SerialName("编号") @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @SerialName("吃喝碗") val name: String,
    @SerialName("净重") val tareWeight: Float,
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
    @SerialName("编号") @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Transient val timestamp: Long = 0,
    @SerialName("记录时间") val recordTime: String = if (timestamp == 0L) "" else timestamp.toDbTime(),
    @SerialName("变动数值") val amount: Float,
    @SerialName("总重") val grossWeight: Float = 0f,

    @SerialName("动作") val action: String = "",       // 增加 / 减少
    @SerialName("吃喝方式") val method: String = "",   // 添加饮食 / 吃吃 / 添加饮水 / 喝喝

    // 内部逻辑字段 (Room 存储)
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
    @SerialName("编号") @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Transient val timestamp: Long = 0,
    @SerialName("记录时间") val recordTime: String = if (timestamp == 0L) "" else timestamp.toDbTime(),
    @SerialName("体重") val weight: Float,
    @SerialName("备注") val note: String? = null,
    @Transient val isSynced: Boolean = false
)

@Serializable
@Entity(tableName = "medications")
data class Medication(
    @SerialName("编号") @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @SerialName("药品名称") val name: String,
    @SerialName("剂量单位") val unit: String = "粒",
    @Transient val isSynced: Boolean = false
)

@Serializable
@Entity(tableName = "medication_logs")
data class MedicationLog(
    @SerialName("编号") @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @SerialName("药品编号") val medicationId: Long,
    @SerialName("药品名称") val medicationName: String = "",
    @Transient val timestamp: Long = 0,
    @SerialName("用药时间") val recordTime: String = if (timestamp == 0L) "" else timestamp.toDbTime(),
    @SerialName("用药剂量") val dosage: Float,
    @Transient val isSynced: Boolean = false
)

@Serializable
@Entity(tableName = "excretion_logs")
data class ExcretionLog(
    @SerialName("编号") @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    @SerialName("编号") @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @SerialName("零食名称") val name: String,
    @SerialName("计量单位") val unit: String = "g",
    @Transient val isSynced: Boolean = false
)

@Serializable
@Entity(tableName = "snack_logs")
data class SnackLog(
    @SerialName("编号") @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @SerialName("零食编号") val snackId: Long,
    @SerialName("零食名称") val snackName: String = "",
    @Transient val timestamp: Long = 0,
    @SerialName("喂食时间") val recordTime: String = if (timestamp == 0L) "" else timestamp.toDbTime(),
    @SerialName("喂食数量") val amount: Float,
    @Transient val isSynced: Boolean = false
)

@Serializable
@Entity(tableName = "activity_logs")
data class ActivityLog(
    @SerialName("编号") @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Transient val timestamp: Long = System.currentTimeMillis(),
    @SerialName("记录时间") val recordTime: String = timestamp.toDbTime(),
    @SerialName("动作类型") val action: String,
    @SerialName("实体类型") val entityType: String,
    @SerialName("详情") val details: String
)

data class SupabaseConfig(
    val url: String = "",
    val publishableKey: String = "",
    val secretKey: String = "",
    val jwksUrl: String = ""
) {
    val isValid: Boolean get() = url.isNotBlank() && publishableKey.isNotBlank() && secretKey.isNotBlank()
}
