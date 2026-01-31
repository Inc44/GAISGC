package im.bpu.gaisgc.service

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import im.bpu.gaisgc.Screen
import im.bpu.gaisgc.State
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DriveService {
	private const val APPLICATION_NAME = "GAISGC"
	private const val TOKENS_DIRECTORY_PATH = "tokens"
	private const val CREDENTIALS_FILE_PATH = "credentials.json"
	private const val USER_ID = "user"
	private const val PORT = 8888
	private const val TIMEOUT_MS = 0
	private val JSON_FACTORY = GsonFactory.getDefaultInstance()
	private val SCOPES = listOf(DriveScopes.DRIVE)
	private var driveService: Drive? = null

	fun getJSONFactory(): GsonFactory {
		return JSON_FACTORY
	}

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
		State.isConnected = true
		return credential
	}

	suspend fun getService(): Drive {
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
			State.isConnected = false
			State.screen = im.bpu.gaisgc.Screen.MAIN
			State.clearSelection()
			State.items.clear()
			State.unlinkedItems.clear()
			State.duplicateItems.clear()
		} catch (exception: Exception) {
			exception.printStackTrace()
		}
	}

	suspend fun downloadFileBytes(service: Drive, id: String): ByteArray {
		val baos = ByteArrayOutputStream()
		service.files().get(id).executeMediaAndDownloadTo(baos)
		val bytes = baos.toByteArray()
		return bytes
	}

	suspend fun downloadLinkBytes(service: Drive, link: String): ByteArray {
		val resp = service.requestFactory.buildGetRequest(GenericUrl(link)).execute()
		val bytes = resp.content.use { it.readBytes() }
		return bytes
	}
}