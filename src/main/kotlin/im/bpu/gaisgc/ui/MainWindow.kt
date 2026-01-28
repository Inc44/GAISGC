package im.bpu.gaisgc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.FilterMimeType
import im.bpu.gaisgc.Item
import im.bpu.gaisgc.Screen
import im.bpu.gaisgc.Sort
import im.bpu.gaisgc.State
import im.bpu.gaisgc.manager.DriveManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ApplicationLayout() {
	val scope = rememberCoroutineScope()
	LaunchedEffect(Unit) { DriveManager.fetch() }
	MaterialTheme {
		Scaffold(modifier = Modifier.onPreviewKeyEvent { handleKeyEvent(it, scope) }) { padding ->
			Row(modifier = Modifier.padding(padding).fillMaxSize()) {
				NavigationSideBar()
				ContentArea(
					modifier = Modifier.weight(1f),
					onRefresh = { scope.launch { DriveManager.fetch() } },
					onTrash = { scope.launch { DriveManager.trash(State.selectedIds.toList()) } },
				)
				if (
					State.selectedDocument != null ||
						State.selectedImage != null ||
						State.selectedPdf != null
				) {
					PreviewPane(modifier = Modifier.width(560.dp))
				}
			}
		}
	}
}

private fun handleKeyEvent(event: KeyEvent, scope: CoroutineScope): Boolean {
	if (event.key == Key.ShiftLeft || event.key == Key.ShiftRight) {
		State.isShiftPressed = event.type == KeyEventType.KeyDown
		return false
	}
	if (event.type != KeyEventType.KeyDown || State.screen != Screen.UNLINKED) return false
	if (event.key == Key.Delete && State.selectedIds.isNotEmpty()) {
		scope.launch { DriveManager.trash(State.selectedIds.toList()) }
		return true
	}
	if (event.isCtrlPressed && event.key == Key.A) {
		State.selectAll(State.getFilteredUnlinkedItems().map { it.id })
		return true
	}
	return false
}

@Composable
private fun NavigationSideBar() {
	NavigationRail(
		modifier =
			Modifier.width(80.dp).fillMaxHeight().clickable(
				interactionSource = remember { MutableInteractionSource() },
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
	}
}

@Composable
private fun ContentArea(modifier: Modifier, onRefresh: () -> Unit, onTrash: () -> Unit) {
	var showFilters by remember { mutableStateOf(false) }
	Column(
		modifier =
			modifier.clickable(
				interactionSource = remember { MutableInteractionSource() },
				indication = null,
			) {
				State.clearSelection()
			}
	) {
		Header(
			onRefresh = onRefresh,
			onToggleFilters = { showFilters = !showFilters },
			onTrash = onTrash,
		)
		if (State.screen == Screen.UNLINKED && showFilters) {
			FilterPanel()
		}
		Box(
			modifier =
				Modifier.weight(1f).clickable(
					interactionSource = remember { MutableInteractionSource() },
					indication = null,
				) {
					State.clearSelection()
				}
		) {
			when (State.screen) {
				Screen.MAIN -> ChatList()
				Screen.UNLINKED -> UnlinkedList()
			}
		}
	}
}

@Composable
private fun PreviewPane(modifier: Modifier) {
	val document = State.selectedDocument
	val image = State.selectedImage
	val pdf = State.selectedPdf
	Column(modifier = modifier.fillMaxHeight().padding(16.dp)) {
		if (pdf != null) {
			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				verticalArrangement = Arrangement.spacedBy(16.dp),
			) {
				items(pdf.pages) { bitmap ->
					Image(
						bitmap = bitmap,
						contentDescription = "Preview pane",
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
		} else if (image != null) {
			Image(
				bitmap = image,
				contentDescription = "Preview pane",
				modifier = Modifier.fillMaxSize(),
			)
		} else if (document != null) {
			SelectionContainer {
				LazyColumn(modifier = Modifier.fillMaxSize()) {
					item {
						Text(
							text = document,
							modifier = Modifier.fillMaxWidth(),
							fontFamily = FontFamily.Monospace,
						)
					}
				}
			}
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
					scope.launch {
						State.previewId = item.id
						State.selectedDocument = null
						State.selectedImage = null
						State.selectedPdf?.close()
						State.selectedPdf = null
						val lowercaseMimeType = item.mimeType.lowercase()
						if (State.isDocument(lowercaseMimeType)) {
							State.selectedDocument = DriveManager.getDocumentById(item.id)
						} else if (State.isPhoto(lowercaseMimeType)) {
							State.selectedImage = DriveManager.getImageById(item.id)
						} else if (State.isPdf(lowercaseMimeType)) {
							State.selectedPdf = DriveManager.getPdfById(item.id, scope)
						} else if (State.isVideo(lowercaseMimeType)) {
							State.selectedImage = DriveManager.getVideoById(item.id)
						} else if (State.isOther(lowercaseMimeType)) {
							State.selectedDocument = DriveManager.getDocumentById(item.id)
						}
					}
				},
				hasCheckbox = true,
				isChecked = item.id in State.selectedIds,
				onCheckedChange = {
					State.toggleSelection(item.id, filteredUnlinkedItems.map { it.id })
				},
				isOpened = item.id == State.previewId,
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
	isOpened: Boolean = false,
) {
	val color =
		if (item.isNotFound) MaterialTheme.colorScheme.error
		else MaterialTheme.colorScheme.onSurface
	val modifier =
		Modifier.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(
				if (isOpened) MaterialTheme.colorScheme.primary.copy(alpha = 0.128f)
				else Color.Transparent
			)
			.let { if (onClick != null) it.clickable(onClick = onClick) else it }
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