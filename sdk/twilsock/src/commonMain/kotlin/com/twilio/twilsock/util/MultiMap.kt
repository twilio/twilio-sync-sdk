package com.twilio.twilsock.util

import com.twilio.twilsock.util.MultiMap.MultiMapSerializer
import io.ktor.http.ParametersBuilder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = MultiMapSerializer::class)
class MultiMap<K, V>() {
    private val map: MutableMap<K, MutableSet<V>> = mutableMapOf()

    private constructor(data: Map<K, Set<V>>) : this() {
        data.forEach { (key, value) ->
            putAll(key, value)
        }
    }

    override fun toString() = map.toString()

    operator fun get(key: K): Set<V>? = map[key]

    operator fun set(key: K, value: V) = putAll(key, listOf(value))

    fun putAll(key: K, values: Collection<V>) {
        val set = map[key] ?: mutableSetOf<V>().also { map[key] = it }
        set.addAll(values)
    }

    internal class MultiMapSerializer<K, V>(
        keySerializer: KSerializer<K>,
        valueSerializer: KSerializer<V>,
    ) : KSerializer<MultiMap<K, V>> {

        private val serializer = MapSerializer(keySerializer, SetSerializer(valueSerializer))

        override val descriptor: SerialDescriptor = serializer.descriptor

        override fun serialize(encoder: Encoder, value: MultiMap<K, V>) {
            encoder.encodeSerializableValue(serializer, value.map)
        }

        override fun deserialize(decoder: Decoder): MultiMap<K, V> {
            return MultiMap(decoder.decodeSerializableValue(serializer))
        }
    }
}

internal fun ParametersBuilder.toMultiMap(): MultiMap<String, String> {
    val multiMap = MultiMap<String, String>()

    entries().forEach { (key, list) ->
        multiMap.putAll(key, list)
    }

    return multiMap
}

internal fun JsonObject.toMultiMap(): MultiMap<String, String> {
    val multiMap = MultiMap<String, String>()

    forEach { (key, jsonElement) ->
        multiMap[key] = jsonElement.jsonPrimitive.content
    }

    return multiMap
}
