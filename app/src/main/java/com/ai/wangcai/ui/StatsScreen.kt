package com.ai.wangcai.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.wangcai.R
import com.ai.wangcai.data.*
import com.ai.wangcai.viewmodel.PetViewModel
import kotlinx.coroutines.launch
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration

data class ChartData(val data: List<Double>, val labels: List<String>)

@Composable
fun StatsScreen(viewModel: PetViewModel) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val excretionLogs by viewModel.excretionLogs.collectAsState()
    val foodLogs by viewModel.foodLogs.collectAsState()
    val waterLogs by viewModel.waterLogs.collectAsState()
    val weightLogsState by viewModel.weightLogs.collectAsState()
    val medications by viewModel.medications.collectAsState()
    val medicationLogs by viewModel.medicationLogs.collectAsState()
    val snacks by viewModel.snacks.collectAsState()
    val snackLogs by viewModel.snackLogs.collectAsState()
    val activityLogs by viewModel.activityLogs.collectAsState()

    var showTableDialog by rememberSaveable { mutableStateOf(false) }
    var dialogTitle by rememberSaveable { mutableStateOf("") }
    var tableDataList by remember { mutableStateOf<List<Pair<String, Map<String, String>>>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // 修复：处理横竖屏切换导致的数据丢失。如果弹窗开启但数据为空，则自动重拉数据。
    LaunchedEffect(showTableDialog) {
        if (showTableDialog && tableDataList.isEmpty()) {
            if (dialogTitle == "本地数据") {
                tableDataList = viewModel.getLocalTablesWithLatestRow()
            } else if (dialogTitle == "云端数据") {
                tableDataList = viewModel.getCloudTablesWithLatestRow()
            }
        }
    }

    if (showTableDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showTableDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false // 关键：禁用系统默认宽度限制
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.95f else 0.92f)
                    .heightIn(max = if (isLandscape) 350.dp else 750.dp) // 增加竖屏最大高度，从 600 提升到 750
                    .padding(vertical = 12.dp), // 稍微减小外部留白
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    // 顶部栏：标题（缩小）+ 右上角关闭按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dialogTitle,
                            fontSize = 14.sp, // 大约是 headlineSmall 的 0.6 倍
                            fontWeight = FontWeight.ExtraBold,
                            color = DeepGreen
                        )
                        
                        IconButton(
                            onClick = { showTableDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // 内容区
                    Box(modifier = Modifier.weight(1f)) {
                        if (tableDataList.isEmpty()) {
                            // 加载中占位
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = DeepGreen)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(tableDataList.size) { index ->
                                    val (tableName, rowData) = tableDataList[index]
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // 表名作为一个小副标题
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(3.dp, 16.dp).background(DeepGreen, RoundedCornerShape(2.dp)))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = tableName,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = DeepGreen,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        if (isLandscape) {
                                            // --- 横屏：表格布局 ---
                                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                                val minRowWidth = maxWidth
                                                val columns = rowData.keys.toList()
                                                val values = rowData.values.toList()

                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(1.dp, DeepGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                        .horizontalScroll(rememberScrollState())
                                                ) {
                                                    // 表头
                                                    Row(
                                                        modifier = Modifier
                                                            .widthIn(min = minRowWidth)
                                                            .background(DeepGreen)
                                                            .padding(vertical = 8.dp, horizontal = 12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                    ) {
                                                        columns.forEach { colName ->
                                                            Text(
                                                                text = colName,
                                                                modifier = Modifier.width(120.dp),
                                                                fontSize = 11.sp,
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
                                                        values.forEach { value ->
                                                            Text(
                                                                text = value,
                                                                modifier = Modifier.width(120.dp),
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
                                            // --- 竖屏：一体化块状布局 ---
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.dp, DeepGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            ) {
                                                rowData.toList().forEachIndexed { _, (key, value) ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(IntrinsicSize.Min)
                                                    ) {
                                                        // 左侧标签柱
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight()
                                                                .background(DeepGreen)
                                                                .padding(horizontal = 10.dp, vertical = 10.dp),
                                                            contentAlignment = Alignment.CenterStart
                                                        ) {
                                                            Text(
                                                                text = key,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                maxLines = 1
                                                            )
                                                        }
                                                        // 右侧数值区
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(2.2f)
                                                                .fillMaxHeight()
                                                                .background(Color.White)
                                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                                            contentAlignment = Alignment.CenterStart
                                                        ) {
                                                            Text(
                                                                text = value,
                                                                fontSize = 12.sp,
                                                                color = Color.DarkGray,
                                                                textAlign = TextAlign.Start,
                                                                maxLines = 1,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun LazyListScope.mainStatsContent() {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!viewModel.supabaseConfig.value.isValid) {
                            // 调用 ViewModel 发送重定向事件
                            viewModel.syncAllFromCloud() // 该方法内部会自动触发 CONFIG_NEEDED
                        } else {
                            scope.launch {
                                dialogTitle = "云端数据"
                                tableDataList = viewModel.getCloudTablesWithLatestRow()
                                showTableDialog = true
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
                ) {
                    Text("查看云端表", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        scope.launch {
                            dialogTitle = "本地数据"
                            tableDataList = viewModel.getLocalTablesWithLatestRow()
                            showTableDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
                ) {
                    Text("查看本地表", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        item { LastExcretionInfo(excretionLogs) }

        item {
            FoldingStatCard("体重波动 (kg)", R.drawable.ic_weight, weightLogsState, "weight", ChartBarColor)
        }

        item {
            FoldingStatCard("饮食消耗 (g)", R.drawable.ic_food, foodLogs.filter { it.type == ConsumptionType.EAT }, "consumption", FoodColor)
        }

        item {
            FoldingStatCard("饮水消耗 (ml)", R.drawable.ic_water, waterLogs.filter { it.type == ConsumptionType.EAT }, "consumption", WaterColor)
        }

        item {
            FoldingStatCard("排便次数", R.drawable.ic_poop, excretionLogs.filter { it.type == ExcretionType.POOP }, "excretion", PoopColor.copy(alpha = 0.6f))
        }

        item {
            FoldingStatCard("排尿次数", R.drawable.ic_pee, excretionLogs.filter { it.type == ExcretionType.PEE }, "excretion", PeeColor.copy(alpha = 0.6f))
        }

        medications.forEach { med ->
            item(key = "med_${med.id}") {
                val medLogs = medicationLogs.filter { it.medicationId == med.id }
                FoldingStatCard("用药: ${med.name} (${med.unit})", R.drawable.ic_medicine, medLogs, "medication", MediumGreen)
            }
        }

        snacks.forEach { snack ->
            item(key = "snack_${snack.id}") {
                val sLogs = snackLogs.filter { it.snackId == snack.id }
                FoldingStatCard("零食: ${snack.name} (${snack.unit})", R.drawable.ic_snack, sLogs, "snack", DeepGreen)
            }
        }

        item {
            ActivityLogSection(activityLogs)
        }
        
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize().background(Color.White)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { LastExcretionInfo(excretionLogs) }
                item { FoldingStatCard("体重波动 (kg)", R.drawable.ic_weight, weightLogsState, "weight", ChartBarColor) }
                item { FoldingStatCard("饮食消耗 (g)", R.drawable.ic_food, foodLogs.filter { it.type == ConsumptionType.EAT }, "consumption", FoodColor) }
                item { FoldingStatCard("饮水消耗 (ml)", R.drawable.ic_water, waterLogs.filter { it.type == ConsumptionType.EAT }, "consumption", WaterColor) }
                item { FoldingStatCard("排便次数", R.drawable.ic_poop, excretionLogs.filter { it.type == ExcretionType.POOP }, "excretion", PoopColor.copy(alpha = 0.6f)) }
                item { FoldingStatCard("排尿次数", R.drawable.ic_pee, excretionLogs.filter { it.type == ExcretionType.PEE }, "excretion", PeeColor.copy(alpha = 0.6f)) }
                item { ActivityLogSection(activityLogs) }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (!viewModel.supabaseConfig.value.isValid) {
                                    viewModel.syncAllFromCloud()
                                } else {
                                    scope.launch {
                                        dialogTitle = "云端数据"
                                        tableDataList = viewModel.getCloudTablesWithLatestRow()
                                        showTableDialog = true
                                    }
                                }
                            }, 
                            modifier = Modifier.weight(1f), 
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
                        ) {
                            Text("查看云端表", color = Color.White, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    dialogTitle = "本地数据"
                                    tableDataList = viewModel.getLocalTablesWithLatestRow()
                                    showTableDialog = true
                                }
                            }, 
                            modifier = Modifier.weight(1f), 
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
                        ) {
                            Text("查看本地表", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
                snacks.forEach { snack ->
                    item(key = "snack_${snack.id}") {
                        val sLogs = snackLogs.filter { it.snackId == snack.id }
                        FoldingStatCard("零食: ${snack.name}", R.drawable.ic_snack, sLogs, "snack", DeepGreen)
                    }
                }
                medications.forEach { med ->
                    item(key = "med_${med.id}") {
                        val medLogs = medicationLogs.filter { it.medicationId == med.id }
                        FoldingStatCard("用药: ${med.name}", R.drawable.ic_medicine, medLogs, "medication", MediumGreen)
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            mainStatsContent()
        }
    }
}

@Composable
fun ActivityLogSection(logs: List<ActivityLog>) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("操作记录", style = MaterialTheme.typography.titleSmall, color = Color.Gray)
                }
                Text(if (expanded) "收起" else "查看全部", fontSize = 12.sp, color = DeepGreen)
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (logs.isEmpty()) {
                        Text("暂无操作记录", fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        logs.take(20).forEach { log ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val actionCn = when(log.action) {
                                        "ADD" -> "添加"
                                        "DELETE" -> "删除"
                                        "UPDATE" -> "修改"
                                        "SYNC" -> "同步"
                                        else -> log.action
                                    }
                                    val entityCn = when(log.entityType) {
                                        "Consumption" -> "饮食饮水"
                                        "Weight" -> "体重"
                                        "Medication" -> "药品记录"
                                        "MedicationType" -> "药品库"
                                        "MedicationLog" -> "用药记录"
                                        "Excretion" -> "拉撒"
                                        "Snack" -> "零食记录"
                                        "SnackType" -> "零食库"
                                        "SnackLog" -> "零食记录"
                                        "PetProfile" -> "宠物档案"
                                        "Bowl" -> "食具"
                                        "Cloud" -> "云端"
                                        else -> log.entityType
                                    }
                                    Text(
                                        text = "$actionCn($entityCn)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when(log.action) {
                                            "ADD" -> Color(0xFF4CAF50)
                                            "DELETE" -> Color(0xFFF44336)
                                            else -> Color(0xFF2196F3)
                                        }
                                    )
                                    Text(text = log.details, fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(
                                    text = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(log.timestamp)),
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                            }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FoldingStatCard(title: String, resId: Int, logs: List<Any>, type: String, barColor: Color) {
    var expandedTab by remember { mutableIntStateOf(-1) } // -1 = folded, 0=W, 1=M, 2=Y

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(resId), null, modifier = Modifier.size(18.dp), contentScale = ContentScale.Fit)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatSummaryItem("周", logs, type, 0, isSelected = expandedTab == 0) { expandedTab = if (expandedTab == 0) -1 else 0 }
                StatSummaryItem("月", logs, type, 1, isSelected = expandedTab == 1) { expandedTab = if (expandedTab == 1) -1 else 1 }
                StatSummaryItem("年", logs, type, 2, isSelected = expandedTab == 2) { expandedTab = if (expandedTab == 2) -1 else 2 }
            }

            AnimatedVisibility(visible = expandedTab != -1) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    val chartData = remember(logs, expandedTab) {
                        if (expandedTab != -1) getAggregatedData(logs, expandedTab, type) else ChartData(emptyList(), emptyList())
                    }
                    BarStatSection("", chartData, barColor)
                }
            }
        }
    }
}

@Composable
fun RowScope.StatSummaryItem(label: String, logs: List<Any>, type: String, tabIndex: Int, isSelected: Boolean, onClick: () -> Unit) {
    val summaryData = remember(logs, tabIndex) {
        getAggregatedData(logs, tabIndex, type)
    }
    val total = summaryData.data.sum()
    val nonZeroCount = summaryData.data.count { it > 0 }.toDouble().coerceAtLeast(1.0)
    
    val divisor = if (type == "weight") {
        // 体重逻辑：按实际有记录的天数计算平均值
        nonZeroCount
    } else {
        // 其他（饮食等）逻辑：周按7天，月按30天，年按有记录的月份
        when(tabIndex) {
            0 -> 7.0
            1 -> 30.0
            else -> nonZeroCount
        }
    }
    val avg = total / divisor

    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) DeepGreen.copy(alpha = 0.1f) else Color.Transparent)
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (isSelected) DeepGreen else Color.Gray)
        Text(
            text = if (type == "weight") "%.1f".format(logs.filterIsInstance<WeightLog>().lastOrNull()?.weight ?: 0f) 
                   else "%.0f".format(total),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray
        )
        Text(
            text = if (tabIndex == 2) "月均:%.1f".format(avg) else "日均:%.1f".format(avg),
            style = TextStyle(fontSize = 9.sp),
            color = Color.Gray
        )
    }
}

fun getAggregatedData(logs: List<Any>, tabIndex: Int, type: String): ChartData {
    val data = mutableListOf<Double>()
    val labels = mutableListOf<String>()
    val now = Calendar.getInstance()

    when (tabIndex) {
        0 -> { // 周 (7天)
            for (i in 6 downTo 0) {
                val d = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) }
                val value = getValueForDate(logs, d, type)
                // 体重逻辑：只显示有记录的日期。其他逻辑：保留所有日期以观察消耗趋势。
                if (type != "weight" || value > 0) {
                    data.add(value)
                    labels.add(SimpleDateFormat("MM/dd", Locale.CHINA).format(d.time))
                }
            }
        }
        1 -> { // 月 (30天)
            for (i in 29 downTo 0) {
                val d = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) }
                val value = getValueForDate(logs, d, type)
                if (type != "weight" || value > 0) {
                    data.add(value)
                    labels.add(SimpleDateFormat("MM/dd", Locale.CHINA).format(d.time))
                }
            }
        }
        2 -> { // 年 (12个月)
            for (i in 11 downTo 0) {
                val d = (now.clone() as Calendar).apply { add(Calendar.MONTH, -i) }
                val year = d.get(Calendar.YEAR)
                val month = d.get(Calendar.MONTH)
                
                val value = when (type) {
                    "weight" -> logs.filterIsInstance<WeightLog>().filter { 
                        val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                        c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
                    }.lastOrNull()?.weight?.toDouble() ?: 0.0
                    "consumption" -> logs.filterIsInstance<ConsumptionLog>().filter { 
                        val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                        c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
                    }.sumOf { -it.amount.toDouble() }
                    "excretion" -> logs.filterIsInstance<ExcretionLog>().filter { 
                        val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                        c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
                    }.count().toDouble()
                    "medication" -> logs.filterIsInstance<MedicationLog>().filter { 
                        val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                        c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
                    }.sumOf { it.dosage.toDouble() }
                    "snack" -> logs.filterIsInstance<SnackLog>().filter { 
                        val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                        c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
                    }.sumOf { it.amount.toDouble() }
                    else -> 0.0
                }
                // 年视图下，如果是体重，也只显示有记录的月份
                if (type != "weight" || value > 0) {
                    data.add(value)
                    labels.add("${month + 1}月")
                }
            }
        }
    }
    return ChartData(data, labels)
}

fun getValueForDate(logs: List<Any>, d: Calendar, type: String): Double {
    return when (type) {
        "weight" -> logs.filterIsInstance<WeightLog>().filter { isSameDay(it.timestamp, d) }.lastOrNull()?.weight?.toDouble() ?: 0.0
        "consumption" -> logs.filterIsInstance<ConsumptionLog>().filter { isSameDay(it.timestamp, d) }.sumOf { -it.amount.toDouble() }
        "excretion" -> logs.filterIsInstance<ExcretionLog>().filter { isSameDay(it.timestamp, d) }.count().toDouble()
        "medication" -> logs.filterIsInstance<MedicationLog>().filter { isSameDay(it.timestamp, d) }.sumOf { it.dosage.toDouble() }
        "snack" -> logs.filterIsInstance<SnackLog>().filter { isSameDay(it.timestamp, d) }.sumOf { it.amount.toDouble() }
        else -> 0.0
    }
}

@Composable
fun BarStatSection(title: String, chartData: ChartData, barColor: Color = ChartBarColor) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val data = chartData.data
    val labels = chartData.labels

    LaunchedEffect(data) {
        if (data.isNotEmpty()) {
            modelProducer.runTransaction { 
                columnModel { series(data) }
            }
        }
    }

    val customRangeProvider = remember(data) {
        object : com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider {
            override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
                (if (minY == 0.0 && maxY == 0.0) 1.0 else maxY) * 1.3
        }
    }

    Column {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (data.any { it > 0 }) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(
                        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                            rememberLineComponent(
                                fill = Fill(barColor),
                                thickness = 10.dp,
                                shape = RoundedCornerShape(topStartPercent = 8, topEndPercent = 8)
                            )
                        ),
                        dataLabel = rememberTextComponent(
                            style = TextStyle(color = Color.DarkGray, fontSize = 9.sp),
                            margins = Insets(bottom = 4.dp) // Reset to smaller margin, let position handle it
                        ),
                        dataLabelPosition = Position.Vertical.Top,
                        dataLabelValueFormatter = { _, value, _ -> 
                            if (value >= 100) "%.0f".format(value) else "%.1f".format(value) 
                        },
                        rangeProvider = customRangeProvider
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        label = null, 
                        line = null,
                        tick = null,
                        guideline = null
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        label = rememberTextComponent(style = TextStyle(fontSize = 9.sp), margins = Insets(top = 4.dp)),
                        valueFormatter = { _, value, _ -> labels.getOrNull(value.toInt()) ?: value.toInt().toString() },
                        guideline = null
                    )
                ),
                modelProducer = modelProducer,
                modifier = Modifier.height(130.dp).fillMaxWidth() // 稍微增加总高度给文字留位
            )
        } else {
            Box(modifier = Modifier.height(60.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无数据", color = Color.LightGray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun LastExcretionInfo(logs: List<ExcretionLog>) {
    val lastPoop = logs.find { it.type == ExcretionType.POOP }?.timestamp
    val lastPee = logs.find { it.type == ExcretionType.PEE }?.timestamp
    val now = System.currentTimeMillis()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            InfoColumn("距离上次拉屎", lastPoop, now, PoopColor)
            VerticalDivider(modifier = Modifier.height(40.dp), color = BgGreen)
            InfoColumn("距离上次撒尿", lastPee, now, PeeColor)
        }
    }
}

@Composable
fun InfoColumn(label: String, timestamp: Long?, now: Long, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        val diffText = if (timestamp == null) "无记录" else {
            val diff = now - timestamp
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
            if (hours > 24) "${hours / 24}天 ${hours % 24}时" else "${hours}时 ${minutes}分"
        }
        Text(diffText, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
    }
}
