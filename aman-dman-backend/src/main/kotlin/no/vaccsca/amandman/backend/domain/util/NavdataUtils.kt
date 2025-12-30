package no.vaccsca.amandman.backend.domain.util

import no.vaccsca.amandman.common.domain.valueobjects.StarFix
import no.vaccsca.amandman.common.domain.valueobjects.Waypoint
import no.vaccsca.amandman.common.domain.valueobjects.distanceTo
import kotlin.collections.sumOf

object NavdataUtils {
    /**
     * Interpolate typical speed for a STAR fix that doesn't have a typical speed, but is between two fixes that do.
     */
    fun List<Waypoint>.getInterpolatedSpeedExpectation(star: List<StarFix>, atWaypoint: Waypoint): Int? {
        val exactExpectation = star.find { it.id == atWaypoint.id }?.typicalSpeedIas
        if (exactExpectation != null) {
            return exactExpectation
        }

        val laterSpeedRestriction = this.nextSpeedExpectation(atWaypoint, star)
        val priorSpeedRestriction = this.previousSpeedExpectation(atWaypoint, star)

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
     * Find the next STAR fix with a typical speed expectation after the given waypoint.
     * Returns a Pair of the StarFix and the corresponding Waypoint, or null if none found.
     */
    private fun List<Waypoint>.nextSpeedExpectation(atWaypoint: Waypoint, star: List<StarFix>): Pair<StarFix, Waypoint>? {
        val currentPointIndex = this.indexOf(atWaypoint)
        if (currentPointIndex == -1) return null

        for (i in currentPointIndex until this.size) {
            val routePoint = this[i]
            val starFix = star.find { it.id == routePoint.id }
            if (starFix?.typicalSpeedIas != null) {
                return Pair(starFix, routePoint)
            }
        }
        return null
    }

    /**
     * Find the previous STAR fix with a typical speed expectation before the given waypoint.
     * Returns a Pair of the StarFix and the corresponding Waypoint, or null if none found.
     */
    private fun List<Waypoint>.previousSpeedExpectation(atWaypoint: Waypoint, star: List<StarFix>): Pair<StarFix, Waypoint>? {
        val currentPointIndex = this.indexOf(atWaypoint)
        if (currentPointIndex == -1) return null

        for (i in currentPointIndex - 1 downTo 0) {
            val routePoint = this[i]
            val starFix = star.find { it.id == routePoint.id }
            if (starFix?.typicalSpeedIas != null) {
                return Pair(starFix, routePoint)
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