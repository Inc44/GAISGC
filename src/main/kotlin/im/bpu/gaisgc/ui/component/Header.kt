package im.bpu.gaisgc.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.State
import im.bpu.gaisgc.model.Screen

@Composable
fun Header(
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
fun CompactHeader(
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
fun StandardHeader(
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
fun ScreenSpecificPathInput(modifier: Modifier) {
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
fun PathInput(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier) {
	OutlinedTextField(
		value = value,
		onValueChange = onValueChange,
		label = { Text(label) },
		modifier = modifier,
		singleLine = true,
	)
}

@Composable
fun Title(modifier: Modifier) {
	Text(
		text = if (State.screen == Screen.SETTINGS) "Settings" else "GAISGC",
		style = MaterialTheme.typography.headlineSmall,
		fontWeight = FontWeight.Bold,
		modifier = modifier,
	)
}