package im.bpu.gaisgc.manager

import com.google.api.client.util.DateTime
import com.google.api.services.drive.model.File as DriveFile
import im.bpu.gaisgc.State
import im.bpu.gaisgc.model.Constants
import im.bpu.gaisgc.model.DuplicateMatch
import im.bpu.gaisgc.model.Item
import im.bpu.gaisgc.parser.ChatParser
import im.bpu.gaisgc.service.DriveService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DriveManager {
	suspend fun fetch() = coroutineScope {
		withContext(Dispatchers.Main) {
			State.clearSelection()
			State.items.clear()
			State.unlinkedItems.clear()
			State.duplicateItems.clear()
		}
		val service = DriveService.getService()
		val chatFiles = QueryManager.getFilesByMime(service, Constants.MIME_PROMPT)
		val chatItems =
			chatFiles.map {
				Item(
					createdTime = it.createdTime?.value ?: 0L,
					id = it.id,
					modifiedTime = it.modifiedTime?.value ?: 0L,
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
						if (cachedItem != null) cachedItem.subItems
						else {
							val subItemIds = ChatParser.getChatSubItems(service, file.id)
							val fetchedSubItems =
								subItemIds.map { id -> QueryManager.getFileDetails(service, id) }
							if (State.cache)
								CacheManager.saveToCache(
									file.createdTime?.value ?: 0L,
									file.id,
									file.modifiedTime?.value ?: 0L,
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
								modifiedTime = file.modifiedTime?.value ?: 0L,
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
		val gaisFolderId = QueryManager.getFolderId(service, State.gaisPath)
		if (gaisFolderId != null) {
			val gaisFolderFiles = QueryManager.getChildFilesByParent(service, gaisFolderId)
			val gaisFolderItems =
				gaisFolderFiles.map {
					Item(
						createdTime = it.createdTime?.value ?: 0L,
						fileExtension = it.fileExtension,
						id = it.id,
						mimeType = it.mimeType ?: "",
						modifiedTime = it.modifiedTime?.value ?: 0L,
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
		val duplicatesFolderId = QueryManager.getFolderId(service, State.duplicatesPath)
		if (duplicatesFolderId != null) {
			val duplicatesFolderFiles =
				QueryManager.getDescendantFilesByParent(service, duplicatesFolderId)
			val duplicatesFolderItems =
				duplicatesFolderFiles.map {
					Item(
						createdTime = it.createdTime?.value ?: 0L,
						fileExtension = it.fileExtension,
						id = it.id,
						mimeType = it.mimeType ?: "",
						modifiedTime = it.modifiedTime?.value ?: 0L,
						name = it.name,
						sha256Checksum = it.sha256Checksum,
						size = it.getSize() ?: 0L,
					)
				}
			val duplicateItems =
				DuplicateManager.findDuplicates(State.items.toList(), duplicatesFolderItems)
			withContext(Dispatchers.Main) { State.duplicateItems.addAll(duplicateItems) }
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
								if (State.lastSelectedId == id) State.lastSelectedId = null
							}
						} catch (exception: Exception) {
							exception.printStackTrace()
						}
					}
				}
			}
		}

	suspend fun update(id: String, name: String, createdTime: Long, modifiedTime: Long) =
		withContext(Dispatchers.IO) {
			try {
				val service = DriveService.getService()
				val currentFile =
					service.files().get(id).setFields("createdTime, modifiedTime, name").execute()
				val currentCreatedTime = currentFile.createdTime?.value ?: 0L
				val currentModifiedTime = currentFile.modifiedTime?.value ?: 0L
				val currentName = currentFile.name ?: ""
				var newId = id
				if (
					State.devMode &&
						State.createdTimeModification &&
						createdTime != currentCreatedTime
				) {
					val metadataContentCopy =
						DriveFile().apply {
							this.name = name
							this.createdTime = DateTime(createdTime)
							this.modifiedTime = DateTime(modifiedTime)
						}
					val newFile =
						service.files().copy(id, metadataContentCopy).setFields("id").execute()
					val chatItems =
						State.items.filter { it.subItems.any { subItem -> subItem.id == id } }
					val matches =
						chatItems.map { chatItem ->
							val subItem = chatItem.subItems.first { it.id == id }
							val newSubItem = subItem.copy(id = newFile.id)
							DuplicateMatch(chatItem, subItem, newSubItem)
						}
					if (matches.isNotEmpty()) RelinkManager.relink(matches)
					val metadataContentTrash = DriveFile().apply { trashed = true }
					service.files().update(id, metadataContentTrash).execute()
					newId = newFile.id
				} else if (modifiedTime != currentModifiedTime || name != currentName) {
					val file = DriveFile()
					file.name = name
					file.modifiedTime = DateTime(modifiedTime)
					service.files().update(id, file).execute()
				}
				withContext(Dispatchers.Main) {
					for (index in State.items.indices) {
						val item = State.items[index]
						if (item.id == id) {
							val updatedItem =
								item.copy(
									id = newId,
									name = name,
									createdTime = createdTime,
									modifiedTime = modifiedTime,
								)
							State.items[index] = updatedItem
							File(State.cacheDirectoryPath, id).delete()
						} else if (item.subItems.any { it.id == id }) {
							val updatedSubItems =
								item.subItems.map { subItem ->
									if (subItem.id == id)
										subItem.copy(
											id = newId,
											name = name,
											createdTime = createdTime,
											modifiedTime = modifiedTime,
										)
									else subItem
								}
							val updatedItem = item.copy(subItems = updatedSubItems)
							State.items[index] = updatedItem
							File(State.cacheDirectoryPath, item.id).delete()
						}
					}
				}
			} catch (exception: Exception) {
				exception.printStackTrace()
			}
		}

	suspend fun relink(id: String, newId: String) =
		withContext(Dispatchers.IO) {
			val chatItems = State.items.filter { it.subItems.any { subItem -> subItem.id == id } }
			if (chatItems.isNotEmpty()) {
				val matches =
					chatItems.map { chatItem ->
						val subItem = chatItem.subItems.first { it.id == id }
						val newSubItem = subItem.copy(id = newId)
						DuplicateMatch(chatItem, subItem, newSubItem)
					}
				RelinkManager.relink(matches)
				withContext(Dispatchers.Main) {
					for (index in State.items.indices) {
						val chatItem = State.items[index]
						if (chatItem.subItems.any { it.id == id }) {
							val updatedSubItems =
								chatItem.subItems.map {
									if (it.id == id)
										it.copy(id = newId, isNotFound = false, name = newId)
									else it
								}
							State.items[index] = chatItem.copy(subItems = updatedSubItems)
						}
					}
				}
			}
		}
}