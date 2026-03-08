import no.vaccsca.amandman.model.config.mapper.toDomain
import no.vaccsca.amandman.model.config.yaml.AirportTimelinesYaml
import no.vaccsca.amandman.model.config.yaml.AmanDmanSettingsYaml
import no.vaccsca.amandman.model.config.yaml.AtcClientConnectionParamsYaml
import no.vaccsca.amandman.model.config.yaml.ConnectionConfigYaml
import no.vaccsca.amandman.model.config.yaml.LabelItemSourceEnumYaml
import no.vaccsca.amandman.model.config.yaml.LabelItemYaml
import no.vaccsca.amandman.model.config.yaml.MasterSlaveApiConnectionParamsYaml
import no.vaccsca.amandman.model.config.yaml.MeteringPointTimelineYaml
import no.vaccsca.amandman.model.config.yaml.RunwayTimelineYaml
import no.vaccsca.amandman.model.config.yaml.ThemeYaml
import no.vaccsca.amandman.model.config.yaml.TimelineDefaultsYaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimelineSettingsMapperTest {

    @Test
    fun `runway timeline applies defaults when entry omits layouts`() {
        val settings = baseSettings(
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
        val runway = mapped.timelines.getValue("TEST").runwayBased.single()

        assertEquals("ARR", runway.arrivalLabelLayoutId)
        assertEquals("DEP", runway.departureLabelLayoutId)
    }

    @Test
    fun `runway timeline override takes precedence over defaults`() {
        val settings = baseSettings(
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
        val runway = mapped.timelines.getValue("TEST").runwayBased.single()

        assertEquals("ARR2", runway.arrivalLabelLayoutId)
        assertEquals("DEP2", runway.departureLabelLayoutId)
    }

    @Test
    fun `missing effective departure layout fails fast for runway timelines`() {
        val settings = baseSettings(
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
    fun `metering timelines map without departure layout field`() {
        val settings = baseSettings(
            timelines = mapOf(
                "TEST" to AirportTimelinesYaml(
                    defaults = TimelineDefaultsYaml(
                        defaultArrivalLabelLayoutId = "ARR",
                        defaultDepartureLabelLayoutId = "DEP",
                    ),
                    meteringPointBased = listOf(
                        MeteringPointTimelineYaml(
                            timelineTitle = "MP",
                            right = listOf("BAVAD")
                        )
                    )
                )
            )
        )

        val mapped = settings.toDomain()
        val metering = mapped.timelines.getValue("TEST").meteringPointBased.single()

        assertEquals("ARR", metering.arrivalLabelLayoutId)
    }

    private fun baseSettings(
        timelines: Map<String, AirportTimelinesYaml>
    ) = AmanDmanSettingsYaml(
        timelines = timelines,
        arrivalLabelLayouts = mapOf(
            "ARR" to listOf(LabelItemYaml(src = LabelItemSourceEnumYaml.CALL_SIGN, w = 8)),
            "ARR2" to listOf(LabelItemYaml(src = LabelItemSourceEnumYaml.CALL_SIGN, w = 8))
        ),
        connectionConfig = ConnectionConfigYaml(
            atcClient = AtcClientConnectionParamsYaml(host = "127.0.0.1", port = 6809),
            masterSlaveApi = MasterSlaveApiConnectionParamsYaml(host = "http://localhost:3000"),
        ),
        departureLabelLayouts = mapOf(
            "DEP" to listOf(LabelItemYaml(src = LabelItemSourceEnumYaml.CALL_SIGN, w = 8)),
            "DEP2" to listOf(LabelItemYaml(src = LabelItemSourceEnumYaml.CALL_SIGN, w = 8)),
        ),
        theme = ThemeYaml.FLATLAF_DARK,
    )
}
