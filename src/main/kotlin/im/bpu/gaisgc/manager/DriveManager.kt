package im.bpu.gaisgc.manager

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.GenericJson
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import im.bpu.gaisgc.DuplicateMatch
import im.bpu.gaisgc.Item
import im.bpu.gaisgc.PdfDocument
import im.bpu.gaisgc.State
import im.bpu.gaisgc.manager.MediaManager.getVideoMiddleFrame
import im.bpu.gaisgc.manager.MediaManager.isVideoThumbnailBlack
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
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
	private const val APPLICATION_NAME = "GAISGC"
	private const val TOKENS_DIRECTORY_PATH = "tokens"
	private const val CREDENTIALS_FILE_PATH = "credentials.json"
	private const val MIME_PROMPT = "application/vnd.google-makersuite.prompt"
	private const val MIME_FOLDER = "application/vnd.google-apps.folder"
	private const val USER_ID = "user"
	private const val PORT = 8888
	private const val TIMEOUT_MS = 0
	private const val MAX_VIDEO_SIZE = 25 * 1024 * 1024
	private val JSON_FACTORY = GsonFactory.getDefaultInstance()
	private val SCOPES = listOf(DriveScopes.DRIVE)
	private var driveService: Drive? = null

	private fun getCredentials(httpTransport: HttpTransport): Credential {
		val file = File(CREDENTIALS_FILE_PATH)
		if (!file.exists()) throw Exception("Resource not found: $CREDENTIALS_FILE_PATH")
		val clientSecrets =
			GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(file.inputStream()))
		val flow =
			GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
				.setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
				.setAccessType("offline")
				.build()
		val receiver = LocalServerReceiver.Builder().setPort(PORT).build()
		val credential = AuthorizationCodeInstalledApp(flow, receiver).authorize(USER_ID)
		return credential
	}

	private suspend fun getService(): Drive {
		return withContext(Dispatchers.IO) {
			driveService
				?: run {
					val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
					val service =
						Drive.Builder(httpTransport, JSON_FACTORY) { request ->
								val credential = getCredentials(httpTransport)
								credential.initialize(request)
								request.connectTimeout = TIMEOUT_MS
								request.readTimeout = TIMEOUT_MS
							}
							.setApplicationName(APPLICATION_NAME)
							.build()
					driveService = service
					service
				}
		}
	}

	fun logout() {
		try {
			val tokensDirectoryPath = File(TOKENS_DIRECTORY_PATH)
			if (tokensDirectoryPath.exists()) {
				tokensDirectoryPath.deleteRecursively()
			}
			driveService = null
			State.clearSelection()
			State.items.clear()
			State.unlinkedItems.clear()
			State.duplicateItems.clear()
		} catch (exception: Exception) {
			exception.printStackTrace()
		}
	}

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
						.setFields("nextPageToken, files(createdTime, id, name)")
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

	private fun extractSubItemIds(ins: InputStream): List<String> {
		val ids = mutableListOf<String>()
		val reader = JsonReader(InputStreamReader(ins, "UTF-8"))
		reader.use {
			if (it.peek() == JsonToken.BEGIN_OBJECT) {
				parseObject(it, ids)
			}
		}
		return ids
	}

	private fun parseObject(reader: JsonReader, ids: MutableList<String>) {
		reader.beginObject()
		while (reader.hasNext()) {
			val name = reader.nextName()
			when {
				name == "driveDocument" || name == "driveImage" || name == "driveVideo" -> {
					parseIdContainer(reader, ids)
				}
				reader.peek() == JsonToken.BEGIN_OBJECT -> parseObject(reader, ids)
				reader.peek() == JsonToken.BEGIN_ARRAY -> parseArray(reader, ids)
				else -> reader.skipValue()
			}
		}
		reader.endObject()
	}

	private fun parseArray(reader: JsonReader, ids: MutableList<String>) {
		reader.beginArray()
		while (reader.hasNext()) {
			when (reader.peek()) {
				JsonToken.BEGIN_OBJECT -> parseObject(reader, ids)
				JsonToken.BEGIN_ARRAY -> parseArray(reader, ids)
				else -> reader.skipValue()
			}
		}
		reader.endArray()
	}

	private fun parseIdContainer(reader: JsonReader, ids: MutableList<String>) {
		reader.beginObject()
		while (reader.hasNext()) {
			val name = reader.nextName()
			if (name == "id" && reader.peek() == JsonToken.STRING) {
				ids.add(reader.nextString())
			} else {
				reader.skipValue()
			}
		}
		reader.endObject()
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
							"mimeType = '$MIME_FOLDER' and name = '$part' and '$parentId' in parents and trashed = false"
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
		val service = getService()
		val chatFiles = getFilesByMime(service, MIME_PROMPT)
		val chatItems =
			chatFiles.map {
				Item(createdTime = it.createdTime?.value ?: 0L, id = it.id, name = it.name)
			}
		withContext(Dispatchers.Main) { State.items.addAll(chatItems) }
		val deferredSubItems =
			chatFiles.indices.map { i ->
				async(Dispatchers.IO) {
					val file = chatFiles[i]
					val subItemIds = getChatSubItems(service, file.id)
					val subItems = subItemIds.map { id -> getFileDetails(service, id) }
					withContext(Dispatchers.Main) {
						State.items[i] =
							Item(
								createdTime = file.createdTime?.value ?: 0L,
								id = file.id,
								name = file.name,
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

	private fun getChatSubItems(service: Drive, id: String): List<String> {
		return service.files().get(id).executeMediaAsInputStream().use { extractSubItemIds(it) }
	}

	suspend fun getDocumentById(id: String): List<String>? =
		withContext(Dispatchers.IO) {
			try {
				val service = getService()
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
				val service = getService()
				val bytes = downloadFileBytes(service, id)
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
				val service = getService()
				val bytes = downloadFileBytes(service, id)
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
				val service = getService()
				val file = service.files().get(id).setFields("thumbnailLink").execute()
				val link = file.thumbnailLink ?: return@withContext null
				val bytes = downloadLinkBytes(service, link)
				if (State.middleFrame && size < MAX_VIDEO_SIZE && isVideoThumbnailBlack(bytes)) {
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

	suspend fun downloadFileBytes(service: Drive, id: String): ByteArray {
		val baos = ByteArrayOutputStream()
		service.files().get(id).executeMediaAndDownloadTo(baos)
		val bytes = baos.toByteArray()
		return bytes
	}

	private suspend fun downloadLinkBytes(service: Drive, link: String): ByteArray {
		val resp = service.requestFactory.buildGetRequest(GenericUrl(link)).execute()
		val bytes = resp.content.use { it.readBytes() }
		return bytes
	}

	suspend fun trash(ids: List<String>) =
		withContext(Dispatchers.IO) {
			val service = getService()
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

	suspend fun relink(matches: List<DuplicateMatch>) =
		withContext(Dispatchers.IO) {
			val service = getService()
			val matchesByChat = matches.groupBy { it.chat.id }
			matchesByChat.forEach { (chatId, chatMatches) ->
				try {
					val baos = ByteArrayOutputStream()
					service.files().get(chatId).executeMediaAndDownloadTo(baos)
					val content = baos.toString("UTF-8")
					val chat = JSON_FACTORY.fromString(content, GenericJson::class.java)
					var modified = false
					val chunkedPrompt = chat["chunkedPrompt"] as? Map<*, *>
					val chunks = chunkedPrompt?.get("chunks") as? List<*>
					chunks?.forEach { chunk ->
						val chunkMap = chunk as? MutableMap<String, Any?>
						if (chunkMap != null) {
							listOf("driveDocument", "driveImage", "driveVideo").forEach { key ->
								val driveReference = chunkMap[key] as? MutableMap<String, Any?>
								val driveReferenceId = driveReference?.get("id") as? String
								if (driveReferenceId != null) {
									val match =
										chatMatches.find { it.original.id == driveReferenceId }
									if (match != null) {
										driveReference["id"] = match.duplicate.id
										modified = true
									}
								}
							}
						}
					}
					if (modified) {
						var contentString = JSON_FACTORY.toString(chat)
						val json = JsonParser.parseString(contentString)
						val gson = GsonBuilder().setPrettyPrinting().create()
						contentString = gson.toJson(json)
						val mediaContent = ByteArrayContent.fromString(MIME_PROMPT, contentString)
						val file = service.files().get(chatId).setFields("modifiedTime").execute()
						val chatModifiedTime = file.modifiedTime
						val metadataContent = DriveFile()
						metadataContent.modifiedTime = chatModifiedTime
						service.files().update(chatId, metadataContent, mediaContent).execute()
					}
					withContext(Dispatchers.Main) {
						State.duplicateItems.removeAll(chatMatches)
						val matchIds = chatMatches.map { "${it.chat.id}|${it.original.id}" }
						State.selectedIds.removeAll(matchIds)
					}
				} catch (exception: Exception) {
					exception.printStackTrace()
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