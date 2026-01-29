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
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.Key
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import im.bpu.gaisgc.Item
import im.bpu.gaisgc.PdfDocument
import im.bpu.gaisgc.State
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import javax.imageio.ImageIO
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

class Chat {
	@Key("chunkedPrompt") var chunkedPrompt: ChunkedPrompt? = null
}

class ChunkedPrompt {
	@Key("chunks") var chunks: List<Chunk>? = null
}

class Chunk {
	@Key("driveDocument") var driveDocument: DriveReference? = null
	@Key("driveImage") var driveImage: DriveReference? = null
	@Key("driveVideo") var driveVideo: DriveReference? = null
}

class DriveReference {
	@Key("id") var id: String? = null
}

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
	private const val LUMINANCE_THRESHOLD = 0.128
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
						.setFields("nextPageToken, files(id, name)")
						.setPageToken(pageToken)
						.execute()
				}
			result.files?.let { files.addAll(it) }
			pageToken = result.nextPageToken
		} while (pageToken != null)
		return files
	}

	private fun getFileName(service: Drive, fileId: String): String? {
		return try {
			service.files().get(fileId).setFields("name").execute().name
		} catch (exception: GoogleJsonResponseException) {
			if (exception.statusCode == 404) null else throw exception
		}
	}

	private fun extractSubItemIds(json: String): List<String> {
		val chat = JSON_FACTORY.fromString(json, Chat::class.java)
		return chat.chunkedPrompt?.chunks?.mapNotNull {
			it.driveDocument?.id ?: it.driveImage?.id ?: it.driveVideo?.id
		} ?: emptyList()
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
						.setFields("nextPageToken, files(id, name, createdTime, mimeType, size)")
						.setPageToken(pageToken)
						.execute()
				}
			result.files?.let { files.addAll(it) }
			pageToken = result.nextPageToken
		} while (pageToken != null)
		return files
	}

	suspend fun fetch() = coroutineScope {
		withContext(Dispatchers.Main) { State.clearSelection() }
		withContext(Dispatchers.Main) {
			State.items.clear()
			State.unlinkedItems.clear()
		}
		val service = getService()
		val chatFiles = getFilesByMime(service, MIME_PROMPT)
		withContext(Dispatchers.Main) {
			chatFiles.forEach { State.items.add(Item(it.id, it.name)) }
		}
		val deferredSubItems =
			chatFiles.indices.map { i ->
				async(Dispatchers.IO) {
					val file = chatFiles[i]
					val subItemIds = getChatSubItems(service, file.id)
					val subItems =
						subItemIds
							.map { id ->
								async {
									val name = getFileName(service, id)
									if (name == null) Item(id, id, isNotFound = true)
									else Item(id, name)
								}
							}
							.awaitAll()
					withContext(Dispatchers.Main) {
						State.items[i] = Item(file.id, file.name, subItems)
					}
					subItemIds
				}
			}
		val chatIds = chatFiles.map { it.id }.toSet()
		val driveReferenceIds = deferredSubItems.awaitAll().flatten().toSet()
		val gaisFolderId = getFolderId(service, State.gaisPath) ?: return@coroutineScope
		val gaisFolderFiles = getFilesByParent(service, gaisFolderId)
		val orphanFiles =
			gaisFolderFiles.filter { it.id !in chatIds && it.id !in driveReferenceIds }
		withContext(Dispatchers.Main) {
			orphanFiles.forEach {
				State.unlinkedItems.add(
					Item(
						id = it.id,
						name = it.name,
						createdTime = it.createdTime?.value ?: 0L,
						mimeType = it.mimeType ?: "",
						isNotFound = true,
						size = it.getSize() ?: 0L,
					)
				)
			}
		}
	}

	private fun getChatSubItems(service: Drive, id: String): List<String> {
		val baos = ByteArrayOutputStream()
		service.files().get(id).executeMediaAndDownloadTo(baos)
		val content = baos.toString("UTF-8")
		val subItemIds = extractSubItemIds(content)
		return subItemIds
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

	suspend fun getPdfById(id: String, scope: CoroutineScope): PdfDocument? =
		withContext(Dispatchers.IO) {
			try {
				val service = getService()
				val bytes = downloadFileBytes(service, id)
				val document = Loader.loadPDF(bytes)
				val renderer = PDFRenderer(document)
				val firstPage = renderer.renderImageWithDPI(0, 300f).toComposeImageBitmap()
				val pdfDocument = PdfDocument(document, firstPage)
				pdfDocument.renderRemaining(scope)
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
				if (size < MAX_VIDEO_SIZE && isVideoThumbnailBlack(bytes)) {
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

	private suspend fun downloadFileBytes(service: Drive, id: String): ByteArray {
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

	private suspend fun getVideoMiddleFrame(service: Drive, id: String): ImageBitmap? {
		return try {
			val bytes = downloadFileBytes(service, id)
			val videoFile = File.createTempFile("gaisgc", ".mkv")
			videoFile.writeBytes(bytes)
			val imageFile = File.createTempFile("gaisgc", ".png")
			val duration = getVideoDuration(videoFile)
			if (duration != null) {
				extractVideoFrame(videoFile, duration / 2, imageFile)
				if (imageFile.exists() && imageFile.length() > 0) {
					val bytes = imageFile.readBytes()
					videoFile.delete()
					imageFile.delete()
					val image = Image.makeFromEncoded(bytes).toComposeImageBitmap()
					return image
				}
			}
			videoFile.delete()
			imageFile.delete()
			null
		} catch (exception: Exception) {
			null
		}
	}

	private fun getVideoDuration(videoFile: File): Double? {
		val ffmpegProcess =
			ProcessBuilder("ffmpeg", "-i", videoFile.absolutePath).redirectErrorStream(true).start()
		val ffmpegProcessIS = ffmpegProcess.inputStream
		val reader = ffmpegProcessIS.reader()
		val text = reader.readText()
		ffmpegProcess.waitFor()
		val regex = Regex("Duration: (\\d+):(\\d+):(\\d+)\\.(\\d+)")
		val match = regex.find(text) ?: return null
		val (hours, minutes, seconds, centiseconds) = match.destructured
		return hours.toLong() * 3600 +
			minutes.toLong() * 60 +
			seconds.toLong() +
			centiseconds.toDouble() / 100
	}

	private fun extractVideoFrame(videoFile: File, time: Double, imageFile: File) {
		ProcessBuilder(
				"ffmpeg",
				"-ss",
				time.toString(),
				"-i",
				videoFile.absolutePath,
				"-frames:v",
				"1",
				"-y",
				imageFile.absolutePath,
			)
			.start()
			.waitFor()
	}

	private fun srgb(channel: Double): Double {
		return if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
	}

	private fun relativeLuminance(r: Double, g: Double, b: Double): Double {
		return 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b)
	}

	private fun isVideoThumbnailBlack(bytes: ByteArray): Boolean {
		return try {
			val bais = ByteArrayInputStream(bytes)
			val img = ImageIO.read(bais) ?: return false
			val width = img.width
			val height = img.height
			var luminance = 0.0
			val pixelCount = width * height
			val rgbPixels = img.getRGB(0, 0, width, height, null, 0, width)
			for (rgbPixel in rgbPixels) {
				val r = ((rgbPixel shr 16) and 0xFF) / 255.0
				val g = ((rgbPixel shr 8) and 0xFF) / 255.0
				val b = (rgbPixel and 0xFF) / 255.0
				luminance += relativeLuminance(r, g, b)
			}
			(luminance / pixelCount) < LUMINANCE_THRESHOLD
		} catch (exception: Exception) {
			false
		}
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

	suspend fun loadPreview(item: Item, scope: CoroutineScope) {
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
				State.selectedPdf = getPdfById(item.id, scope)
			}
			State.isVideo(lowercaseMimeType) -> {
				State.selectedImage = getVideoById(item.id, item.size)
			}
		}
	}
}