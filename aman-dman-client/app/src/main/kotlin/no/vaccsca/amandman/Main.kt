package no.vaccsca.amandman

import com.jtattoo.plaf.hifi.HiFiLookAndFeel
import no.vaccsca.amandman.presenter.Presenter
import no.vaccsca.amandman.view.AmanDmanMainFrame
import java.util.*
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.UnsupportedLookAndFeelException


fun main() {

    // Disable hardware acceleration to prevent artifacts with popup menus
    System.setProperty("sun.java2d.d3d", "false")

    try {
        HiFiLookAndFeel.setCurrentTheme(Properties().apply {
            put("logoString", "") // Removes "JTattoo"-attribution from all popup menus
            put("backgroundPattern", "off")
        })
        UIManager.setLookAndFeel("com.jtattoo.plaf.hifi.HiFiLookAndFeel")
        //UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel")

    } catch (e: ClassNotFoundException) {
        e.printStackTrace()
    } catch (e: InstantiationException) {
        e.printStackTrace()
    } catch (e: IllegalAccessException) {
        e.printStackTrace()
    } catch (e: UnsupportedLookAndFeelException) {
        e.printStackTrace()
    }

    fun initializeApplication() {
        // --- View ---
        val view = AmanDmanMainFrame()

        // --- Service ---
        /*val guiUpdater = GuiDataHandler()

        // --- Controller ---
        val presenter = Presenter(PlannerManager(), view, guiUpdater)
        guiUpdater.presenter = presenter*/

        view.presenterInterface = Presenter(view)

        view.openWindow()
    }

    // Create a new JFrame
    SwingUtilities.invokeLater {
        initializeApplication()
    }
}
