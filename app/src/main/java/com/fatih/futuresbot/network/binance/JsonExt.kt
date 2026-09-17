package com.fatih.futuresbot.network.binance

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.dbl(key: String): Double? = str(key)?.toDoubleOrNull()
internal fun JsonObject.lng(key: String): Long? = str(key)?.toLongOrNull()
internal fun JsonArray.textAt(index: Int): String? = (getOrNull(index) as? JsonPrimitive)?.contentOrNull
internal fun JsonElement.objOrNull(): JsonObject? = this as? JsonObject
