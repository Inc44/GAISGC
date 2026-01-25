package im.bpu.gaisgc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.Item
import im.bpu.gaisgc.State
import im.bpu.gaisgc.manager.DriveManager
import kotlinx.coroutines.launch

@Composable
fun ItemRow(item: Item, depth: Int = 0) {
	val color = if (item.isNotFound) Color(237, 53, 36) else MaterialTheme.colorScheme.onSurface
	Column(modifier = Modifier.padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp)) {
		Text(text = item.name, style = MaterialTheme.typography.bodyMedium, color = color)
		item.subItems.forEach { subItem -> ItemRow(subItem, depth + 1) }
	}
}

@Composable
fun MainWindow() {
	val scope = rememberCoroutineScope()
	LaunchedEffect(Unit) { DriveManager.fetch() }
	MaterialTheme {
		Scaffold(
			topBar = {
				Row(
					modifier = Modifier.padding(16.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = "GAISGC",
						style = MaterialTheme.typography.headlineSmall,
						modifier = Modifier.weight(1f),
						fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
					)
					Button(onClick = { scope.launch { DriveManager.fetch() } }) { Text("Refresh") }
				}
			}
		) { topBarPadding ->
			LazyColumn(modifier = Modifier.padding(topBarPadding).padding(horizontal = 16.dp)) {
				items(State.items) { item -> ItemRow(item) }
			}
		}
	}
}