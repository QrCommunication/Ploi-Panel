package com.qrcommunication.ploipanel

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class Server(val id: Long, val name: String, val status: String, val ipAddress: String)
internal data class ServerPage(val servers: List<Server>, val currentPage: Int, val lastPage: Int) {
    val hasNext: Boolean get() = currentPage < lastPage
}
internal data class MonitorSample(val cpu: String, val ram: String, val disk: String, val load: String, val date: String)
internal class PloiHttpException(val status: Int, val retryAfterSeconds: String? = null) : Exception("Ploi HTTP $status")

/** Read-only API foundation. Never persist or log a bearer token. */
internal object PloiApi {
    fun validateToken(token: String): String {
        require(token.isNotBlank() && token == token.trim() && !token.contains('\r') && !token.contains('\n')) {
            "Invalid bearer token"
        }
        return token
    }

    fun validatePageSize(size: Int): Int {
        require(size in 1..50) { "Ploi supports 1 to 50 items per request" }
        return size
    }

    fun parseServers(json: String): ServerPage {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val meta = root.getJSONObject("meta")
        val page = meta.getInt("current_page")
        val lastPage = meta.getInt("last_page")
        require(page >= 1 && lastPage >= 1 && page <= lastPage) { "Invalid pagination metadata" }
        val servers = (0 until data.length()).map { index ->
            val item = data.getJSONObject(index)
            Server(item.getLong("id"), item.getString("name"), item.optString("status"), item.optString("ip_address"))
        }
        return ServerPage(servers, page, lastPage)
    }

    fun parseMonitoring(json: String): MonitorSample? {
        val data = JSONObject(json).getJSONArray("data")
        if (data.length() == 0) return null
        val sample = (0 until data.length()).map(data::getJSONObject).maxBy { it.getString("date") }
        return MonitorSample(
            sample.optString("cpu"), sample.optString("ram"), sample.optString("disk"),
            sample.optString("load_average"), sample.getString("date")
        )
    }

    fun servers(token: String, page: Int = 1, perPage: Int = 15): ServerPage {
        require(page >= 1) { "Page must be positive" }
        return parseServers(get("/servers?page=$page&per_page=${validatePageSize(perPage)}", token))
    }

    fun monitoring(token: String, serverId: Long): MonitorSample? {
        require(serverId > 0) { "Invalid server ID" }
        return parseMonitoring(get("/servers/$serverId/monitor", token))
    }

    private fun get(path: String, token: String): String {
        val connection = (URL("https://ploi.io/api$path").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer ${validateToken(token)}")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) throw PloiHttpException(status, connection.getHeaderField("Retry-After"))
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
