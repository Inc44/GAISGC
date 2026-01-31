package im.bpu.gaisgc.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import im.bpu.gaisgc.State

@Composable
fun PreviewPane(modifier: Modifier) {
	val document = State.selectedDocument
	val image = State.selectedImage
	val pdf = State.selectedPdf
	Column(modifier = modifier.fillMaxHeight().padding(16.dp)) {
		when {
			pdf != null -> {
				LazyColumn(
					modifier = Modifier.fillMaxSize(),
					verticalArrangement = Arrangement.spacedBy(16.dp),
				) {
					items(pdf.pages) { bitmap ->
						Image(
							bitmap = bitmap,
							contentDescription = "PDF Preview Pane",
							modifier = Modifier.fillMaxWidth(),
						)
					}
				}
			}
			image != null -> {
				Image(
					bitmap = image,
					contentDescription = "Image Preview Pane",
					modifier = Modifier.fillMaxSize(),
				)
			}
			document.isNotEmpty() -> {
				SelectionContainer {
					LazyColumn(modifier = Modifier.fillMaxSize()) {
						items(document) { line ->
							Text(
								text = line,
								modifier = Modifier.fillMaxWidth(),
								fontFamily = FontFamily.Monospace,
							)
						}
					}
				}
			}
		}
	}
}