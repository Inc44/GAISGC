package im.bpu.gaisgc.manager

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import im.bpu.gaisgc.Constants
import im.bpu.gaisgc.DuplicateMatch
import im.bpu.gaisgc.Item
import im.bpu.gaisgc.PdfDocument
import im.bpu.gaisgc.State
import im.bpu.gaisgc.manager.MediaManager.getVideoMiddleFrame
import im.bpu.gaisgc.manager.MediaManager.isVideoThumbnailBlack
import im.bpu.gaisgc.parser.ChatParser
import im.bpu.gaisgc.service.DriveService
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.skia.Image

object DriveManager {

	private suspend fun getFilesByMime(service: Drive, mime: String): List<DriveFile> {
		val files = mutableListOf<DriveFile>()
		var pageToken: String? = null
		do {
			val result =
				withContext(Dispatchers.IO) {
					service
						.files()
						.list()
						.setQ("mimeType = '$mime' and trashed = false")
						.setFields("nextPageToken, files(createdTime, id, name, sha256Checksum)")
						.setPageToken(pageToken)
						.execute()
				}
			result.files?.let { files.addAll(it) }
			pageToken = result.nextPageToken
		} while (pageToken != null)
		return files
	}

	private fun getFileDetails(service: Drive, fileId: String): Item {
		return try {
			val file =
				service
					.files()
					.get(fileId)
					.setFields(
						"createdTime, fileExtension, id, mimeType, name, sha256Checksum, size"
					)
					.execute()
			Item(
				createdTime = file.createdTime?.value ?: 0L,
				fileExtension = file.fileExtension,
				id = file.id,
				mimeType = file.mimeType ?: "",
				name = file.name,
				sha256Checksum = file.sha256Checksum,
				size = file.getSize() ?: 0L,
			)
		} catch (exception: GoogleJsonResponseException) {
			if (exception.statusCode == 404) Item(id = fileId, name = fileId, isNotFound = true)
			else throw exception
		}
	}

	private suspend fun getFolderId(service: Drive, path: String): String? =
		withContext(Dispatchers.IO) {
			val parts = path.split("/").filter { it.isNotEmpty() }
			var parentId = "root"
			for (part in parts) {
				val result =
					service
						.files()
						.list()
						.setQ(
							"mimeType = '${Constants.MIME_FOLDER}' and name = '$part' and '$parentId' in parents and trashed = false"
						)
						.setFields("files(id)")
						.execute()
				val files = result.files ?: emptyList()
				if (files.isEmpty()) return@withContext null
				parentId = files[0].id
			}
			parentId
		}

	private suspend fun getFilesByParent(service: Drive, parentId: String): List<DriveFile> {
		val files = mutableListOf<DriveFile>()
		var pageToken: String? = null
		do {
			val result =
				withContext(Dispatchers.IO) {
					service
						.files()
						.list()
						.setQ("'$parentId' in parents and trashed = false")
						.setFields(
							"nextPageToken, files(createdTime, fileExtension, id, mimeType, name, sha256Checksum, size)"
						)
						.setPageToken(pageToken)
						.execute()
				}
			result.files?.let { files.addAll(it) }
			pageToken = result.nextPageToken
		} while (pageToken != null)
		return files
	}

	suspend fun fetch() = coroutineScope {
		withContext(Dispatchers.Main) {
			State.clearSelection()
			State.items.clear()
			State.unlinkedItems.clear()
			State.duplicateItems.clear()
		}
		val service = DriveService.getService()
		val chatFiles = getFilesByMime(service, Constants.MIME_PROMPT)
		val chatItems =
			chatFiles.map {
				Item(
					createdTime = it.createdTime?.value ?: 0L,
					id = it.id,
					name = it.name,
					sha256Checksum = it.sha256Checksum,
				)
			}
		withContext(Dispatchers.Main) { State.items.addAll(chatItems) }
		val deferredSubItems =
			chatFiles.indices.map { i ->
				async(Dispatchers.IO) {
					val file = chatFiles[i]
					val cachedItem =
						if (State.cache) CacheManager.loadFromCache(file.id, file.sha256Checksum)
						else null
					val subItems =
						if (cachedItem != null) {
							cachedItem.subItems
						} else {
							val subItemIds = ChatParser.getChatSubItems(service, file.id)
							val fetchedSubItems =
								subItemIds.map { id -> getFileDetails(service, id) }
							if (State.cache)
								CacheManager.saveToCache(
									file.createdTime?.value ?: 0L,
									file.id,
									file.name,
									file.sha256Checksum,
									fetchedSubItems,
								)
							fetchedSubItems
						}
					withContext(Dispatchers.Main) {
						State.items[i] =
							Item(
								createdTime = file.createdTime?.value ?: 0L,
								id = file.id,
								name = file.name,
								sha256Checksum = file.sha256Checksum,
								subItems = subItems,
							)
					}
					subItems
				}
			}
		val referencedItems = deferredSubItems.awaitAll().flatten()
		val referencedItemIds = referencedItems.map { it.id }.toSet()
		val chatIds = chatFiles.map { it.id }.toSet()
		val gaisFolderId = getFolderId(service, State.gaisPath)
		if (gaisFolderId != null) {
			val gaisFolderFiles = getFilesByParent(service, gaisFolderId)
			val gaisFolderItems =
				gaisFolderFiles.map {
					Item(
						createdTime = it.createdTime?.value ?: 0L,
						fileExtension = it.fileExtension,
						id = it.id,
						mimeType = it.mimeType ?: "",
						name = it.name,
						sha256Checksum = it.sha256Checksum,
						size = it.getSize() ?: 0L,
					)
				}
			val orphanItems =
				gaisFolderItems
					.filter { it.id !in chatIds && it.id !in referencedItemIds }
					.map { it.copy(isNotFound = true) }
			withContext(Dispatchers.Main) { State.unlinkedItems.addAll(orphanItems) }
		}
		val duplicatesFolderId = getFolderId(service, State.duplicatesPath)
		if (duplicatesFolderId != null) {
			val duplicatesFolderFiles = getFilesByParent(service, duplicatesFolderId)
			val duplicatesFolderItems =
				duplicatesFolderFiles.map {
					Item(
						createdTime = it.createdTime?.value ?: 0L,
						fileExtension = it.fileExtension,
						id = it.id,
						mimeType = it.mimeType ?: "",
						name = it.name,
						sha256Checksum = it.sha256Checksum,
						size = it.getSize() ?: 0L,
					)
				}
			val duplicateItems = findDuplicates(State.items.toList(), duplicatesFolderItems)
			withContext(Dispatchers.Main) { State.duplicateItems.addAll(duplicateItems) }
		}
	}

	private fun findDuplicates(
		chatItems: List<Item>,
		folderItems: List<Item>,
	): List<DuplicateMatch> {
		val duplicateItems = mutableListOf<DuplicateMatch>()
		for (chatItem in chatItems) {
			for (subItem in chatItem.subItems) {
				if (
					subItem.isNotFound ||
						subItem.fileExtension == null ||
						subItem.size == 0L ||
						subItem.sha256Checksum == null
				)
					continue
				val duplicateItem =
					folderItems.firstOrNull { folderItem ->
						folderItem.id != subItem.id &&
							folderItem.fileExtension == subItem.fileExtension &&
							folderItem.size == subItem.size &&
							folderItem.sha256Checksum == subItem.sha256Checksum
					}
				if (duplicateItem != null) {
					duplicateItems.add(DuplicateMatch(chatItem, subItem, duplicateItem))
				}
			}
		}
		return duplicateItems
	}

	suspend fun getDocumentById(id: String): List<String>? =
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

	suspend fun getImageById(id: String): ImageBitmap? =
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

	suspend fun getPdfById(
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

	suspend fun getVideoById(id: String, size: Long): ImageBitmap? =
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
				) {
					val image = getVideoMiddleFrame(service, id)
					image
				} else {
					val image = Image.makeFromEncoded(bytes).toComposeImageBitmap()
					image
				}
			} catch (exception: Exception) {
				null
			}
		}

	suspend fun trash(ids: List<String>) =
		withContext(Dispatchers.IO) {
			val service = DriveService.getService()
			coroutineScope {
				ids.forEach { id ->
					launch {
						try {
							val file = DriveFile()
							file.trashed = true
							service.files().update(id, file).execute()
							withContext(Dispatchers.Main) {
								State.unlinkedItems.removeIf { it.id == id }
								State.selectedIds.remove(id)
								if (State.lastSelectedId == id) {
									State.lastSelectedId = null
								}
							}
						} catch (exception: Exception) {
							exception.printStackTrace()
						}
					}
				}
			}
		}

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
			State.isPhoto(lowercaseMimeType) -> {
				State.selectedImage = getImageById(item.id)
			}
			State.isPdf(lowercaseMimeType) -> {
				State.selectedPdf = getPdfById(item.id, scope, previewPaneWidthPx)
			}
			State.isVideo(lowercaseMimeType) -> {
				State.selectedImage = getVideoById(item.id, item.size)
			}
		}
	}
}