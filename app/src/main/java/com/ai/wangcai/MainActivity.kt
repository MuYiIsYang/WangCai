package com.ai.wangcai

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ai.wangcai.ui.DashboardScreen
import com.ai.wangcai.ui.DeepGreen
import com.ai.wangcai.ui.SelectAllOutlinedTextField
import com.ai.wangcai.ui.StatsScreen
import com.ai.wangcai.ui.SupabaseManagerScreen
import com.ai.wangcai.util.BackupWorker
import com.ai.wangcai.viewmodel.PetViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import android.content.ClipData
import android.os.Environment
import java.io.File

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure /Download/WangCai directory exists
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val wangCaiDir = File(downloadsDir, "WangCai")
        if (!wangCaiDir.exists()) {
            wangCaiDir.mkdirs()
        }

        // Schedule Auto-Backup
        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(2, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "auto_backup",
            ExistingPeriodicWorkPolicy.REPLACE,
            backupRequest
        )

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT, 
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT, 
                android.graphics.Color.TRANSPARENT
            )
        )

        val colorDeep = Color(0xFF659287)
        val colorLight = Color(0xFFB1D3B9)
        val colorBackground = Color.White 

        setContent {
            MaterialTheme {
                val viewModel: PetViewModel = viewModel()
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                
                var currentTab by rememberSaveable { mutableIntStateOf(0) }
                var dashboardKey by rememberSaveable { mutableLongStateOf(0L) }
                var showSupabaseManager by rememberSaveable { mutableStateOf(false) }

                // 全局 Supabase 配置弹窗状态
                var showSupabaseConfigDialog by rememberSaveable { mutableStateOf(false) }

                // 监听全局重定向事件 (自动弹出配置)
                LaunchedEffect(Unit) {
                    viewModel.redirectionEvent.collect { reason ->
                        if (reason == "CONFIG_NEEDED") {
                            showSupabaseConfigDialog = true
                        }
                    }
                }

                if (showSupabaseManager) {
                    BackHandler {
                        showSupabaseManager = false
                    }
                }
                
                if (showSupabaseConfigDialog) {
                    GlobalSupabaseConfigDialog(
                        viewModel = viewModel,
                        onDismiss = { showSupabaseConfigDialog = false }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = colorBackground,
                    contentColor = Color.Black,
                    bottomBar = {
                        if (!isLandscape && !showSupabaseManager) {
                            NavigationBar(containerColor = Color.White) {
                                NavigationBarItem(
                                    selected = currentTab == 0,
                                    onClick = { 
                                        if (currentTab == 0) dashboardKey = System.currentTimeMillis()
                                        currentTab = 0 
                                    },
                                    icon = { Icon(Icons.Default.Home, null) },
                                    label = { Text("首页") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colorDeep,
                                        selectedTextColor = colorDeep,
                                        indicatorColor = colorLight.copy(alpha = 0.3f)
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 },
                                    icon = { Icon(Icons.Default.BarChart, null) },
                                    label = { Text("统计") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colorDeep,
                                        selectedTextColor = colorDeep,
                                        indicatorColor = colorLight.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        // 主内容区
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (showSupabaseManager) {
                                SupabaseManagerScreen(viewModel)
                            } else {
                                when(currentTab) {
                                    0 -> key(currentTab, dashboardKey) { 
                                        DashboardScreen(
                                            viewModel = viewModel, 
                                            onSupabaseConfigClick = { showSupabaseConfigDialog = true }
                                        ) 
                                    }
                                    1 -> StatsScreen(viewModel)
                                }
                            }
                        }
                        
                        // 横屏下的右侧纵向导航
                        if (isLandscape && !showSupabaseManager) {
                            NavigationRail(
                                containerColor = Color.White,
                                modifier = Modifier.fillMaxHeight(),
                                header = {
                                    Image(
                                        painter = painterResource(id = R.drawable.logo),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).padding(vertical = 8.dp)
                                    )
                                }
                            ) {
                                NavigationRailItem(
                                    selected = currentTab == 0,
                                    onClick = { 
                                        if (currentTab == 0) dashboardKey = System.currentTimeMillis()
                                        currentTab = 0 
                                    },
                                    icon = { Icon(Icons.Default.Home, null) },
                                    label = { Text("首页") },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = colorDeep,
                                        selectedTextColor = colorDeep,
                                        indicatorColor = colorLight.copy(alpha = 0.3f)
                                    )
                                )
                                NavigationRailItem(
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 },
                                    icon = { Icon(Icons.Default.BarChart, null) },
                                    label = { Text("统计") },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = colorDeep,
                                        selectedTextColor = colorDeep,
                                        indicatorColor = colorLight.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalSupabaseConfigDialog(viewModel: PetViewModel, onDismiss: () -> Unit) {
    var rawText by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    val currentConfig by viewModel.supabaseConfig.collectAsState()

    if (showTutorial) {
        SupabaseTutorialDialog(onDismiss = { showTutorial = false })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "粘贴 Supabase 配置", 
                        fontSize = 13.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = DeepGreen
                    )
                    if (currentConfig.isValid) {
                        Text(
                            text = "当前已连接: ${currentConfig.url.take(25)}...", 
                            fontSize = 9.sp, 
                            color = Color.Gray,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
                if (currentConfig.isValid) {
                    Button(
                        onClick = { viewModel.clearSupabaseConfig(); onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        Text("断开连接", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("请直接粘贴完整的配置内容块:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    TextButton(
                        onClick = { showTutorial = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("查看配置教程", fontSize = 11.sp, color = DeepGreen, fontWeight = FontWeight.Bold)
                    }
                }
                // 重要：不要在 TextField 外面包裹 SelectionContainer，防止小米系统长按闪退
                SelectAllOutlinedTextField(
                    value = rawText, 
                    onValueChange = { rawText = it; parseError = false }, 
                    placeholder = { Text("SUPABASE_URL=...\nSUPABASE_PUBLISHABLE_KEY=...") },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    isError = parseError,
                    supportingText = { if (parseError) Text("解析失败，请检查格式是否正确", color = Color.Red) }
                )
                Text("格式要求: 每行一个 KEY=VALUE", fontSize = 10.sp, color = Color.LightGray)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (viewModel.parseAndSaveConfig(rawText)) {
                        onDismiss()
                    } else {
                        parseError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
            ) {
                Text("解析并保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun SupabaseTutorialDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val sqlCode = remember {
        """
-- 1. 宠物档案
CREATE TABLE IF NOT EXISTS "public"."宠物档案" (
    "编号" bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    "昵称" text NOT NULL,
    "品种" text,
    "生日" date,
    "创建时间" timestamp with time zone DEFAULT now()
);

-- 2. 体重记录
CREATE TABLE IF NOT EXISTS "public"."体重记录" (
    "编号" bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    "体重" numeric NOT NULL,
    "备注" text,
    "记录时间" timestamp with time zone NOT NULL DEFAULT now()
);

-- 3. 拉撒记录
CREATE TABLE IF NOT EXISTS "public"."拉撒记录" (
    "编号" bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    "拉撒类型" text NOT NULL,
    "拉撒形态" text,
    "记录时间" timestamp with time zone NOT NULL DEFAULT now()
);

-- 4. 药品库
CREATE TABLE IF NOT EXISTS "public"."药品库" (
    "编号" bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    "药品名称" text NOT NULL,
    "剂量单位" text NOT NULL
);

-- 5. 用药打卡记录
CREATE TABLE IF NOT EXISTS "public"."用药打卡记录" (
    "编号" bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    "药品名称" text NOT NULL,
    "用药剂量" numeric NOT NULL,
    "用药时间" timestamp with time zone NOT NULL DEFAULT now(),
    "药品编号" bigint
);

-- 6. 零食库
CREATE TABLE IF NOT EXISTS "public"."零食库" (
    "编号" bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    "零食名称" text NOT NULL,
    "计量单位" text NOT NULL
);

-- 7. 零食打卡记录
CREATE TABLE IF NOT EXISTS "public"."零食打卡记录" (
    "编号" bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    "零食名称" text NOT NULL,
    "喂食数量" numeric NOT NULL,
    "喂食时间" timestamp with time zone NOT NULL DEFAULT now(),
    "零食编号" bigint
);

-- 8. 食具配置
CREATE TABLE IF NOT EXISTS "public"."食具配置" (
    "编号" bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    "吃喝碗" text NOT NULL,
    "净重" numeric
);

-- 9. 饮食饮水记录
CREATE TABLE IF NOT EXISTS "public"."饮食饮水记录" (
    "编号" bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    "吃喝方式" text NOT NULL,
    "动作" text NOT NULL,
    "变动数值" numeric NOT NULL,
    "记录时间" timestamp with time zone NOT NULL DEFAULT now()
);
        """.trimIndent()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Supabase 创建教程",
                    color = DeepGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                TextButton(onClick = {
                    scope.launch {
                        // 使用 ClipData.newPlainText 创建 ClipEntry
                        val clipEntry = ClipEntry(ClipData.newPlainText("SQL Code", sqlCode))
                        clipboard.setClipEntry(clipEntry)
                        Toast.makeText(context, "SQL 代码已复制", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("点击一键复制", fontSize = 12.sp, color = DeepGreen)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "步骤说明：\n1. 登录 Supabase 后创建一个新项目。\n2. 进入 SQL Editor 页面。\n3. 点击 'New query'，将下方代码粘贴并运行 (Run)。\n4. 在 Project Settings -> API 页面获取 URL 和 Key。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    lineHeight = 18.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = sqlCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF333333)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = DeepGreen, fontWeight = FontWeight.Bold)
            }
        }
    )
}



