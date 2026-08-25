package com.ai.wangcai.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.wangcai.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.wangcai.util.TranslationHelper
import java.util.Calendar

class PetViewModel(application: Application) : AndroidViewModel(application) {
    private val db = PetDatabase.getDatabase(application)
    private val dao = db.petDao()
    private val supabase = SupabaseRepository()

    private val _redirectionEvent = MutableSharedFlow<String>()
    val redirectionEvent = _redirectionEvent.asSharedFlow()

    init {
        // 初始同步配置到 repository
        val initialConfig = SupabaseConfig(
            url = application.getSharedPreferences("supabase_prefs", android.content.Context.MODE_PRIVATE).getString("url", "") ?: "",
            publishableKey = application.getSharedPreferences("supabase_prefs", android.content.Context.MODE_PRIVATE).getString("publishable_key", "") ?: "",
            secretKey = application.getSharedPreferences("supabase_prefs", android.content.Context.MODE_PRIVATE).getString("secret_key", "") ?: "",
            jwksUrl = application.getSharedPreferences("supabase_prefs", android.content.Context.MODE_PRIVATE).getString("jwks_url", "") ?: ""
        )
        supabase.updateConfig(initialConfig)
    }

    val allBowls = dao.getAllBowls().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val foodBowl = dao.getBowlByType(BowlType.FOOD).stateIn(viewModelScope, SharingStarted.Lazily, null)
    val waterBowl = dao.getBowlByType(BowlType.WATER).stateIn(viewModelScope, SharingStarted.Lazily, null)

    val foodLogs = dao.getConsumptionLogs(BowlType.FOOD).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val waterLogs = dao.getConsumptionLogs(BowlType.WATER).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val weightLogs = dao.getWeightLogs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val medications = dao.getAllMedications().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val medicationLogs = dao.getMedicationLogs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val excretionLogs = dao.getAllExcretionLogs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val snacks = dao.getAllSnacks().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val snackLogs = dao.getSnackLogs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val activityLogs = dao.getAllActivityLogs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val petProfile = dao.getPetProfile().stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val prefs = application.getSharedPreferences("supabase_prefs", android.content.Context.MODE_PRIVATE)
    
    private val _supabaseConfig = MutableStateFlow(
        SupabaseConfig(
            url = prefs.getString("url", "") ?: "",
            publishableKey = prefs.getString("publishable_key", "") ?: "",
            secretKey = prefs.getString("secret_key", "") ?: "",
            jwksUrl = prefs.getString("jwks_url", "") ?: ""
        )
    )
    val supabaseConfig = _supabaseConfig.asStateFlow()

    fun updateSupabaseConfig(config: SupabaseConfig) {
        _supabaseConfig.value = config
        prefs.edit()
            .putString("url", config.url)
            .putString("publishable_key", config.publishableKey)
            .putString("secret_key", config.secretKey)
            .putString("jwks_url", config.jwksUrl)
            .apply()
        // 更新 repository 配置
        supabase.updateConfig(config)
    }

    fun clearSupabaseConfig() {
        updateSupabaseConfig(SupabaseConfig())
    }

    fun parseAndSaveConfig(rawText: String): Boolean {
        try {
            val lines = rawText.lines()
            var url = ""
            var pKey = ""
            var sKey = ""
            var jwks = ""
            
            lines.forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("SUPABASE_URL=") -> url = trimmed.substringAfter("=").trim()
                    trimmed.startsWith("SUPABASE_PUBLISHABLE_KEY=") -> pKey = trimmed.substringAfter("=").trim()
                    trimmed.startsWith("SUPABASE_SECRET_KEY=") -> sKey = trimmed.substringAfter("=").trim()
                    trimmed.startsWith("SUPABASE_JWKS_URL=") -> jwks = trimmed.substringAfter("=").trim()
                }
            }
            
            if (url.isNotBlank() && pKey.isNotBlank() && sKey.isNotBlank()) {
                updateSupabaseConfig(SupabaseConfig(url, pKey, sKey, jwks))
                return true
            }
        } catch (e: Exception) {
            Log.e("ConfigParse", "Failed to parse: ${e.message}")
        }
        return false
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    // 【手动下载】从云端同步全量数据到本地
    fun syncAllFromCloud() {
        if (!supabaseConfig.value.isValid) {
            triggerRedirection("CONFIG_NEEDED")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            try {
                supabase.fetchTableData<PetProfile>("宠物档案").forEach { 
                    dao.insertPetProfile(it.copy(timestamp = it.createdAt.toTimestamp(), isSynced = true))
                }
                supabase.fetchTableData<Medication>("药品库").forEach { dao.insertMedication(it.copy(isSynced = true)) }
                supabase.fetchTableData<MedicationLog>("药品打卡记录").forEach { 
                    dao.insertMedicationLog(it.copy(timestamp = it.recordTime.toTimestamp(), isSynced = true)) 
                }
                supabase.fetchTableData<Snack>("零食库").forEach { dao.insertSnack(it.copy(isSynced = true)) }
                supabase.fetchTableData<SnackLog>("零食打卡记录").forEach { 
                    dao.insertSnackLog(it.copy(timestamp = it.recordTime.toTimestamp(), isSynced = true)) 
                }
                supabase.fetchTableData<Bowl>("食具配置").forEach { 
                    val type = if (it.name.contains("水")) BowlType.WATER else BowlType.FOOD
                    dao.insertBowl(it.copy(type = type, isSynced = true)) 
                }
                supabase.fetchTableData<ConsumptionLog>("饮食饮水记录").forEach { 
                    val fixed = it.copy(
                        timestamp = it.recordTime.toTimestamp(),
                        isSynced = true,
                        type = when(it.action) {
                            "增加" -> ConsumptionType.ADD
                            "减少" -> ConsumptionType.EAT
                            else -> ConsumptionType.CLEAR
                        },
                        bowlType = when(it.method) {
                            "添加饮食", "吃吃" -> BowlType.FOOD
                            "添加饮水", "喝喝" -> BowlType.WATER
                            else -> BowlType.FOOD
                        }
                    )
                    dao.insertConsumptionLog(fixed) 
                }
                supabase.fetchTableData<WeightLog>("体重记录").forEach { 
                    dao.insertWeightLog(it.copy(timestamp = it.recordTime.toTimestamp(), isSynced = true)) 
                }
                supabase.fetchTableData<ExcretionLog>("拉撒记录").forEach { 
                    dao.insertExcretionLog(it.copy(timestamp = it.recordTime.toTimestamp(), isSynced = true)) 
                }

                addActivityLog("SYNC", "Cloud", "Manual full download completed")
            } catch (e: Exception) {
                Log.e("SyncError", "Download failed: ${e.localizedMessage}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // 【手动上传】将本地尚未同步成功的记录批量上传到云端
    fun syncAllToCloud() {
        if (!supabaseConfig.value.isValid) {
            triggerRedirection("CONFIG_NEEDED")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            try {
                // 1. 宠物档案
                petProfile.value?.let { profile ->
                    if (!profile.isSynced) {
                        supabase.upsertData("宠物档案", profile)
                        dao.updatePetProfile(profile.copy(isSynced = true))
                    }
                }

                // 2. 食具配置
                val unsyncedBowls = allBowls.value.filter { !it.isSynced }
                if (unsyncedBowls.isNotEmpty()) {
                    supabase.upsertData("食具配置", unsyncedBowls)
                    dao.updateBowls(unsyncedBowls.map { it.copy(isSynced = true) })
                }

                // 3. 药品/用药
                val unsyncedMeds = medications.value.filter { !it.isSynced }
                if (unsyncedMeds.isNotEmpty()) {
                    supabase.upsertData("药品库", unsyncedMeds)
                    dao.updateMedications(unsyncedMeds.map { it.copy(isSynced = true) })
                }
                val unsyncedMedLogs = medicationLogs.value.filter { !it.isSynced }
                if (unsyncedMedLogs.isNotEmpty()) {
                    supabase.upsertData("药品打卡记录", unsyncedMedLogs)
                    dao.updateMedicationLogs(unsyncedMedLogs.map { it.copy(isSynced = true) })
                }

                // 4. 零食/喂食
                val unsyncedSnacks = snacks.value.filter { !it.isSynced }
                if (unsyncedSnacks.isNotEmpty()) {
                    supabase.upsertData("零食库", unsyncedSnacks)
                    dao.updateSnacks(unsyncedSnacks.map { it.copy(isSynced = true) })
                }
                val unsyncedSnackLogs = snackLogs.value.filter { !it.isSynced }
                if (unsyncedSnackLogs.isNotEmpty()) {
                    supabase.upsertData("零食打卡记录", unsyncedSnackLogs)
                    dao.updateSnackLogs(unsyncedSnackLogs.map { it.copy(isSynced = true) })
                }

                // 5. 饮食饮水
                val unsyncedCons = (foodLogs.value + waterLogs.value).filter { !it.isSynced }
                if (unsyncedCons.isNotEmpty()) {
                    supabase.upsertData("饮食饮水记录", unsyncedCons)
                    dao.updateConsumptionLogs(unsyncedCons.map { it.copy(isSynced = true) })
                }

                // 6. 体重
                val unsyncedWeight = weightLogs.value.filter { !it.isSynced }
                if (unsyncedWeight.isNotEmpty()) {
                    supabase.upsertData("体重记录", unsyncedWeight)
                    dao.updateWeightLogs(unsyncedWeight.map { it.copy(isSynced = true) })
                }

                // 7. 拉撒
                val unsyncedEx = excretionLogs.value.filter { !it.isSynced }
                if (unsyncedEx.isNotEmpty()) {
                    supabase.upsertData("拉撒记录", unsyncedEx)
                    dao.updateExcretionLogs(unsyncedEx.map { it.copy(isSynced = true) })
                }

                addActivityLog("SYNC", "Cloud", "Manual incremental upload completed")
            } catch (e: Exception) {
                Log.e("SyncError", "Manual upload failed: ${e.localizedMessage}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun updatePetProfile(nickname: String, breed: String?, birthday: String?, avatarPath: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = petProfile.value
            val profile = PetProfile(
                id = current?.id ?: 0L,
                nickname = nickname,
                breed = breed,
                birthday = birthday,
                avatarPath = avatarPath,
                isSynced = false
            )
            val id = dao.insertPetProfile(profile)
            val toSync = profile.copy(id = id)
            supabase.upsertData("宠物档案", toSync)
            dao.insertPetProfile(toSync.copy(isSynced = true))
            addActivityLog("UPDATE", "PetProfile", nickname)
        }
    }

    fun updateBowl(name: String, tareWeight: Float, type: BowlType) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = if (type == BowlType.FOOD) foodBowl.value else waterBowl.value
            val bowl = Bowl(
                id = existing?.id ?: 0L,
                name = name,
                tareWeight = tareWeight,
                type = type,
                isSynced = false
            )
            val id = dao.insertBowl(bowl)
            val toSync = bowl.copy(id = id)
            supabase.upsertData("食具配置", toSync)
            dao.insertBowl(toSync.copy(isSynced = true))
            addActivityLog("UPDATE", "Bowl", "$name (${type.name})")
        }
    }

    fun recordConsumption(grossWeight: Float, type: BowlType, isFromEmpty: Boolean, newTareWeight: Float? = null, targetDate: Calendar? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentBowl = if (type == BowlType.FOOD) foodBowl.value else waterBowl.value
            val tare = newTareWeight ?: currentBowl?.tareWeight ?: 0f
            
            // 确保碗存在且皮重更新
            if (currentBowl == null || (newTareWeight != null && newTareWeight != currentBowl.tareWeight)) {
                val bowl = Bowl(
                    id = currentBowl?.id ?: 0L,
                    name = currentBowl?.name ?: (if (type == BowlType.FOOD) "食盆" else "水盆"),
                    tareWeight = tare,
                    type = type,
                    isSynced = false
                )
                val bId = dao.insertBowl(bowl)
                supabase.upsertData("食具配置", bowl.copy(id = bId))
                dao.insertBowl(bowl.copy(id = bId, isSynced = true))
            }

            val logs = if (type == BowlType.FOOD) foodLogs.value else waterLogs.value
            val lastLog = logs.firstOrNull()
            // 修复：如果 lastLog.grossWeight 为 0（旧数据），则回退到碗重作为计算基准
            val lastGross = if ((lastLog?.grossWeight ?: 0f) > 0f) lastLog!!.grossWeight else tare
            val timestamp = generateTimestamp(targetDate)

            val finalDiff: Float
            if (isFromEmpty) {
                // 逻辑：空碗记录。公式：新数值 - 碗重。之前的剩余量不记作消耗（视为倒掉或清理了）。
                finalDiff = grossWeight - tare
            } else {
                // 逻辑：未空记录。公式：新数值 - 上次总重 = 计算插值。
                finalDiff = grossWeight - lastGross
            }

            val roundedDiff = kotlin.math.round(finalDiff * 10f) / 10f

            if (roundedDiff != 0f) {
                val log = ConsumptionLog(
                    timestamp = timestamp,
                    amount = roundedDiff,
                    grossWeight = grossWeight, // 存放当前总重，供下次计算作为基准
                    type = if (roundedDiff > 0) ConsumptionType.ADD else ConsumptionType.EAT,
                    bowlType = type,
                    action = if (roundedDiff > 0) "增加" else "减少",
                    method = when {
                        roundedDiff > 0 && type == BowlType.FOOD -> "添加饮食"
                        roundedDiff > 0 && type == BowlType.WATER -> "添加饮水"
                        roundedDiff <= 0 && type == BowlType.FOOD -> "吃吃"
                        roundedDiff <= 0 && type == BowlType.WATER -> "喝喝"
                        else -> ""
                    },
                    isSynced = false
                )
                val lId = dao.insertConsumptionLog(log)
                supabase.upsertData("饮食饮水记录", log.copy(id = lId))
                dao.insertConsumptionLog(log.copy(id = lId, isSynced = true))
                addActivityLog("ADD", "Consumption", "${if(roundedDiff>0) "Refill" else "Eat"} ${kotlin.math.abs(roundedDiff)}${if(type==BowlType.FOOD) "g" else "ml"}")
            }
        }
    }

    private fun generateTimestamp(targetDate: Calendar?): Long {
        return if (targetDate != null) {
            val now = Calendar.getInstance()
            val cal = targetDate.clone() as Calendar
            cal.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY))
            cal.set(Calendar.MINUTE, now.get(Calendar.MINUTE))
            cal.set(Calendar.SECOND, now.get(Calendar.SECOND))
            cal.set(Calendar.MILLISECOND, now.get(Calendar.MILLISECOND))
            cal.timeInMillis
        } else System.currentTimeMillis()
    }

    fun deleteConsumption(log: ConsumptionLog) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteConsumptionLog(log)
            supabase.deleteData("饮食饮水记录", log.id)
            addActivityLog("DELETE", "Consumption", "${log.amount}${if(log.bowlType==BowlType.FOOD) "g" else "ml"}")
        }
    }

    fun deleteWeight(log: WeightLog) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteWeightLog(log)
            supabase.deleteData("体重记录", log.id)
            addActivityLog("DELETE", "Weight", "${log.weight}kg")
        }
    }

    fun deleteMedicationLog(log: MedicationLog) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteMedicationLog(log)
            supabase.deleteData("药品打卡记录", log.id)
            addActivityLog("DELETE", "Medication", "Dosage: ${log.dosage}")
        }
    }

    fun deleteExcretion(log: ExcretionLog) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteExcretionLog(log)
            supabase.deleteData("拉撒记录", log.id)
            addActivityLog("DELETE", "Excretion", log.type.name)
        }
    }

    fun addWeightLog(weight: Float, note: String? = null, targetDate: Calendar? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val log = WeightLog(timestamp = generateTimestamp(targetDate), weight = weight, note = note, isSynced = false)
            val id = dao.insertWeightLog(log)
            val toSync = log.copy(id = id)
            supabase.upsertData("体重记录", toSync)
            dao.insertWeightLog(toSync.copy(isSynced = true))
            addActivityLog("ADD", "Weight", "${weight}kg ${note ?: ""}")
        }
    }

    fun addMedication(name: String, unit: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val med = Medication(name = name, unit = unit, isSynced = false)
            val id = dao.insertMedication(med)
            val toSync = med.copy(id = id)
            supabase.upsertData("药品库", toSync)
            dao.insertMedication(toSync.copy(isSynced = true))
            addActivityLog("ADD", "MedicationType", name)
        }
    }

    fun addMedicationLog(medicationId: Long, dosage: Float, targetDate: Calendar? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val med = medications.value.find { it.id == medicationId }
            val log = MedicationLog(
                medicationId = medicationId, 
                medicationName = med?.name ?: "未知药品",
                timestamp = generateTimestamp(targetDate), 
                dosage = dosage,
                isSynced = false
            )
            val id = dao.insertMedicationLog(log)
            val toSync = log.copy(id = id)
            supabase.upsertData("药品打卡记录", toSync)
            dao.insertMedicationLog(toSync.copy(isSynced = true))
            addActivityLog("ADD", "MedicationLog", "${med?.name ?: "Unknown"} $dosage")
        }
    }

    fun addExcretionLog(type: ExcretionType, shape: String? = null, targetDate: Calendar? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val log = ExcretionLog(timestamp = generateTimestamp(targetDate), type = type, shape = shape, isSynced = false)
            val id = dao.insertExcretionLog(log)
            val toSync = log.copy(id = id)
            supabase.upsertData("拉撒记录", toSync)
            dao.insertExcretionLog(toSync.copy(isSynced = true))
            addActivityLog("ADD", "Excretion", "${type.name} - ${shape ?: "未指定"}")
        }
    }

    fun updateConsumption(log: ConsumptionLog) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = log.copy(isSynced = false)
            dao.updateConsumptionLog(updated)
            supabase.upsertData("饮食饮水记录", updated)
            dao.updateConsumptionLog(updated.copy(isSynced = true))
            addActivityLog("UPDATE", "Consumption", "${log.amount}")
        }
    }

    fun updateWeightLog(log: WeightLog) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = log.copy(isSynced = false)
            dao.updateWeightLog(updated)
            supabase.upsertData("体重记录", updated)
            dao.updateWeightLog(updated.copy(isSynced = true))
            addActivityLog("UPDATE", "Weight", "${log.weight}kg")
        }
    }

    fun updateMedicationLog(log: MedicationLog) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = log.copy(isSynced = false)
            dao.updateMedicationLog(updated)
            supabase.upsertData("药品打卡记录", updated)
            dao.updateMedicationLog(updated.copy(isSynced = true))
            addActivityLog("UPDATE", "Medication", "Dosage: ${log.dosage}")
        }
    }

    fun updateExcretion(log: ExcretionLog) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = log.copy(isSynced = false)
            dao.updateExcretionLog(updated)
            supabase.upsertData("拉撒记录", updated)
            dao.updateExcretionLog(updated.copy(isSynced = true))
            addActivityLog("UPDATE", "Excretion", log.type.name)
        }
    }

    fun addSnack(name: String, unit: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val snack = Snack(name = name, unit = unit, isSynced = false)
            val id = dao.insertSnack(snack)
            val toSync = snack.copy(id = id)
            supabase.upsertData("零食库", toSync)
            dao.insertSnack(toSync.copy(isSynced = true))
            addActivityLog("ADD", "SnackType", name)
        }
    }

    fun addSnackLog(snackId: Long, amount: Float, targetDate: Calendar? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val snack = snacks.value.find { it.id == snackId }
            val log = SnackLog(
                snackId = snackId, 
                snackName = snack?.name ?: "未知零食",
                timestamp = generateTimestamp(targetDate), 
                amount = amount,
                isSynced = false
            )
            val id = dao.insertSnackLog(log)
            val toSync = log.copy(id = id)
            supabase.upsertData("零食打卡记录", toSync)
            dao.insertSnackLog(toSync.copy(isSynced = true))
            addActivityLog("ADD", "SnackLog", "${snack?.name ?: "Unknown"} $amount")
        }
    }

    fun deleteSnackLog(log: SnackLog) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteSnackLog(log)
            supabase.deleteData("零食打卡记录", log.id)
            addActivityLog("DELETE", "SnackLog", "Amount: ${log.amount}")
        }
    }

    fun updateSnackLog(log: SnackLog) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = log.copy(isSynced = false)
            dao.updateSnackLog(updated)
            supabase.upsertData("零食打卡记录", updated)
            dao.updateSnackLog(updated.copy(isSynced = true))
            addActivityLog("UPDATE", "SnackLog", "${log.amount}")
        }
    }

    private fun addActivityLog(action: String, type: String, details: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val log = ActivityLog(
                action = action,
                entityType = type,
                details = details
            )
            dao.insertActivityLog(log)
        }
    }

    private fun triggerRedirection(reason: String) {
        viewModelScope.launch {
            _redirectionEvent.emit(reason)
        }
    }

    suspend fun getTableNames(): List<String> {
        return withContext(Dispatchers.IO) {
            val supabaseTables = supabase.getAllSupabaseTables()
            if (supabaseTables.isNotEmpty()) return@withContext supabaseTables

            val cursor = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_metadata' AND name NOT LIKE 'sqlite_sequence' AND name NOT LIKE 'room_master_table'")
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0))
            }
            cursor.close()
            names
        }
    }

    suspend fun getLocalTablesWithLatestRow(): List<Pair<String, Map<String, String>>> = withContext(Dispatchers.IO) {
        val tables = mutableListOf<Pair<String, Map<String, String>>>()
        val dbSql = db.openHelper.readableDatabase
        val cursorNames = dbSql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_metadata' AND name NOT LIKE 'sqlite_sequence' AND name NOT LIKE 'room_master_table'")
        
        while (cursorNames.moveToNext()) {
            val tableName = cursorNames.getString(0)
            val translatedTableName = TranslationHelper.translateTable(tableName)
            val orderedCols = TranslationHelper.getColumnOrder(tableName)
            val dataMap = mutableMapOf<String, String>()
            
            // 查询该表最新一条数据
            val dataCursor = dbSql.query("SELECT * FROM $tableName ORDER BY rowid DESC LIMIT 1")
            if (dataCursor.moveToFirst()) {
                // 1. 先按指定顺序添加列
                orderedCols.forEach { colName ->
                    try {
                        val index = dataCursor.getColumnIndex(colName)
                        if (index != -1) {
                            val translatedColName = TranslationHelper.translateColumn(colName)
                            val value = dataCursor.getString(index) ?: "-"
                            dataMap[translatedColName] = TranslationHelper.translateValue(value)
                        }
                    } catch (e: Exception) {}
                }
                // 2. 添加不在排序列表中的其他列 (排除头像路径)
                for (i in 0 until dataCursor.columnCount) {
                    val colName = dataCursor.getColumnName(i)
                    if (!orderedCols.contains(colName) && colName != "avatarPath") {
                        val translatedColName = TranslationHelper.translateColumn(colName)
                        val value = try { dataCursor.getString(i) ?: "-" } catch(e: Exception) { "[BLOB/Error]" }
                        dataMap[translatedColName] = TranslationHelper.translateValue(value)
                    }
                }
            } else {
                // 如果没数据，至少拉下列名 (排除头像路径)
                for (i in 0 until dataCursor.columnCount) {
                    val colName = dataCursor.getColumnName(i)
                    if (colName != "avatarPath") {
                        dataMap[TranslationHelper.translateColumn(colName)] = "[无数据]"
                    }
                }
            }
            dataCursor.close()
            tables.add(translatedTableName to dataMap)
        }
        cursorNames.close()
        
        // 按照用户要求的顺序进行排序
        val displayOrder = listOf("宠物档案", "体重记录", "饮食饮水记录", "拉撒记录", "零食库", "零食打卡记录", "药品库", "药品打卡记录", "食具配置", "操作记录")
        tables.sortedBy { (name, _) -> 
            val index = displayOrder.indexOf(name)
            if (index != -1) index else displayOrder.size
        }
    }

    suspend fun getCloudTablesWithLatestRow(): List<Pair<String, Map<String, String>>> = withContext(Dispatchers.IO) {
        val tableNames = supabase.getAllSupabaseTables()
        val tables = tableNames.map { name ->
            val latestRow = supabase.fetchLatestRow(name)
            val dataMap = mutableMapOf<String, String>()
            
            latestRow?.forEach { (k, v) ->
                // 云端保持原样顺序，仅翻译 Key
                dataMap[TranslationHelper.translateColumn(k)] = TranslationHelper.translateValue(v.toString().removeSurrounding("\""))
            }

            if (dataMap.isEmpty()) {
                // 如果没拉到记录，占位
                dataMap["提示"] = "表为空或无法获取最新行"
            }
            TranslationHelper.translateTable(name) to dataMap
        }

        // 按照用户要求的顺序进行排序
        val displayOrder = listOf("宠物档案", "体重记录", "饮食饮水记录", "拉撒记录", "零食库", "零食打卡记录", "药品库", "药品打卡记录", "食具配置", "操作记录")
        tables.sortedBy { (name, _) -> 
            val index = displayOrder.indexOf(name)
            if (index != -1) index else displayOrder.size
        }
    }
}
