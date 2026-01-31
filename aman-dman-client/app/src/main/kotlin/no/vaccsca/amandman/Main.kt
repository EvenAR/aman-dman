package no.vaccsca.amandman

import com.jtattoo.plaf.hifi.HiFiLookAndFeel
import no.vaccsca.amandman.model.data.repository.SettingsRepository
import no.vaccsca.amandman.presenter.MainPresenter
import no.vaccsca.amandman.model.domain.PlannerManager
import no.vaccsca.amandman.model.domain.valueobjects.Theme
import no.vaccsca.amandman.view.AmanDmanMainFrame
import java.util.*
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.UnsupportedLookAndFeelException


fun main() {
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
            Theme.MOTIF -> {
                try {
                    UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel")
                } catch (e: UnsupportedLookAndFeelException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun initializeApplication() {
        val settings = SettingsRepository.getSettings()
        setTheme(settings.theme)

        val view = AmanDmanMainFrame()
        val presenter = MainPresenter(PlannerManager(), view)

        view.openWindow()
    }

    SwingUtilities.invokeLater {
        initializeApplication()
    }
}
