package com.ai.wangcai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.ai.wangcai.util.TranslationHelper
import com.ai.wangcai.viewmodel.PetViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

// 1. 通用动态 Supabase 客户端
class DynamicSupabaseClient(
    private val supabaseUrl: String,
    private val secretKey: String
) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    // 从 OpenAPI 文档中提取所有表名和定义
    suspend fun getTablesAndSchemas(): Pair<List<String>, Map<String, List<String>>> = withContext(Dispatchers.IO) {
        val responseText = client.get("$supabaseUrl/rest/v1/") {
            header("apikey", secretKey)
            header("Authorization", "Bearer $secretKey")
        }.bodyAsText()

        val rootObj = Json.parseToJsonElement(responseText).jsonObject
        val tables = mutableSetOf<String>()
        val schemaMap = mutableMapOf<String, List<String>>()

        rootObj["definitions"]?.jsonObject?.forEach { (tableName, defElement) ->
            tables.add(tableName)
            val properties = defElement.jsonObject["properties"]?.jsonObject
            if (properties != null) {
                schemaMap[tableName] = properties.keys.toList()
            }
        }

        rootObj["paths"]?.jsonObject?.keys?.forEach { path ->
            val clean = path.trim('/')
            if (clean.isNotEmpty() && !clean.startsWith("rpc/")) {
                tables.add(clean)
            }
        }

        Pair(tables.toList().sorted(), schemaMap)
    }

    // 查询表数据
    suspend fun getTableData(tableName: String): List<JsonObject> = withContext(Dispatchers.IO) {
        val responseText = client.get("$supabaseUrl/rest/v1/$tableName") {
            parameter("select", "*")
            header("apikey", secretKey)
            header("Authorization", "Bearer $secretKey")
        }.bodyAsText()

        val jsonElement = Json.parseToJsonElement(responseText)
        if (jsonElement is JsonArray) {
            jsonElement.jsonArray.filterIsInstance<JsonObject>()
        } else {
            emptyList()
        }
    }

    // 新增记录
    suspend fun insertRow(tableName: String, data: JsonObject): Boolean = withContext(Dispatchers.IO) {
        val response = client.post("$supabaseUrl/rest/v1/$tableName") {
            header("apikey", secretKey)
            header("Authorization", "Bearer $secretKey")
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody(data.toString())
        }
        response.status.value in 200..299
    }

    // 修改记录
    suspend fun updateRow(tableName: String, primaryKey: String, primaryValue: String, data: JsonObject): Boolean = withContext(Dispatchers.IO) {
        val response = client.patch("$supabaseUrl/rest/v1/$tableName") {
            parameter(primaryKey, "eq.$primaryValue")
            header("apikey", secretKey)
            header("Authorization", "Bearer $secretKey")
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody(data.toString())
        }
        response.status.value in 200..299
    }

    // 删除记录
    suspend fun deleteRow(tableName: String, primaryKey: String, value: String): Boolean = withContext(Dispatchers.IO) {
        val response = client.delete("$supabaseUrl/rest/v1/$tableName") {
            parameter(primaryKey, "eq.$value")
            header("apikey", secretKey)
            header("Authorization", "Bearer $secretKey")
        }
        response.status.value in 200..299
    }
}

// 2. 主页面 Composable
@Composable
fun SupabaseManagerScreen(viewModel: PetViewModel) {
    val config by viewModel.supabaseConfig.collectAsState()
    val client = remember(config) { 
        DynamicSupabaseClient(
            supabaseUrl = config.url,
            secretKey = config.secretKey
        ) 
    }
    val scope = rememberCoroutineScope()

    // 数据表与模式状态
    var tableList by remember { mutableStateOf<List<String>>(emptyList()) }
    var schemaMap by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var selectedTable by remember { mutableStateOf<String?>(null) }
    var tableRows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("等待操作...") }

    // 弹窗状态
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<JsonObject?>(null) }

    // 当前表的动态列推导
    val currentColumns by remember(selectedTable, tableRows, schemaMap) {
        derivedStateOf {
            val fromRows = linkedSetOf<String>()
            tableRows.forEach { row -> fromRows.addAll(row.keys) }
            if (fromRows.isNotEmpty()) {
                fromRows.toList()
            } else {
                schemaMap[selectedTable] ?: emptyList()
            }
        }
    }

    // 确定主键名称
    fun getPrimaryKey(row: JsonObject? = tableRows.firstOrNull()): String {
        val candidateKeys = listOf("编号", "id", "ID", "_id")
        if (row != null) {
            return candidateKeys.firstOrNull { row.containsKey(it) } ?: row.keys.firstOrNull() ?: "id"
        }
        return candidateKeys.firstOrNull { currentColumns.contains(it) } ?: currentColumns.firstOrNull() ?: "id"
    }

    // 切换并加载表数据
    fun loadTableData(tableName: String) {
        selectedTable = tableName
        scope.launch {
            isLoading = true
            try {
                tableRows = client.getTableData(tableName)
                statusMessage = "表 [$tableName] 数据已加载 (共 ${tableRows.size} 条)"
            } catch (e: Exception) {
                tableRows = emptyList()
                statusMessage = "加载 [$tableName] 失败: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    // 刷新全部表名
    fun refreshTables() {
        scope.launch {
            isLoading = true
            statusMessage = "正在拉取云端数据表清单..."
            try {
                val (tables, schemas) = client.getTablesAndSchemas()
                
                // 按照重要程度对表名进行排序
                val priorityOrder = listOf("宠物档案", "饮食饮水记录", "体重记录", "用药打卡记录", "零食打卡记录", "拉撒记录", "药品库", "零食库", "食具配置", "操作记录")
                tableList = tables.sortedBy { tableName ->
                    val index = priorityOrder.indexOf(tableName)
                    if (index != -1) index else priorityOrder.size
                }
                
                schemaMap = schemas
                statusMessage = "成功获取 ${tables.size} 张数据表"
                if (tables.isNotEmpty() && selectedTable == null) {
                    loadTableData(tables.first())
                } else if (selectedTable != null) {
                    loadTableData(selectedTable!!)
                }
            } catch (e: Exception) {
                statusMessage = "获取数据表失败: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    // 初始自动加载表清单
    LaunchedEffect(Unit) {
        refreshTables()
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTable != null && currentColumns.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = DeepGreen,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Add, contentDescription = "新增") },
                    text = { Text("新增记录") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // 顶部操作栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "云端同步实验室",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DeepGreen
                )

                IconButton(onClick = { refreshTables() }, enabled = !isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新所有表", tint = DeepGreen)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 表切换横向选择栏
            if (tableList.isNotEmpty()) {
                Text("选择数据表:", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tableList) { tableName ->
                        val isSelected = tableName == selectedTable
                        FilterChip(
                            selected = isSelected,
                            onClick = { loadTableData(tableName) },
                            label = { Text(TranslationHelper.translateTable(tableName)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DeepGreen.copy(alpha = 0.2f),
                                selectedLabelColor = DeepGreen
                            )
                        )
                    }
                }
            }

            // 状态提示与进度条
            if (statusMessage.isNotEmpty()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = DeepGreen
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 数据记录列表
            if (tableRows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTable == null) "请点击上方获取并选择表" else "表 [${TranslationHelper.translateTable(selectedTable!!)}] 暂无记录\n点击右下角悬浮按钮新增",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                // 新增：表头 Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(DeepGreen.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    currentColumns.forEach { colName ->
                        Text(
                            text = TranslationHelper.translateColumn(colName),
                            modifier = Modifier.width(100.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = DeepGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(96.dp)) // 为操作按钮留空
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 80.dp) // 预留悬浮按钮空间
                ) {
                    items(tableRows) { row ->
                        DynamicRecordCard(
                            row = row,
                            columns = currentColumns,
                            onEdit = {
                                editingRow = row
                                showEditDialog = true
                            },
                            onDelete = {
                                val targetTable = selectedTable ?: return@DynamicRecordCard
                                val pk = getPrimaryKey(row)
                                val pkValue = row[pk]?.jsonPrimitive?.content ?: return@DynamicRecordCard

                                scope.launch {
                                    isLoading = true
                                    try {
                                        val success = client.deleteRow(targetTable, pk, pkValue)
                                        if (success) {
                                            tableRows = client.getTableData(targetTable)
                                            statusMessage = "删除成功"
                                        } else {
                                            statusMessage = "删除失败"
                                        }
                                    } catch (e: Exception) {
                                        statusMessage = "删除出错: ${e.localizedMessage}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 动态新增弹窗
    if (showAddDialog && selectedTable != null) {
        DynamicEditDialog(
            title = "新增到 [${TranslationHelper.translateTable(selectedTable!!)}]",
            columns = currentColumns,
            primaryKey = getPrimaryKey(),
            initialData = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { formData ->
                val targetTable = selectedTable ?: return@DynamicEditDialog
                scope.launch {
                    isLoading = true
                    try {
                        val payload = formData.toJsonObject()
                        val success = client.insertRow(targetTable, payload)
                        if (success) {
                            statusMessage = "新增成功！"
                            tableRows = client.getTableData(targetTable)
                        } else {
                            statusMessage = "新增失败，请检查字段格式"
                        }
                    } catch (e: Exception) {
                        statusMessage = "新增异常: ${e.localizedMessage}"
                    } finally {
                        isLoading = false
                    }
                }
                showAddDialog = false
            }
        )
    }

    // 动态修改弹窗
    if (showEditDialog && editingRow != null && selectedTable != null) {
        DynamicEditDialog(
            title = "修改 [${TranslationHelper.translateTable(selectedTable!!)}] 记录",
            columns = currentColumns,
            primaryKey = getPrimaryKey(editingRow),
            initialData = editingRow,
            onDismiss = {
                showEditDialog = false
                editingRow = null
            },
            onConfirm = { formData ->
                val targetTable = selectedTable ?: return@DynamicEditDialog
                val original = editingRow ?: return@DynamicEditDialog
                val pk = getPrimaryKey(original)
                val pkValue = original[pk]?.jsonPrimitive?.content ?: return@DynamicEditDialog

                scope.launch {
                    isLoading = true
                    try {
                        val updatePayload = formData.filterKeys { it != pk }.toJsonObject()
                        val success = client.updateRow(targetTable, pk, pkValue, updatePayload)
                        if (success) {
                            statusMessage = "修改成功！"
                            tableRows = client.getTableData(targetTable)
                        } else {
                            statusMessage = "修改失败"
                        }
                    } catch (e: Exception) {
                        statusMessage = "修改异常: ${e.localizedMessage}"
                    } finally {
                        isLoading = false
                    }
                }
                showEditDialog = false
                editingRow = null
            }
        )
    }
}

// 3. 动态数据行卡片
@Composable
private fun DynamicRecordCard(
    row: JsonObject,
    columns: List<String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(0.dp)) { // 边距由内部元素控制
            if (isLandscape) {
                // --- 横屏：表格化卡片 (参照图2) ---
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val minRowWidth = maxWidth

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        // 模拟表头 (每个卡片自带，确保对齐)
                        Row(
                            modifier = Modifier
                                .widthIn(min = minRowWidth)
                                .background(DeepGreen)
                                .padding(vertical = 6.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            columns.forEach { colName ->
                                Text(
                                    text = TranslationHelper.translateColumn(colName),
                                    modifier = Modifier.width(120.dp), // 统一固定宽度
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        // 数据行
                        Row(
                            modifier = Modifier
                                .widthIn(min = minRowWidth)
                                .background(Color.White)
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            columns.forEach { colName ->
                                val value = row[colName]?.jsonPrimitive?.content ?: "-"
                                Text(
                                    text = value,
                                    modifier = Modifier.width(120.dp), // 统一固定宽度
                                    fontSize = 11.sp,
                                    color = Color.DarkGray,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // --- 竖屏：一体化卡片 (左侧色块相连) ---
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, DeepGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                ) {
                    columns.forEachIndexed { _, colName ->
                        val value = row[colName]?.jsonPrimitive?.content ?: "-"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(DeepGreen)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = TranslationHelper.translateColumn(colName),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(2.4f)
                                    .fillMaxHeight()
                                    .background(Color.White)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = value,
                                    fontSize = 11.sp,
                                    color = Color.DarkGray,
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    )
                            }
                        }
                    }
                }
            }

            // 操作按钮栏 (统一样式)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF9F9F9)) // 按钮区域浅灰背景
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp), tint = DeepGreen)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("修改", color = DeepGreen, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        }
    }
}

// 4. 动态表单弹窗
@Composable
private fun DynamicEditDialog(
    title: String,
    columns: List<String>,
    primaryKey: String,
    initialData: JsonObject? = null,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    val formData = remember {
        mutableStateMapOf<String, String>().apply {
            columns.forEach { col ->
                this[col] = initialData?.get(col)?.jsonPrimitive?.content ?: ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(columns) { colName ->
                    val isPk = (colName == primaryKey)
                    val isEditMode = (initialData != null)

                    SelectAllOutlinedTextField(
                        value = formData[colName] ?: "",
                        onValueChange = { formData[colName] = it },
                        label = { Text(if (isPk) "${TranslationHelper.translateColumn(colName)} (主键)" else TranslationHelper.translateColumn(colName)) },
                        enabled = !(isEditMode && isPk),
                        placeholder = {
                            if (isPk && !isEditMode) {
                                Text("自增主键可留空")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(formData.toMap()) },
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
            ) {
                Text("提交")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// 5. 辅助方法：Map 转 JsonObject 自动做类型适配
private fun Map<String, String>.toJsonObject(): JsonObject {
    val map = mutableMapOf<String, JsonElement>()
    this.forEach { (key, value) ->
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) {
            val longVal = trimmed.toLongOrNull()
            val doubleVal = trimmed.toDoubleOrNull()
            val boolVal = trimmed.toBooleanStrictOrNull()
            when {
                longVal != null -> map[key] = JsonPrimitive(longVal)
                doubleVal != null -> map[key] = JsonPrimitive(doubleVal)
                boolVal != null -> map[key] = JsonPrimitive(boolVal)
                else -> map[key] = JsonPrimitive(trimmed)
            }
        }
    }
    return JsonObject(map)
}