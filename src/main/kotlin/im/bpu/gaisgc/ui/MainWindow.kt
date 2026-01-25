package im.bpu.gaisgc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.Item
import im.bpu.gaisgc.State
import im.bpu.gaisgc.manager.DriveManager

@Composable
fun ItemRow(item: Item, depth: Int = 0) {
	Column(modifier = Modifier.padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp)) {
		Text(text = item.name)
		item.subItems.forEach { subItem -> ItemRow(subItem, depth + 1) }
	}
}

@Composable
fun MainWindow() {
	LaunchedEffect(Unit) { DriveManager.fetch() }
	LazyColumn(modifier = Modifier.padding(16.dp)) { items(State.items) { item -> ItemRow(item) } }
}