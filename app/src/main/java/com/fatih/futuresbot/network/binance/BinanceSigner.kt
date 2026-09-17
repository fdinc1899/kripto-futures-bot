package com.fatih.futuresbot.network.binance

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object BinanceSigner {
    fun sign(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
