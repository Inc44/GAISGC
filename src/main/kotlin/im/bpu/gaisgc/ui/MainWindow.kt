package im.bpu.gaisgc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.graphics.toArgb
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
					onRelink = {
						scope.launch {
							val matches =
								State.duplicateItems.filter {
									"${it.chat.id}|${it.original.id}" in State.selectedIds
								}
							DriveManager.relink(matches)
						}
					},
				)
				if (
					State.selectedDocument.isNotEmpty() ||
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
	if (event.type != KeyEventType.KeyDown) return false
	if (event.key == Key.Escape && (State.selectedIds.isNotEmpty() || State.previewId != null)) {
		State.clearSelection()
		return true
	}
	if (
		State.screen == Screen.UNLINKED && event.key == Key.Delete && State.selectedIds.isNotEmpty()
	) {
		scope.launch { DriveManager.trash(State.selectedIds.toList()) }
		return true
	}
	if (event.isCtrlPressed && event.key == Key.A) {
		when (State.screen) {
			Screen.UNLINKED -> {
				State.selectAll(State.getFilteredUnlinkedItems().map { it.id })
				return true
			}
			Screen.RELINKER -> {
				State.selectAll(
					State.getFilteredDuplicateItems().map { "${it.chat.id}|${it.original.id}" }
				)
				return true
			}
			else -> return false
		}
	}
	return false
}

@Composable
private fun NavigationSideBar() {
	val interactionSource = remember { MutableInteractionSource() }
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
	}
}

@Composable
private fun ContentArea(
	modifier: Modifier,
	onRefresh: () -> Unit,
	onTrash: () -> Unit,
	onRelink: () -> Unit,
) {
	var showFilters by remember { mutableStateOf(false) }
	val interactionSource = remember { MutableInteractionSource() }
	Column(
		modifier =
			modifier.clickable(interactionSource = interactionSource, indication = null) {
				State.clearSelection()
			}
	) {
		Header(
			onRefresh = onRefresh,
			onToggleFilters = { showFilters = !showFilters },
			onTrash = onTrash,
			onRelink = onRelink,
		)
		if ((State.screen == Screen.UNLINKED || State.screen == Screen.RELINKER) && showFilters) {
			FilterPanel()
		}
		Box(
			modifier =
				Modifier.weight(1f).clickable(
					interactionSource = interactionSource,
					indication = null,
				) {
					State.clearSelection()
				}
		) {
			when (State.screen) {
				Screen.MAIN -> ChatList()
				Screen.UNLINKED -> UnlinkedList()
				Screen.RELINKER -> RelinkerList()
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
		when {
			pdf != null -> {
				LazyColumn(
					modifier = Modifier.fillMaxSize(),
					verticalArrangement = Arrangement.spacedBy(16.dp),
				) {
					items(pdf.pages) { bitmap ->
						Image(
							bitmap = bitmap,
							contentDescription = "PDF Preview Pane",
							modifier = Modifier.fillMaxWidth(),
						)
					}
				}
			}
			image != null -> {
				Image(
					bitmap = image,
					contentDescription = "Image Preview Pane",
					modifier = Modifier.fillMaxSize(),
				)
			}
			document.isNotEmpty() -> {
				SelectionContainer {
					LazyColumn(modifier = Modifier.fillMaxSize()) {
						items(document) { line ->
							Text(
								text = line,
								modifier = Modifier.fillMaxWidth(),
								fontFamily = FontFamily.Monospace,
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun Header(
	onRefresh: () -> Unit,
	onToggleFilters: () -> Unit,
	onTrash: () -> Unit,
	onRelink: () -> Unit,
) {
	BoxWithConstraints(modifier = Modifier.padding(16.dp)) {
		if (
			(State.screen == Screen.UNLINKED || State.screen == Screen.RELINKER) &&
				maxWidth < 480.dp
		) {
			CompactHeader(onRefresh, onToggleFilters, onTrash, onRelink)
		} else {
			StandardHeader(onRefresh, onToggleFilters, onTrash, onRelink)
		}
	}
}

@Composable
private fun CompactHeader(
	onRefresh: () -> Unit,
	onToggleFilters: () -> Unit,
	onTrash: () -> Unit,
	onRelink: () -> Unit,
) {
	Column {
		ScreenSpecificPathInput(modifier = Modifier.fillMaxWidth())
		Spacer(Modifier.height(8.dp))
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.End,
			verticalAlignment = Alignment.CenterVertically,
		) {
			ActionButtons(onRefresh, onToggleFilters, onTrash, onRelink, showFilter = true)
		}
	}
}

@Composable
private fun StandardHeader(
	onRefresh: () -> Unit,
	onToggleFilters: () -> Unit,
	onTrash: () -> Unit,
	onRelink: () -> Unit,
) {
	Row(modifier = Modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
		if (State.screen == Screen.UNLINKED || State.screen == Screen.RELINKER) {
			ScreenSpecificPathInput(modifier = Modifier.weight(1f))
			Spacer(Modifier.width(8.dp))
			ActionButtons(onRefresh, onToggleFilters, onTrash, onRelink, showFilter = true)
		} else {
			Title(modifier = Modifier.weight(1f))
			ActionButtons(onRefresh, onToggleFilters, onTrash, onRelink, showFilter = false)
		}
	}
}

@Composable
private fun ScreenSpecificPathInput(modifier: Modifier) {
	when (State.screen) {
		Screen.UNLINKED -> {
			PathInput(
				value = State.gaisPath,
				onValueChange = { State.saveGaisPath(it) },
				label = "Google AI Studio Path",
				modifier = modifier,
			)
		}
		Screen.RELINKER -> {
			PathInput(
				value = State.duplicatesPath,
				onValueChange = { State.saveDuplicatesPath(it) },
				label = "Duplicates Path",
				modifier = modifier,
			)
		}
		else -> {}
	}
}

@Composable
private fun ActionButtons(
	onRefresh: () -> Unit,
	onToggleFilters: () -> Unit,
	onTrash: () -> Unit,
	onRelink: () -> Unit,
	showFilter: Boolean,
) {
	if (showFilter) {
		IconButton(onClick = onToggleFilters) {
			Icon(Icons.Filled.FilterList, contentDescription = "Filters")
		}
		Spacer(Modifier.width(8.dp))
	}
	Button(onClick = onRefresh) { Text("Refresh") }
	if (State.selectedIds.isNotEmpty()) {
		Spacer(Modifier.width(8.dp))
		if (State.screen == Screen.RELINKER) {
			Button(
				onClick = onRelink,
				colors =
					ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
			) {
				Text("Relink (${State.selectedIds.size})")
			}
		} else {
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
private fun PathInput(
	value: String,
	onValueChange: (String) -> Unit,
	label: String,
	modifier: Modifier,
) {
	OutlinedTextField(
		value = value,
		onValueChange = onValueChange,
		label = { Text(label) },
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
				onClick = { scope.launch { DriveManager.loadPreview(item, scope) } },
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
private fun RelinkerList() {
	val scope = rememberCoroutineScope()
	val matches = State.getFilteredDuplicateItems()
	LazyColumn(modifier = Modifier.padding(16.dp)) {
		items(matches) { match ->
			val id = "${match.chat.id}|${match.original.id}"
			ItemRow(
				item =
					Item(
						id = id,
						name =
							"${match.chat.name}: ${match.original.name} → ${match.duplicate.name}",
					),
				onClick = { scope.launch { DriveManager.loadPreview(match.duplicate, scope) } },
				hasCheckbox = true,
				isChecked = id in State.selectedIds,
				onCheckedChange = {
					State.toggleSelection(id, matches.map { "${it.chat.id}|${it.original.id}" })
				},
				isOpened = match.duplicate.id == State.previewId,
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
		when {
			item.size == 0L -> Color(MaterialTheme.colorScheme.error.toArgb() xor 0x00FFFFFF)
			item.isNotFound -> MaterialTheme.colorScheme.error
			else -> MaterialTheme.colorScheme.onSurface
		}
	val background =
		if (isOpened) MaterialTheme.colorScheme.primary.copy(alpha = 0.128f) else Color.Transparent
	val modifier =
		Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(background).let {
			if (onClick != null) it.clickable(onClick = onClick) else it
		}
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