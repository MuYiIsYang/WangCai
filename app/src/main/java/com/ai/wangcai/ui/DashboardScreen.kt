package com.ai.wangcai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.wangcai.R
import com.ai.wangcai.data.*
import com.ai.wangcai.viewmodel.PetViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit

// User Palette
val DeepGreen = Color(0xFF659287)
val MediumGreen = Color(0xFF88BDA4)
val BgGreen = Color(0xFFE6F2DD)

// Requested Colors
val FoodColor = Color(0xFFFF9A86)
val WaterColor = Color(0xFF3368A0)
val PoopColor = Color(0xFF0B0909)
val PeeColor = Color(0xFF30AFFF)
val ChartBarColor = Color(0xFFFFFCE1)

@Composable
fun DashboardScreen(viewModel: PetViewModel, onSupabaseConfigClick: () -> Unit) {
    // 使用 rememberSaveable 保存日期（通过毫秒数转换）
    var selectedDateMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val selectedDate = remember(selectedDateMillis) { 
        Calendar.getInstance().apply { timeInMillis = selectedDateMillis } 
    }
    
    // 移除 LaunchedEffect(Unit) 重置逻辑，因为它会导致旋转时日期重置
    // 如果需要点击底部菜单重置，已由 MainActivity 的 dashboardKey 处理
    
    var showFabMenu by rememberSaveable { mutableStateOf(false) }
    
    // 新增: 记录主布局相对于窗口的偏移，用于校正 Popup 位置
    var layoutOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    
    // 记录点击位置、大小和父容器位置
    var popoverOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var popoverSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var parentBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var popoverDate by remember { mutableStateOf<Calendar?>(null) }
    
    // Dialog states
    var showFoodDialog by remember { mutableStateOf(false) }
    var showWaterDialog by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showMedLogDialog by remember { mutableStateOf(false) }
    var showAddMedTypeDialog by remember { mutableStateOf(false) }
    var showExcretionDialog by remember { mutableStateOf(false) }
    var showSnackLogDialog by remember { mutableStateOf(false) }
    var showAddSnackTypeDialog by remember { mutableStateOf(false) }
    var showBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showPetProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showAccountMenu by rememberSaveable { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // 云同步启用状态 (持久化存储)
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    var cloudSyncEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("cloud_sync_enabled", true)) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let {
            scope.launch {
                com.ai.wangcai.util.ExcelManager.exportData(
                    context, it,
                    viewModel.allBowls.value,
                    viewModel.foodLogs.value + viewModel.waterLogs.value,
                    viewModel.weightLogs.value,
                    viewModel.medications.value,
                    viewModel.medicationLogs.value,
                    viewModel.excretionLogs.value,
                    viewModel.snacks.value,
                    viewModel.snackLogs.value
                )
                android.widget.Toast.makeText(context, "备份成功", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    com.ai.wangcai.util.ExcelManager.importData(context, it, PetDatabase.getDatabase(context).petDao())
                    android.widget.Toast.makeText(context, "导入成功 (部分数据可能重复)", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "导入失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    Box(modifier = Modifier.fillMaxSize().background(Color.White).onGloballyPositioned { layoutOffset = it.localToWindow(androidx.compose.ui.geometry.Offset.Zero) }) {
        // 修改4: 展开菜单时，点击背景关闭
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize().background(BgGreen.copy(alpha = 0.3f))) {
                // 左侧日历区 (3:2 比例)
                Card(
                    modifier = Modifier.weight(3f).fillMaxHeight().padding(8.dp)
                        .onGloballyPositioned { parentBounds = it.boundsInWindow() },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp), // 统一圆角
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    MonthCalendarView(
                        viewModel = viewModel,
                        selectedDate = selectedDate,
                        modifier = Modifier.fillMaxSize(),
                        cloudSyncEnabled = cloudSyncEnabled,
                        onCloudSyncToggle = { enabled ->
                            cloudSyncEnabled = enabled
                            prefs.edit { putBoolean("cloud_sync_enabled", enabled) }
                        },
                        onDateSelected = { cal -> selectedDateMillis = cal.timeInMillis },
                        onDateClicked = { date, offset, size -> 
                            popoverDate = date
                            popoverOffset = offset
                            popoverSize = size
                        },
                        onAccountClick = { showAccountMenu = !showAccountMenu },
                        onPetProfileClick = { showPetProfileDialog = true }
                    )
                }
                
                // 右侧详情区 (3:2 比例)
                Card(
                    modifier = Modifier.weight(2f).fillMaxHeight().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        DayDetailView(viewModel = viewModel, date = selectedDate)
                        if (isSyncing) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).height(2.dp),
                                color = DeepGreen,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }
            }
        } else {
            // 修改1: 竖屏也采用双卡片设计
            Column(modifier = Modifier.fillMaxSize().background(BgGreen.copy(alpha = 0.2f)).padding(8.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(3f) // 调整为 3:2
                        .onGloballyPositioned { parentBounds = it.boundsInWindow() },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp), // 统一圆角
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    MonthCalendarView(
                        viewModel = viewModel,
                        selectedDate = selectedDate,
                        cloudSyncEnabled = cloudSyncEnabled,
                        onCloudSyncToggle = { enabled ->
                            cloudSyncEnabled = enabled
                            prefs.edit { putBoolean("cloud_sync_enabled", enabled) }
                        },
                        onDateSelected = { cal -> selectedDateMillis = cal.timeInMillis },
                        onDateClicked = { date, offset, size -> 
                            popoverDate = date
                            popoverOffset = offset
                            popoverSize = size
                        },
                        onAccountClick = { showAccountMenu = !showAccountMenu },
                        onPetProfileClick = { showPetProfileDialog = true }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().weight(2f), // 调整为 3:2
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp), // 统一圆角
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        DayDetailView(viewModel = viewModel, date = selectedDate)
                        if (isSyncing) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).height(2.dp),
                                color = DeepGreen,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }
            }
        }

        // --- 新增：透明点击拦截层，用于点击空白处自动关闭悬浮菜单 ---
        if (showFabMenu || showAccountMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { 
                        showFabMenu = false
                        showAccountMenu = false
                    }
            )
        }

        // --- 账户菜单 (从 MonthCalendarView 移至此处，解决 z-index 遮挡问题) ---
        AnimatedVisibility(
            visible = showAccountMenu,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .padding(top = 54.dp, end = 24.dp) // 适配全局坐标
                .align(Alignment.TopEnd)
        ) {
            val config by viewModel.supabaseConfig.collectAsState()
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AvatarMenuItem(
                    label = if (config.isValid) "仓库信息" else "配置仓库",
                    icon = {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Cloud, 
                                null, 
                                tint = if (config.isValid) DeepGreen else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Icon(
                                imageVector = if (config.isValid) Icons.Default.Check else Icons.Default.Close,
                                null,
                                tint = if (config.isValid) Color.Green else Color.Red,
                                modifier = Modifier.size(10.dp).align(Alignment.BottomEnd).background(Color.White, CircleShape)
                            )
                        }
                    },
                    onClick = { 
                        showAccountMenu = false
                        onSupabaseConfigClick() 
                    }
                )

                AvatarMenuItem(
                    label = "导入导出",
                    icon = {
                        Icon(Icons.Default.Share, null, tint = DeepGreen, modifier = Modifier.size(20.dp))
                    },
                    onClick = { 
                        showAccountMenu = false
                        showBackupDialog = true
                    }
                )
            }
        }

        // 新增3: 悬浮放大展示区 (1.4倍大小，半透明高斯模糊视觉)
        if (popoverDate != null) {
            DaySummaryPopover(
                viewModel = viewModel,
                date = popoverDate!!,
                anchorOffset = popoverOffset,
                anchorSize = popoverSize,
                parentBounds = parentBounds,
                layoutOffset = layoutOffset, // 传递布局偏移
                onDismiss = { popoverDate = null }
            )
        }

        // Floating Multi-Action Button (FAB)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(if (isLandscape) 16.dp else 24.dp)
        ) {
            val menuContent = @Composable {
                FabMenuItem("拉屎撒尿", painterResource(R.drawable.ic_poop), isLandscape) {
                    showExcretionDialog = true
                    showFabMenu = false
                }
                FabMenuItem("来个零食", painterResource(R.drawable.ic_snack), isLandscape) {
                    showSnackLogDialog = true
                    showFabMenu = false
                }
                FabMenuItem("需要吃药", painterResource(R.drawable.ic_medicine), isLandscape) {
                    showMedLogDialog = true
                    showFabMenu = false
                }
                FabMenuItem("吃吃", painterResource(R.drawable.ic_food), isLandscape) {
                    showFoodDialog = true
                    showFabMenu = false
                }
                FabMenuItem("喝喝", painterResource(R.drawable.ic_water), isLandscape) {
                    showWaterDialog = true
                    showFabMenu = false
                }
                FabMenuItem("体重", painterResource(R.drawable.ic_weight), isLandscape) {
                    showWeightDialog = true
                    showFabMenu = false
                }
            }

            if (isLandscape) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(
                        visible = showFabMenu,
                        enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            menuContent()
                        }
                    }
                    FloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu },
                        containerColor = DeepGreen,
                        contentColor = Color.White
                    ) {
                        Icon(if (showFabMenu) Icons.Default.Close else Icons.Default.Add, "添加", modifier = Modifier.size(32.dp))
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(
                        visible = showFabMenu,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            menuContent()
                        }
                    }
                    FloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu },
                        containerColor = DeepGreen,
                        contentColor = Color.White
                    ) {
                        Icon(if (showFabMenu) Icons.Default.Close else Icons.Default.Add, "添加", modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }

    // Dialogs ...
    if (showFoodDialog) {
        val bowl by viewModel.foodBowl.collectAsState()
        val logs by viewModel.foodLogs.collectAsState()
        val lastGross = logs.firstOrNull()?.grossWeight ?: 0f
        val tare = bowl?.tareWeight ?: 0f
        ConsumptionDialog(
            title = "吃吃",
            bowl = bowl,
            // 修复：如果上次总重为0，则使用碗重作为基准
            lastGrossWeight = if (lastGross > 0f) lastGross else tare,
            onConfirm = { gross, isFromEmpty -> viewModel.recordConsumption(gross, BowlType.FOOD, isFromEmpty, null, selectedDate) },
            onUpdateTare = { newTare -> viewModel.updateBowl(bowl?.name ?: "食盆", newTare, BowlType.FOOD) },
            onDismiss = { showFoodDialog = false }
        )
    }

    if (showWaterDialog) {
        val bowl by viewModel.waterBowl.collectAsState()
        val logs by viewModel.waterLogs.collectAsState()
        val lastGross = logs.firstOrNull()?.grossWeight ?: 0f
        val tare = bowl?.tareWeight ?: 0f
        ConsumptionDialog(
            title = "喝喝",
            bowl = bowl,
            // 修复：如果上次总重为0，则使用碗重作为基准
            lastGrossWeight = if (lastGross > 0f) lastGross else tare,
            onConfirm = { gross, isFromEmpty -> viewModel.recordConsumption(gross, BowlType.WATER, isFromEmpty, null, selectedDate) },
            onUpdateTare = { newTare -> viewModel.updateBowl(bowl?.name ?: "水盆", newTare, BowlType.WATER) },
            onDismiss = { showWaterDialog = false }
        )
    }

    if (showWeightDialog) UpdateWeightDialog("体重记录", "当前体重 (kg)", { weight, note -> viewModel.addWeightLog(weight, note, selectedDate) }) { showWeightDialog = false }
    
    if (showMedLogDialog) {
        val meds by viewModel.medications.collectAsState()
        MedicationLogDialog(
            meds = meds,
            onConfirm = { id, dose, date -> viewModel.addMedicationLog(id, dose, date) },
            onAddMedType = { showAddMedTypeDialog = true },
            onDismiss = { showMedLogDialog = false },
            initialDate = selectedDate
        )
    }

    if (showAddMedTypeDialog) {
        AddMedTypeDialog(
            onConfirm = { name, unit -> viewModel.addMedication(name, unit) },
            onDismiss = { showAddMedTypeDialog = false }
        )
    }

    if (showExcretionDialog) {
        ExcretionLogDialog(
            onConfirm = { type, shape -> viewModel.addExcretionLog(type, shape, selectedDate) },
            onDismiss = { showExcretionDialog = false }
        )
    }

    if (showSnackLogDialog) {
        val snacks by viewModel.snacks.collectAsState()
        SnackLogDialog(
            snacks = snacks,
            onConfirm = { id, amount, date -> viewModel.addSnackLog(id, amount, date) },
            onAddSnackType = { showAddSnackTypeDialog = true },
            onDismiss = { showSnackLogDialog = false },
            initialDate = selectedDate
        )
    }

    if (showAddSnackTypeDialog) {
        AddSnackTypeDialog(
            onConfirm = { name, unit -> viewModel.addSnack(name, unit) },
            onDismiss = { showAddSnackTypeDialog = false }
        )
    }

    if (showPetProfileDialog) {
        val profile by viewModel.petProfile.collectAsState()
        PetProfileDialog(
            profile = profile,
            onConfirm = { nickname, breed, birthday, avatarPath ->
                viewModel.updatePetProfile(nickname, breed, birthday, avatarPath)
                showPetProfileDialog = false
            },
            onDismiss = { showPetProfileDialog = false }
        )
    }

    if (showBackupDialog) {
        val lastBackup = context.getSharedPreferences("backup_prefs", android.content.Context.MODE_PRIVATE)
            .getLong("last_auto_backup", 0L)
        val lastBackupStr = if (lastBackup == 0L) "暂无" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(lastBackup))
        val lastFileName = if (lastBackup == 0L) "无" else "autobackup_${SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date(lastBackup))}_xxxxxx.xlsx"

        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("数据管理", color = DeepGreen, fontWeight = FontWeight.Bold) },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { 
                                importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                showBackupDialog = false 
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("导入数据")
                        }
                        Button(
                            onClick = { 
                                val fileName = "mybackup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())}.xlsx"
                                exportLauncher.launch(fileName)
                                showBackupDialog = false 
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("导出数据")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 自动备份信息
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("上次自动导出：$lastBackupStr", style = MaterialTheme.typography.labelSmall, color = DeepGreen)
                        Text("文件名：$lastFileName", style = MaterialTheme.typography.labelSmall, color = DeepGreen)
                        Text("存储路径：Download/WangCai", style = MaterialTheme.typography.labelSmall, color = DeepGreen)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupDialog = false }) { Text("关闭", color = Color.Gray) }
            }
        )
    }

}

@Composable
fun MonthCalendarView(
    viewModel: PetViewModel,
    selectedDate: Calendar,
    modifier: Modifier = Modifier,
    cloudSyncEnabled: Boolean,
    onCloudSyncToggle: (Boolean) -> Unit,
    onDateSelected: (Calendar) -> Unit,
    onDateClicked: (Calendar, androidx.compose.ui.geometry.Offset, androidx.compose.ui.unit.IntSize) -> Unit,
    onAccountClick: () -> Unit,
    // 新增：专门触发配置弹窗
    onPetProfileClick: () -> Unit
) {
    // 使用 rememberSaveable 保存当前显示的月份（通过毫秒数转换）
    var currentMonthMillis by rememberSaveable { mutableLongStateOf(selectedDate.timeInMillis) }
    val currentMonth = remember(currentMonthMillis) { 
        Calendar.getInstance().apply { timeInMillis = currentMonthMillis } 
    }
    
    val foodLogs by viewModel.foodLogs.collectAsState()
    val waterLogs by viewModel.waterLogs.collectAsState()
    val excretionLogs by viewModel.excretionLogs.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val petProfile by viewModel.petProfile.collectAsState()

    val daysInMonth = remember(currentMonth) {
        val list = mutableListOf<Calendar?>()
        val temp = currentMonth.clone() as Calendar
        temp.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = temp.get(Calendar.DAY_OF_WEEK) - 1
        repeat(firstDayOfWeek) { list.add(null) }
        val maxDays = temp.getActualMaximum(Calendar.DAY_OF_MONTH)
        repeat(maxDays) {
            list.add(temp.clone() as Calendar)
            temp.add(Calendar.DAY_OF_MONTH, 1)
        }
        while (list.size % 7 != 0) { list.add(null) }
        list
    }

    Box(modifier = modifier.fillMaxWidth().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp).clickable { onPetProfileClick() }) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(DeepGreen.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val avatarPath = petProfile?.avatarPath
                        if (avatarPath != null) {
                            AsyncImage(model = avatarPath, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.Pets, null, tint = DeepGreen, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { 
                        val prev = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                        currentMonthMillis = prev.timeInMillis
                    }) { 
                        Icon(Icons.Default.ChevronLeft, null, tint = DeepGreen) 
                    }
                    Text(
                        text = SimpleDateFormat("yyyy年MM月", Locale.CHINA).format(currentMonth.time),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                    IconButton(onClick = { 
                        val next = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                        currentMonthMillis = next.timeInMillis
                    }) { 
                        Icon(Icons.Default.ChevronRight, null, tint = DeepGreen) 
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 修改 2: 云同步状态切换
                    IconButton(onClick = { onCloudSyncToggle(!cloudSyncEnabled) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (cloudSyncEnabled) Icons.Default.CloudQueue else Icons.Default.CloudOff, 
                            contentDescription = "同步切换", 
                            tint = DeepGreen, 
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (cloudSyncEnabled) {
                        val config by viewModel.supabaseConfig.collectAsState()
                        IconButton(
                            onClick = { 
                                if (config.isValid) viewModel.syncAllToCloud() 
                                else onAccountClick() // 未设置时自动跳转
                            }, 
                            modifier = Modifier.size(32.dp), 
                            enabled = !isSyncing
                        ) {
                            Icon(Icons.Default.CloudUpload, "上传", tint = if (isSyncing) Color.Gray else DeepGreen, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { 
                                if (config.isValid) viewModel.syncAllFromCloud() 
                                else onAccountClick() // 未设置时自动跳转
                            }, 
                            modifier = Modifier.size(32.dp), 
                            enabled = !isSyncing
                        ) {
                            Icon(Icons.Default.CloudDownload, "下载", tint = if (isSyncing) Color.Gray else DeepGreen, modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    IconButton(onClick = onAccountClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.AccountCircle, "账户", tint = DeepGreen, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // Days Header
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach { day ->
                    Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            // Calendar Grid
            val rows = daysInMonth.chunked(7)
            Column(modifier = Modifier.weight(1f)) {
                rows.forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        week.forEach { date ->
                            val cellModifier = Modifier.weight(1f).fillMaxHeight().padding(1.dp)
                            Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    val isSelected = isSameDay(date, selectedDate)
                                    val isToday = isSameDay(date, Calendar.getInstance())
                                    
                                    val totalFood = foodLogs.filter { isSameDay(it.timestamp, date) && it.type == ConsumptionType.EAT }.sumOf { -it.amount.toDouble() }
                                    val totalWater = waterLogs.filter { isSameDay(it.timestamp, date) && it.type == ConsumptionType.EAT }.sumOf { -it.amount.toDouble() }
                                    val totalPoop = excretionLogs.count { isSameDay(it.timestamp, date) && it.type == ExcretionType.POOP }
                                    val totalPee = excretionLogs.count { isSameDay(it.timestamp, date) && it.type == ExcretionType.PEE }

                                    var cellOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                                    var cellSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) DeepGreen.copy(alpha = 0.15f) else Color.Transparent)
                                            .onGloballyPositioned { 
                                                cellOffset = it.localToWindow(androidx.compose.ui.geometry.Offset(it.size.width / 2f, it.size.height / 2f))
                                                cellSize = it.size
                                            }
                                            .clickable { 
                                                onDateSelected(date)
                                                onDateClicked(date, cellOffset, cellSize) 
                                            }.padding(top = 1.dp)
                                    ) {
                                        val isBday = isBirthday(date, petProfile?.birthday)
                                        val age = if (isBday) calculateAge(petProfile?.birthday, date) else 0

                                        Box(
                                            modifier = Modifier
                                                .height(16.dp) 
                                                .let { if (!isBday) it.width(16.dp) else it.wrapContentWidth() } 
                                                .clip(CircleShape)
                                                .background(if (isSelected) DeepGreen else Color.Transparent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isBday) {
                                                Text(
                                                    text = "🎂   $age", 
                                                    color = if (isSelected) Color.White else FoodColor, 
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = date.get(Calendar.DAY_OF_MONTH).toString(), 
                                                    color = if (isSelected) Color.White else if (isToday) DeepGreen else Color.DarkGray, 
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), 
                                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                        
                                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                            if (totalFood > 0) CalendarDataRow(R.drawable.ic_food, "${"%.0f".format(totalFood)}g", FoodColor)
                                            if (totalWater > 0) CalendarDataRow(R.drawable.ic_water, "${"%.0f".format(totalWater)}ml", WaterColor)
                                            if (totalPoop > 0) CalendarDataRow(R.drawable.ic_poop, "${totalPoop}次", PoopColor)
                                            if (totalPee > 0) CalendarDataRow(R.drawable.ic_pee, "${totalPee}次", PeeColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 0.5.dp)
        }
    }
}

@Composable
fun AvatarMenuItem(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )
        }
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
    }
}

@Composable
fun CalendarDataRow(resId: Int, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth().height(9.dp).padding(start = 8.dp)) {
        Image(painterResource(resId), null, modifier = Modifier.size(7.dp), contentScale = ContentScale.Fit)
        Text(text = " $text", fontSize = 7.sp, lineHeight = 7.sp, color = color, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
fun DayDetailView(viewModel: PetViewModel, date: Calendar) {
    val foodLogs by viewModel.foodLogs.collectAsState()
    val waterLogs by viewModel.waterLogs.collectAsState()
    val weightLogs by viewModel.weightLogs.collectAsState()
    val medLogs by viewModel.medicationLogs.collectAsState()
    val excretionLogs by viewModel.excretionLogs.collectAsState()
    val snacks by viewModel.snacks.collectAsState()
    val snackLogs by viewModel.snackLogs.collectAsState()
    val medications by viewModel.medications.collectAsState()

    val dailyRecords = remember(date, foodLogs, waterLogs, weightLogs, medLogs, excretionLogs, snackLogs) {
        val list = mutableListOf<RecordItem>()
        foodLogs.filter { isSameDay(it.timestamp, date) }.forEach { 
            val (typeStr, resId, color) = when(it.type) {
                ConsumptionType.ADD -> Triple("添加饮食", R.drawable.ic_food, DeepGreen)
                ConsumptionType.EAT -> Triple("吃吃", R.drawable.ic_food, FoodColor)
                ConsumptionType.CLEAR -> Triple("清空碗", R.drawable.ic_food, DeepGreen)
            }
            list.add(RecordItem(it.timestamp, typeStr, "${if(it.amount > 0) "+" else ""}${"%.1f".format(it.amount)}g", resId, color, it))
        }
        waterLogs.filter { isSameDay(it.timestamp, date) }.forEach {
            val (typeStr, resId, color) = when(it.type) {
                ConsumptionType.ADD -> Triple("添加饮水", R.drawable.ic_water, DeepGreen)
                ConsumptionType.EAT -> Triple("喝喝", R.drawable.ic_water, WaterColor)
                ConsumptionType.CLEAR -> Triple("清空碗", R.drawable.ic_water, DeepGreen)
            }
            list.add(RecordItem(it.timestamp, typeStr, "${if(it.amount > 0) "+" else ""}${"%.1f".format(it.amount)}ml", resId, color, it))
        }
        weightLogs.filter { isSameDay(it.timestamp, date) }.forEach {
            list.add(RecordItem(it.timestamp, "体重记录", "${it.weight}kg", R.drawable.ic_weight, MediumGreen, it))
        }
        medLogs.filter { isSameDay(it.timestamp, date) }.forEach { log ->
            val medName = log.medicationName.ifBlank { medications.find { it.id == log.medicationId }?.name ?: "未知药品" }
            list.add(RecordItem(log.timestamp, "用药", "$medName ${log.dosage}", R.drawable.ic_medicine, MediumGreen, log))
        }
        excretionLogs.filter { isSameDay(it.timestamp, date) }.forEach {
            val (typeStr, resId, color) = if(it.type == ExcretionType.POOP) Triple("拉屎", R.drawable.ic_poop, PoopColor) else Triple("撒尿", R.drawable.ic_pee, PeeColor)
            list.add(RecordItem(it.timestamp, typeStr, it.shape ?: "记录", resId, color, it))
        }
        snackLogs.filter { isSameDay(it.timestamp, date) }.forEach { log ->
            val snackName = log.snackName.ifBlank { snacks.find { it.id == log.snackId }?.name ?: "未知零食" }
            list.add(RecordItem(log.timestamp, "零食", "$snackName ${log.amount}", R.drawable.ic_snack, DeepGreen, log))
        }
        list.sortByDescending { it.timestamp }
        list
    }

    var selectedRecord by remember { mutableStateOf<RecordItem?>(null) }
    var showActionDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (dailyRecords.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                Text("当天暂无记录", color = Color.Gray)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.White), 
            contentPadding = PaddingValues(12.dp), // 缩小内边距
            verticalArrangement = Arrangement.spacedBy(8.dp) // 缩小行间距
        ) {
            items(dailyRecords) { record ->
                RecordRow(record, onClick = { selectedRecord = record; showActionDialog = true })
            }
        }
    }

    if (showActionDialog && selectedRecord != null) {
        AlertDialog(
            onDismissRequest = { showActionDialog = false },
            title = { Text("选择操作") },
            text = { Text("您想要对这条 ${selectedRecord?.title} 记录做什么？") },
            confirmButton = { TextButton(onClick = { showActionDialog = false; showEditDialog = true }) { Text("修改", color = DeepGreen) } },
            dismissButton = {
                TextButton(onClick = {
                    showActionDialog = false
                    showDeleteConfirmDialog = true
                }) { Text("删除", color = Color.Red) }
            }
        )
    }

    if (showDeleteConfirmDialog && selectedRecord != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条 ${selectedRecord?.title} 记录吗？此操作不可撤销。") },
            confirmButton = { 
                TextButton(onClick = {
                    when (val raw = selectedRecord?.rawData) {
                        is ConsumptionLog -> viewModel.deleteConsumption(raw)
                        is WeightLog -> viewModel.deleteWeight(raw)
                        is MedicationLog -> viewModel.deleteMedicationLog(raw)
                        is ExcretionLog -> viewModel.deleteExcretion(raw)
                        is SnackLog -> viewModel.deleteSnackLog(raw)
                    }
                    showDeleteConfirmDialog = false
                    selectedRecord = null
                }) { Text("确认删除", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("取消", color = Color.Gray) }
            }
        )
    }

    if (showEditDialog && selectedRecord != null) {
        EditRecordDialog(record = selectedRecord!!, onConfirm = { updatedData ->
            when (updatedData) {
                is ConsumptionLog -> viewModel.updateConsumption(updatedData)
                is WeightLog -> viewModel.updateWeightLog(updatedData)
                is MedicationLog -> viewModel.updateMedicationLog(updatedData)
                is ExcretionLog -> viewModel.updateExcretion(updatedData)
                is SnackLog -> viewModel.updateSnackLog(updatedData)
            }
            showEditDialog = false; selectedRecord = null
        }, onDismiss = { showEditDialog = false; selectedRecord = null })
    }
}

@Composable
fun RecordRow(record: RecordItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() }
            .padding(vertical = 2.dp, horizontal = 4.dp), // 进一步缩小纵向间距
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(record.timestamp)), 
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp, // 显式缩小字号
            color = Color.Gray, 
            modifier = Modifier.width(38.dp) // 缩小时间占用宽度
        )
        Spacer(modifier = Modifier.width(6.dp)) 
        Box(
            modifier = Modifier
                .size(28.dp) // 缩小背景圆圈
                .clip(CircleShape)
                .background(record.color.copy(alpha = 0.2f)), 
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(record.resId), null, modifier = Modifier.size(16.dp), contentScale = ContentScale.Fit) // 缩小图标
        }
        Spacer(modifier = Modifier.width(10.dp)) 
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.title, 
                style = MaterialTheme.typography.bodyMedium, // 降低字号等级
                fontSize = 13.sp, // 显式设置
                fontWeight = FontWeight.Medium,
                color = Color.DarkGray
            )
        }
        Text(
            text = record.value, 
            style = MaterialTheme.typography.bodyMedium, 
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold, 
            color = record.color
        )
        Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(12.dp)) // 缩小箭头
    }
}

@Composable
fun EditRecordDialog(record: RecordItem, onConfirm: (Any) -> Unit, onDismiss: () -> Unit) {
    val raw = record.rawData
    var valueInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf(if (raw is WeightLog) raw.note ?: "" else "") }
    var excretionType by remember { mutableStateOf(if (raw is ExcretionLog) raw.type else ExcretionType.POOP) }
    var excretionShape by remember { mutableStateOf(if (raw is ExcretionLog) raw.shape ?: "正常" else "正常") }
    LaunchedEffect(raw) {
        valueInput = when(raw) {
            is ConsumptionLog -> raw.amount.toString()
            is WeightLog -> raw.weight.toString()
            is MedicationLog -> raw.dosage.toString()
            is SnackLog -> raw.amount.toString()
            else -> ""
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (raw is ExcretionLog) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = excretionType == ExcretionType.POOP, onClick = { excretionType = ExcretionType.POOP })
                            Text("拉屎")
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(selected = excretionType == ExcretionType.PEE, onClick = { excretionType = ExcretionType.PEE })
                            Text("撒尿")
                        }
                        val shapes = if (excretionType == ExcretionType.POOP) listOf("正常", "软便", "稀便") else listOf("正常", "尿多", "尿少")
                        Text("状态:", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            shapes.forEach { s ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { excretionShape = s }) {
                                    RadioButton(selected = excretionShape == s, onClick = { excretionShape = s })
                                    Text(s, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(value = valueInput, onValueChange = { valueInput = it }, label = { Text("数值") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    if (raw is WeightLog) OutlinedTextField(value = noteInput, onValueChange = { noteInput = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newVal = valueInput.toFloatOrNull() ?: 0f
                val updated: Any? = when(raw) {
                    is ConsumptionLog -> raw.copy(amount = newVal)
                    is WeightLog -> raw.copy(weight = newVal, note = noteInput.ifBlank { null })
                    is MedicationLog -> raw.copy(dosage = newVal)
                    is ExcretionLog -> raw.copy(type = excretionType, shape = excretionShape)
                    is SnackLog -> raw.copy(amount = newVal)
                    else -> null
                }
                updated?.let { onConfirm(it) }
            }) { Text("保存", color = DeepGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun ConsumptionDialog(title: String, bowl: Bowl?, onConfirm: (Float, Boolean) -> Unit, onUpdateTare: (Float) -> Unit, onDismiss: () -> Unit, lastGrossWeight: Float) {
    var grossInput by remember { mutableStateOf("") }
    var showTareEdit by remember { mutableStateOf(false) }
    var mode by remember { mutableIntStateOf(0) } // 0: 未空记录, 1: 空碗记录
    val currentTare = bowl?.tareWeight ?: 0f
    var errorText by remember { mutableStateOf<String?>(null) }
    
    // 计算增减数值 (预览显示)
    val diffAmount = remember(grossInput, mode) {
        val input = grossInput.toFloatOrNull() ?: return@remember null
        if (mode == 1) {
            // 空碗模式：新数值 - 碗重
            input - currentTare
        } else {
            // 未空模式：新数值 - 上次总重
            input - lastGrossWeight
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = DeepGreen, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showTareEdit = true }) {
                        Icon(Icons.Default.Kitchen, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (currentTare == 0f) "碗重:未设置" else "碗重:${currentTare}g", fontSize = 12.sp, color = if (currentTare == 0f) Color.Red else Color.Gray)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (mode == 1) "基准: 碗重(${currentTare}g)" else "基准: 上次总重(${"%.1f".format(lastGrossWeight)}g)", fontSize = 11.sp, color = Color.Gray)
                    if (diffAmount != null && diffAmount != 0f) {
                        Text(
                            text = if (diffAmount > 0) "增加: +${"%.1f".format(diffAmount)}g" else "减少: ${"%.1f".format(diffAmount)}g",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (diffAmount > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = grossInput, onValueChange = { grossInput = it; errorText = null }, label = { Text("当前总重 (碗+内容)") }, placeholder = { Text("请输入电子秤数值") }, isError = errorText != null, supportingText = { errorText?.let { Text(it, color = Color.Red) } }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == 0, onClick = { mode = 0 }, colors = RadioButtonDefaults.colors(selectedColor = DeepGreen))
                    Text("未空记录", modifier = Modifier.clickable { mode = 0 })
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = mode == 1, onClick = { mode = 1 }, colors = RadioButtonDefaults.colors(selectedColor = DeepGreen))
                    Text("空碗记录", modifier = Modifier.clickable { mode = 1 })
                }
            }
        },
        confirmButton = { TextButton(onClick = { val g = grossInput.toFloatOrNull() ?: 0f; if (g < currentTare) errorText = "重量不能小于碗重 (${currentTare}g)" else { onConfirm(g, mode == 1); onDismiss() } }) { Text("确定", color = DeepGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    if (showTareEdit) {
        var newTare by remember { mutableStateOf(currentTare.toString()) }
        AlertDialog(onDismissRequest = { showTareEdit = false }, title = { Text("设置碗重") }, text = { OutlinedTextField(value = newTare, onValueChange = { newTare = it }, label = { Text("皮重 (g)") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)) }, confirmButton = { TextButton(onClick = { newTare.toFloatOrNull()?.let { onUpdateTare(it) }; showTareEdit = false }) { Text("保存", color = DeepGreen) } })
    }
}

@Composable
fun MedicationLogDialog(meds: List<Medication>, onConfirm: (Long, Float, Calendar) -> Unit, onAddMedType: () -> Unit, onDismiss: () -> Unit, initialDate: Calendar) {
    var selectedId by remember { mutableLongStateOf(meds.firstOrNull()?.id ?: 0L) }
    var dose by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("记录用药"); IconButton(onClick = onAddMedType) { Icon(Icons.Default.AddCircle, null, tint = DeepGreen) } } }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { if (meds.isEmpty()) Text("暂无药品，请点击右上角添加", color = Color.Red) else { meds.forEach { med -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { selectedId = med.id }) { RadioButton(selected = selectedId == med.id, onClick = { selectedId = med.id }, colors = RadioButtonDefaults.colors(selectedColor = DeepGreen)); Text("${med.name} (${med.unit})") } }; OutlinedTextField(value = dose, onValueChange = { dose = it }, label = { Text("剂量") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)) } } }, confirmButton = { TextButton(enabled = meds.isNotEmpty(), onClick = { dose.toFloatOrNull()?.let { onConfirm(selectedId, it, initialDate.clone() as Calendar) }; onDismiss() }) { Text("确定", color = DeepGreen) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun AddMedTypeDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("粒") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("新增药品种类") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }); OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("单位") }) } }, confirmButton = { TextButton(onClick = { onConfirm(name, unit); onDismiss() }) { Text("添加", color = DeepGreen) } })
}

@Composable
fun SnackLogDialog(snacks: List<Snack>, onConfirm: (Long, Float, Calendar) -> Unit, onAddSnackType: () -> Unit, onDismiss: () -> Unit, initialDate: Calendar) {
    var selectedId by remember { mutableLongStateOf(snacks.firstOrNull()?.id ?: 0L) }
    var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("啥好吃的?"); IconButton(onClick = onAddSnackType) { Icon(Icons.Default.AddCircle, null, tint = DeepGreen) } } }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { if (snacks.isEmpty()) Text("暂无零食，请点击右上角添加", color = Color.Red) else { snacks.forEach { snack -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { selectedId = snack.id }) { RadioButton(selected = selectedId == snack.id, onClick = { selectedId = snack.id }, colors = RadioButtonDefaults.colors(selectedColor = DeepGreen)); Text("${snack.name} (${snack.unit})") } }; OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("份量") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)) } } }, confirmButton = { TextButton(enabled = snacks.isNotEmpty(), onClick = { amount.toFloatOrNull()?.let { onConfirm(selectedId, it, initialDate.clone() as Calendar) }; onDismiss() }) { Text("确定", color = DeepGreen) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun AddSnackTypeDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("g") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("新增零食种类") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }); OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("单位") }) } }, confirmButton = { TextButton(onClick = { onConfirm(name, unit); onDismiss() }) { Text("添加", color = DeepGreen) } })
}

@Composable
fun UpdateWeightDialog(title: String, label: String, onConfirm: (Float, String?) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title, color = MediumGreen, fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text(label) }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)); OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注 (可选)") }, singleLine = true) } }, confirmButton = { TextButton(onClick = { input.toFloatOrNull()?.let { onConfirm(it, note.ifBlank { null }) }; onDismiss() }) { Text("确定", color = MediumGreen) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun FabMenuItem(label: String, painter: Painter, isLandscape: Boolean = false, onClick: () -> Unit) {
    if (isLandscape) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
            FloatingActionButton(onClick = onClick, modifier = Modifier.size(40.dp), containerColor = Color.White, contentColor = DeepGreen) { Image(painter, null, modifier = Modifier.size(20.dp), contentScale = ContentScale.Fit) }
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 9.sp)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onClick() }) {
            Surface(shape = MaterialTheme.shapes.small, color = Color.White, shadowElevation = 2.dp, modifier = Modifier.padding(end = 8.dp)) { Text(text = label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray) }
            FloatingActionButton(onClick = onClick, modifier = Modifier.size(48.dp), containerColor = Color.White, contentColor = DeepGreen) { Image(painter, null, modifier = Modifier.size(24.dp), contentScale = ContentScale.Fit) }
        }
    }
}

@Composable
fun PetProfileDialog(
    profile: PetProfile?, 
    onConfirm: (String, String?, String?, String?) -> Unit, 
    onDismiss: () -> Unit
) {
    var nickname by remember { mutableStateOf(profile?.nickname ?: "咪咪") }
    var breed by remember { mutableStateOf(profile?.breed ?: "") }
    var birthday by remember { mutableStateOf(profile?.birthday ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text("宠物档案", color = DeepGreen, fontWeight = FontWeight.Bold) }, 
        text = { 
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp), 
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState()) // 允许横屏下滚动
            ) { 
                // 名字和品种在同一行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nickname, 
                        onValueChange = { nickname = it }, 
                        label = { Text("名字") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = breed, 
                        onValueChange = { breed = it }, 
                        label = { Text("品种") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                
                // 生日选择框 (点击触发滚动选择)
                Box(modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }) {
                    OutlinedTextField(
                        value = birthday, 
                        onValueChange = { }, 
                        label = { Text("生日") }, 
                        placeholder = { Text("选择生日") }, 
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false, // 禁用手动输入
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            } 
        }, 
        confirmButton = { 
            Button(
                onClick = { onConfirm(nickname, breed.ifBlank { null }, birthday.ifBlank { null }, profile?.avatarPath) }, 
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
            ) { 
                Text("保存并同步") 
            } 
        }, 
        dismissButton = { 
            TextButton(onClick = onDismiss) { Text("取消") } 
        }
    )

    if (showDatePicker) {
        val calendar = Calendar.getInstance()
        if (birthday.isNotBlank()) {
            try {
                val parts = birthday.split("-")
                calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            } catch (_: Exception) {}
        }
        
        var tempDate by remember { mutableStateOf(calendar.clone() as Calendar) }
        var viewingMonth by remember { mutableStateOf(calendar.clone() as Calendar) }

        androidx.compose.ui.window.Dialog(onDismissRequest = { showDatePicker = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .wrapContentHeight()
                    .scale(0.9f), 
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(8.dp), // 缩小 Padding
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. 月份切换与操作按钮 (合并到一行)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧：月份切换
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewingMonth = (viewingMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) } },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, null, tint = DeepGreen, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = SimpleDateFormat("yyyy年MM月", Locale.CHINA).format(viewingMonth.time),
                                style = MaterialTheme.typography.titleMedium,
                                color = DeepGreen,
                                fontWeight = FontWeight.ExtraBold
                            )
                            IconButton(
                                onClick = { viewingMonth = (viewingMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) } },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, null, tint = DeepGreen, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // 右侧：操作按钮 (红色叉号和绿色对号)
                        IconButton(onClick = { showDatePicker = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                birthday = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(tempDate.time)
                                showDatePicker = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Check, null, tint = DeepGreen, modifier = Modifier.size(22.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp)) // 进一步压缩间距

                    // 2. 自定义日历网格
                    val days = remember(viewingMonth) {
                        val list = mutableListOf<Calendar?>()
                        val cal = viewingMonth.clone() as Calendar
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) 
                        repeat(firstDayOfWeek - 1) { list.add(null) }
                        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        repeat(maxDays) {
                            list.add(cal.clone() as Calendar)
                            cal.add(Calendar.DAY_OF_MONTH, 1)
                        }
                        list
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp) // 统一行间距
                    ) {
                        // 周标题
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                                Text(
                                    text = it,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (it == "日" || it == "六") Color.Red.copy(alpha = 0.6f) else Color.Gray
                                )
                            }
                        }
                        
                        // 日期数字
                        val rows = days.chunked(7)
                        rows.forEach { week ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                week.forEach { day ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1.1f) // 微调高度比例，防止挤压
                                            .clip(CircleShape)
                                            .background(
                                                if (day != null && isSameDay(day, tempDate)) DeepGreen 
                                                else Color.Transparent
                                            )
                                            .clickable(enabled = day != null) {
                                                if (day != null) tempDate = day.clone() as Calendar
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (day != null) {
                                            Text(
                                                text = day.get(Calendar.DAY_OF_MONTH).toString(),
                                                color = if (isSameDay(day, tempDate)) Color.White else Color.DarkGray,
                                                fontWeight = if (isSameDay(day, tempDate)) FontWeight.Bold else FontWeight.Normal,
                                                style = MaterialTheme.typography.bodyLarge // 调大日期字号
                                            )
                                        }
                                    }
                                }
                                if (week.size < 7) {
                                    repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExcretionLogDialog(onConfirm: (ExcretionType, String?) -> Unit, onDismiss: () -> Unit) {
    var selectedType by remember { mutableStateOf(ExcretionType.POOP) }
    val shapes = if (selectedType == ExcretionType.POOP) listOf("正常", "软便", "稀便") else listOf("正常", "尿多", "尿少")
    
    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text("拉了?撒了?", fontWeight = FontWeight.Bold, color = DeepGreen) },
        text = { 
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 类型选择层
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally, 
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedType == ExcretionType.POOP) PoopColor.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { selectedType = ExcretionType.POOP }
                            .padding(8.dp)
                    ) {
                        Image(painterResource(R.drawable.ic_poop), null, modifier = Modifier.size(52.dp), contentScale = ContentScale.Fit)
                        Text("拉屎", color = if (selectedType == ExcretionType.POOP) PoopColor else Color.Gray, fontWeight = if (selectedType == ExcretionType.POOP) FontWeight.Bold else FontWeight.Normal)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally, 
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedType == ExcretionType.PEE) PeeColor.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { selectedType = ExcretionType.PEE }
                            .padding(8.dp)
                    ) {
                        Image(painterResource(R.drawable.ic_pee), null, modifier = Modifier.size(52.dp), contentScale = ContentScale.Fit)
                        Text("撒尿", color = if (selectedType == ExcretionType.PEE) PeeColor else Color.Gray, fontWeight = if (selectedType == ExcretionType.PEE) FontWeight.Bold else FontWeight.Normal)
                    }
                }

                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                // 状况选择层 (优化为一排三个单选按钮)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("状况如何?", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        shapes.forEach { shape ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if(selectedType == ExcretionType.POOP) PoopColor else PeeColor)
                                    .clickable { onConfirm(selectedType, shape); onDismiss() }
                            ) {
                                Text(
                                    text = shape,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }, 
        confirmButton = {}, 
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color.Gray) } }
    )
}

@Composable
fun DaySummaryPopover(
    viewModel: PetViewModel, 
    date: Calendar, 
    anchorOffset: androidx.compose.ui.geometry.Offset,
    anchorSize: androidx.compose.ui.unit.IntSize,
    parentBounds: androidx.compose.ui.geometry.Rect,
    layoutOffset: androidx.compose.ui.geometry.Offset,
    onDismiss: () -> Unit
) {
    val foodLogs by viewModel.foodLogs.collectAsState()
    val waterLogs by viewModel.waterLogs.collectAsState()
    val excretionLogs by viewModel.excretionLogs.collectAsState()
    val petProfile by viewModel.petProfile.collectAsState()

    val totalFood = foodLogs.filter { isSameDay(it.timestamp, date) && it.type == ConsumptionType.EAT }.sumOf { -it.amount.toDouble() }
    val totalWater = waterLogs.filter { isSameDay(it.timestamp, date) && it.type == ConsumptionType.EAT }.sumOf { -it.amount.toDouble() }
    val totalPoop = excretionLogs.count { isSameDay(it.timestamp, date) && it.type == ExcretionType.POOP }
    val totalPee = excretionLogs.count { isSameDay(it.timestamp, date) && it.type == ExcretionType.PEE }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val heightFactor = if (isLandscape) 2.0f else 1.6f
    
    val popWidthDp = (anchorSize.width / density.density * 1.4f).dp
    val popHeightDp = (anchorSize.height / density.density * heightFactor).dp

    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            Surface(
                modifier = Modifier
                    .offset {
                        val popWidthPx = anchorSize.width * 1.4f
                        val popHeightPx = anchorSize.height * heightFactor
                        
                        // anchorOffset 是 cell 的中心坐标 (Window 坐标系)
                        val targetX = anchorOffset.x - (popWidthPx / 2f)
                        val targetY = anchorOffset.y - (popHeightPx / 2f)
                        
                        // 严格约束在日历卡片内部
                        // 修正：确保 bottom - popHeightPx 计算出的起始坐标能让底部刚好贴合 parentBounds.bottom
                        val constrainedX = targetX.coerceIn(parentBounds.left, parentBounds.right - popWidthPx)
                        val constrainedY = targetY.coerceIn(parentBounds.top, parentBounds.bottom - popHeightPx)
                        
                        androidx.compose.ui.unit.IntOffset(
                            (constrainedX - layoutOffset.x).toInt(),
                            (constrainedY - layoutOffset.y).toInt()
                        )
                    }
                    .size(width = popWidthDp, height = popHeightDp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White, // 修改：改为完全不透明
                shadowElevation = 6.dp, 
                border = androidx.compose.foundation.BorderStroke(1.dp, DeepGreen.copy(alpha = 0.2f)) // 恢复边框
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top // 切换为顶部对齐，实现固定位置
                ) {
                    // 压缩顶部间距，从 10dp 减小到 6dp
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    val isBday = isBirthday(date, petProfile?.birthday)
                    val age = if (isBday) calculateAge(petProfile?.birthday, date) else 0

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(DeepGreen.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isBday) "🎂   $age" else SimpleDateFormat("MM/dd", Locale.CHINA).format(date.time),
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepGreen,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                    
                    // 压缩日期与内容之间的间距，从 4dp 减小到 2dp
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (totalFood > 0) PopoverItem(R.drawable.ic_food, "${"%.0f".format(totalFood)}g", FoodColor, 8.sp)
                        if (totalWater > 0) PopoverItem(R.drawable.ic_water, "${"%.0f".format(totalWater)}ml", WaterColor, 8.sp)
                        if (totalPoop > 0) PopoverItem(R.drawable.ic_poop, "${totalPoop}次", PoopColor, 8.sp)
                        if (totalPee > 0) PopoverItem(R.drawable.ic_pee, "${totalPee}次", PeeColor, 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PopoverItem(resId: Int, value: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .height(11.dp) // 强制固定较小行高，压缩行间距
            .padding(start = 10.dp)
    ) {
        Image(painterResource(resId), null, modifier = Modifier.size(9.dp), contentScale = ContentScale.Fit)
        Text(
            text = "  $value", 
            color = color, 
            fontWeight = FontWeight.Bold, 
            fontSize = fontSize, 
            maxLines = 1,
            lineHeight = 10.sp // 显式压缩行高
        )
    }
}

fun isBirthday(date: Calendar, birthdayStr: String?): Boolean {
    if (birthdayStr.isNullOrBlank()) return false
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val bDate = sdf.parse(birthdayStr)
        val bCal = Calendar.getInstance().apply { time = bDate!! }
        date.get(Calendar.MONTH) == bCal.get(Calendar.MONTH) && 
        date.get(Calendar.DAY_OF_MONTH) == bCal.get(Calendar.DAY_OF_MONTH)
    } catch (_: Exception) {
        false
    }
}

fun calculateAge(birthdayStr: String?, currentDate: Calendar): Int {
    if (birthdayStr.isNullOrBlank()) return 0
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val bDate = sdf.parse(birthdayStr)
        val bCal = Calendar.getInstance().apply { time = bDate!! }
        var age = currentDate.get(Calendar.YEAR) - bCal.get(Calendar.YEAR)
        // 还没过生日月日，则减 1
        if (currentDate.get(Calendar.MONTH) < bCal.get(Calendar.MONTH) || 
            (currentDate.get(Calendar.MONTH) == bCal.get(Calendar.MONTH) && currentDate.get(Calendar.DAY_OF_MONTH) < bCal.get(Calendar.DAY_OF_MONTH))) {
            age--
        }
        if (age < 0) 0 else age
    } catch (_: Exception) {
        0
    }
}

data class RecordItem(val timestamp: Long, val title: String, val value: String, val resId: Int, val color: Color, val rawData: Any)
fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
fun isSameDay(millis: Long, cal: Calendar): Boolean = isSameDay(Calendar.getInstance().apply { timeInMillis = millis }, cal)
