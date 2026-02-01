package im.bpu.gaisgc.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.State
import im.bpu.gaisgc.manager.PreviewManager
import im.bpu.gaisgc.model.Item
import kotlinx.coroutines.launch

@Composable
fun ChatList(previewPaneWidthPx: Float) {
	val scope = rememberCoroutineScope()
	LazyColumn(modifier = Modifier.padding(16.dp)) {
		items(State.items) { item ->
			ItemRow(
				item = item,
				onClick = {
					scope.launch { PreviewManager.loadPreview(item, scope, previewPaneWidthPx) }
				},
				isOpened = item.id == State.previewId,
				onSubItemClick = { subItem ->
					scope.launch { PreviewManager.loadPreview(subItem, scope, previewPaneWidthPx) }
				},
				previewId = State.previewId,
			)
		}
	}
}

@Composable
fun UnlinkedList(previewPaneWidthPx: Float) {
	val scope = rememberCoroutineScope()
	val filteredUnlinkedItems = State.getFilteredUnlinkedItems()
	LazyColumn(modifier = Modifier.padding(16.dp)) {
		items(filteredUnlinkedItems) { item ->
			ItemRow(
				item = item,
				onClick = {
					scope.launch { PreviewManager.loadPreview(item, scope, previewPaneWidthPx) }
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
fun RelinkerList(previewPaneWidthPx: Float) {
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
				onClick = {
					scope.launch {
						PreviewManager.loadPreview(match.duplicate, scope, previewPaneWidthPx)
					}
				},
				hasCheckbox = true,
				isChecked = id in State.selectedIds,
				onCheckedChange = {
					State.toggleSelection(id, matches.map { "${it.chat.id}|${it.original.id}" })
				},
				isOpened = match.duplicate.id == State.previewId,
				canEdit = false,
			)
		}
	}
}