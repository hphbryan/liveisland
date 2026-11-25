package io.github.hphbryan.liveisland.platform

import org.json.JSONArray
import org.json.JSONException

/** One of an app's notification types (a notification channel): its id and the name the app gives it. */
data class ChannelInfo(val id: String, val name: String) {
    companion object {
        fun encode(channels: List<ChannelInfo>): String =
            JSONArray().apply { channels.forEach { put(JSONArray().put(it.id).put(it.name)) } }.toString()

        /** Unreadable data is treated as empty. */
        fun decode(json: String): List<ChannelInfo> = try {
            val array = JSONArray(json)
            (0 until array.length()).map { i -> array.getJSONArray(i).let { ChannelInfo(it.getString(0), it.getString(1)) } }
        } catch (_: JSONException) {
            emptyList()
        }
    }
}
