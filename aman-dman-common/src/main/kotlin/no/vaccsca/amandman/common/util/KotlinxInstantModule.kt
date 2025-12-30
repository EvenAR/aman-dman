package no.vaccsca.amandman.common.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

object KotlinxInstantModule : SimpleModule("KotlinxInstantModule") {
        init {
            // Instant serializer/deserializer using ISO-8601 format
            addSerializer(Instant::class.java, object : JsonSerializer<Instant>() {
                override fun serialize(
                    value: Instant,
                    gen: JsonGenerator,
                    serializers: SerializerProvider
                ) {
                    gen.writeString(value.toString()) // ISO-8601
                }
            })
            addDeserializer(Instant::class.java, object : JsonDeserializer<Instant>() {
                override fun deserialize(
                    p: JsonParser,
                    ctxt: DeserializationContext
                ): Instant = Instant.Companion.parse(p.text)
            })
            // Duration serializer/deserializer using ISO-8601 format
            addSerializer(Duration::class.java, object : JsonSerializer<Duration>() {
                override fun serialize(value: Duration, gen: JsonGenerator, serializers: SerializerProvider) {
                    val isoString = value.toJavaDuration().toString() // Convert to Java Duration and then ISO string
                    gen.writeString(isoString)
                }
            })
            addDeserializer(Duration::class.java, object : JsonDeserializer<Duration>() {
                override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Duration {
                    val isoString = p.text
                    return java.time.Duration.parse(isoString).toKotlinDuration() // Java Duration -> Kotlin Duration
                }
            })
        }
}