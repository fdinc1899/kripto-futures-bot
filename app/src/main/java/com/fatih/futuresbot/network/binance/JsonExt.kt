package com.fatih.futuresbot.network.binance

import java.math.BigDecimal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.dbl(key: String): Double? = str(key)?.toDoubleOrNull()
internal fun JsonObject.lng(key: String): Long? = str(key)?.toLongOrNull()
internal fun JsonObject.dec(key: String): BigDecimal? = str(key)?.toBigDecimalOrNull()
internal fun JsonObject.bool(key: String): Boolean? = str(key)?.toBooleanStrictOrNull()
internal fun JsonArray.textAt(index: Int): String? = (getOrNull(index) as? JsonPrimitive)?.contentOrNull
internal fun JsonElement.objOrNull(): JsonObject? = this as? JsonObject
