package no.vaccsca.amandman.presenter

import integration.AmanDmanRestApiClient
import no.vaccsca.amandman.common.domain.UserRole
import no.vaccsca.amandman.common.domain.valueobjects.Airport

class Presenter(
    val view: ViewInterface,
) : PresenterInterface {
    private val timelineGroups = mutableListOf<TimelineGroup>()
    private val amanDmanApi: AmanDmanRestApiClient = AmanDmanRestApiClient()

    private val airports = amanDmanApi.fetchAirports()

    override fun onOpenMetWindowClicked(airportIcao: String) {
        view.openMetWindow(airportIcao)
    }

    override fun onOpenVerticalProfileWindowClicked(callsign: String) {
        view.openDescentProfileWindow(callsign)
    }

    override fun onStartButtonClicked() {
        view.openStartWindow(airports.map { it.icao }.toSet())
    }

    override fun onNewTimelineGroup(airportIcao: String, userRole: UserRole) {
        val airport: Airport? = airports.find { it.icao == airportIcao }

        if (airport == null) {
            view.showErrorMessage("Airport $airportIcao not found in navdata")
            return
        }

        val presenter = AirportPresenter(airportIcao,this)
        val airportView = view.createAirportView(airportIcao, presenter)
        presenter.airportView = airportView

        val newGroup = TimelineGroup(
            airport = airport,
            name = airportIcao,
            timelines = mutableListOf(),
            userRole = userRole,
            presenter = presenter
        )

        timelineGroups.add(newGroup)

        view.updateTimelineGroups(timelineGroups)

        /*registerNewTimelineGroup(
            TimelineGroup(
                airport = airport,
                name = airport.icao,
                timelines = mutableListOf(),
                userRole = userRole
            )
        )*/
        view.closeTimelineForm()
    }

    private fun registerNewTimelineGroup(timelineGroup: TimelineGroup) {
        if (timelineGroups.any { it.airport == timelineGroup.airport }) {
            return // Group already exists
        }

        val airport = null // SettingsRepository.getAirportData().find { it == timelineGroup.airport }

        if (airport == null) {
            view.showErrorMessage("Airport ${timelineGroup.airport} not found in navdata")
            return
        }
    }

    override fun onReloadWindsClicked(airportIcao: String) {
        TODO("Not yet implemented")
    }

}