package im.bpu.gaisgc.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.model.Screen
import im.bpu.gaisgc.State

@Composable
fun ActionButtons(
	onRefresh: () -> Unit,
	onToggleFilters: () -> Unit,
	onTrash: () -> Unit,
	onRelink: () -> Unit,
	showFilter: Boolean,
) {
	if (State.screen != Screen.SETTINGS) {
		if (showFilter) {
			IconButton(onClick = onToggleFilters) {
				Icon(Icons.Filled.FilterList, contentDescription = "Filters")
			}
			Spacer(Modifier.width(8.dp))
		}
		Button(onClick = onRefresh) { Text("Refresh") }
	}
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