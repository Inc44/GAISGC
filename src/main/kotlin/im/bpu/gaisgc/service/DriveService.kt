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
import im.bpu.gaisgc.State
import im.bpu.gaisgc.model.Constants
import im.bpu.gaisgc.model.Screen
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object DriveService {
	private val JSON_FACTORY = GsonFactory.getDefaultInstance()
	private val SCOPES = listOf(DriveScopes.DRIVE)
	private var driveService: Drive? = null
	private val mutex = Mutex()

	fun getJSONFactory(): GsonFactory {
		return JSON_FACTORY
	}

	private fun getCredentials(httpTransport: HttpTransport): Credential {
		val file = File(Constants.CREDENTIALS_FILE_PATH)
		if (!file.exists())
			throw Exception("Resource not found: ${Constants.CREDENTIALS_FILE_PATH}")
		val clientSecrets =
			GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(file.inputStream()))
		val flow =
			GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
				.setDataStoreFactory(FileDataStoreFactory(File(Constants.TOKENS_DIRECTORY_PATH)))
				.setAccessType("offline")
				.build()
		val receiver = LocalServerReceiver.Builder().setPort(Constants.PORT).build()
		val credential = AuthorizationCodeInstalledApp(flow, receiver).authorize(Constants.USER_ID)
		State.isConnected = true
		return credential
	}

	suspend fun getService(): Drive {
		mutex.withLock {
			return withContext(Dispatchers.IO) {
				driveService
					?: run {
						val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
						val credential = getCredentials(httpTransport)
						val service =
							Drive.Builder(httpTransport, JSON_FACTORY) { request ->
									credential.initialize(request)
									request.connectTimeout = Constants.TIMEOUT_MS
									request.readTimeout = Constants.TIMEOUT_MS
								}
								.setApplicationName(Constants.APPLICATION_NAME)
								.build()
						driveService = service
						service
					}
			}
		}
	}

	suspend fun logout() {
		mutex.withLock {
			withContext(Dispatchers.IO) {
				try {
					val tokensDirectoryPath = File(Constants.TOKENS_DIRECTORY_PATH)
					if (tokensDirectoryPath.exists()) {
						tokensDirectoryPath.deleteRecursively()
					}
					driveService = null
					State.isConnected = false
					withContext(Dispatchers.Main) {
						State.screen = Screen.MAIN
						State.clearSelection()
						State.items.clear()
						State.unlinkedItems.clear()
						State.duplicateItems.clear()
					}
				} catch (exception: Exception) {
					exception.printStackTrace()
				}
			}
		}
	}

	suspend fun downloadFileBytes(service: Drive, id: String): ByteArray {
		val baos = ByteArrayOutputStream()
		withContext(Dispatchers.IO) { service.files().get(id).executeMediaAndDownloadTo(baos) }
		val bytes = baos.toByteArray()
		return bytes
	}

	suspend fun downloadLinkBytes(service: Drive, link: String): ByteArray {
		return withContext(Dispatchers.IO) {
			val resp = service.requestFactory.buildGetRequest(GenericUrl(link)).execute()
			val bytes = resp.content.use { it.readBytes() }
			bytes
		}
	}
}