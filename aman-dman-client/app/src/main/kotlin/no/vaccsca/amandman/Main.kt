package no.vaccsca.amandman

import com.jtattoo.plaf.hifi.HiFiLookAndFeel
import no.vaccsca.amandman.model.config.SettingsRepository
import no.vaccsca.amandman.model.config.TimelineSettingsRepository
import no.vaccsca.amandman.presenter.MainPresenter
import no.vaccsca.amandman.model.planning.AirportDataSourceManager
import no.vaccsca.amandman.model.config.Theme
import no.vaccsca.amandman.model.weather.NoaaApiClient
import no.vaccsca.amandman.model.sharedstate.MasterSlaveSharedStateHttpClient
import no.vaccsca.amandman.model.cdm.RpuigCdmClient
import no.vaccsca.amandman.model.aircraft.AircraftPerformanceData
import no.vaccsca.amandman.view.AmanDmanMainFrame
import org.slf4j.LoggerFactory
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit
import java.util.*
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.UnsupportedLookAndFeelException


fun main() {
    val logger = LoggerFactory.getLogger("GlobalExceptionHandler")

    fun installGlobalExceptionLogging() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logger.error("Uncaught exception on thread '${thread.name}'", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        Toolkit.getDefaultToolkit().systemEventQueue.push(object : EventQueue() {
            override fun dispatchEvent(event: AWTEvent) {
                try {
                    super.dispatchEvent(event)
                } catch (throwable: Throwable) {
                    logger.error("Uncaught exception while dispatching AWT event '${event.javaClass.simpleName}'", throwable)
                    throw throwable
                }
            }
        })
    }

    fun setTheme(theme: Theme) {
        when (theme) {
            Theme.JTATTOO -> {
                System.setProperty("sun.java2d.d3d", "false") // Disable hardware acceleration to prevent artifacts with popup menus
                HiFiLookAndFeel.setCurrentTheme(Properties().apply {
                    put("logoString", "") // Removes "JTattoo"-attribution from all popup menus
                    put("backgroundPattern", "off")
                })
                try {
                    UIManager.setLookAndFeel("com.jtattoo.plaf.hifi.HiFiLookAndFeel")
                } catch (e: UnsupportedLookAndFeelException) {
                    e.printStackTrace()
                }
            }
            Theme.FLATLAF_DARK -> {
                try {
                    UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf");
                } catch (e: UnsupportedLookAndFeelException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun initializeApplication() {
        installGlobalExceptionLogging()

        val settings = SettingsRepository.getSettings()
        setTheme(settings.theme)

        val view = AmanDmanMainFrame()

        // Inject dependencies into the presenter
        val dataSourceManager = AirportDataSourceManager()
        val windProfileProvider = NoaaApiClient()
        val settingsProvider = SettingsRepository
        val timelineSettingsStore = TimelineSettingsRepository
        val atcClientFactory = AppAtcClientFactory(settingsProvider)
        val sharedState = MasterSlaveSharedStateHttpClient(settingsProvider)
        val cdmProvider = RpuigCdmClient()
        val performanceProvider = AircraftPerformanceData
        val uiDispatcher = SwingUiDispatcher()

        MainPresenter(
            dataSourceManager,
            view,
            windProfileProvider,
            settingsProvider,
            timelineSettingsStore,
            atcClientFactory,
            sharedState,
            cdmProvider,
            performanceProvider,
            uiDispatcher
        )

        view.openWindow()
    }

    SwingUtilities.invokeLater {
        initializeApplication()
    }
}
