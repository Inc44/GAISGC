package im.bpu.gaisgc

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import im.bpu.gaisgc.ui.ApplicationLayout
import java.awt.Dimension

fun main() = application {
	val state = WindowState(size = DpSize(Constants.DEFAULT_WIDTH.dp, Constants.DEFAULT_HEIGHT.dp))

	Window(onCloseRequest = ::exitApplication, state = state, title = Constants.APPLICATION_NAME) {
		window.minimumSize = Dimension(Constants.MIN_WIDTH, Constants.MIN_HEIGHT)
		ApplicationLayout()
	}
}