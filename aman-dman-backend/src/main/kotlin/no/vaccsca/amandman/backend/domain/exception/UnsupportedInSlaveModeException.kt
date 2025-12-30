package no.vaccsca.amandman.backend.domain.exception

data class UnsupportedInSlaveModeException(val msg: String) : Exception(msg)
