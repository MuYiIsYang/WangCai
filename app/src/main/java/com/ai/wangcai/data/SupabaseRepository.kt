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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.decodeFromString

class SupabaseRepository {
    private var config = SupabaseConfig()

    fun updateConfig(newConfig: SupabaseConfig) {
        config = newConfig
    }

    private val supabaseUrl get() = config.url
    private val secretKey get() = config.secretKey

    val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = true
            })
        }
    }

    // 复刻 testsipabase 的 upsert 逻辑
    suspend fun upsertData(tableName: String, data: Any) {
        try {
            val response = client.post("$supabaseUrl/rest/v1/$tableName") {
                parameter("on_conflict", "编号") // 明确指定冲突检查字段
                header("apikey", secretKey)
                header("Authorization", "Bearer $secretKey")
                header("Prefer", "resolution=merge-duplicates")
                contentType(ContentType.Application.Json)
                setBody(data)
            }
            val status = response.status.value
            Log.d("SupabaseSync", "Upsert $tableName Status: $status")
            if (status >= 400) {
                val errorBody = response.bodyAsText()
                Log.e("SupabaseSync", "Upsert $tableName Error Body: $errorBody")
            }
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Upsert $tableName Error: ${e.localizedMessage}")
        }
    }

    internal suspend inline fun <reified T> fetchTableData(tableName: String): List<T> {
        return try {
            val responseText = client.get("$supabaseUrl/rest/v1/$tableName") {
                parameter("select", "*")
                header("apikey", secretKey)
                header("Authorization", "Bearer $secretKey")
            }.bodyAsText()
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<List<T>>(responseText)
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Fetch $tableName Error: ${e.localizedMessage}")
            emptyList()
        }
    }

    suspend fun fetchLatestRow(tableName: String): kotlinx.serialization.json.JsonObject? {
        return try {
            val responseText = client.get("$supabaseUrl/rest/v1/$tableName") {
                parameter("select", "*")
                parameter("limit", "1")
                parameter("order", "编号.desc")
                header("apikey", secretKey)
                header("Authorization", "Bearer $secretKey")
            }.bodyAsText()
            val json = Json { ignoreUnknownKeys = true }
            val list = json.decodeFromString<List<kotlinx.serialization.json.JsonObject>>(responseText)
            list.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    // 复刻 testsipabase 的 delete 逻辑
    suspend fun deleteData(tableName: String, id: Long) {
        try {
            val response = client.delete("$supabaseUrl/rest/v1/$tableName") {
                parameter("编号", "eq.$id")
                header("apikey", secretKey)
                header("Authorization", "Bearer $secretKey")
            }
            Log.d("SupabaseSync", "Delete $tableName Status: ${response.status}")
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Delete $tableName Error: ${e.localizedMessage}")
        }
    }

    // 复刻 testsipabase 的 getAllTables 逻辑
    suspend fun getAllSupabaseTables(): List<String> {
        return try {
            val responseText = client.get("$supabaseUrl/rest/v1/") {
                header("apikey", secretKey)
                header("Authorization", "Bearer $secretKey")
            }.bodyAsText()

            val jsonElement = Json.parseToJsonElement(responseText)
            val rootObj = jsonElement.jsonObject
            val tables = mutableSetOf<String>()

            rootObj["paths"]?.jsonObject?.keys?.forEach { path ->
                val clean = path.trim('/')
                if (clean.isNotEmpty() && !clean.startsWith("rpc/")) {
                    tables.add(clean)
                }
            }
            rootObj["definitions"]?.jsonObject?.keys?.forEach { key ->
                if (key.isNotEmpty()) tables.add(key)
            }

            tables.toList().sorted()
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Fetch Tables Error: ${e.localizedMessage}")
            emptyList()
        }
    }
}
