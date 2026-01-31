package im.bpu.gaisgc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.State
import im.bpu.gaisgc.manager.DriveManager
import im.bpu.gaisgc.manager.RelinkManager
import im.bpu.gaisgc.model.Constants
import im.bpu.gaisgc.model.Screen
import im.bpu.gaisgc.ui.component.ChatList
import im.bpu.gaisgc.ui.component.ConnectView
import im.bpu.gaisgc.ui.component.FilterPanel
import im.bpu.gaisgc.ui.component.Header
import im.bpu.gaisgc.ui.component.NavigationSideBar
import im.bpu.gaisgc.ui.component.PreviewPane
import im.bpu.gaisgc.ui.component.RelinkerList
import im.bpu.gaisgc.ui.component.SettingsPanel
import im.bpu.gaisgc.ui.component.UnlinkedList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ApplicationLayout() {
	val scope = rememberCoroutineScope()
	val density = LocalDensity.current.density
	LaunchedEffect(State.isConnected) { if (State.isConnected) DriveManager.fetch() }
	MaterialTheme {
		Scaffold(modifier = Modifier.onPreviewKeyEvent { handleKeyEvent(it, scope) }) { padding ->
			if (!State.isConnected) {
				ConnectView(modifier = Modifier.padding(padding).fillMaxSize())
			} else {
				Row(modifier = Modifier.padding(padding).fillMaxSize()) {
					NavigationSideBar()
					ContentArea(
						modifier = Modifier.weight(1f),
						onRefresh = { scope.launch { DriveManager.fetch() } },
						onTrash = {
							scope.launch { DriveManager.trash(State.selectedIds.toList()) }
						},
						onRelink = {
							scope.launch {
								val matches =
									State.duplicateItems.filter {
										"${it.chat.id}|${it.original.id}" in State.selectedIds
									}
								RelinkManager.relink(matches)
							}
						},
						previewPaneWidthPx = Constants.PREVIEW_PANE_WIDTH_DP * density,
					)
					if (
						State.selectedDocument.isNotEmpty() ||
							State.selectedImage != null ||
							State.selectedPdf != null
					) {
						PreviewPane(modifier = Modifier.width(Constants.PREVIEW_PANE_WIDTH_DP.dp))
					}
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
private fun ContentArea(
	modifier: Modifier,
	onRefresh: () -> Unit,
	onTrash: () -> Unit,
	onRelink: () -> Unit,
	previewPaneWidthPx: Float,
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
				Screen.MAIN -> ChatList(previewPaneWidthPx)
				Screen.UNLINKED -> UnlinkedList(previewPaneWidthPx)
				Screen.RELINKER -> RelinkerList(previewPaneWidthPx)
				Screen.SETTINGS -> SettingsPanel()
			}
		}
	}
}