package no.vaccsca.amandman.model.navigation

data class StarFix(
    val id: String,
    val typicalAltitude: Int? = null,
    val typicalSpeedIas: Int? = null
) {
    class StarFixBuilder(private val id: String) {
        private var typicalAltitude: Int? = null
        private var typicalSpeedIas: Int? = null

        fun altitude(value: Int): StarFixBuilder {
            typicalAltitude = value
            return this
        }

        fun speed(value: Int): StarFixBuilder {
            typicalSpeedIas = value
            return this
        }

        fun build(): StarFix {
            return StarFix(id, typicalAltitude, typicalSpeedIas)
        }
    }
}



