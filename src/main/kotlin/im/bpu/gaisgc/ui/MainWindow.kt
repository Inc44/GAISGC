package im.bpu.gaisgc.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.State
import im.bpu.gaisgc.manager.DriveManager

@Composable
fun ItemRow(name: String) {
	Text(text = name, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun MainWindow() {
	LaunchedEffect(Unit) { DriveManager.fetch() }
	LazyColumn(modifier = Modifier.padding(16.dp)) {
		items(State.items) { item -> ItemRow(item.name) }
	}
}