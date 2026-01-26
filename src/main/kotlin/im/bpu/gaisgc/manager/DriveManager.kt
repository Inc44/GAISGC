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
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.Key
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import im.bpu.gaisgc.Item
import im.bpu.gaisgc.State
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
						Drive.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
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
						.setFields("nextPageToken, files(id, name, createdTime, mimeType)")
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
			State.items.clear()
			State.unlinkedItems.clear()
			State.selectedImage = null
			State.selectedIds.clear()
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
					val baos = ByteArrayOutputStream()
					service.files().get(file.id).executeMediaAndDownloadTo(baos)
					val content = baos.toString("UTF-8")
					val subItemIds = extractSubItemIds(content)
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
					)
				)
			}
		}
	}

	suspend fun getImageById(id: String): ImageBitmap? =
		withContext(Dispatchers.IO) {
			try {
				val service = getService()
				val baos = ByteArrayOutputStream()
				service.files().get(id).executeMediaAndDownloadTo(baos)
				val bytes = baos.toByteArray()
				Image.makeFromEncoded(bytes).toComposeImageBitmap()
			} catch (exception: Exception) {
				null
			}
		}

	suspend fun trash(ids: List<String>) =
		withContext(Dispatchers.IO) {
			val service = getService()
			ids.forEach { id ->
				try {
					val file = DriveFile()
					file.trashed = true
					service.files().update(id, file).execute()
				} catch (exception: Exception) {
					exception.printStackTrace()
				}
			}
			withContext(Dispatchers.Main) {
				State.unlinkedItems.removeIf { it.id in ids }
				State.selectedIds.removeAll(ids)
				if (State.lastSelectedId in ids) {
					State.lastSelectedId = null
				}
			}
		}
}