package no.vaccsca.amandman.common

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.time.Duration.ofSeconds

object NtpClock : Clock {

    @Volatile
    private var offsetMs: Long = 0L

    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    init {
        startAutoSync()
    }

    private fun startAutoSync() {
        scheduler.schedule(::safeSync, 0, TimeUnit.SECONDS)
        scheduler.scheduleAtFixedRate(::safeSync, 5, 5, TimeUnit.MINUTES)
    }

    override fun now(): Instant =
        Instant.fromEpochMilliseconds(System.currentTimeMillis() + offsetMs)

    private fun safeSync() =
        runCatching { sync() }.getOrElse { /* ignore */ }

    private fun sync() {
        NTPUDPClient().use { client ->
            client.setDefaultTimeout(ofSeconds(3))
            val info: TimeInfo = client.getTime(InetAddress.getByName("pool.ntp.org"))
            info.computeDetails()
            offsetMs = info.offset ?: offsetMs
        }
    }

    // Extension to auto-close NTPUDPClient
    private inline fun <T> NTPUDPClient.use(block: (NTPUDPClient) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}