package im.bpu.gaisgc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.Item

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
) {
	val color =
		when {
			item.size == 0L -> Color(MaterialTheme.colorScheme.error.toArgb() xor 0x00FFFFFF)
			item.isNotFound -> MaterialTheme.colorScheme.error
			else -> MaterialTheme.colorScheme.onSurface
		}
	val background =
		if (isOpened) MaterialTheme.colorScheme.primary.copy(alpha = 0.128f) else Color.Transparent
	val modifier =
		Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(background).let {
			if (onClick != null) it.clickable(onClick = onClick) else it
		}
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
				)
			}
		}
	}
}