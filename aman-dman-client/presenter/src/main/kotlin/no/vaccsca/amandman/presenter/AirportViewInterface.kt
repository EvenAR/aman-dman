package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.common.domain.TabData
import no.vaccsca.amandman.common.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.common.domain.valueobjects.TrajectoryPoint
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile
import java.awt.Point

interface AirportViewInterface {
    fun updateMinimumSpacing(minimumSpacingNm: Double)
    fun updateRunwayModes(runwayModes: Map<String, RunwayStatus>)
    fun showAirportContextMenu(availableTimelines: List<TimelineConfig>, screenPos: Point)
    fun updateDraggedLabel(timelineEvent: TimelineEvent, newInstant: Instant, isAvailable: Boolean)
    fun updateTab(tabData: TabData)
    fun updateWeatherData(weather: VerticalWeatherProfile?)
    fun updateDescentTrajectory(callsign: String, trajectory: List<TrajectoryPoint>)
    fun showMinimumSpacingDialog(d: Double)
}