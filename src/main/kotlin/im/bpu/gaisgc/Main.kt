package im.bpu.gaisgc

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import im.bpu.gaisgc.ui.MainWindow

fun main() = application {
	val APPLICATION_NAME = "GAISGC"
	Window(title = APPLICATION_NAME, onCloseRequest = ::exitApplication) { MainWindow() }
}