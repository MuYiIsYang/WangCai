package com.ai.wangcai.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString

class SupabaseRepository {
    private var config = SupabaseConfig()

    data class NetworkResult(
        val isSuccess: Boolean,
        val statusCode: Int,
        val requestBody: String,
        val responseBody: String
    )

    private val jsonSerializer = Json { 
        encodeDefaults = true 
        ignoreUnknownKeys = true 
        isLenient = true
    }

    fun serializeForSync(data: Any): String {
        return try {
            val element = when (data) {
                is ConsumptionLog -> jsonSerializer.encodeToJsonElement(data)
                is WeightLog -> jsonSerializer.encodeToJsonElement(data)
                is MedicationLog -> jsonSerializer.encodeToJsonElement(data)
                is ExcretionLog -> jsonSerializer.encodeToJsonElement(data)
                is SnackLog -> jsonSerializer.encodeToJsonElement(data)
                is Medication -> jsonSerializer.encodeToJsonElement(data)
                is Snack -> jsonSerializer.encodeToJsonElement(data)
                is Bowl -> jsonSerializer.encodeToJsonElement(data)
                is PetProfile -> jsonSerializer.encodeToJsonElement(data)
                else -> jsonSerializer.encodeToJsonElement(data.toString())
            }
            
            if (element is JsonObject) {
                // 【智选模式】仅保留包含中文字符的 Key（因为云端列名全是中文）
                // 这能自动过滤掉所有本地控制字段（isSynced, type, timestamp 等）
                val content = element.toMutableMap()
                val keysToRemove = content.keys.filter { key ->
                    !key.any { it.code in 0x4E00..0x9FA5 }
                }
                keysToRemove.forEach { content.remove(it) }
                
                val cleanedJson = JsonObject(content).toString()
                Log.d("SupabaseSync", "Final Cleaned JSON: $cleanedJson")
                cleanedJson
            } else {
                element.toString()
            }
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Serialize error: ${e.message}")
            "{ \"error\": \"serialization_failed\" }"
        }
    }

    fun updateConfig(newConfig: SupabaseConfig) {
        config = newConfig
    }

    private val supabaseUrl get() = config.url
    private val secretKey get() = config.secretKey

    val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(jsonSerializer)
        }
    }

    suspend fun upsertData(tableName: String, data: Any): NetworkResult {
        val jsonString = serializeForSync(data)
        return try {
            val response = client.post("$supabaseUrl/rest/v1/$tableName") {
                parameter("on_conflict", "编号") 
                header("apikey", secretKey)
                header("Authorization", "Bearer $secretKey")
                header("Prefer", "resolution=merge-duplicates,return=representation")
                contentType(ContentType.Application.Json)
                setBody(Json.parseToJsonElement(jsonString))
            }
            val status = response.status.value
            val responseText = response.bodyAsText()
            Log.d("SupabaseSync", "UPSERT $tableName | Status: $status | Body: $jsonString")
            NetworkResult(status < 400, status, jsonString, responseText)
        } catch (e: Exception) {
            Log.e("SupabaseSync", "UPSERT Error: ${e.localizedMessage}")
            NetworkResult(false, 0, jsonString, e.localizedMessage ?: "Network Error")
        }
    }

    internal suspend inline fun <reified T> fetchTableData(tableName: String, filter: String? = null): List<T> {
        return try {
            val response = client.get("$supabaseUrl/rest/v1/$tableName") {
                parameter("select", "*")
                filter?.let { f ->
                    f.split("&").forEach { pair ->
                        val parts = pair.split("=", limit = 2)
                        if (parts.size == 2) parameter(parts[0], parts[1])
                    }
                }
                header("apikey", secretKey)
                header("Authorization", "Bearer $secretKey")
            }
            val responseText = response.bodyAsText()
            if (response.status.value >= 400) return emptyList()
            jsonSerializer.decodeFromString<List<T>>(responseText)
        } catch (e: Exception) { emptyList() }
    }

    suspend fun fetchLatestRow(tableName: String): JsonObject? {
        return try {
            val responseText = client.get("$supabaseUrl/rest/v1/$tableName") {
                parameter("select", "*")
                parameter("limit", "1")
                parameter("order", "编号.desc")
                header("apikey", secretKey)
                header("Authorization", "Bearer $secretKey")
            }.bodyAsText()
            jsonSerializer.decodeFromString<List<JsonObject>>(responseText).firstOrNull()
        } catch (e: Exception) { null }
    }

    suspend fun deleteData(tableName: String, id: String): NetworkResult {
        return try {
            val response = client.delete("$supabaseUrl/rest/v1/$tableName") {
                parameter("编号", "eq.$id")
                header("apikey", secretKey)
                header("Authorization", "Bearer $secretKey")
                header("Prefer", "return=representation")
            }
            val status = response.status.value
            val responseText = response.bodyAsText()
            NetworkResult(status < 400, status, "{\"编号\":\"$id\"}", responseText)
        } catch (e: Exception) {
            NetworkResult(false, 0, "{\"编号\":\"$id\"}", e.localizedMessage ?: "Delete Error")
        }
    }

    suspend fun getAllSupabaseTables(): List<String> {
        return try {
            val responseText = client.get("$supabaseUrl/rest/v1/") {
                header("apikey", secretKey)
                header("Authorization", "Bearer $secretKey")
            }.bodyAsText()
            val rootObj = Json.parseToJsonElement(responseText).jsonObject
            val tables = mutableSetOf<String>()
            rootObj["paths"]?.jsonObject?.keys?.forEach { path ->
                val clean = path.trim('/')
                if (clean.isNotEmpty() && !clean.startsWith("rpc/")) tables.add(clean)
            }
            tables.toList().sorted()
        } catch (e: Exception) { emptyList() }
    }
}
