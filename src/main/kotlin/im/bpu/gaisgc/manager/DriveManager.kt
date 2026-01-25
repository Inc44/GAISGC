package im.bpu.gaisgc.manager

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

class Chat {
	@Key("chunkedPrompt") var chunkedPrompt: ChunkedPrompt? = null
}

class ChunkedPrompt {
	@Key("chunks") var chunks: List<Chunk>? = null
}

class Chunk {
	@Key("driveDocument") var driveDocument: DriveDocument? = null
}

class DriveDocument {
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
	private val SCOPES = listOf(DriveScopes.DRIVE_READONLY)

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
		return chat.chunkedPrompt?.chunks?.mapNotNull { it.driveDocument?.id } ?: emptyList()
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
						.setFields("nextPageToken, files(id, name)")
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
		}
		val httpTransport =
			withContext(Dispatchers.IO) { GoogleNetHttpTransport.newTrustedTransport() }
		val service =
			withContext(Dispatchers.IO) {
				Drive.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
					.setApplicationName(APPLICATION_NAME)
					.build()
			}
		val chatFiles = getFilesByMime(service, MIME_PROMPT)
		withContext(Dispatchers.Main) {
			chatFiles.forEach { State.items.add(Item(it.id, it.name)) }
		}
		val deferreds =
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
					val item = Item(file.id, file.name, subItems)
					withContext(Dispatchers.Main) { State.items[i] = item }
					subItemIds
				}
			}
		val chatIds = chatFiles.map { it.id }.toSet()
		val driveDocumentIds = deferreds.awaitAll().flatten().toSet()
		val gaisId = getFolderId(service, State.gaisPath) ?: return@coroutineScope
		val gaisFiles = getFilesByParent(service, gaisId)
		val orphanFiles = gaisFiles.filter { it.id !in chatIds && it.id !in driveDocumentIds }
		withContext(Dispatchers.Main) {
			orphanFiles.forEach { State.unlinkedItems.add(Item(it.id, it.name, isNotFound = true)) }
		}
	}
}