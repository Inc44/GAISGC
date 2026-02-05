package im.bpu.gaisgc.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.State
import im.bpu.gaisgc.manager.CacheManager
import im.bpu.gaisgc.model.RelinkMethod
import im.bpu.gaisgc.service.DriveService

private val RelinkMethod.displayName: String
	get() =
		when (this) {
			RelinkMethod.DIRECT -> "Direct"
			RelinkMethod.REGEX -> "Regex"
			RelinkMethod.PRETTY -> "Pretty"
			RelinkMethod.JS_BEAUTIFY -> "js-beautify"
		}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel() {
	var relinkMenuExpanded by remember { mutableStateOf(false) }
	Column(
		modifier =
			Modifier.fillMaxSize()
				.padding(16.dp)
				.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Checkbox(
				checked = State.middleFrame,
				onCheckedChange = { State.saveSettings(it, State.cache, State.relinkMethod) },
			)
			Spacer(Modifier.width(8.dp))
			Text("Use the middle frame of the video if a black thumbnail is detected")
		}
		Row(verticalAlignment = Alignment.CenterVertically) {
			Checkbox(
				checked = State.cache,
				onCheckedChange = { State.saveSettings(State.middleFrame, it, State.relinkMethod) },
			)
			Spacer(Modifier.width(8.dp))
			Text("Use cached chats if the SHA256 checksum has not changed.")
		}
		if (State.devMode) {
			Column {
				Text(text = "Relink method", style = MaterialTheme.typography.bodyMedium)
				Spacer(Modifier.height(4.dp))
				ExposedDropdownMenuBox(
					expanded = relinkMenuExpanded,
					onExpandedChange = { relinkMenuExpanded = it },
				) {
					OutlinedTextField(
						value = State.relinkMethod.displayName,
						onValueChange = {},
						readOnly = true,
						trailingIcon = {
							ExposedDropdownMenuDefaults.TrailingIcon(expanded = relinkMenuExpanded)
						},
						colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
						modifier =
							Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
					)
					ExposedDropdownMenu(
						expanded = relinkMenuExpanded,
						onDismissRequest = { relinkMenuExpanded = false },
					) {
						RelinkMethod.entries.forEach { method ->
							DropdownMenuItem(
								text = { Text(method.displayName) },
								onClick = {
									State.saveSettings(State.middleFrame, State.cache, method)
									relinkMenuExpanded = false
								},
								contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
							)
						}
					}
				}
				Spacer(Modifier.height(4.dp))
			}
		}
		Button(
			onClick = { CacheManager.clearCache() },
			colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
		) {
			Text("Clear Cache")
		}
		Button(
			onClick = { DriveService.logout() },
			colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
		) {
			Text("Log out")
		}
	}
}