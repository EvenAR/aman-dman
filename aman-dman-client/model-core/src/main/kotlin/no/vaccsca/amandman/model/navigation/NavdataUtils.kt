package no.vaccsca.amandman.model.navigation

import no.vaccsca.amandman.model.airport.ArrivalFixExpectation

object NavdataUtils {
    /**
     * Interpolate typical speed for a route fix that doesn't have an exact speed expectation,
     * but is between two configured arrival fixes that do.
     */
    fun List<Waypoint>.getInterpolatedSpeedExpectation(
        arrivalFixExpectations: List<ArrivalFixExpectation>,
        atWaypoint: Waypoint,
    ): Int? {
        val exactExpectation = arrivalFixExpectations
            .find { it.fixName == atWaypoint.id.uppercase() }
            ?.typicalSpeedIas
        if (exactExpectation != null) {
            return exactExpectation
        }

        val laterSpeedRestriction = this.nextSpeedExpectation(atWaypoint, arrivalFixExpectations)
        val priorSpeedRestriction = this.previousSpeedExpectation(atWaypoint, arrivalFixExpectations)

        if (laterSpeedRestriction == null) {
            return priorSpeedRestriction?.first?.typicalSpeedIas
        }

        if (priorSpeedRestriction == null) {
            return null
        }

        val distanceToSpeedExpectation = distanceBetweenPoints(atWaypoint, laterSpeedRestriction.second)
        val distanceToSpeedExpectationBehind = distanceBetweenPoints(atWaypoint, priorSpeedRestriction.second)

        // Interpolate
        val ratio = distanceToSpeedExpectation / (distanceToSpeedExpectation + distanceToSpeedExpectationBehind)
        val speedAhead = laterSpeedRestriction.first.typicalSpeedIas ?: return null
        val speedBehind = priorSpeedRestriction.first.typicalSpeedIas ?: return null
        return (speedBehind * ratio + speedAhead * (1 - ratio)).toInt()
    }

    /**
     * Find the next route fix with a configured speed expectation after the given waypoint.
     */
    private fun List<Waypoint>.nextSpeedExpectation(
        atWaypoint: Waypoint,
        arrivalFixExpectations: List<ArrivalFixExpectation>,
    ): Pair<ArrivalFixExpectation, Waypoint>? {
        val currentPointIndex = this.indexOf(atWaypoint)
        if (currentPointIndex == -1) return null

        for (i in currentPointIndex until this.size) {
            val routePoint = this[i]
            val arrivalFixExpectation = arrivalFixExpectations.find {
                it.fixName == routePoint.id.uppercase()
            }
            if (arrivalFixExpectation?.typicalSpeedIas != null) {
                return Pair(arrivalFixExpectation, routePoint)
            }
        }
        return null
    }

    /**
     * Find the previous route fix with a configured speed expectation before the given waypoint.
     */
    private fun List<Waypoint>.previousSpeedExpectation(
        atWaypoint: Waypoint,
        arrivalFixExpectations: List<ArrivalFixExpectation>,
    ): Pair<ArrivalFixExpectation, Waypoint>? {
        val currentPointIndex = this.indexOf(atWaypoint)
        if (currentPointIndex == -1) return null

        for (i in currentPointIndex - 1 downTo 0) {
            val routePoint = this[i]
            val arrivalFixExpectation = arrivalFixExpectations.find {
                it.fixName == routePoint.id.uppercase()
            }
            if (arrivalFixExpectation?.typicalSpeedIas != null) {
                return Pair(arrivalFixExpectation, routePoint)
            }
        }
        return null
    }

    private fun List<Waypoint>.distanceBetweenPoints(fromPoint: Waypoint, toWaypoint: Waypoint): Double {
        val fromIndex = this.indexOf(fromPoint)
        val toIndex = this.indexOf(toWaypoint)

        val subList =
            if (fromIndex > toIndex) {
                this.subList(toIndex, fromIndex + 1)
            } else {
                this.subList(fromIndex, toIndex + 1)
            }

        return subList
            .map { it.latLng }
            .zipWithNext()
            .sumOf { (from, to) -> from.distanceTo(to) }
    }
}
