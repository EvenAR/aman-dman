package tools

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.victools.jsonschema.generator.*
import com.github.victools.jsonschema.module.jackson.JacksonModule
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption
import no.vaccsca.amandman.model.config.yaml.AircraftPerformanceConfigYaml
import no.vaccsca.amandman.model.config.yaml.AirportDataJson
import no.vaccsca.amandman.model.config.yaml.AirportsConfigYaml
import no.vaccsca.amandman.model.config.yaml.AmanDmanSettingsYaml
import no.vaccsca.amandman.model.config.yaml.TimelineSettingsYaml
import java.nio.file.Files
import java.nio.file.Paths

private val jakartaValidationModule = JakartaValidationModule(
    JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS,
    JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
)

private val fourSpaceIndenter = DefaultIndenter("    ", DefaultIndenter.SYS_LF)
private val fourSpacePrettyPrinter = DefaultPrettyPrinter().apply {
    indentObjectsWith(fourSpaceIndenter)
    indentArraysWith(fourSpaceIndenter)
}
private val jsonMapper = ObjectMapper()
private val yamlMapper = ObjectMapper(com.fasterxml.jackson.dataformat.yaml.YAMLFactory()).apply {
    findAndRegisterModules()
}

var jacksonModule = JacksonModule()

private val config = SchemaGeneratorConfigBuilder(
    SchemaVersion.DRAFT_2020_12,
    OptionPreset.PLAIN_JSON
)
    .with(JacksonModule())
    .with(Option.DEFINITIONS_FOR_ALL_OBJECTS)
    .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
    .with(Option.MAP_VALUES_AS_ADDITIONAL_PROPERTIES)
    .with(Option.FLATTENED_ENUMS_FROM_TOSTRING)
    .with(jakartaValidationModule)
    .with(jacksonModule)
    .build()

private val generator = SchemaGenerator(config)

private fun generateSchemas(outputPath: String) {
    generateForClass(AmanDmanSettingsYaml::class.java, outputPath, "settings")
    generateForClass(TimelineSettingsYaml::class.java, outputPath, "timelines")
    generateForClass(AircraftPerformanceConfigYaml::class.java, outputPath, "aircraft-performance")
    generateForClass(AirportsConfigYaml::class.java, outputPath, "airports")
    generateForClass(AirportDataJson::class.java, "$outputPath/airports", "airport")
}

private fun generateForClass(clazz: Class<*>, outputPath: String, name: String) {
    val schema = generator.generateSchema(clazz)
    if (schema is ObjectNode) {
        patchAirportAreaSchema(schema)
    }

    // Use the outputPath passed from Gradle
    val outputDir = Paths.get(outputPath)
    Files.createDirectories(outputDir)

    val yamlFile = outputDir.resolve("$name.schema.yaml").toFile()
    yamlMapper
        .writer(fourSpacePrettyPrinter)
        .writeValue(yamlFile, schema)

    println("✅ YAML Schema generated: ${yamlFile.absolutePath}")
}

private fun patchAirportAreaSchema(schema: ObjectNode) {
    val objectVariantName = "AirportAreaYamlObject"
    val legacyVariantName = "LegacyAirportAreaYaml"
    val unionVariantName = "AirportAreaYaml"
    val areaMapName = "Map(String,AirportAreaYaml)"
    val defs = (schema.path("\$defs") as? ObjectNode) ?: jsonMapper.createObjectNode().also {
        schema.set<ObjectNode>("\$defs", it)
    }

    defs.set<ObjectNode>(
        legacyVariantName,
        jsonMapper.createObjectNode().apply {
            put("type", "array")
            set<ObjectNode>(
                "items",
                jsonMapper.createObjectNode().apply {
                    put("type", "string")
                }
            )
        }
    )
    defs.set<ObjectNode>(
        objectVariantName,
        jsonMapper.createObjectNode().apply {
            put("type", "object")
            set<ObjectNode>(
                "properties",
                jsonMapper.createObjectNode().apply {
                    set<ObjectNode>(
                        "boundary",
                        jsonMapper.createObjectNode().apply {
                            put("type", "array")
                            set<ObjectNode>(
                                "items",
                                jsonMapper.createObjectNode().apply {
                                    put("type", "string")
                                }
                            )
                        }
                    )
                    set<ObjectNode>(
                        "ceilingFt",
                        jsonMapper.createObjectNode().apply {
                            put("type", "integer")
                            put("exclusiveMinimum", 0)
                        }
                    )
                }
            )
            set<ArrayNode>(
                "required",
                jsonMapper.createArrayNode().apply {
                    add("boundary")
                }
            )
            put("additionalProperties", false)
        }
    )
    defs.set<ObjectNode>(
        unionVariantName,
        jsonMapper.createObjectNode().apply {
            set<ArrayNode>(
                "oneOf",
                jsonMapper.createArrayNode().apply {
                    add(
                        jsonMapper.createObjectNode().apply {
                            put("\$ref", "#/\$defs/$legacyVariantName")
                        }
                    )
                    add(
                        jsonMapper.createObjectNode().apply {
                            put("\$ref", "#/\$defs/$objectVariantName")
                        }
                    )
                }
            )
        }
    )
    defs.set<ObjectNode>(
        areaMapName,
        jsonMapper.createObjectNode().apply {
            put("type", "object")
            set<ObjectNode>(
                "additionalProperties",
                jsonMapper.createObjectNode().apply {
                    put("\$ref", "#/\$defs/$unionVariantName")
                }
            )
        }
    )

    patchAreasPropertyOnNode(schema, areaMapName)
    defs.fields().forEachRemaining { (_, definition) ->
        if (definition is ObjectNode) {
            patchAreasPropertyOnNode(definition, areaMapName)
        }
    }
}

private fun patchAreasPropertyOnNode(node: ObjectNode, areaMapName: String) {
    val properties = node.path("properties") as? ObjectNode ?: return
    if (!properties.has("areas")) {
        return
    }

    properties.set<ObjectNode>(
        "areas",
        jsonMapper.createObjectNode().apply {
            put("\$ref", "#/\$defs/$areaMapName")
        }
    )
}

fun main(args: Array<String>) {
    val outputPath = args[0]
    generateSchemas(outputPath)
}
