package com.piku.client.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializerOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

class LenientJsonConverterFactory(private val json: Json) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        if (type == Unit::class.java || type == ResponseBody::class.java) return null
        val serializer = serializerFor(type) ?: return null
        return Converter { body ->
            json.decodeFromString(serializer, body.string().trim())
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody>? {
        if (type == String::class.java) return null
        val serializer = serializerFor(type) ?: return null
        return Converter { value: Any? ->
            json.encodeToString(serializer, value as Any).toRequestBody(JSON_MEDIA_TYPE)
        }
    }

    private fun serializerFor(type: Type): KSerializer<Any>? = try {
        json.serializersModule.serializerOrNull(type)
    } catch (e: Exception) {
        null
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}