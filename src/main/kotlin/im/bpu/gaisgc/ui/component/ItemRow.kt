package im.bpu.gaisgc.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.manager.DriveManager
import im.bpu.gaisgc.model.Item
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ItemRow(
	item: Item,
	depth: Int = 0,
	onClick: (() -> Unit)? = null,
	hasCheckbox: Boolean = false,
	isChecked: Boolean = false,
	onCheckedChange: ((Boolean) -> Unit)? = null,
	isOpened: Boolean = false,
	onSubItemClick: ((Item) -> Unit)? = null,
	previewId: String? = null,
	canEdit: Boolean = true,
) {
	var showEditDialog by remember { mutableStateOf(false) }
	val color =
		when {
			item.size == 0L -> Color(MaterialTheme.colorScheme.error.toArgb() xor 0x00FFFFFF)
			item.isNotFound -> MaterialTheme.colorScheme.error
			else -> MaterialTheme.colorScheme.onSurface
		}
	val background =
		if (isOpened) MaterialTheme.colorScheme.primary.copy(alpha = 0.128f) else Color.Transparent
	val modifier =
		Modifier.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(background)
			.combinedClickable(
				onClick = { onClick?.invoke() },
				onDoubleClick = if (canEdit) { { showEditDialog = true } } else null,
			)
	Row(
		modifier =
			modifier.padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp).fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
	) {
		if (hasCheckbox && onCheckedChange != null) {
			Checkbox(checked = isChecked, onCheckedChange = onCheckedChange)
			Spacer(Modifier.width(8.dp))
		}
		Column {
			Text(text = item.name, style = MaterialTheme.typography.bodyMedium, color = color)
			item.subItems.forEach { subItem ->
				ItemRow(
					item = subItem,
					depth = depth + 1,
					onClick = { onSubItemClick?.invoke(subItem) },
					isOpened = subItem.id == previewId,
					onSubItemClick = onSubItemClick,
					previewId = previewId,
					canEdit = canEdit,
				)
			}
		}
	}
	if (showEditDialog) {
		EditDialog(item = item, onDismiss = { showEditDialog = false })
	}
}

@Composable
fun EditDialog(item: Item, onDismiss: () -> Unit) {
	val scope = rememberCoroutineScope()
	val dateTimeFormatter = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()) }
	var name by remember { mutableStateOf(item.name) }
	val createdTimeStr =
		remember {
			if (item.createdTime > 0) dateTimeFormatter.format(Date(item.createdTime)) else ""
		}
	var modifiedTimeStr by remember {
		mutableStateOf(
			if (item.modifiedTime > 0) dateTimeFormatter.format(Date(item.modifiedTime)) else ""
		)
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Edit") },
		text = {
			Column {
				OutlinedTextField(
					value = name,
					onValueChange = { name = it },
					label = { Text("Name") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
				)
				Spacer(Modifier.height(8.dp))
				OutlinedTextField(
					value = createdTimeStr,
					onValueChange = {},
					readOnly = true,
					label = { Text("Created Time") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
				)
				Spacer(Modifier.height(8.dp))
				OutlinedTextField(
					value = modifiedTimeStr,
					onValueChange = { modifiedTimeStr = it },
					label = { Text("Modified Time") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
				)
			}
		},
		confirmButton = {
			TextButton(
				onClick = {
					try {
						val modifiedTime = dateTimeFormatter.parse(modifiedTimeStr).time
						scope.launch { DriveManager.update(item.id, name, modifiedTime) }
						onDismiss()
					} catch (exception: Exception) {
						exception.printStackTrace()
					}
				}
			) {
				Text("Save")
			}
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
	)
}