import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.vaccsca.amandman.model.config.LabelItemSource
import no.vaccsca.amandman.model.config.mapper.toDomain
import no.vaccsca.amandman.model.config.yaml.AmanDmanSettingsYaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScheduledArrivalTimeLabelSourceMappingTest {

    private val yamlMapper = YAMLMapper().registerKotlinModule()

    @Test
    fun `scheduledArrivalTime should map to domain label source`() {
        val yaml = """
            timelines:
              ENGM:
                - timelineTitle: "01ALL"
                  arrivalLabelLayoutId: "arr"
                  right:
                    runways: ["01L"]
            arrivalLabelLayouts:
              arr:
                - src: scheduledArrivalTime
                  w: 8
            connectionConfig:
              atcClient:
                host: "localhost"
                port: 12345
              masterSlaveApi:
                host: "https://example.com"
        """.trimIndent()

        val parsed = yamlMapper.readValue<AmanDmanSettingsYaml>(yaml)
        val domain = parsed.toDomain()

        assertEquals(
            LabelItemSource.SCHEDULED_ARRIVAL_TIME,
            domain.arrivalLabelLayouts.getValue("arr").single().source
        )
        assertEquals(null, domain.arrivalLabelLayouts.getValue("arr").single().timeFormat)
    }

    @Test
    fun `estimatedLandingTime should map to deprecated domain label source`() {
        val yaml = """
            timelines:
              ENGM:
                - timelineTitle: "01ALL"
                  arrivalLabelLayoutId: "arr"
                  right:
                    runways: ["01L"]
            arrivalLabelLayouts:
              arr:
                - src: estimatedLandingTime
                  w: 8
            connectionConfig:
              atcClient:
                host: "localhost"
                port: 12345
              masterSlaveApi:
                host: "https://example.com"
        """.trimIndent()

        val parsed = yamlMapper.readValue<AmanDmanSettingsYaml>(yaml)
        val domain = parsed.toDomain()

        assertEquals(
            LabelItemSource.ESTIMATED_LANDING_TIME,
            domain.arrivalLabelLayouts.getValue("arr").single().source
        )
    }

    @Test
    fun `timeFormat should map to domain label item`() {
        val yaml = """
            timelines:
              ENGM:
                - timelineTitle: "01ALL"
                  arrivalLabelLayoutId: "arr"
                  right:
                    runways: ["01L"]
            arrivalLabelLayouts:
              arr:
                - src: scheduledArrivalTime
                  w: 5
                  timeFormat: "HH:mm"
                  maxLen: 5
            connectionConfig:
              atcClient:
                host: "localhost"
                port: 12345
              masterSlaveApi:
                host: "https://example.com"
        """.trimIndent()

        val parsed = yamlMapper.readValue<AmanDmanSettingsYaml>(yaml)
        val domain = parsed.toDomain()
        val item = domain.arrivalLabelLayouts.getValue("arr").single()

        assertEquals(LabelItemSource.SCHEDULED_ARRIVAL_TIME, item.source)
        assertEquals("HH:mm", item.timeFormat)
        assertEquals(5, item.maxLength)
    }

    @Test
    fun `timeFormat should fail on non-time label item`() {
        val yaml = """
            timelines:
              ENGM:
                - timelineTitle: "01ALL"
                  arrivalLabelLayoutId: "arr"
                  right:
                    runways: ["01L"]
            arrivalLabelLayouts:
              arr:
                - src: callSign
                  w: 8
                  timeFormat: "HH:mm"
            connectionConfig:
              atcClient:
                host: "localhost"
                port: 12345
              masterSlaveApi:
                host: "https://example.com"
        """.trimIndent()

        assertFailsWith<ValueInstantiationException> {
            yamlMapper.readValue<AmanDmanSettingsYaml>(yaml)
        }
    }

    @Test
    fun `blank timeFormat should fail parsing`() {
        val yaml = """
            timelines:
              ENGM:
                - timelineTitle: "01ALL"
                  arrivalLabelLayoutId: "arr"
                  right:
                    runways: ["01L"]
            arrivalLabelLayouts:
              arr:
                - src: scheduledArrivalTime
                  w: 8
                  timeFormat: ""
            connectionConfig:
              atcClient:
                host: "localhost"
                port: 12345
              masterSlaveApi:
                host: "https://example.com"
        """.trimIndent()

        assertFailsWith<ValueInstantiationException> {
            yamlMapper.readValue<AmanDmanSettingsYaml>(yaml)
        }
    }

    @Test
    fun `invalid label source should still fail parsing`() {
        val yaml = """
            timelines:
              ENGM:
                - timelineTitle: "01ALL"
                  arrivalLabelLayoutId: "arr"
                  right:
                    runways: ["01L"]
            arrivalLabelLayouts:
              arr:
                - src: definitelyInvalidSource
                  w: 8
            connectionConfig:
              atcClient:
                host: "localhost"
                port: 12345
              masterSlaveApi:
                host: "https://example.com"
        """.trimIndent()

        assertFailsWith<Exception> {
            yamlMapper.readValue<AmanDmanSettingsYaml>(yaml)
        }
    }
}
