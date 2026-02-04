package im.bpu.gaisgc.manager

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import im.bpu.gaisgc.model.Constants
import im.bpu.gaisgc.model.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QueryManager {

	suspend fun getFilesByMime(service: Drive, mime: String): List<DriveFile> {
		val files = mutableListOf<DriveFile>()
		var pageToken: String? = null
		do {
			val result =
				withContext(Dispatchers.IO) {
					service
						.files()
						.list()
						.setQ("mimeType = '$mime' and trashed = false")
						.setFields(
							"nextPageToken, files(createdTime, id, modifiedTime, name, sha256Checksum)"
						)
						.setPageToken(pageToken)
						.execute()
				}
			result.files?.let { files.addAll(it) }
			pageToken = result.nextPageToken
		} while (pageToken != null)
		return files
	}

	fun getFileDetails(service: Drive, fileId: String): Item {
		return try {
			val file =
				service
					.files()
					.get(fileId)
					.setFields(
						"createdTime, fileExtension, id, mimeType, modifiedTime, name, sha256Checksum, size"
					)
					.execute()
			/*
			val metadata = service.files().get(fileId).setFields("*").execute()
			for (field in metadata.keys) {
				println("$field")
			}
			println("\n\n")
			*/
			Item(
				createdTime = file.createdTime?.value ?: 0L,
				fileExtension = file.fileExtension,
				id = file.id,
				mimeType = file.mimeType ?: "",
				modifiedTime = file.modifiedTime?.value ?: 0L,
				name = file.name,
				sha256Checksum = file.sha256Checksum,
				size = file.getSize() ?: 0L,
			)
		} catch (exception: GoogleJsonResponseException) {
			if (exception.statusCode == 404) Item(id = fileId, name = fileId, isNotFound = true)
			else throw exception
		}
	}

	suspend fun getFolderId(service: Drive, path: String): String? =
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

	suspend fun getChildFilesByParent(service: Drive, parentId: String): List<DriveFile> {
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
							"nextPageToken, files(createdTime, fileExtension, id, mimeType, modifiedTime, name, sha256Checksum, size)"
						)
						.setPageToken(pageToken)
						.execute()
				}
			result.files?.let { files.addAll(it) }
			pageToken = result.nextPageToken
		} while (pageToken != null)
		return files
	}

	suspend fun getDescendantFilesByParent(service: Drive, parentId: String): List<DriveFile> {
		val files = mutableListOf<DriveFile>()
		val queue = ArrayDeque<String>()
		queue.add(parentId)
		while (queue.isNotEmpty()) {
			val parentId = queue.removeFirst()
			val children = getChildFilesByParent(service, parentId)
			children.forEach { child ->
				if (child.mimeType == Constants.MIME_FOLDER) {
					queue.add(child.id)
				} else {
					files.add(child)
				}
			}
		}
		return files
	}
}