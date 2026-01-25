package im.bpu.gaisgc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import im.bpu.gaisgc.manager.DriveManager
import im.bpu.gaisgc.ui.MainView
import im.bpu.gaisgc.ui.UnlinkedView
import kotlinx.coroutines.launch

fun main() = application {
	val APPLICATION_NAME = "GAISGC"
	Window(title = APPLICATION_NAME, onCloseRequest = ::exitApplication) {
		val scope = rememberCoroutineScope()
		LaunchedEffect(Unit) { DriveManager.fetch() }
		MaterialTheme {
			Row() {
				NavigationRail(modifier = Modifier.width(80.dp)) {
					NavigationRailItem(
						selected = State.screen == Screen.MAIN,
						onClick = { State.screen = Screen.MAIN },
						icon = { Icon(Icons.Filled.ChatBubble, null) },
						label = { Text("Chats") },
					)
					NavigationRailItem(
						selected = State.screen == Screen.UNLINKED,
						onClick = { State.screen = Screen.UNLINKED },
						icon = { Icon(Icons.Filled.LinkOff, null) },
						label = { Text("Unlinked") },
					)
				}
				Column() {
					Row(
						modifier = Modifier.padding(16.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						if (State.screen == Screen.UNLINKED) {
							OutlinedTextField(
								value = State.gaisPath,
								onValueChange = { State.savePath(it) },
								label = { Text("Google AI Studio Path") },
								modifier = Modifier.weight(1f),
								singleLine = true,
							)
							Spacer(Modifier.width(16.dp))
						} else {
							Text(
								text = "GAISGC",
								style = MaterialTheme.typography.headlineSmall,
								modifier = Modifier.weight(1f),
								fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
							)
						}
						Button(onClick = { scope.launch { DriveManager.fetch() } }) {
							Text("Refresh")
						}
					}
					Box(modifier = Modifier.weight(1f)) {
						when (State.screen) {
							Screen.MAIN -> MainView()
							Screen.UNLINKED -> UnlinkedView()
						}
					}
				}
			}
		}
	}
}