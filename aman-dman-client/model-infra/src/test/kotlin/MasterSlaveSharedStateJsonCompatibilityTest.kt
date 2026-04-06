import com.fasterxml.jackson.core.type.TypeReference
import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.planning.SequenceStatus
import no.vaccsca.amandman.model.sharedstate.SharedStateJson
import no.vaccsca.amandman.model.sharedstate.SharedStateTimelineEventJson
import no.vaccsca.amandman.model.sharedstate.createSharedStateObjectMapper
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class MasterSlaveSharedStateJsonCompatibilityTest {

    private val objectMapper = createSharedStateObjectMapper()

    @Test
    fun `legacy runway arrival payloads with assignedStarOk still parse`() {
        val parsed = objectMapper.readValue(
            """
            {
              "lastUpdate": "2026-04-06T10:00:00Z",
              "data": [
                {
                  "type": "runwayArrival",
                  "event": {
                    "scheduledTime": "2026-04-06T10:05:00Z",
                    "estimatedTime": "2026-04-06T10:03:00Z",
                    "lastTimestamp": "2026-04-06T10:00:00Z",
                    "runway": "19L",
                    "callsign": "SAS123",
                    "icaoType": "B738",
                    "wakeCategory": "M",
                    "airportIcao": "ENGM",
                    "trackingController": "ENGM_APP",
                    "assignedStar": "INREX4M",
                    "assignedStarOk": true,
                    "flightLevel": 120,
                    "pressureAltitude": 10000,
                    "groundSpeed": 250,
                    "remainingDistance": 80.0,
                    "withinActiveAdvisoryHorizon": true,
                    "sequenceStatus": "OK",
                    "landingIas": 140,
                    "assignedDirect": null,
                    "scratchPad": null
                  }
                }
              ]
            }
            """.trimIndent(),
            object : TypeReference<SharedStateJson<List<SharedStateTimelineEventJson>>>() {}
        )

        val event = assertIs<RunwayArrivalEvent>(parsed.data.single().event)
        assertEquals("INREX4M", event.assignedStar)
        assertEquals(SequenceStatus.OK, event.sequenceStatus)
        assertEquals(250, event.groundSpeed)
    }

    @Test
    fun `new runway arrival payloads serialize without assignedStarOk`() {
        val json = objectMapper.writeValueAsString(
            SharedStateJson(
                lastUpdate = Instant.parse("2026-04-06T10:00:00Z"),
                data = listOf(
                    SharedStateTimelineEventJson(
                        type = "runwayArrival",
                        event = RunwayArrivalEvent(
                            scheduledTime = Instant.parse("2026-04-06T10:05:00Z"),
                            estimatedTime = Instant.parse("2026-04-06T10:03:00Z"),
                            lastTimestamp = Instant.parse("2026-04-06T10:00:00Z"),
                            runway = "19L",
                            callsign = "SAS123",
                            icaoType = "B738",
                            wakeCategory = 'M',
                            airportIcao = "ENGM",
                            trackingController = "ENGM_APP",
                            assignedStar = "INREX4M",
                            flightLevel = 120,
                            pressureAltitude = 10000,
                            groundSpeed = 250,
                            remainingDistance = 80f,
                            withinActiveAdvisoryHorizon = true,
                            sequenceStatus = SequenceStatus.OK,
                            landingIas = 140,
                            assignedDirect = null,
                            scratchPad = null,
                            assignedDirectIsIAF = false,
                            assignedDirectIsIF = false,
                        ),
                    )
                )
            )
        )

        assertFalse(json.contains("assignedStarOk"))
    }
}
