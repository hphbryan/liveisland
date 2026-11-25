package io.github.hphbryan.liveisland.platform

import io.github.hphbryan.liveisland.core.PersonRule
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Stores [PersonRule]s and name lists as JSON in preferences. Unreadable data is treated as empty. Rules
 * saved by earlier versions may carry a "label"; it is no longer used and is dropped on the next save.
 */
internal object PeopleCodec {
    fun encode(rules: List<PersonRule>): String = JSONArray().apply {
        rules.forEach { r ->
            put(JSONObject().put("name", r.name).put("untilRead", r.untilRead))
        }
    }.toString()

    fun decode(json: String): List<PersonRule> = try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            PersonRule(
                name = o.getString("name"),
                untilRead = o.optBoolean("untilRead", true),
            )
        }
    } catch (_: JSONException) {
        emptyList()
    }

    fun encodeNames(names: List<String>): String = JSONArray(names).toString()

    fun decodeNames(json: String): List<String> = try {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    } catch (_: JSONException) {
        emptyList()
    }
}
