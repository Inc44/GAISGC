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
import im.bpu.gaisgc.Item
import im.bpu.gaisgc.State
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
	private val JSON_FACTORY = GsonFactory.getDefaultInstance()
	private const val TOKENS_DIRECTORY_PATH = "tokens"
	private val SCOPES = listOf(DriveScopes.DRIVE_READONLY)
	private const val CREDENTIALS_FILE_PATH = "credentials.json"
	private const val GAIS = "application/vnd.google-makersuite.prompt"

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
		val receiver = LocalServerReceiver.Builder().setPort(8888).build()
		val credential = AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
		return credential
	}

	private fun fetchName(service: Drive, id: String): String? {
		return try {
			service.files().get(id).setFields("name").execute().name
		} catch (exception: GoogleJsonResponseException) {
			if (exception.statusCode == 404) null else throw exception
		}
	}

	private fun extractSubItemIds(json: String): List<String> {
		val chat = JSON_FACTORY.fromString(json, Chat::class.java)
		return chat.chunkedPrompt?.chunks?.mapNotNull { it.driveDocument?.id } ?: emptyList()
	}

	suspend fun fetch() = coroutineScope {
		withContext(Dispatchers.Main) { State.items.clear() }
		val httpTransport =
			withContext(Dispatchers.IO) { GoogleNetHttpTransport.newTrustedTransport() }
		val service =
			withContext(Dispatchers.IO) {
				Drive.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
					.setApplicationName(APPLICATION_NAME)
					.build()
			}
		val result =
			withContext(Dispatchers.IO) {
				service
					.files()
					.list()
					.setQ("mimeType = '$GAIS' and trashed = false")
					.setFields("files(id, name)")
					.execute()
			}
		val files = result.files ?: emptyList()
		withContext(Dispatchers.Main) {
			files.forEach { file -> State.items.add(Item(file.id, file.name)) }
		}
		files.indices.forEach { i ->
			launch(Dispatchers.IO) {
				val file = files[i]
				val baos = ByteArrayOutputStream()
				service.files().get(file.id).executeMediaAndDownloadTo(baos)
				val content = baos.toString("UTF-8")
				val subItemIds = extractSubItemIds(content)
				val subItems =
					subItemIds
						.map { id ->
							async {
								val name = fetchName(service, id)
								if (name == null) Item(id, id, isNotFound = true)
								else Item(id, name)
							}
						}
						.awaitAll()
				val item = Item(file.id, file.name, subItems)
				withContext(Dispatchers.Main) { State.items[i] = item }
			}
		}
	}
}