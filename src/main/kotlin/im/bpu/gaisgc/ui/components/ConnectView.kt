package im.bpu.gaisgc.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.manager.DriveManager
import kotlinx.coroutines.launch

@Composable
fun ConnectView(modifier: Modifier) {
	val scope = rememberCoroutineScope()
	Box(modifier = modifier, contentAlignment = Alignment.Center) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Text("Keep Your Google Drive Clean", style = MaterialTheme.typography.headlineMedium)
			Spacer(Modifier.height(16.dp))
			Button(onClick = { scope.launch { DriveManager.fetch() } }) {
				Text("Connect to Google Drive")
			}
		}
	}
}