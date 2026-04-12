package no.vaccsca.amandman.model

import no.vaccsca.amandman.model.config.mapper.toDomain
import no.vaccsca.amandman.model.config.mapper.toYaml
import no.vaccsca.amandman.model.config.yaml.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimelineSettingsMapperTest {

    @Test
    fun `runway timeline applies defaults when entry omits layouts`() {
        val settings = TimelineSettingsYaml(
            timelines = mapOf(
                "TEST" to AirportTimelinesYaml(
                    defaults = TimelineDefaultsYaml(
                        defaultArrivalLabelLayoutId = "ARR",
                        defaultDepartureLabelLayoutId = "DEP",
                    ),
                    runwayBased = listOf(
                        RunwayTimelineYaml(
                            timelineTitle = "RWY",
                            right = listOf("19L")
                        )
                    )
                )
            )
        )

        val mapped = settings.toDomain()
        val runway = mapped.getValue("TEST").runwayBased.single()

        assertEquals("ARR", runway.arrivalLabelLayoutId)
        assertEquals("DEP", runway.departureLabelLayoutId)
    }

    @Test
    fun `runway timeline override takes precedence over defaults`() {
        val settings = TimelineSettingsYaml(
            timelines = mapOf(
                "TEST" to AirportTimelinesYaml(
                    defaults = TimelineDefaultsYaml(
                        defaultArrivalLabelLayoutId = "ARR",
                        defaultDepartureLabelLayoutId = "DEP",
                    ),
                    runwayBased = listOf(
                        RunwayTimelineYaml(
                            timelineTitle = "RWY",
                            right = listOf("19L"),
                            arrivalLabelLayoutId = "ARR2",
                            departureLabelLayoutId = "DEP2",
                        )
                    )
                )
            )
        )

        val mapped = settings.toDomain()
        val runway = mapped.getValue("TEST").runwayBased.single()

        assertEquals("ARR2", runway.arrivalLabelLayoutId)
        assertEquals("DEP2", runway.departureLabelLayoutId)
    }

    @Test
    fun `missing effective departure layout fails fast for runway timelines`() {
        val settings = TimelineSettingsYaml(
            timelines = mapOf(
                "TEST" to AirportTimelinesYaml(
                    defaults = TimelineDefaultsYaml(
                        defaultArrivalLabelLayoutId = "ARR",
                        defaultDepartureLabelLayoutId = null,
                    ),
                    runwayBased = listOf(
                        RunwayTimelineYaml(
                            timelineTitle = "RWY",
                            right = listOf("19L"),
                        )
                    )
                )
            )
        )

        assertFailsWith<IllegalArgumentException> {
            settings.toDomain()
        }
    }

    @Test
    fun `feeder fix timelines map without departure layout field`() {
        val settings = TimelineSettingsYaml(
            timelines = mapOf(
                "TEST" to AirportTimelinesYaml(
                    defaults = TimelineDefaultsYaml(
                        defaultArrivalLabelLayoutId = "ARR",
                        defaultDepartureLabelLayoutId = "DEP",
                    ),
                    feederFixBased = listOf(
                        FeederFixTimelineYaml(
                            timelineTitle = "MP",
                            right = listOf("BAVAD")
                        )
                    )
                )
            )
        )

        val mapped = settings.toDomain()
        val feederFixTimeline = mapped.getValue("TEST").feederFixBased.single()

        assertEquals("ARR", feederFixTimeline.arrivalLabelLayoutId)
    }

    @Test
    fun `timeline domain serializes back to yaml with explicit layouts`() {
        val yaml = TimelineSettingsYaml(
            timelines = mapOf(
                "TEST" to AirportTimelinesYaml(
                    defaults = TimelineDefaultsYaml(
                        defaultArrivalLabelLayoutId = "ARR",
                        defaultDepartureLabelLayoutId = "DEP",
                    ),
                    runwayBased = listOf(
                        RunwayTimelineYaml(
                            timelineTitle = "RWY",
                            right = listOf("19L"),
                            arrivalLabelLayoutId = "ARR2",
                            departureLabelLayoutId = "DEP2",
                        )
                    )
                )
            )
        )

        val roundTrip = yaml.toDomain().toYaml()
        val runway = roundTrip.timelines.getValue("TEST").runwayBased.single()

        assertEquals("ARR2", runway.arrivalLabelLayoutId)
        assertEquals("DEP2", runway.departureLabelLayoutId)
    }
}
