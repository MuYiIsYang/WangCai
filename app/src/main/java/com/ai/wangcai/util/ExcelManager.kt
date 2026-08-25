package com.ai.wangcai.util

import android.content.Context
import android.net.Uri
import com.ai.wangcai.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit

object ExcelManager {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    suspend fun exportData(
        context: Context,
        uri: Uri?,
        bowls: List<Bowl>,
        consumptionLogs: List<ConsumptionLog>,
        weightLogs: List<WeightLog>,
        medications: List<Medication>,
        medicationLogs: List<MedicationLog>,
        excretionLogs: List<ExcretionLog>,
        snacks: List<Snack>,
        snackLogs: List<SnackLog>,
        outputFile: File? = null
    ) = withContext(Dispatchers.IO) {
        val originalClassLoader = Thread.currentThread().contextClassLoader
        try {
            // Set ClassLoader for XmlBeans which is used by Apache POI
            Thread.currentThread().contextClassLoader = XSSFWorkbook::class.java.classLoader
            val workbook = XSSFWorkbook()

            // 1. 食具配置
            val bowlSheet = workbook.createSheet("食具配置")
            val bowlHeader = bowlSheet.createRow(0)
            listOf("编号", "名称", "皮重(g)", "类型", "已同步").forEachIndexed { i, s -> bowlHeader.createCell(i).setCellValue(s) }
            bowls.forEachIndexed { index, b ->
                val row = bowlSheet.createRow(index + 1)
                row.createCell(0).setCellValue(b.id.toDouble())
                row.createCell(1).setCellValue(b.name)
                row.createCell(2).setCellValue(b.tareWeight.toDouble())
                row.createCell(3).setCellValue(if (b.type == BowlType.FOOD) "粮食" else "水")
                row.createCell(4).setCellValue(if (b.isSynced) "是" else "否")
            }

            // 2. 饮食饮水记录
            val consumptionSheet = workbook.createSheet("饮食饮水记录")
            val consHeader = consumptionSheet.createRow(0)
            listOf("编号", "动作", "方式", "类型", "食具类型", "分量/份量", "当前总重", "时间戳", "记录时间", "已同步").forEachIndexed { i, s -> consHeader.createCell(i).setCellValue(s) }
            consumptionLogs.forEachIndexed { index, log ->
                val row = consumptionSheet.createRow(index + 1)
                row.createCell(0).setCellValue(log.id.toDouble())
                row.createCell(1).setCellValue(log.action)
                row.createCell(2).setCellValue(log.method)
                row.createCell(3).setCellValue(log.type.name)
                row.createCell(4).setCellValue(log.bowlType.name)
                row.createCell(5).setCellValue(log.amount.toDouble())
                row.createCell(6).setCellValue(log.grossWeight.toDouble())
                row.createCell(7).setCellValue(log.timestamp.toDouble())
                row.createCell(8).setCellValue(log.recordTime)
                row.createCell(9).setCellValue(if (log.isSynced) "是" else "否")
            }

            // 3. 体重记录
            val weightSheet = workbook.createSheet("体重记录")
            val weightHeader = weightSheet.createRow(0)
            listOf("编号", "体重(kg)", "备注", "时间戳", "记录时间", "已同步").forEachIndexed { i, s -> weightHeader.createCell(i).setCellValue(s) }
            weightLogs.forEachIndexed { index, log ->
                val row = weightSheet.createRow(index + 1)
                row.createCell(0).setCellValue(log.id.toDouble())
                row.createCell(1).setCellValue(log.weight.toDouble())
                row.createCell(2).setCellValue(log.note ?: "")
                row.createCell(3).setCellValue(log.timestamp.toDouble())
                row.createCell(4).setCellValue(log.recordTime)
                row.createCell(5).setCellValue(if (log.isSynced) "是" else "否")
            }

            // 4. 药品库
            val medSheet = workbook.createSheet("药品库")
            val medHeader = medSheet.createRow(0)
            listOf("编号", "名称", "单位", "已同步").forEachIndexed { i, s -> medHeader.createCell(i).setCellValue(s) }
            medications.forEachIndexed { index, m ->
                val row = medSheet.createRow(index + 1)
                row.createCell(0).setCellValue(m.id.toDouble())
                row.createCell(1).setCellValue(m.name)
                row.createCell(2).setCellValue(m.unit)
                row.createCell(3).setCellValue(if (m.isSynced) "是" else "否")
            }

            // 5. 用药打卡记录
            val medLogSheet = workbook.createSheet("用药打卡记录")
            val medLogHeader = medLogSheet.createRow(0)
            listOf("编号", "药品编号", "药品名称", "剂量", "时间戳", "记录时间", "已同步").forEachIndexed { i, s -> medLogHeader.createCell(i).setCellValue(s) }
            medicationLogs.forEachIndexed { index, log ->
                val row = medLogSheet.createRow(index + 1)
                row.createCell(0).setCellValue(log.id.toDouble())
                row.createCell(1).setCellValue(log.medicationId.toDouble())
                row.createCell(2).setCellValue(log.medicationName)
                row.createCell(3).setCellValue(log.dosage.toDouble())
                row.createCell(4).setCellValue(log.timestamp.toDouble())
                row.createCell(5).setCellValue(log.recordTime)
                row.createCell(6).setCellValue(if (log.isSynced) "是" else "否")
            }

            // 6. 拉撒记录
            val excretionSheet = workbook.createSheet("拉撒记录")
            val excretionHeader = excretionSheet.createRow(0)
            listOf("编号", "拉撒类型", "拉屎形态", "时间戳", "记录时间", "已同步").forEachIndexed { i, s -> excretionHeader.createCell(i).setCellValue(s) }
            excretionLogs.forEachIndexed { index, log ->
                val row = excretionSheet.createRow(index + 1)
                row.createCell(0).setCellValue(log.id.toDouble())
                row.createCell(1).setCellValue(if (log.type == ExcretionType.POOP) "拉屎" else "撒尿")
                row.createCell(2).setCellValue(log.shape ?: "")
                row.createCell(3).setCellValue(log.timestamp.toDouble())
                row.createCell(4).setCellValue(log.recordTime)
                row.createCell(5).setCellValue(if (log.isSynced) "是" else "否")
            }

            // 7. 零食库
            val snackTypeSheet = workbook.createSheet("零食库")
            val snackTypeHeader = snackTypeSheet.createRow(0)
            listOf("编号", "名称", "单位", "已同步").forEachIndexed { i, s -> snackTypeHeader.createCell(i).setCellValue(s) }
            snacks.forEachIndexed { index, s ->
                val row = snackTypeSheet.createRow(index + 1)
                row.createCell(0).setCellValue(s.id.toDouble())
                row.createCell(1).setCellValue(s.name)
                row.createCell(2).setCellValue(s.unit)
                row.createCell(3).setCellValue(if (s.isSynced) "是" else "否")
            }

            // 8. 零食打卡记录
            val snackLogSheet = workbook.createSheet("零食打卡记录")
            val snackLogHeader = snackLogSheet.createRow(0)
            listOf("编号", "零食编号", "零食名称", "分量/份量", "时间戳", "记录时间", "已同步").forEachIndexed { i, s -> snackLogHeader.createCell(i).setCellValue(s) }
            snackLogs.forEachIndexed { index, log ->
                val row = snackLogSheet.createRow(index + 1)
                row.createCell(0).setCellValue(log.id.toDouble())
                row.createCell(1).setCellValue(log.snackId.toDouble())
                row.createCell(2).setCellValue(log.snackName)
                row.createCell(3).setCellValue(log.amount.toDouble())
                row.createCell(4).setCellValue(log.timestamp.toDouble())
                row.createCell(5).setCellValue(log.recordTime)
                row.createCell(6).setCellValue(if (log.isSynced) "是" else "否")
            }

            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { workbook.write(it) }
            } else if (outputFile != null) {
                outputFile.outputStream().use { workbook.write(it) }
            }
            workbook.close()
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }

    suspend fun importData(context: Context, uri: Uri, dao: PetDao) = withContext(Dispatchers.IO) {
        val originalClassLoader = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = XSSFWorkbook::class.java.classLoader
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val workbook = XSSFWorkbook(stream)
                
                workbook.getSheet("食具配置")?.let { sheet ->
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val name = row.getCell(1).stringCellValue
                        val typeStr = row.getCell(3).stringCellValue
                        dao.insertBowl(Bowl(
                            name = name,
                            tareWeight = row.getCell(2).numericCellValue.toFloat(),
                            type = if (typeStr == "粮食") BowlType.FOOD else BowlType.WATER
                        ))
                    }
                }

                workbook.getSheet("饮食饮水记录")?.let { sheet ->
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val timeStr = row.getCell(1).stringCellValue
                        val ts = dateFormatter.parse(timeStr)?.time ?: continue
                        if (dao.countConsumptionLogAt(ts) == 0) {
                            val bowlTypeStr = row.getCell(2).stringCellValue
                            val actionTypeStr = row.getCell(3).stringCellValue
                            dao.insertConsumptionLog(ConsumptionLog(
                                timestamp = ts,
                                bowlType = if (bowlTypeStr == "粮食") BowlType.FOOD else BowlType.WATER,
                                type = when(actionTypeStr) {
                                    "加粮" -> ConsumptionType.ADD
                                    "进食" -> ConsumptionType.EAT
                                    else -> ConsumptionType.CLEAR
                                },
                                amount = row.getCell(4).numericCellValue.toFloat(),
                                grossWeight = row.getCell(5).numericCellValue.toFloat()
                            ))
                        }
                    }
                }

                workbook.getSheet("体重记录")?.let { sheet ->
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val timeStr = row.getCell(1).stringCellValue
                        val ts = dateFormatter.parse(timeStr)?.time ?: continue
                        if (dao.countWeightLogAt(ts) == 0) {
                            dao.insertWeightLog(WeightLog(
                                timestamp = ts,
                                weight = row.getCell(2).numericCellValue.toFloat()
                            ))
                        }
                    }
                }

                workbook.getSheet("药品库")?.let { sheet ->
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val name = row.getCell(1).stringCellValue
                        val unit = row.getCell(2).stringCellValue
                        if (dao.getMedicationByName(name) == null) {
                            dao.insertMedication(Medication(name = name, unit = unit))
                        }
                    }
                }
                workbook.getSheet("用药打卡记录")?.let { sheet ->
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val medId = row.getCell(1).numericCellValue.toLong()
                        val timeStr = row.getCell(2).stringCellValue
                        val ts = dateFormatter.parse(timeStr)?.time ?: continue
                        val dose = row.getCell(3).numericCellValue.toFloat()
                        if (dao.countMedicationLogAt(ts) == 0) {
                            dao.insertMedicationLog(MedicationLog(medicationId = medId, timestamp = ts, dosage = dose))
                        }
                    }
                }

                workbook.getSheet("拉撒记录")?.let { sheet ->
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val timeStr = row.getCell(1).stringCellValue
                        val ts = dateFormatter.parse(timeStr)?.time ?: continue
                        if (dao.countExcretionLogAt(ts) == 0) {
                            val typeStr = row.getCell(2).stringCellValue
                            dao.insertExcretionLog(ExcretionLog(
                                timestamp = ts,
                                type = if (typeStr == "拉屎") ExcretionType.POOP else ExcretionType.PEE
                            ))
                        }
                    }
                }

                workbook.getSheet("零食库")?.let { sheet ->
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val name = row.getCell(1).stringCellValue
                        val unit = row.getCell(2).stringCellValue
                        if (dao.getSnackByName(name) == null) {
                             dao.insertSnack(Snack(name = name, unit = unit))
                        }
                    }
                }
                workbook.getSheet("零食打卡记录")?.let { sheet ->
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val snackId = row.getCell(1).numericCellValue.toLong()
                        val timeStr = row.getCell(2).stringCellValue
                        val ts = dateFormatter.parse(timeStr)?.time ?: continue
                        val amount = row.getCell(3).numericCellValue.toFloat()
                        if (dao.countSnackLogAt(ts) == 0) {
                            dao.insertSnackLog(SnackLog(snackId = snackId, timestamp = ts, amount = amount))
                        }
                    }
                }

                workbook.close()
            }
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }

    suspend fun performAutoBackup(context: Context, 
                           bowls: List<Bowl>, cLogs: List<ConsumptionLog>, wLogs: List<WeightLog>, 
                           meds: List<Medication>, mLogs: List<MedicationLog>, eLogs: List<ExcretionLog>,
                           snacks: List<Snack>, sLogs: List<SnackLog>) = withContext(Dispatchers.IO) {
        
        val fileName = "autobackup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())}.xlsx"
        val resolver = context.contentResolver
        
        // 1. 使用 MediaStore 写入公共下载目录 (Download/WangCai)
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/WangCai")
        }

        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI

        try {
            val uri = resolver.insert(collection, contentValues)
            if (uri != null) {
                exportData(context, uri, bowls, cLogs, wLogs, meds, mLogs, eLogs, snacks, sLogs, null)
                
                // 清理旧备份 (保持最近 4 个)
                cleanupOldAutoBackups(context)
            }
        } catch (_: Exception) {
            // Fallback to internal storage if MediaStore fails
            val backupDir = File(context.getExternalFilesDir(null), "autobackups")
            if (!backupDir.exists()) backupDir.mkdirs()
            val file = File(backupDir, fileName)
            exportData(context, null, bowls, cLogs, wLogs, meds, mLogs, eLogs, snacks, sLogs, file)
        }
        
        context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE).edit {
            putLong("last_auto_backup", System.currentTimeMillis())
        }
    }

    private fun cleanupOldAutoBackups(context: Context) {
        val resolver = context.contentResolver
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DATE_ADDED)
        val selection = "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("Download/WangCai%")
        val sortOrder = "${android.provider.MediaStore.MediaColumns.DATE_ADDED} ASC"

        resolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
            if (cursor.count >= 5) {
                val countToDelete = cursor.count - 4
                var deleted = 0
                while (cursor.moveToNext() && deleted < countToDelete) {
                    val id = cursor.getLong(idColumn)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    resolver.delete(uri, null, null)
                    deleted++
                }
            }
        }
    }
}
