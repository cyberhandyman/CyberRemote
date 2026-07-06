package dev.companionremote.cli

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Flat JSON file mapping host address → pyatv-format credentials string.
 */
class CredentialsStore(private val file: File) {

    private fun readAll(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        return root.mapValues { (_, v) -> v.jsonPrimitive.content }
    }

    fun load(host: String): String? = readAll()[host]

    fun save(host: String, credentials: String) {
        val all = readAll() + (host to credentials)
        val json = buildJsonObject { for ((k, v) in all) put(k, v) }
        file.writeText(json.toString())
    }
}
