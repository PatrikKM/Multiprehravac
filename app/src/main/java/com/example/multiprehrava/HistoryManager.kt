package com.example.multiprehrava

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object HistoryManager {
    private const val PREFS_NAME = "multiprehrava_history"
    private const val KEY_HISTORY_JSON = "history_list"
    private const val MAX_ITEMS = 30

    fun getHistory(context: Context): List<HistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        val list = mutableListOf<HistoryItem>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    HistoryItem(
                        title = obj.getString("title"),
                        url = obj.getString("url"),
                        thumbnailUrl = obj.optString("thumbnailUrl", "").takeIf { it.isNotBlank() && it != "null" },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addHistoryItem(context: Context, item: HistoryItem) {
        val currentList = getHistory(context).toMutableList()
        currentList.removeAll { it.url == item.url }
        currentList.add(0, item)
        val trimmedList = currentList.take(MAX_ITEMS)

        try {
            val jsonArray = JSONArray()
            trimmedList.forEach { histItem ->
                val obj = JSONObject().apply {
                    put("title", histItem.title)
                    put("url", histItem.url)
                    put("thumbnailUrl", histItem.thumbnailUrl ?: "")
                    put("timestamp", histItem.timestamp)
                }
                jsonArray.put(obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_HISTORY_JSON, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeItem(context: Context, url: String) {
        val currentList = getHistory(context).filterNot { it.url == url }
        try {
            val jsonArray = JSONArray()
            currentList.forEach { histItem ->
                val obj = JSONObject().apply {
                    put("title", histItem.title)
                    put("url", histItem.url)
                    put("thumbnailUrl", histItem.thumbnailUrl ?: "")
                    put("timestamp", histItem.timestamp)
                }
                jsonArray.put(obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_HISTORY_JSON, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY_JSON).apply()
    }
}
