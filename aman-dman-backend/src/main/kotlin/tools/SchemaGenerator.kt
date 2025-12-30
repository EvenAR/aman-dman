package tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.victools.jsonschema.generator.*
import com.github.victools.jsonschema.module.jackson.JacksonModule
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption
import no.vaccsca.amandman.backend.data.config.yaml.AircraftPerformanceConfigYaml
import no.vaccsca.amandman.backend.data.config.yaml.AirportDataJson
import no.vaccsca.amandman.backend.data.config.yaml.AmanDmanSettingsYaml
import no.vaccsca.amandman.backend.data.config.yaml.StarYamlFile
import java.nio.file.Files
import java.nio.file.Paths

private val jakartaValidationModule = JakartaValidationModule(
    JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS,
    JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
)

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
    generateForClass(AircraftPerformanceConfigYaml::class.java, outputPath, "aircraft-performance")
    generateForClass(AirportDataJson::class.java, outputPath, "airports")
    generateForClass(StarYamlFile::class.java, "$outputPath/stars", "stars")
}

private fun generateForClass(clazz: Class<*>, outputPath: String, name: String) {
    val schema = generator.generateSchema(clazz)

    val yamlMapper = ObjectMapper(YAMLFactory())
    yamlMapper.findAndRegisterModules()

    // Use the outputPath passed from Gradle
    val outputDir = Paths.get(outputPath)
    Files.createDirectories(outputDir)

    val yamlFile = outputDir.resolve("$name.schema.yaml").toFile()
    yamlMapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(yamlFile, schema)

    println("✅ YAML Schema generated: ${yamlFile.absolutePath}")
}

fun main(args: Array<String>) {
    val outputPath = args[0]
    generateSchemas(outputPath)
}
