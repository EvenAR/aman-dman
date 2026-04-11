package tools

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.victools.jsonschema.generator.Option
import com.github.victools.jsonschema.generator.OptionPreset
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.github.victools.jsonschema.module.jackson.JacksonModule
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption
import no.vaccsca.amandman.model.config.yaml.AircraftPerformanceConfigYaml
import no.vaccsca.amandman.model.config.yaml.AirportDataJson
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
    generateForClass(AirportDataJson::class.java, "$outputPath/airports", "airport")
}

private fun generateForClass(clazz: Class<*>, outputPath: String, name: String) {
    val schema = generator.generateSchema(clazz)

    val yamlMapper = ObjectMapper(com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
    yamlMapper.findAndRegisterModules()

    // Use the outputPath passed from Gradle
    val outputDir = Paths.get(outputPath)
    Files.createDirectories(outputDir)

    val yamlFile = outputDir.resolve("$name.schema.yaml").toFile()
    yamlMapper
        .writer(fourSpacePrettyPrinter)
        .writeValue(yamlFile, schema)

    println("✅ YAML Schema generated: ${yamlFile.absolutePath}")
}

fun main(args: Array<String>) {
    val outputPath = args[0]
    generateSchemas(outputPath)
}
