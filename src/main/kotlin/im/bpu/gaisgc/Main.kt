package im.bpu.gaisgc

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import im.bpu.gaisgc.ui.ApplicationLayout

fun main() = application {
	val APPLICATION_NAME = "GAISGC"
	val state = WindowState(size = DpSize(1200.dp, 800.dp))
	Window(onCloseRequest = ::exitApplication, state = state, title = APPLICATION_NAME) {
		ApplicationLayout()
	}
}