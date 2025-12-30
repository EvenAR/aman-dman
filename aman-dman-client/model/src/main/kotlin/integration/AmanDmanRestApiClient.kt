package integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.vaccsca.amandman.common.domain.valueobjects.Airport
import no.vaccsca.amandman.common.util.KotlinxInstantModule
import okhttp3.OkHttpClient

class AmanDmanRestApiClient(
    val host: String = "http://127.0.0.1:3000"
) {
    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        registerModule(KotlinxInstantModule)
        findAndRegisterModules()
    }

    private val httpClient: OkHttpClient = OkHttpClient()

    fun fetchAirports(): List<Airport> {
        val request = okhttp3.Request.Builder()
            .url("$host/airports")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Unexpected code $response")
            val responseBody = response.body?.string() ?: throw Exception("Empty response body")
            return objectMapper.readValue(responseBody, Array<Airport>::class.java).toList()
        }
    }

}