package com.ai.wangcai.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.wangcai.data.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.ai.wangcai.util.TranslationHelper
import java.util.Calendar
import kotlinx.serialization.json.*

class PetViewModel(application: Application) : AndroidViewModel(application) {
    private val db = PetDatabase.getDatabase(application)
    private val dao = db.petDao()
    private val supabase = SupabaseRepository()

    private val prefs = application.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    private val supabasePrefs = application.getSharedPreferences("supabase_prefs", android.content.Context.MODE_PRIVATE)

    private val _redirectionEvent = MutableSharedFlow<String>()
    val redirectionEvent = _redirectionEvent.asSharedFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _supabaseConfig = MutableStateFlow(
        SupabaseConfig(
            url = supabasePrefs?.getString("url", "") ?: "",
            publishableKey = supabasePrefs?.getString("publishable_key", "") ?: "",
            secretKey = supabasePrefs?.getString("secret_key", "") ?: "",
            jwksUrl = supabasePrefs?.getString("jwks_url", "") ?: ""
        )
    )
    val supabaseConfig = _supabaseConfig.asStateFlow()

    private val _pendingConfirmRequest = MutableStateFlow<PendingConfirmRequest?>(null)
    val pendingConfirmRequest = _pendingConfirmRequest.asStateFlow()

    // 互斥锁，防止并发操作导致的基础表（食具、药品、零食）重复
    private val baseDataMutex = Mutex()

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
    val syncLogs = dao.getRecentSyncLogs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val petProfile = dao.getPetProfile().stateIn(viewModelScope, SharingStarted.Lazily, null)
    val pendingTasksCount = dao.getPendingTasksCount().stateIn(viewModelScope, SharingStarted.Lazily, 0)

    data class PendingConfirmRequest(val message: String, val onResolve: (Boolean) -> Unit)

    init {
        val initialConfig = SupabaseConfig(
            url = supabasePrefs?.getString("url", "") ?: "",
            publishableKey = supabasePrefs?.getString("publishable_key", "") ?: "",
            secretKey = supabasePrefs?.getString("secret_key", "") ?: "",
            jwksUrl = supabasePrefs?.getString("jwks_url", "") ?: ""
        )
        supabase.updateConfig(initialConfig)
        viewModelScope.launch(Dispatchers.IO) { 
            cleanupDuplicateBowls() // 启动时清理由于旧 Bug 产生的重复食具
            processPendingTasks() 
        }
    }

    private suspend fun cleanupDuplicateBowls() {
        val allBowls = dao.getAllBowlsDirect()
        // 按类型分组，如果某组数量 > 1，则保留第一个，删除其余
        allBowls.groupBy { it.type }.forEach { (type, list) ->
            if (list.size > 1) {
                Log.w("SupabaseSync", "Found duplicate bowls for type $type: ${list.size}")
                val keep = list.first()
                list.drop(1).forEach { redundant ->
                    dao.deleteBowlById(redundant.id)
                    // 同时尝试从云端抹除冗余 ID
                    if (isCloudSyncEnabled()) {
                        supabase.deleteData("食具配置", redundant.id)
                    }
                }
            }
        }
    }

    private suspend fun processPendingTasks() {
        if (!isCloudSyncEnabled() || !supabaseConfig.value.isValid) return
        val tasks = dao.getAllPendingTasks()
        if (tasks.isEmpty()) return
        Log.d("SupabaseSync", "Processing ${tasks.size} pending tasks...")
        tasks.forEach { task ->
            val res = when(task.operation) {
                "DELETE" -> supabase.deleteData(task.tableName, task.recordId)
                else -> {
                    val item = getLocalRecordDirect(task.tableName, task.recordId)
                    if (item != null) supabase.upsertData(task.tableName, item) 
                    else SupabaseRepository.NetworkResult(true, 200, "Record no longer exists", "SKIP")
                }
            }
            if (res.isSuccess) {
                dao.removePendingTask(task.tableName, task.recordId, task.operation)
                if (task.operation != "DELETE") markRecordAsSynced(task.tableName, task.recordId)
                updateSyncLogToSuccess(task.tableName, task.recordId, res)
            }
        }
    }

    private suspend fun getLocalRecordDirect(tableName: String, id: String): Any? {
        return when(tableName) {
            "饮食饮水记录" -> dao.getConsumptionLogById(id); "体重记录" -> dao.getWeightLogById(id); "用药打卡记录" -> dao.getMedicationLogById(id); "拉撒记录" -> dao.getExcretionLogById(id); "零食打卡记录" -> dao.getSnackLogById(id); "食具配置" -> dao.getBowlById(id); "药品库" -> dao.getMedicationById(id); "零食库" -> dao.getSnackById(id); "宠物档案" -> dao.getPetProfile().first()?.takeIf { it.id == id }; else -> null
        }
    }

    private suspend fun updateSyncLogToSuccess(tableName: String, recordId: String, res: SupabaseRepository.NetworkResult) {
        val lastLog = dao.getLatestSyncLogForRecord(tableName, recordId)
        if (lastLog != null) {
            dao.updateSyncLog(lastLog.copy(operation = "${lastLog.operation} (已补传成功)", responseBody = res.responseBody, statusCode = res.statusCode, timestamp = System.currentTimeMillis(), recordTime = System.currentTimeMillis().toDbTime()))
        } else if (res.responseBody != "SKIP") {
            addSyncLog(tableName, "UPSERT (补传)", recordId, res)
        }
    }

    private fun isCloudSyncEnabled(): Boolean = prefs?.getBoolean("cloud_sync_enabled", true) ?: true

    private suspend fun markRecordAsSynced(tableName: String, id: String) {
        val record = getLocalRecordDirect(tableName, id) ?: return
        when(record) {
            is ConsumptionLog -> dao.updateConsumptionLog(record.copy(isSynced = true)); is WeightLog -> dao.updateWeightLog(record.copy(isSynced = true)); is MedicationLog -> dao.updateMedicationLog(record.copy(isSynced = true)); is ExcretionLog -> dao.updateExcretionLog(record.copy(isSynced = true)); is SnackLog -> dao.updateSnackLog(record.copy(isSynced = true)); is Medication -> dao.insertMedication(record.copy(isSynced = true)); is Snack -> dao.insertSnack(record.copy(isSynced = true)); is Bowl -> dao.insertBowl(record.copy(isSynced = true)); is PetProfile -> dao.insertPetProfile(record.copy(isSynced = true))
        }
    }

    private suspend fun handleSyncDecision(tableName: String, op: String, record: Any): String {
        val id = when(record) {
            is ConsumptionLog -> record.id; is WeightLog -> record.id; is MedicationLog -> record.id; is ExcretionLog -> record.id; is SnackLog -> record.id; is Medication -> record.id; is Snack -> record.id; is Bowl -> record.id; is PetProfile -> record.id; else -> ""
        }
        insertLocalRecord(tableName, record, false)
        if (!isCloudSyncEnabled() || !supabaseConfig.value.isValid) { markRecordAsSynced(tableName, id); return id }
        val res = supabase.upsertData(tableName, record)
        addSyncLog(tableName, op, id, res)
        if (res.isSuccess) markRecordAsSynced(tableName, id)
        else {
            val deferred = CompletableDeferred<Boolean>()
            _pendingConfirmRequest.value = PendingConfirmRequest("云端同步失败，是否加入待办？") { add -> _pendingConfirmRequest.value = null; deferred.complete(add) }
            if (deferred.await()) { dao.clearSuccessfulSyncLogs(); dao.insertPendingTask(PendingSyncTask(tableName = tableName, operation = op, recordId = id)) }
            else markRecordAsSynced(tableName, id)
        }
        return id
    }

    private suspend fun insertLocalRecord(tableName: String, record: Any, isSynced: Boolean) {
        when(record) {
            is ConsumptionLog -> dao.insertConsumptionLog(record.copy(isSynced = isSynced)); is WeightLog -> dao.insertWeightLog(record.copy(isSynced = isSynced)); is MedicationLog -> dao.insertMedicationLog(record.copy(isSynced = isSynced)); is ExcretionLog -> dao.insertExcretionLog(record.copy(isSynced = isSynced)); is SnackLog -> dao.insertSnackLog(record.copy(isSynced = isSynced)); is Medication -> dao.insertMedication(record.copy(isSynced = isSynced)); is Snack -> dao.insertSnack(record.copy(isSynced = isSynced)); is Bowl -> dao.insertBowl(record.copy(isSynced = isSynced)); is PetProfile -> dao.insertPetProfile(record.copy(isSynced = isSynced))
        }
    }

    private suspend fun handleUpdateDeleteSync(tableName: String, op: String, recordId: String, syncCall: suspend () -> SupabaseRepository.NetworkResult, onSuccess: suspend () -> Unit) {
        if (!isCloudSyncEnabled() || !supabaseConfig.value.isValid) { onSuccess(); return }
        val res = syncCall()
        addSyncLog(tableName, op, recordId, res)
        if (res.isSuccess) onSuccess() 
        else {
            val deferred = CompletableDeferred<Boolean>()
            _pendingConfirmRequest.value = PendingConfirmRequest("同步失败，是否加入待办？") { add -> _pendingConfirmRequest.value = null; deferred.complete(add) }
            if (deferred.await()) { dao.clearSuccessfulSyncLogs(); dao.insertPendingTask(PendingSyncTask(tableName = tableName, operation = op, recordId = recordId)) }
            else onSuccess()
        }
    }

    fun updateSupabaseConfig(config: SupabaseConfig) {
        _supabaseConfig.value = config
        supabasePrefs?.edit()?.apply { putString("url", config.url); putString("publishable_key", config.publishableKey); putString("secret_key", config.secretKey); putString("jwks_url", config.jwksUrl); apply() }
        supabase.updateConfig(config)
    }

    fun clearSupabaseConfig() { updateSupabaseConfig(SupabaseConfig()) }

    fun parseAndSaveConfig(rawText: String): Boolean {
        try {
            val lines = rawText.lines()
            var u = ""; var p = ""; var s = ""; var j = ""
            lines.forEach { line ->
                val t = line.trim()
                when { t.startsWith("SUPABASE_URL=") -> u = t.substringAfter("=").trim(); t.startsWith("SUPABASE_PUBLISHABLE_KEY=") -> p = t.substringAfter("=").trim(); t.startsWith("SUPABASE_SECRET_KEY=") -> s = t.substringAfter("=").trim(); t.startsWith("SUPABASE_JWKS_URL=") -> j = t.substringAfter("=").trim() }
            }
            if (u.isNotBlank() && p.isNotBlank() && s.isNotBlank()) { updateSupabaseConfig(SupabaseConfig(u, p, s, j)); return true }
        } catch (e: Exception) { Log.e("ConfigParse", "Failed: ${e.message}") }
        return false
    }

    fun syncFromCloud(syncAll: Boolean, targetDate: Calendar) {
        if (!supabaseConfig.value.isValid) { triggerRedirection("CONFIG_NEEDED"); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            try {
                val pendingTasks = dao.getAllPendingTasks()
                val filter = if (syncAll) null else {
                    val start = (targetDate.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                    val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                    "记录时间=gte.\"${start.timeInMillis.toDbTime()}\"&记录时间=lt.\"${end.timeInMillis.toDbTime()}\""
                }
                supabase.fetchTableData<PetProfile>("宠物档案").forEach { dao.insertPetProfile(it.copy(timestamp = it.recordTime.toTimestamp(), isSynced = true)) }
                supabase.fetchTableData<Medication>("药品库").forEach { dao.insertMedication(it.copy(isSynced = true)) }
                supabase.fetchTableData<Snack>("零食库").forEach { dao.insertSnack(it.copy(isSynced = true)) }
                supabase.fetchTableData<Bowl>("食具配置").forEach { dao.insertBowl(it.copy(type = if (it.name.contains("水") || it.name.contains("喝")) BowlType.WATER else BowlType.FOOD, isSynced = true)) }
                downloadTable<MedicationLog>("用药打卡记录", filter, pendingTasks) { it.copy(timestamp = it.recordTime.toTimestamp(), isSynced = true) }
                downloadTable<SnackLog>("零食打卡记录", filter, pendingTasks) { it.copy(timestamp = it.recordTime.toTimestamp(), isSynced = true) }
                downloadTable<ConsumptionLog>("饮食饮水记录", filter, pendingTasks) { it.copy(timestamp = it.recordTime.toTimestamp(), isSynced = true, type = when(it.action) { "增加" -> ConsumptionType.ADD; "清空" -> ConsumptionType.CLEAR; else -> ConsumptionType.EAT }, bowlType = if (it.method.contains("水") || it.method.contains("喝")) BowlType.WATER else BowlType.FOOD) }
                downloadTable<WeightLog>("体重记录", filter, pendingTasks) { it.copy(timestamp = it.recordTime.toTimestamp(), isSynced = true) }
                downloadTable<ExcretionLog>("拉撒记录", filter, pendingTasks) { it.copy(timestamp = it.recordTime.toTimestamp(), isSynced = true) }
                addActivityLog("SYNC", "Cloud", "Download complete")
            } catch (e: Exception) { Log.e("SyncError", "Download failed: ${e.message}") } finally { _isSyncing.value = false }
        }
    }

    private suspend inline fun <reified T : Any> downloadTable(tableName: String, filter: String?, pendingTasks: List<PendingSyncTask>, crossinline fix: (T) -> T) {
        val data = supabase.fetchTableData<T>(tableName, filter) 
        data.forEach { item ->
            val fixed = fix(item)
            val id = when(fixed) { is MedicationLog -> fixed.id; is SnackLog -> fixed.id; is ConsumptionLog -> fixed.id; is WeightLog -> fixed.id; is ExcretionLog -> fixed.id; else -> "" }
            if (pendingTasks.any { t -> t.tableName == tableName && t.recordId == id && t.operation == "DELETE" }) return@forEach
            when(fixed) { is MedicationLog -> dao.insertMedicationLog(fixed); is SnackLog -> dao.insertSnackLog(fixed); is ConsumptionLog -> dao.insertConsumptionLog(fixed); is WeightLog -> dao.insertWeightLog(fixed); is ExcretionLog -> dao.insertExcretionLog(fixed) }
        }
    }

    fun syncToCloud(syncAll: Boolean, targetDate: Calendar) {
        if (!supabaseConfig.value.isValid) { triggerRedirection("CONFIG_NEEDED"); return }
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            try {
                processPendingTasks()
                val startTs = if (syncAll) 0L else (targetDate.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                val endTs = if (syncAll) Long.MAX_VALUE else (Calendar.getInstance().apply { timeInMillis = startTs }).apply { add(Calendar.MONTH, 1) }.timeInMillis
                petProfile.value?.let { profile -> val res = supabase.upsertData("宠物档案", profile); addSyncLog("宠物档案", "UPSERT", profile.id, res); if (res.isSuccess) dao.insertPetProfile(profile.copy(isSynced = true)) }
                allBowls.value.forEach { val res = supabase.upsertData("食具配置", it); addSyncLog("食具配置", "UPSERT", it.id, res); if (res.isSuccess) dao.insertBowl(it.copy(isSynced = true)) }
                medications.value.forEach { val res = supabase.upsertData("药品库", it); addSyncLog("药品库", "UPSERT", it.id, res); if (res.isSuccess) dao.insertMedication(it.copy(isSynced = true)) }
                snacks.value.forEach { val res = supabase.upsertData("零食库", it); addSyncLog("零食库", "UPSERT", it.id, res); if (res.isSuccess) dao.insertSnack(it.copy(isSynced = true)) }
                uploadTable(medicationLogs.value, "用药打卡记录", startTs, endTs) { dao.updateMedicationLogs(listOf(it.copy(isSynced = true))) }
                uploadTable(snackLogs.value, "零食打卡记录", startTs, endTs) { dao.updateSnackLogs(listOf(it.copy(isSynced = true))) }
                uploadTable(foodLogs.value + waterLogs.value, "饮食饮水记录", startTs, endTs) { dao.updateConsumptionLogs(listOf(it.copy(isSynced = true))) }
                uploadTable(weightLogs.value, "体重记录", startTs, endTs) { dao.updateWeightLogs(listOf(it.copy(isSynced = true))) }
                uploadTable(excretionLogs.value, "拉撒记录", startTs, endTs) { dao.updateExcretionLogs(listOf(it.copy(isSynced = true))) }
                addActivityLog("SYNC", "Cloud", "Upload complete")
            } catch (e: Exception) { Log.e("SyncError", "Upload failed: ${e.message}") } finally { _isSyncing.value = false }
        }
    }

    private suspend fun <T> uploadTable(localList: List<T>, tableName: String, start: Long, end: Long, onSuccess: suspend (T) -> Unit) {
        localList.forEach { item ->
            val ts = when(item) { is MedicationLog -> item.timestamp; is SnackLog -> item.timestamp; is ConsumptionLog -> item.timestamp; is WeightLog -> item.timestamp; is ExcretionLog -> item.timestamp; else -> 0L }
            val id = when(item) { is MedicationLog -> item.id; is SnackLog -> item.id; is ConsumptionLog -> item.id; is WeightLog -> item.id; is ExcretionLog -> item.id; else -> "" }
            val synced = when(item) { is MedicationLog -> item.isSynced; is SnackLog -> item.isSynced; is ConsumptionLog -> item.isSynced; is WeightLog -> item.isSynced; is ExcretionLog -> item.isSynced; else -> true }
            if ((ts in start until end) || !synced) {
                val res = supabase.upsertData(tableName, item as Any)
                addSyncLog(tableName, "UPSERT", id, res)
                if (res.isSuccess) onSuccess(item)
            }
        }
    }

    fun updatePetProfile(nickname: String, breed: String?, birthday: String?, avatarPath: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = petProfile.value
            val profile = PetProfile(id = current?.id ?: java.util.UUID.randomUUID().toString(), nickname = nickname, breed = breed, birthday = birthday, avatarPath = avatarPath, isSynced = false)
            handleUpdateDeleteSync("宠物档案", "UPDATE", profile.id, { supabase.upsertData("宠物档案", profile) }) { dao.insertPetProfile(profile.copy(isSynced = true)) }
        }
    }

    fun updateBowl(name: String, tareWeight: Float, type: BowlType, currentId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            baseDataMutex.withLock {
                // 优先使用传入的 ID，其次从数据库查找，最后才生成新 UUID
                val existingId = currentId ?: dao.getBowlByType(type).first()?.id
                
                val bowl = Bowl(
                    id = existingId ?: java.util.UUID.randomUUID().toString(),
                    name = name,
                    tareWeight = tareWeight,
                    type = type,
                    isSynced = false
                )
                val op = if (existingId != null) "UPDATE" else "ADD"
                handleUpdateDeleteSync("食具配置", op, bowl.id, { supabase.upsertData("食具配置", bowl) }) {
                    dao.insertBowl(bowl.copy(isSynced = true)) 
                }
            }
        }
    }

    fun recordConsumption(grossWeight: Float, type: BowlType, isFromEmpty: Boolean, newTareWeight: Float? = null, targetDate: Calendar? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            baseDataMutex.withLock {
                // 实时查找碗，确保计算绝对准确
                val currentBowl = dao.getBowlByType(type).first()
                val tare = newTareWeight ?: currentBowl?.tareWeight ?: 0f
                
                // 如果碗不存在或皮重更新
                if (currentBowl == null || (newTareWeight != null && newTareWeight != currentBowl.tareWeight)) {
                    val bowl = Bowl(
                        id = currentBowl?.id ?: java.util.UUID.randomUUID().toString(),
                        name = currentBowl?.name ?: (if (type == BowlType.FOOD) "食盆" else "水盆"),
                        tareWeight = tare,
                        type = type,
                        isSynced = false
                    )
                    val op = if (currentBowl != null) "UPDATE" else "ADD"
                    handleUpdateDeleteSync("食具配置", op, bowl.id, { supabase.upsertData("食具配置", bowl) }) {
                        dao.insertBowl(bowl.copy(isSynced = true))
                    }
                }
                
                // 获取最新日志计算差值
                val logs = if (type == BowlType.FOOD) dao.getConsumptionLogs(BowlType.FOOD).first() else dao.getConsumptionLogs(BowlType.WATER).first()
                val lastLog = logs.firstOrNull()
                val lastGross = if ((lastLog?.grossWeight ?: 0f) > 0f) lastLog!!.grossWeight else tare
                val ts = generateTimestamp(targetDate)
                val diff = if (isFromEmpty) grossWeight - tare else grossWeight - lastGross
                val roundedAmount = kotlin.math.round(diff * 10f) / 10f
                val roundedGross = kotlin.math.round(grossWeight * 10f) / 10f
                
                if (roundedAmount != 0f) {
                    val log = ConsumptionLog(
                        timestamp = ts, 
                        amount = roundedAmount, 
                        grossWeight = roundedGross, 
                        type = if (roundedAmount > 0) ConsumptionType.ADD else ConsumptionType.EAT, 
                        bowlType = type, 
                        action = if (roundedAmount > 0) "增加" else "减少", 
                        method = if (roundedAmount > 0) (if(type==BowlType.FOOD) "添加饮食" else "添加饮水") else (if(type==BowlType.FOOD) "吃吃" else "喝喝"), 
                        isSynced = false
                    )
                    handleSyncDecision("饮食饮水记录", "ADD", log)
                }
            }
        }
    }

    private fun generateTimestamp(targetDate: Calendar?): Long = targetDate?.timeInMillis ?: System.currentTimeMillis()

    fun deleteConsumption(log: ConsumptionLog) { viewModelScope.launch(Dispatchers.IO) { dao.deleteConsumptionLog(log); handleUpdateDeleteSync("饮食饮水记录", "DELETE", log.id, { supabase.deleteData("饮食饮水记录", log.id) }) {} } }
    fun deleteWeight(log: WeightLog) { viewModelScope.launch(Dispatchers.IO) { dao.deleteWeightLog(log); handleUpdateDeleteSync("体重记录", "DELETE", log.id, { supabase.deleteData("体重记录", log.id) }) {} } }
    fun deleteMedicationLog(log: MedicationLog) { viewModelScope.launch(Dispatchers.IO) { dao.deleteMedicationLog(log); handleUpdateDeleteSync("用药打卡记录", "DELETE", log.id, { supabase.deleteData("用药打卡记录", log.id) }) {} } }
    fun deleteExcretion(log: ExcretionLog) { viewModelScope.launch(Dispatchers.IO) { dao.deleteExcretionLog(log); handleUpdateDeleteSync("拉撒记录", "DELETE", log.id, { supabase.deleteData("拉撒记录", log.id) }) {} } }
    fun deleteSnackLog(log: SnackLog) { viewModelScope.launch(Dispatchers.IO) { dao.deleteSnackLog(log); handleUpdateDeleteSync("零食打卡记录", "DELETE", log.id, { supabase.deleteData("零食打卡记录", log.id) }) {} } }

    fun addWeightLog(weight: Float, note: String? = null, targetDate: Calendar? = null) { viewModelScope.launch(Dispatchers.IO) { handleSyncDecision("体重记录", "ADD", WeightLog(timestamp = generateTimestamp(targetDate), weight = weight, note = note, isSynced = false)) } }
    
    fun addMedication(name: String, unit: String) { 
        viewModelScope.launch(Dispatchers.IO) { 
            baseDataMutex.withLock {
                val existing = dao.getMedicationByName(name)
                val med = Medication(id = existing?.id ?: java.util.UUID.randomUUID().toString(), name = name, unit = unit, isSynced = false)
                val op = if (existing != null) "UPDATE" else "ADD"
                handleSyncDecision("药品库", op, med) 
            }
        } 
    }
    
    fun addMedicationLog(medicationId: String, dosage: Float, targetDate: Calendar? = null) { 
        viewModelScope.launch(Dispatchers.IO) { 
            val med = dao.getMedicationById(medicationId)
            handleSyncDecision("用药打卡记录", "ADD", MedicationLog(medicationId = medicationId, medicationName = med?.name ?: "未知药品", timestamp = generateTimestamp(targetDate), dosage = dosage, isSynced = false)) 
        } 
    }
    
    fun addExcretionLog(type: ExcretionType, shape: String? = null, targetDate: Calendar? = null) { viewModelScope.launch(Dispatchers.IO) { handleSyncDecision("拉撒记录", "ADD", ExcretionLog(timestamp = generateTimestamp(targetDate), type = type, shape = shape, isSynced = false)) } }
    
    fun addSnack(name: String, unit: String) { 
        viewModelScope.launch(Dispatchers.IO) { 
            baseDataMutex.withLock {
                val existing = dao.getSnackByName(name)
                val snack = Snack(id = existing?.id ?: java.util.UUID.randomUUID().toString(), name = name, unit = unit, isSynced = false)
                val op = if (existing != null) "UPDATE" else "ADD"
                handleSyncDecision("零食库", op, snack) 
            }
        } 
    }
    
    fun addSnackLog(snackId: String, amount: Float, targetDate: Calendar? = null) { 
        viewModelScope.launch(Dispatchers.IO) { 
            val snack = dao.getSnackById(snackId)
            handleSyncDecision("零食打卡记录", "ADD", SnackLog(snackId = snackId, snackName = snack?.name ?: "未知零食", timestamp = generateTimestamp(targetDate), amount = amount, isSynced = false)) 
        } 
    }

    fun updateConsumption(log: ConsumptionLog) { viewModelScope.launch(Dispatchers.IO) { dao.updateConsumptionLog(log.copy(isSynced = false)); handleUpdateDeleteSync("饮食饮水记录", "UPDATE", log.id, { supabase.upsertData("饮食饮水记录", log) }) { dao.updateConsumptionLog(log.copy(isSynced = true)) } } }
    fun updateWeightLog(log: WeightLog) { viewModelScope.launch(Dispatchers.IO) { dao.updateWeightLog(log.copy(isSynced = false)); handleUpdateDeleteSync("体重记录", "UPDATE", log.id, { supabase.upsertData("体重记录", log) }) { dao.updateWeightLog(log.copy(isSynced = true)) } } }
    fun updateMedicationLog(log: MedicationLog) { viewModelScope.launch(Dispatchers.IO) { dao.updateMedicationLog(log.copy(isSynced = false)); handleUpdateDeleteSync("用药打卡记录", "UPDATE", log.id, { supabase.upsertData("用药打卡记录", log) }) { dao.updateMedicationLog(log.copy(isSynced = true)) } } }
    fun updateExcretion(log: ExcretionLog) { viewModelScope.launch(Dispatchers.IO) { dao.updateExcretionLog(log.copy(isSynced = false)); handleUpdateDeleteSync("拉撒记录", "UPDATE", log.id, { supabase.upsertData("拉撒记录", log) }) { dao.updateExcretionLog(log.copy(isSynced = true)) } } }
    fun updateSnackLog(log: SnackLog) { viewModelScope.launch(Dispatchers.IO) { dao.updateSnackLog(log.copy(isSynced = false)); handleUpdateDeleteSync("零食打卡记录", "UPDATE", log.id, { supabase.upsertData("零食打卡记录", log) }) { dao.updateSnackLog(log.copy(isSynced = true)) } } }

    private fun addActivityLog(action: String, type: String, details: String) { viewModelScope.launch(Dispatchers.IO) { dao.insertActivityLog(ActivityLog(action = action, entityType = type, details = details)) } }
    private fun addSyncLog(tableName: String, op: String, recordId: String, result: SupabaseRepository.NetworkResult) { viewModelScope.launch(Dispatchers.IO) { dao.insertSyncLog(SyncLog(tableName = tableName, recordId = recordId, operation = op, requestBody = result.requestBody, responseBody = result.responseBody, statusCode = result.statusCode)) } }
    fun triggerConfigCheck() { if (!supabaseConfig.value.isValid) triggerRedirection("CONFIG_NEEDED") }
    private fun triggerRedirection(reason: String) { viewModelScope.launch { _redirectionEvent.emit(reason) } }
    suspend fun getTableNames(): List<String> = withContext(Dispatchers.IO) { val st = supabase.getAllSupabaseTables(); if (st.isNotEmpty()) st else { val c = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_metadata' AND name NOT LIKE 'sqlite_sequence' AND name NOT LIKE 'room_master_table'"); val names = mutableListOf<String>(); while (c.moveToNext()) names.add(c.getString(0)); c.close(); names } }
    suspend fun getLocalTablesWithLatestRow(): List<Pair<String, Map<String, String>>> = withContext(Dispatchers.IO) { val tables = mutableListOf<Pair<String, Map<String, String>>>(); val dbSql = db.openHelper.readableDatabase; val cn = dbSql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_metadata' AND name NOT LIKE 'sqlite_sequence' AND name NOT LIKE 'room_master_table'"); while (cn.moveToNext()) { val tn = cn.getString(0); val ttn = TranslationHelper.translateTable(tn); val ocs = TranslationHelper.getColumnOrder(tn); val dm = mutableMapOf<String, String>(); val dc = dbSql.query("SELECT * FROM $tn ORDER BY rowid DESC LIMIT 1"); if (dc.moveToFirst()) { ocs.forEach { cn -> val idx = dc.getColumnIndex(cn); if (idx != -1) dm[TranslationHelper.translateColumn(cn)] = TranslationHelper.translateValue(dc.getString(idx) ?: "-") }; for (i in 0 until dc.columnCount) { val cn = dc.getColumnName(i); if (!ocs.contains(cn) && cn != "avatarPath") dm[TranslationHelper.translateColumn(cn)] = TranslationHelper.translateValue(try { dc.getString(i) ?: "-" } catch(e: Exception) { "-" }) } } else { for (i in 0 until dc.columnCount) { val cn = dc.getColumnName(i); if (cn != "avatarPath") dm[TranslationHelper.translateColumn(cn)] = "[无数据]" } }; dc.close(); tables.add(ttn to dm) }; cn.close(); val dor = listOf("宠物档案", "体重记录", "饮食饮水记录", "拉撒记录", "零食库", "零食打卡记录", "药品库", "药品打卡记录", "食具配置", "操作记录"); tables.sortedBy { (n, _) -> val idx = dor.indexOf(n); if (idx != -1) idx else dor.size } }
    suspend fun getCloudTablesWithLatestRow(): List<Pair<String, Map<String, String>>> = withContext(Dispatchers.IO) { val tns = supabase.getAllSupabaseTables(); val tables = tns.map { n -> val lr = supabase.fetchLatestRow(n); val dm = mutableMapOf<String, String>(); lr?.forEach { (k, v) -> dm[TranslationHelper.translateColumn(k)] = TranslationHelper.translateValue(v.toString().removeSurrounding("\"")) }; if (dm.isEmpty()) dm["提示"] = "表为空所无法获取最新行"; TranslationHelper.translateTable(n) to dm }; val dor = listOf("宠物档案", "体重记录", "饮食饮水记录", "拉撒记录", "零食库", "零食打卡记录", "药品库", "药品打卡记录", "食具配置", "操作记录"); tables.sortedBy { (n, _) -> val idx = dor.indexOf(n); if (idx != -1) idx else dor.size } }
    suspend fun getAllDataSnapshot(): DataSnapshot = withContext(Dispatchers.IO) { DataSnapshot(dao.getAllBowls().first(), dao.getConsumptionLogs(BowlType.FOOD).first() + dao.getConsumptionLogs(BowlType.WATER).first(), dao.getWeightLogs().first(), dao.getAllMedications().first(), dao.getMedicationLogs().first(), dao.getAllExcretionLogs().first(), dao.getAllSnacks().first(), dao.getSnackLogs().first(), dao.getPetProfile().first()) }
}
