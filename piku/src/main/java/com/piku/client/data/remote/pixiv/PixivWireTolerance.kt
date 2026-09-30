package com.piku.client.data.remote.pixiv

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PixivFlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String = try {
        (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content
    } catch (e: IllegalArgumentException) {
        throw SerializationException(e.message)
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/** 分级字段容错：正常是 {sexual: Int}，占位条目给空数组等怪形态，一律按全年龄处理 */
internal object PixivContentTypeSerializer : KSerializer<PixivContentType> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PixivContentType")

    override fun deserialize(decoder: Decoder): PixivContentType {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        val sexual = (element as? JsonObject)?.get("sexual")
            ?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        return PixivContentType(sexual = sexual)
    }

    override fun serialize(encoder: Encoder, value: PixivContentType) {
        (encoder as JsonEncoder).encodeJsonElement(
            buildJsonObject { put("sexual", JsonPrimitive(value.sexual)) },
        )
    }
}
