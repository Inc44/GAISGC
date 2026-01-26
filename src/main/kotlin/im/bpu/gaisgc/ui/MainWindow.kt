package im.bpu.gaisgc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.FilterMimeType
import im.bpu.gaisgc.Item
import im.bpu.gaisgc.Screen
import im.bpu.gaisgc.Sort
import im.bpu.gaisgc.State
import im.bpu.gaisgc.manager.DriveManager
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

@Composable
fun ApplicationLayout() {
	val scope = rememberCoroutineScope()
	LaunchedEffect(Unit) { DriveManager.fetch() }
	MaterialTheme {
		Scaffold(
			modifier =
				Modifier.onPreviewKeyEvent { event ->
					if (event.key == Key.ShiftLeft || event.key == Key.ShiftRight) {
						State.isShiftPressed = event.type == KeyEventType.KeyDown
						false
					} else if (
						event.type == KeyEventType.KeyDown &&
							event.isCtrlPressed &&
							event.key == Key.A &&
							State.screen == Screen.UNLINKED
					) {
						val filteredUnlinkedItems = State.getFilteredUnlinkedItems()
						val allSelectedIds =
							filteredUnlinkedItems.all { it.id in State.selectedIds }
						if (allSelectedIds) {
							State.selectedIds.clear()
						} else {
							filteredUnlinkedItems.forEach {
								if (it.id !in State.selectedIds) State.selectedIds.add(it.id)
							}
						}
						true
					} else {
						false
					}
				}
		) { padding ->
			Row(modifier = Modifier.padding(padding).fillMaxSize()) {
				NavigationSideBar()
				ContentArea(
					modifier = Modifier.weight(1f),
					onRefresh = { scope.launch { DriveManager.fetch() } },
					onTrash = { scope.launch { DriveManager.trash(State.selectedIds.toList()) } },
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
			onClick = {
				State.screen = Screen.MAIN
				State.selectedIds.clear()
				State.lastSelectedId = null
				State.shiftRangeIds.clear()
			},
			icon = { Icon(Icons.Filled.ChatBubble, contentDescription = null) },
			label = { Text("Chats") },
		)
		NavigationRailItem(
			selected = State.screen == Screen.UNLINKED,
			onClick = {
				State.screen = Screen.UNLINKED
				State.selectedIds.clear()
				State.lastSelectedId = null
				State.shiftRangeIds.clear()
			},
			icon = { Icon(Icons.Filled.LinkOff, contentDescription = null) },
			label = { Text("Unlinked") },
		)
	}
}

@Composable
private fun ContentArea(modifier: Modifier, onRefresh: () -> Unit, onTrash: () -> Unit) {
	var showFilters by remember { mutableStateOf(false) }
	Column(modifier = modifier) {
		Header(
			onRefresh = onRefresh,
			onToggleFilters = { showFilters = !showFilters },
			onTrash = onTrash,
		)
		if (State.screen == Screen.UNLINKED && showFilters) {
			FilterPanel()
		}
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
private fun Header(onRefresh: () -> Unit, onToggleFilters: () -> Unit, onTrash: () -> Unit) {
	Row(
		modifier = Modifier.padding(16.dp).heightIn(min = 64.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		if (State.screen == Screen.UNLINKED) {
			PathInput(modifier = Modifier.weight(1f))
			Spacer(Modifier.width(8.dp))
			IconButton(onClick = onToggleFilters) {
				Icon(Icons.Filled.FilterList, contentDescription = "Filters")
			}
			Spacer(Modifier.width(8.dp))
		} else {
			Title(modifier = Modifier.weight(1f))
		}
		Button(onClick = onRefresh) { Text("Refresh") }
		if (State.selectedIds.isNotEmpty()) {
			Spacer(Modifier.width(8.dp))
			Button(
				onClick = onTrash,
				colors =
					ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
			) {
				Text("Trash (${State.selectedIds.size})")
			}
		}
	}
}

@Composable
private fun FilterPanel() {
	Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
		OutlinedTextField(
			value = State.filterName,
			onValueChange = { State.filterName = it },
			label = { Text("Filter by Name") },
			modifier = Modifier.fillMaxWidth(),
			singleLine = true,
		)
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier.padding(top = 8.dp),
		) {
			items(FilterMimeType.entries.toTypedArray()) { type ->
				FilterChip(
					selected = State.filterMimeType == type,
					onClick = { State.filterMimeType = type },
					label = { Text(type.name) },
				)
			}
		}
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier.padding(top = 8.dp),
		) {
			items(Sort.entries.toTypedArray()) { option ->
				FilterChip(
					selected = State.sort == option,
					onClick = { State.sort = option },
					label = { Text(option.name.replace("_", " ")) },
				)
			}
		}
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
	val filteredUnlinkedItems = State.getFilteredUnlinkedItems()
	LazyColumn(modifier = Modifier.padding(16.dp)) {
		items(filteredUnlinkedItems) { item ->
			ItemRow(
				item = item,
				onClick = {
					scope.launch { State.selectedImage = DriveManager.getImageById(item.id) }
				},
				hasCheckbox = true,
				isChecked = item.id in State.selectedIds,
				onCheckedChange = { checked ->
					if (State.isShiftPressed && State.lastSelectedId != null) {
						val last =
							filteredUnlinkedItems.indexOfFirst { it.id == State.lastSelectedId }
						val current = filteredUnlinkedItems.indexOfFirst { it.id == item.id }
						if (last != -1 && current != -1) {
							val start = min(last, current)
							val end = max(last, current)
							val rangeIds = (start..end).map { filteredUnlinkedItems[it].id }
							val deselectedIds = State.shiftRangeIds - rangeIds.toSet()
							State.selectedIds.removeAll(deselectedIds)
							State.selectedIds.addAll(rangeIds)
							State.shiftRangeIds.clear()
							State.shiftRangeIds.addAll(rangeIds)
						}
					} else {
						if (checked) State.selectedIds.add(item.id)
						else State.selectedIds.remove(item.id)
						State.lastSelectedId = item.id
						State.shiftRangeIds.clear()
					}
				},
			)
		}
	}
}

@Composable
private fun ItemRow(
	item: Item,
	depth: Int = 0,
	onClick: (() -> Unit)? = null,
	hasCheckbox: Boolean = false,
	isChecked: Boolean = false,
	onCheckedChange: ((Boolean) -> Unit)? = null,
) {
	val color =
		if (item.isNotFound) MaterialTheme.colorScheme.error
		else MaterialTheme.colorScheme.onSurface
	val modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
	Row(
		modifier =
			modifier.padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp).fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
	) {
		if (hasCheckbox && onCheckedChange != null) {
			Checkbox(checked = isChecked, onCheckedChange = onCheckedChange)
			Spacer(Modifier.width(8.dp))
		}
		Column {
			Text(text = item.name, style = MaterialTheme.typography.bodyMedium, color = color)
			item.subItems.forEach { subItem -> ItemRow(subItem, depth + 1) }
		}
	}
}