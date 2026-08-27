package com.ai.wangcai.util

import android.content.Context
import android.net.Uri
import com.ai.wangcai.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit

object ExcelManager {

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
        petProfile: PetProfile? = null,
        outputFile: File? = null
    ) = withContext(Dispatchers.IO) {
        val originalClassLoader = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = XSSFWorkbook::class.java.classLoader
            val workbook = XSSFWorkbook()

            // 1. 食具配置 (编号, 吃喝碗, 净重)
            val bowlSheet = workbook.createSheet("食具配置")
            val bowlHeader = bowlSheet.createRow(0)
            listOf("编号", "吃喝碗", "净重").forEachIndexed { i, s -> bowlHeader.createCell(i).setCellValue(s) }
            bowls.forEachIndexed { index, b ->
                val row = bowlSheet.createRow(index + 1)
                row.createCell(0).setCellValue(b.id) // UUID String
                row.createCell(1).setCellValue(b.name)
                row.createCell(2).setCellValue(b.tareWeight.toDouble())
            }

            // 2. 饮食饮水记录 (编号, 吃喝方式, 动作, 变动数值, 记录时间)
            val consumptionSheet = workbook.createSheet("饮食饮水记录")
            val consHeader = consumptionSheet.createRow(0)
            listOf("编号", "吃喝方式", "动作", "变动数值", "记录时间").forEachIndexed { i, s -> consHeader.createCell(i).setCellValue(s) }
            consumptionLogs.forEachIndexed { index, log ->
                val row = consumptionSheet.createRow(index + 1)
                row.createCell(0).setCellValue(log.id)
                row.createCell(1).setCellValue(log.method)
                row.createCell(2).setCellValue(log.action)
                row.createCell(3).setCellValue(log.amount.toDouble())
                row.createCell(4).setCellValue(log.recordTime)
            }

            // 3. 体重记录 (编号, 体重, 备注, 记录时间)
            val weightSheet = workbook.createSheet("体重记录")
            val weightHeader = weightSheet.createRow(0)
            listOf("编号", "体重", "备注", "记录时间").forEachIndexed { i, s -> weightHeader.createCell(i).setCellValue(s) }
            weightLogs.forEachIndexed { index, log ->
                val row = weightSheet.createRow(index + 1)
                row.createCell(0).setCellValue(log.id)
                row.createCell(1).setCellValue(log.weight.toDouble())
                row.createCell(2).setCellValue(log.note ?: "")
                row.createCell(3).setCellValue(log.recordTime)
            }

            // 4. 药品库 (编号, 药品名称, 剂量单位)
            val medSheet = workbook.createSheet("药品库")
            val medHeader = medSheet.createRow(0)
            listOf("编号", "药品名称", "剂量单位").forEachIndexed { i, s -> medHeader.createCell(i).setCellValue(s) }
            medications.forEachIndexed { index, m ->
                val row = medSheet.createRow(index + 1)
                row.createCell(0).setCellValue(m.id)
                row.createCell(1).setCellValue(m.name)
                row.createCell(2).setCellValue(m.unit)
            }

            // 5. 用药打卡记录 (编号, 药品名称, 用药剂量, 记录时间, 药品编号)
            val medLogSheet = workbook.createSheet("用药打卡记录")
            val medLogHeader = medLogSheet.createRow(0)
            listOf("编号", "药品名称", "用药剂量", "记录时间", "药品编号").forEachIndexed { i, s -> medLogHeader.createCell(i).setCellValue(s) }
            medicationLogs.forEachIndexed { index, log ->
                val row = medLogSheet.createRow(index + 1)
                row.createCell(0).setCellValue(log.id)
                row.createCell(1).setCellValue(log.medicationName)
                row.createCell(2).setCellValue(log.dosage.toDouble())
                row.createCell(3).setCellValue(log.recordTime)
                row.createCell(4).setCellValue(log.medicationId)
            }

            // 6. 拉撒记录 (编号, 拉撒类型, 拉撒形态, 记录时间)
            val excretionSheet = workbook.createSheet("拉撒记录")
            val excretionHeader = excretionSheet.createRow(0)
            listOf("编号", "拉撒类型", "拉撒形态", "记录时间").forEachIndexed { i, s -> excretionHeader.createCell(i).setCellValue(s) }
            excretionLogs.forEachIndexed { index, log ->
                val row = excretionSheet.createRow(index + 1)
                row.createCell(0).setCellValue(log.id)
                row.createCell(1).setCellValue(if (log.type == ExcretionType.POOP) "拉屎" else "撒尿")
                row.createCell(2).setCellValue(log.shape ?: "")
                row.createCell(3).setCellValue(log.recordTime)
            }

            // 7. 零食库 (编号, 零食名称, 计量单位)
            val snackTypeSheet = workbook.createSheet("零食库")
            val snackTypeHeader = snackTypeSheet.createRow(0)
            listOf("编号", "零食名称", "计量单位").forEachIndexed { i, s -> snackTypeHeader.createCell(i).setCellValue(s) }
            snacks.forEachIndexed { index, s ->
                val row = snackTypeSheet.createRow(index + 1)
                row.createCell(0).setCellValue(s.id)
                row.createCell(1).setCellValue(s.name)
                row.createCell(2).setCellValue(s.unit)
            }

            // 8. 零食打卡记录 (编号, 零食名称, 喂食数量, 记录时间, 零食编号)
            val snackLogSheet = workbook.createSheet("零食打卡记录")
            val snackLogHeader = snackLogSheet.createRow(0)
            listOf("编号", "零食名称", "喂食数量", "记录时间", "零食编号").forEachIndexed { i, s -> snackLogHeader.createCell(i).setCellValue(s) }
            snackLogs.forEachIndexed { index, log ->
                val row = snackLogSheet.createRow(index + 1)
                row.createCell(0).setCellValue(log.id)
                row.createCell(1).setCellValue(log.snackName)
                row.createCell(2).setCellValue(log.amount.toDouble())
                row.createCell(3).setCellValue(log.recordTime)
                row.createCell(4).setCellValue(log.snackId)
            }

            // 9. 宠物档案 (编号, 昵称, 品种, 生日, 记录时间)
            petProfile?.let { p ->
                val petSheet = workbook.createSheet("宠物档案")
                val petHeader = petSheet.createRow(0)
                listOf("编号", "昵称", "品种", "生日", "记录时间").forEachIndexed { i, s -> petHeader.createCell(i).setCellValue(s) }
                val row = petSheet.createRow(1)
                row.createCell(0).setCellValue(p.id)
                row.createCell(1).setCellValue(p.nickname ?: "")
                row.createCell(2).setCellValue(p.breed ?: "")
                row.createCell(3).setCellValue(p.birthday ?: "")
                row.createCell(4).setCellValue(p.recordTime)
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
                
                fun getHeaderMap(sheet: org.apache.poi.ss.usermodel.Sheet): Map<String, Int> {
                    val map = mutableMapOf<String, Int>()
                    val headerRow = sheet.getRow(0) ?: return map
                    for (i in 0 until headerRow.lastCellNum) {
                        val cell = headerRow.getCell(i)
                        if (cell != null) map[cell.toString().trim()] = i
                    }
                    return map
                }

                fun getVal(row: org.apache.poi.ss.usermodel.Row, map: Map<String, Int>, vararg keys: String): String {
                    keys.forEach { key ->
                        val idx = map[key]
                        if (idx != null) {
                            val cell = row.getCell(idx) ?: return@forEach
                            return try {
                                when (cell.cellType) {
                                    org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                                        if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                                            val date = cell.dateCellValue
                                            if (date != null) {
                                                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.CHINA).format(date)
                                            } else ""
                                        } else {
                                            val d = cell.numericCellValue
                                            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                                        }
                                    }
                                    org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue.trim()
                                    org.apache.poi.ss.usermodel.CellType.BLANK -> ""
                                    else -> cell.toString().trim()
                                }
                            } catch (e: Exception) {
                                cell.toString().trim()
                            }
                        }
                    }
                    return ""
                }

                // 1. 食具配置 - 关键修复：导入时按名称查重，防止产生重复 UUID
                workbook.getSheet("食具配置")?.let { sheet ->
                    val m = getHeaderMap(sheet)
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val name = getVal(row, m, "吃喝碗", "名称").trim()
                        val tare = getVal(row, m, "净重", "皮重(g)").toFloatOrNull() ?: 0f
                        if (name.isNotBlank()) {
                            val existing = dao.getBowlByType(if (name.contains("水")) BowlType.WATER else BowlType.FOOD).first()
                            dao.insertBowl(Bowl(
                                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name,
                                tareWeight = tare,
                                type = if (name.contains("水")) BowlType.WATER else BowlType.FOOD
                            ))
                        }
                    }
                }

                // 2. 饮食饮水记录
                workbook.getSheet("饮食饮水记录")?.let { sheet ->
                    val m = getHeaderMap(sheet)
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val id = getVal(row, m, "编号")
                        val method = getVal(row, m, "吃喝方式", "方式")
                        val action = getVal(row, m, "动作")
                        val amount = getVal(row, m, "变动数值").toFloatOrNull() ?: 0f
                        val timeStr = getVal(row, m, "记录时间").replace(" ", "T")
                        
                        if (timeStr.isNotBlank() && dao.countConsumptionLogAt(timeStr) == 0) {
                            dao.insertConsumptionLog(ConsumptionLog(
                                id = if (id.length > 10) id else java.util.UUID.randomUUID().toString(),
                                timestamp = timeStr.toTimestamp(),
                                recordTime = timeStr,
                                bowlType = if (method.contains("水")) BowlType.WATER else BowlType.FOOD,
                                method = method,
                                action = action,
                                amount = amount,
                                type = when(action) { "增加" -> ConsumptionType.ADD; "减少" -> ConsumptionType.EAT; else -> ConsumptionType.CLEAR }
                            ))
                        }
                    }
                }

                // 3. 体重记录
                workbook.getSheet("体重记录")?.let { sheet ->
                    val m = getHeaderMap(sheet)
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val id = getVal(row, m, "编号")
                        val weight = getVal(row, m, "体重").toFloatOrNull() ?: 0f
                        val note = getVal(row, m, "备注")
                        val timeStr = getVal(row, m, "记录时间").replace(" ", "T")
                        if (timeStr.isNotBlank() && dao.countWeightLogAt(timeStr) == 0) {
                            dao.insertWeightLog(WeightLog(
                                id = if (id.length > 10) id else java.util.UUID.randomUUID().toString(),
                                timestamp = timeStr.toTimestamp(),
                                recordTime = timeStr,
                                weight = weight,
                                note = note.ifBlank { null }
                            ))
                        }
                    }
                }

                // 4. 药品相关
                workbook.getSheet("药品库")?.let { sheet ->
                    val m = getHeaderMap(sheet)
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val id = getVal(row, m, "编号")
                        val name = getVal(row, m, "药品名称")
                        val unit = getVal(row, m, "剂量单位")
                        if (name.isNotBlank() && dao.getMedicationByName(name) == null) {
                            dao.insertMedication(Medication(
                                id = if (id.length > 10) id else java.util.UUID.randomUUID().toString(),
                                name = name,
                                unit = unit
                            ))
                        }
                    }
                }
                workbook.getSheet("用药打卡记录")?.let { sheet ->
                    val m = getHeaderMap(sheet)
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val id = getVal(row, m, "编号")
                        val medName = getVal(row, m, "药品名称").trim()
                        val dose = getVal(row, m, "用药剂量").toFloatOrNull() ?: 0f
                        val oldMedId = getVal(row, m, "药品编号").trim()
                        val timeStr = getVal(row, m, "记录时间").replace(" ", "T").trim()
                        
                        // 关键修复：根据药品名称查找新的 UUID，避免由于 ID 不匹配导致统计页不显示
                        val realMedicationId = if (medName.isNotBlank()) {
                            dao.getMedicationByName(medName)?.id ?: oldMedId
                        } else oldMedId

                        if (timeStr.isNotBlank() && dao.countMedicationLogAt(timeStr) == 0) {
                            dao.insertMedicationLog(MedicationLog(
                                id = if (id.length > 10) id else java.util.UUID.randomUUID().toString(),
                                medicationId = realMedicationId,
                                medicationName = medName,
                                timestamp = timeStr.toTimestamp(),
                                recordTime = timeStr,
                                dosage = dose
                            ))
                        }
                    }
                }

                // 5. 拉撒记录
                workbook.getSheet("拉撒记录")?.let { sheet ->
                    val m = getHeaderMap(sheet)
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val id = getVal(row, m, "编号")
                        val typeStr = getVal(row, m, "拉撒类型")
                        val shape = getVal(row, m, "拉撒形态")
                        val timeStr = getVal(row, m, "记录时间").replace(" ", "T")
                        if (timeStr.isNotBlank() && dao.countExcretionLogAt(timeStr) == 0) {
                            dao.insertExcretionLog(ExcretionLog(
                                id = if (id.length > 10) id else java.util.UUID.randomUUID().toString(),
                                timestamp = timeStr.toTimestamp(),
                                recordTime = timeStr,
                                type = if (typeStr == "拉屎") ExcretionType.POOP else ExcretionType.PEE,
                                shape = shape.ifBlank { null }
                            ))
                        }
                    }
                }

                // 6. 零食相关
                workbook.getSheet("零食库")?.let { sheet ->
                    val m = getHeaderMap(sheet)
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val id = getVal(row, m, "编号")
                        val name = getVal(row, m, "零食名称")
                        val unit = getVal(row, m, "计量单位")
                        if (name.isNotBlank() && dao.getSnackByName(name) == null) {
                             dao.insertSnack(Snack(
                                 id = if (id.length > 10) id else java.util.UUID.randomUUID().toString(),
                                 name = name,
                                 unit = unit
                             ))
                        }
                    }
                }
                workbook.getSheet("零食打卡记录")?.let { sheet ->
                    val m = getHeaderMap(sheet)
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val id = getVal(row, m, "编号")
                        val snackName = getVal(row, m, "零食名称").trim()
                        val amount = getVal(row, m, "喂食数量").toFloatOrNull() ?: 0f
                        val oldSnackId = getVal(row, m, "零食编号").trim()
                        val timeStr = getVal(row, m, "记录时间").replace(" ", "T").trim()

                        // 关键修复：根据零食名称查找新的 UUID
                        val realSnackId = if (snackName.isNotBlank()) {
                            dao.getSnackByName(snackName)?.id ?: oldSnackId
                        } else oldSnackId

                        if (timeStr.isNotBlank() && dao.countSnackLogAt(timeStr) == 0) {
                            dao.insertSnackLog(SnackLog(
                                id = if (id.length > 10) id else java.util.UUID.randomUUID().toString(),
                                snackId = realSnackId,
                                snackName = snackName,
                                timestamp = timeStr.toTimestamp(),
                                recordTime = timeStr,
                                amount = amount
                            ))
                        }
                    }
                }

                // 7. 宠物档案
                workbook.getSheet("宠物档案")?.let { sheet ->
                    val m = getHeaderMap(sheet)
                    val row = sheet.getRow(1) ?: return@let
                    val id = getVal(row, m, "编号")
                    val nickname = getVal(row, m, "昵称")
                    val breed = getVal(row, m, "品种")
                    val birthday = getVal(row, m, "生日")
                    val timeStr = getVal(row, m, "记录时间").replace(" ", "T")
                    if (nickname.isNotBlank()) {
                        dao.insertPetProfile(PetProfile(
                            id = if (id.length > 10) id else java.util.UUID.randomUUID().toString(),
                            nickname = nickname,
                            breed = breed.ifBlank { null },
                            birthday = birthday.ifBlank { null },
                            timestamp = timeStr.toTimestamp(),
                            recordTime = timeStr
                        ))
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
                           snacks: List<Snack>, sLogs: List<SnackLog>, petProfile: PetProfile?) = withContext(Dispatchers.IO) {
        
        val fileName = "自动_${SimpleDateFormat("MMdd_HHmmss", Locale.CHINA).format(Date())}.xlsx"
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/WangCai")
        }
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        try {
            val uri = resolver.insert(collection, contentValues)
            if (uri != null) {
                exportData(context, uri, bowls, cLogs, wLogs, meds, mLogs, eLogs, snacks, sLogs, petProfile, null)
                cleanupOldAutoBackups(context)
            }
        } catch (_: Exception) {
            val backupDir = File(context.getExternalFilesDir(null), "autobackups")
            if (!backupDir.exists()) backupDir.mkdirs()
            val file = File(backupDir, fileName)
            exportData(context, null, bowls, cLogs, wLogs, meds, mLogs, eLogs, snacks, sLogs, petProfile, file)
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
