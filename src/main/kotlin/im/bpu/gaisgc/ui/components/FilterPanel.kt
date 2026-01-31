package im.bpu.gaisgc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.FilterMimeType
import im.bpu.gaisgc.Sort
import im.bpu.gaisgc.State

@Composable
fun FilterPanel() {
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