package im.bpu.gaisgc.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.State
import im.bpu.gaisgc.model.Constants
import im.bpu.gaisgc.model.Screen

@Composable
fun NavigationSideBar() {
	val interactionSource = remember { MutableInteractionSource() }
	var devModeClickCount by remember { mutableStateOf(0) }
	NavigationRail(
		modifier =
			Modifier.width(80.dp).fillMaxHeight().clickable(
				interactionSource = interactionSource,
				indication = null,
			) {
				State.clearSelection()
			}
	) {
		NavigationRailItem(
			selected = State.screen == Screen.MAIN,
			onClick = {
				State.screen = Screen.MAIN
				State.clearSelection()
			},
			icon = { Icon(Icons.Filled.ChatBubble, contentDescription = null) },
			label = { Text("Chats") },
		)
		NavigationRailItem(
			selected = State.screen == Screen.UNLINKED,
			onClick = {
				State.screen = Screen.UNLINKED
				State.clearSelection()
			},
			icon = { Icon(Icons.Filled.LinkOff, contentDescription = null) },
			label = { Text("Unlinked") },
		)
		NavigationRailItem(
			selected = State.screen == Screen.RELINKER,
			onClick = {
				State.screen = Screen.RELINKER
				State.clearSelection()
			},
			icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
			label = { Text("Relinker") },
		)
		NavigationRailItem(
			selected = State.screen == Screen.SETTINGS,
			onClick = {
				devModeClickCount++
				if (devModeClickCount >= Constants.DEV_MODE_CLICKS) {
					State.saveDevMode(true)
					devModeClickCount = 0
				}
				State.screen = Screen.SETTINGS
				State.clearSelection()
			},
			icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
			label = { Text("Settings") },
		)
	}
}