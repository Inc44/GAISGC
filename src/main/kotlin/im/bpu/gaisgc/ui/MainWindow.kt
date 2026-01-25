package im.bpu.gaisgc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.Item
import im.bpu.gaisgc.Screen
import im.bpu.gaisgc.State
import im.bpu.gaisgc.manager.DriveManager
import kotlinx.coroutines.launch

@Composable
fun ApplicationLayout() {
	val scope = rememberCoroutineScope()
	LaunchedEffect(Unit) { DriveManager.fetch() }

	MaterialTheme {
		Scaffold { padding ->
			Row(modifier = Modifier.padding(padding).fillMaxSize()) {
				NavigationSideBar()
				ContentArea(
					modifier = Modifier.weight(1f),
					onRefresh = { scope.launch { DriveManager.fetch() } },
				)
				if (State.selectedImage != null) {
					PreviewPane(modifier = Modifier.width(560.dp))
				}
			}
		}
	}
}

@Composable
private fun NavigationSideBar() {
	NavigationRail(modifier = Modifier.width(80.dp).fillMaxHeight()) {
		NavigationRailItem(
			selected = State.screen == Screen.MAIN,
			onClick = { State.screen = Screen.MAIN },
			icon = { Icon(Icons.Filled.ChatBubble, contentDescription = null) },
			label = { Text("Chats") },
		)
		NavigationRailItem(
			selected = State.screen == Screen.UNLINKED,
			onClick = { State.screen = Screen.UNLINKED },
			icon = { Icon(Icons.Filled.LinkOff, contentDescription = null) },
			label = { Text("Unlinked") },
		)
	}
}

@Composable
private fun ContentArea(modifier: Modifier, onRefresh: () -> Unit) {
	Column(modifier = modifier) {
		Header(onRefresh)
		Box(modifier = Modifier.weight(1f)) {
			when (State.screen) {
				Screen.MAIN -> ChatList()
				Screen.UNLINKED -> UnlinkedList()
			}
		}
	}
}

@Composable
private fun PreviewPane(modifier: Modifier) {
	Column(modifier = modifier.fillMaxHeight().padding(16.dp)) {
		State.selectedImage?.let {
			Image(
				bitmap = it,
				contentDescription = "Preview pane",
				modifier = Modifier.fillMaxSize(),
			)
		}
	}
}

@Composable
private fun Header(onRefresh: () -> Unit) {
	Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
		if (State.screen == Screen.UNLINKED) {
			PathInput(modifier = Modifier.weight(1f))
			Spacer(Modifier.width(16.dp))
		} else {
			Title(modifier = Modifier.weight(1f))
		}
		Button(onClick = onRefresh) { Text("Refresh") }
	}
}

@Composable
private fun PathInput(modifier: Modifier) {
	OutlinedTextField(
		value = State.gaisPath,
		onValueChange = { State.savePath(it) },
		label = { Text("Google AI Studio Path") },
		modifier = modifier,
		singleLine = true,
	)
}

@Composable
private fun Title(modifier: Modifier) {
	Text(
		text = "GAISGC",
		style = MaterialTheme.typography.headlineSmall,
		fontWeight = FontWeight.Bold,
		modifier = modifier,
	)
}

@Composable
private fun ChatList() {
	LazyColumn(modifier = Modifier.padding(16.dp)) { items(State.items) { item -> ItemRow(item) } }
}

@Composable
private fun UnlinkedList() {
	val scope = rememberCoroutineScope()
	LazyColumn(modifier = Modifier.padding(16.dp)) {
		items(State.unlinkedItems) { item ->
			ItemRow(
				item = item,
				onClick = {
					scope.launch { State.selectedImage = DriveManager.getImageById(item.id) }
				},
			)
		}
	}
}

@Composable
private fun ItemRow(item: Item, depth: Int = 0, onClick: (() -> Unit)? = null) {
	val color =
		if (item.isNotFound) MaterialTheme.colorScheme.error
		else MaterialTheme.colorScheme.onSurface
	val modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
	Column(
		modifier =
			modifier.padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp).then(modifier)
	) {
		Text(text = item.name, style = MaterialTheme.typography.bodyMedium, color = color)
		item.subItems.forEach { subItem -> ItemRow(subItem, depth + 1) }
	}
}