package im.bpu.gaisgc.manager

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import im.bpu.gaisgc.Item
import im.bpu.gaisgc.State
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DriveManager {
	private const val APPLICATION_NAME = "GAISGC"
	private val JSON_FACTORY = GsonFactory.getDefaultInstance()
	private const val TOKENS_DIRECTORY_PATH = "tokens"
	private val SCOPES = listOf(DriveScopes.DRIVE_METADATA_READONLY)
	private const val CREDENTIALS_FILE_PATH = "credentials.json"
	private const val GAIS = "application/vnd.google-makersuite.prompt"

	private fun getCredentials(HTTP_TRANSPORT: HttpTransport): Credential {
		val file = File(CREDENTIALS_FILE_PATH)
		if (!file.exists()) throw Exception("Resource not found: $CREDENTIALS_FILE_PATH")
		val clientSecrets =
			GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(file.inputStream()))
		val flow =
			GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
				.setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
				.setAccessType("offline")
				.build()
		val receiver = LocalServerReceiver.Builder().setPort(8888).build()
		val credential = AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
		return credential
	}

	suspend fun fetch() =
		withContext(Dispatchers.IO) {
			val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
			val service =
				Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
					.setApplicationName(APPLICATION_NAME)
					.build()
			val result =
				service
					.files()
					.list()
					.setQ("mimeType = '$GAIS' and trashed = false")
					.setFields("nextPageToken, files(id, name)")
					.execute()
			val files = result.getFiles()
			if (files == null || files.isEmpty()) {
				println("No files found.")
				return@withContext
			}
			withContext(Dispatchers.Main) {
				State.items.clear()
				State.items.addAll(files.map { Item(it.id, it.name) })
			}
		}
}