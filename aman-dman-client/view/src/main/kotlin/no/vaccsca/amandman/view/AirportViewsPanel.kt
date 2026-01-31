package no.vaccsca.amandman.view

import no.vaccsca.amandman.view.entity.AirportViewState
import no.vaccsca.amandman.view.entity.MainViewState
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTabbedPane

/**
 * Panel that holds multiple AirportView(s).
 * If there are multiple timeline groups, shows them in tabs.
 * If there is only a single timeline group, shows the AirportView directly.
 */
class AirportViewsPanel(
    private val mainViewState: MainViewState,
) : JPanel(BorderLayout()) {

    private val tabPane = JTabbedPane()

    init {
        add(tabPane, BorderLayout.CENTER)

        mainViewState.airportViewStates.addListener {
            updateTimelineGroups(it)
        }

        mainViewState.currentTab.addListener {
            if (it != null)
                changeVisibleGroup(it)
        }
    }

    /** Returns all currently visible AirportView(s) */
    private val visibleTabs: List<AirportView>
        get() = if (components.contains(tabPane)) {
            tabPane.components.filterIsInstance<AirportView>()
        } else {
            components.filterIsInstance<AirportView>()
        }

    /** Updates or adds tabs according to the groups */
    private fun updateTimelineGroups(airportViewStates: List<AirportViewState>) {
        val existingViews = if (components.contains(tabPane)) {
            tabPane.components.filterIsInstance<AirportView>()
        } else {
            components.filterIsInstance<AirportView>()
        }

        // Close tabs that are not in the groups
        for (i in tabPane.tabCount - 1 downTo 0) {
            val tab = tabPane.getComponentAt(i) as AirportView
            if (airportViewStates.none { it.airportIcao == tab.airportIcao }) {
                tabPane.removeTabAt(i)
            }
        }

        // Add new tabs for groups that are not already present
        for (viewState in airportViewStates) {
            if (existingViews.none { it.airportIcao == viewState.airportIcao }) {
                val tabView = AirportView(viewState.airportIcao, mainViewState)
                tabPane.addTab(viewState.airportIcao + " " + viewState.userRole, tabView)
            } else {
                val existingView = existingViews.find { it.airportIcao == viewState.airportIcao }
                if (existingView != null && !tabPane.components.contains(existingView)) {
                    tabPane.addTab(viewState.airportIcao + " " + viewState.userRole, existingView)
                }
            }
        }

        updateTabVisibility()
    }

    /** Switch to a specific airport tab */
    private fun changeVisibleGroup(airportIcao: String) {
        if (components.contains(tabPane)) {
            for (i in 0 until tabPane.tabCount) {
                val tab = tabPane.getComponentAt(i) as AirportView
                if (tab.airportIcao == airportIcao) {
                    tabPane.selectedIndex = i
                    return
                }
            }
        }
    }

    private fun updateTabVisibility() {
        when {
            tabPane.tabCount == 0 -> {
                remove(tabPane)
                components.filterIsInstance<AirportView>().forEach { remove(it) }
            }
            tabPane.tabCount == 1 -> {
                val single = tabPane.getComponentAt(0)
                remove(tabPane)
                removeAll()
                add(single, BorderLayout.CENTER)
            }
            tabPane.tabCount > 1 -> {
                if (!components.contains(tabPane)) {
                    removeAll()
                    add(tabPane, BorderLayout.CENTER)
                }
            }
        }

        revalidate()
        repaint()
    }

    fun getAirportView(airportIcao: String): AirportView? {
        return visibleTabs.find { it.airportIcao == airportIcao }
    }
}