package im.bpu.gaisgc.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer

class PdfDocument(private val document: PDDocument, firstPage: ImageBitmap) : Closeable {
	val pages = mutableStateListOf(firstPage)
	@Volatile private var isClosed = false

	fun renderRemaining(scope: CoroutineScope, previewPaneWidthPx: Float) {
		scope.launch(Dispatchers.IO) {
			val renderer = PDFRenderer(document)
			for (i in 1 until document.numberOfPages) {
				if (isClosed) break
				val bitmap =
					synchronized(document) {
						if (isClosed) return@synchronized null
						val pdfPageWidthPts = document.getPage(i).mediaBox.width
						val dpi = (previewPaneWidthPx * 72f) / pdfPageWidthPts
						renderer.renderImageWithDPI(i, dpi).toComposeImageBitmap()
					}
				if (bitmap != null) {
					withContext(Dispatchers.Main) { if (!isClosed) pages.add(bitmap) }
				}
			}
		}
	}

	override fun close() {
		isClosed = true
		synchronized(document) { document.close() }
	}
}