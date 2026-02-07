package im.bpu.gaisgc.manager

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import im.bpu.gaisgc.State
import im.bpu.gaisgc.manager.MediaManager.getVideoMiddleFrame
import im.bpu.gaisgc.manager.MediaManager.isVideoThumbnailBlack
import im.bpu.gaisgc.model.Constants
import im.bpu.gaisgc.model.Item
import im.bpu.gaisgc.model.PdfDocument
import im.bpu.gaisgc.service.DriveService
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.skia.Image

object PreviewManager {
	suspend fun loadPreview(item: Item, scope: CoroutineScope, previewPaneWidthPx: Float) {
		State.previewId = item.id
		State.selectedDocument.clear()
		State.selectedImage = null
		State.selectedPdf?.close()
		State.selectedPdf = null
		val lowercaseMimeType = item.mimeType.lowercase()
		when {
			State.isDocument(lowercaseMimeType) || State.isOther(lowercaseMimeType) -> {
				val lines = getDocumentById(item.id)
				if (lines != null) State.selectedDocument.addAll(lines)
			}
			State.isPhoto(lowercaseMimeType) -> State.selectedImage = getImageById(item.id)
			State.isPdf(lowercaseMimeType) ->
				State.selectedPdf = getPdfById(item.id, scope, previewPaneWidthPx)
			State.isVideo(lowercaseMimeType) ->
				State.selectedImage = getVideoById(item.id, item.size)
		}
	}

	private suspend fun getDocumentById(id: String): List<String>? =
		withContext(Dispatchers.IO) {
			try {
				val service = DriveService.getService()
				val baos = ByteArrayOutputStream()
				service.files().get(id).executeMediaAndDownloadTo(baos)
				val bytes = baos.toByteArray()
				val document = String(bytes, Charsets.UTF_8)
				val reader = document.reader()
				val lines = reader.readLines()
				lines
			} catch (exception: Exception) {
				null
			}
		}

	private suspend fun getImageById(id: String): ImageBitmap? =
		withContext(Dispatchers.IO) {
			try {
				val service = DriveService.getService()
				val bytes = DriveService.downloadFileBytes(service, id)
				val image = Image.makeFromEncoded(bytes).toComposeImageBitmap()
				image
			} catch (exception: Exception) {
				null
			}
		}

	private suspend fun getPdfById(
		id: String,
		scope: CoroutineScope,
		previewPaneWidthPx: Float,
	): PdfDocument? =
		withContext(Dispatchers.IO) {
			try {
				val service = DriveService.getService()
				val bytes = DriveService.downloadFileBytes(service, id)
				val document = Loader.loadPDF(bytes)
				val renderer = PDFRenderer(document)
				val firstPdfPageWidthPts = document.getPage(0).mediaBox.width
				val dpi = (previewPaneWidthPx * 72f) / firstPdfPageWidthPts
				val firstPdfPage = renderer.renderImageWithDPI(0, dpi).toComposeImageBitmap()
				val pdfDocument = PdfDocument(document, firstPdfPage)
				pdfDocument.renderRemaining(scope, previewPaneWidthPx)
				pdfDocument
			} catch (exception: Exception) {
				null
			}
		}

	private suspend fun getVideoById(id: String, size: Long): ImageBitmap? =
		withContext(Dispatchers.IO) {
			try {
				val service = DriveService.getService()
				val file = service.files().get(id).setFields("thumbnailLink").execute()
				val link = file.thumbnailLink ?: return@withContext null
				val bytes = DriveService.downloadLinkBytes(service, link)
				if (
					State.middleFrame &&
						size < Constants.MAX_VIDEO_SIZE &&
						isVideoThumbnailBlack(bytes)
				)
					getVideoMiddleFrame(service, id)
				else Image.makeFromEncoded(bytes).toComposeImageBitmap()
			} catch (exception: Exception) {
				null
			}
		}
}