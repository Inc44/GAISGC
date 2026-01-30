package im.bpu.gaisgc

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import im.bpu.gaisgc.ui.ApplicationLayout
import java.awt.Dimension

private const val APPLICATION_NAME = "GAISGC"
private const val DEFAULT_WIDTH = 1200
private const val DEFAULT_HEIGHT = 800
private const val MIN_WIDTH = 1000
private const val MIN_HEIGHT = 600

fun main() = application {
	val state = WindowState(size = DpSize(DEFAULT_WIDTH.dp, DEFAULT_HEIGHT.dp))

	Window(onCloseRequest = ::exitApplication, state = state, title = APPLICATION_NAME) {
		window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
		ApplicationLayout()
	}
}