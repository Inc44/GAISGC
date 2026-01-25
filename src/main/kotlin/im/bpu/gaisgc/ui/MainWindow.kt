package im.bpu.gaisgc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.Item
import im.bpu.gaisgc.State

@Composable
fun ItemRow(item: Item, depth: Int = 0) {
	val color = if (item.isNotFound) Color(237, 53, 36) else MaterialTheme.colorScheme.onSurface
	Column(modifier = Modifier.padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp)) {
		Text(text = item.name, style = MaterialTheme.typography.bodyMedium, color = color)
		item.subItems.forEach { subItem -> ItemRow(subItem, depth + 1) }
	}
}

@Composable
fun MainView() {
	LazyColumn(modifier = Modifier.padding(16.dp)) { items(State.items) { item -> ItemRow(item) } }
}

@Composable
fun UnlinkedView() {
	LazyColumn(modifier = Modifier.padding(16.dp)) {
		items(State.unlinkedItems) { item -> ItemRow(item) }
	}
}