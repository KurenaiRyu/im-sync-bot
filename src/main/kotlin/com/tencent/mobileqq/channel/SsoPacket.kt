@file:OptIn(ExperimentalSerializationApi::class)

package com.tencent.mobileqq.channel

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class SsoPacket(
    val cmd: String,
    val body: String,
    @JsonNames("callback_id")
    val callbackId: Long
)
