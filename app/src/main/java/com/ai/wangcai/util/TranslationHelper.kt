package com.ai.wangcai.util

object TranslationHelper {
    private val tableMap = mapOf(
        "bowls" to "食具配置",
        "consumption_logs" to "饮食饮水记录",
        "weight_logs" to "体重记录",
        "medications" to "药品库",
        "medication_logs" to "药品打卡记录",
        "excretion_logs" to "拉撒记录",
        "snacks" to "零食库",
        "snack_logs" to "零食打卡记录",
        "activity_logs" to "操作记录",
        "pet_profiles" to "宠物档案",
        "食具配置" to "食具配置",
        "饮食饮水记录" to "饮食饮水记录",
        "体重记录" to "体重记录",
        "药品库" to "药品库",
        "medication_logs" to "用药打卡记录",
        "药品打卡记录" to "用药打卡记录",
        "用药打卡记录" to "用药打卡记录",
        "拉撒记录" to "拉撒记录",
        "零食库" to "零食库",
        "零食打卡记录" to "零食打卡记录",
        "操作记录" to "操作记录",
        "宠物档案" to "宠物档案"
    )

    private val columnMap = mapOf(
        "id" to "编号",
        "name" to "药品名称",
        "tareWeight" to "净重",
        "type" to "拉撒类型",
        "isSynced" to "已同步",
        "timestamp" to "时间戳",
        "recordTime" to "记录时间",
        "amount" to "喂食数量",
        "grossWeight" to "变动数值",
        "action" to "动作",
        "method" to "吃喝方式",
        "bowlType" to "食具类型",
        "weight" to "体重",
        "note" to "备注",
        "unit" to "剂量单位",
        "medicationId" to "药品编号",
        "medicationName" to "药品名称",
        "dosage" to "用药剂量",
        "shape" to "拉撒形态",
        "snackId" to "零食编号",
        "snackName" to "零食名称",
        "entityType" to "对象类型",
        "details" to "详情",
        "nickname" to "昵称",
        "breed" to "品种",
        "birthday" to "生日",
        "avatarPath" to "头像路径",
        "createdAt" to "记录时间",
        "编号" to "编号"
    )

    fun translateTable(name: String): String {
        return tableMap[name] ?: name
    }

    fun translateColumn(name: String): String {
        return columnMap[name] ?: name
    }

    fun translateValue(value: String): String {
        return when (value) {
            "POOP" -> "拉屎"
            "PEE" -> "撒尿"
            "ADD" -> "增加"
            "EAT" -> "减少"
            "CLEAR" -> "清空"
            "FOOD" -> "粮食"
            "WATER" -> "水"
            "TRUE", "true" -> "是"
            "FALSE", "false" -> "否"
            else -> value
        }
    }

    fun getColumnOrder(tableName: String): List<String> {
        return when (tableName) {
            "bowls", "食具配置" -> listOf("id", "name", "tareWeight")
            "consumption_logs", "饮食饮水记录" -> listOf("id", "method", "action", "amount", "recordTime")
            "weight_logs", "体重记录" -> listOf("id", "weight", "note", "recordTime")
            "medications", "药品库" -> listOf("id", "name", "unit")
            "medication_logs", "用药打卡记录" -> listOf("id", "medicationName", "dosage", "recordTime", "medicationId")
            "excretion_logs", "拉撒记录" -> listOf("id", "type", "shape", "recordTime")
            "snacks", "零食库" -> listOf("id", "name", "unit")
            "snack_logs", "零食打卡记录" -> listOf("id", "snackName", "amount", "recordTime", "snackId")
            "pet_profiles", "宠物档案" -> listOf("id", "nickname", "breed", "birthday", "recordTime")
            else -> emptyList()
        }
    }
}
