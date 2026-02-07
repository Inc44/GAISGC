package im.bpu.gaisgc.manager

import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
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
		resetState()
		val service = DriveService.getService()
		val chatFiles = QueryManager.getFilesByMime(service, Constants.MIME_PROMPT)
		val chatItems = chatFiles.map { fileToItem(it) }
		withContext(Dispatchers.Main) { State.items.addAll(chatItems) }
		val deferredSubItems = getDeferredSubItems(service, chatFiles)
		loadOrphanItems(service, deferredSubItems)
		loadDuplicateItems(service, deferredSubItems)
	}

	private suspend fun resetState() =
		withContext(Dispatchers.Main) {
			State.clearSelection()
			State.items.clear()
			State.unlinkedItems.clear()
			State.duplicateItems.clear()
		}

	private suspend fun getDeferredSubItems(
		service: Drive,
		chatFiles: List<DriveFile>,
	): List<Item> = coroutineScope {
		chatFiles
			.mapIndexed { i, file ->
				async(Dispatchers.IO) {
					val subItems = getSubItems(service, file)
					val item = fileToItem(file).copy(subItems = subItems)
					withContext(Dispatchers.Main) { State.items[i] = item }
					item
				}
			}
			.awaitAll()
	}

	private fun getSubItems(service: Drive, file: DriveFile): List<Item> {
		val cachedItem =
			if (State.cache) CacheManager.loadFromCache(file.id, file.sha256Checksum) else null
		if (cachedItem != null) return cachedItem.subItems
		val subItemIds = ChatParser.getChatSubItems(service, file.id)
		val fetchedSubItems = subItemIds.map { id -> QueryManager.getFileDetails(service, id) }
		if (State.cache) {
			CacheManager.saveToCache(
				file.createdTime?.value ?: 0L,
				file.id,
				file.modifiedTime?.value ?: 0L,
				file.name,
				file.sha256Checksum,
				fetchedSubItems,
			)
		}
		return fetchedSubItems
	}

	private suspend fun loadOrphanItems(service: Drive, chatItems: List<Item>) {
		val referencedItems = chatItems.flatMap { it.subItems }
		val referencedItemIds = referencedItems.map { it.id }.toSet()
		val chatIds = chatItems.map { it.id }.toSet()
		val gaisFolderId = QueryManager.getFolderId(service, State.gaisPath) ?: return
		val gaisFolderFiles = QueryManager.getChildFilesByParent(service, gaisFolderId)
		val gaisFolderItems =
			gaisFolderFiles
				.filter { it.id !in chatIds && it.id !in referencedItemIds }
				.map { fileToDetailedItem(it).copy(isNotFound = true) }
		withContext(Dispatchers.Main) { State.unlinkedItems.addAll(gaisFolderItems) }
	}

	private suspend fun loadDuplicateItems(service: Drive, chatItems: List<Item>) {
		val duplicatesFolderId = QueryManager.getFolderId(service, State.duplicatesPath) ?: return
		val duplicatesFolderFiles =
			QueryManager.getDescendantFilesByParent(service, duplicatesFolderId)
		val duplicatesFolderItems = duplicatesFolderFiles.map { fileToDetailedItem(it) }
		val duplicateItems = DuplicateManager.findDuplicates(chatItems, duplicatesFolderItems)
		withContext(Dispatchers.Main) { State.duplicateItems.addAll(duplicateItems) }
	}

	private fun fileToItem(file: DriveFile) =
		Item(
			createdTime = file.createdTime?.value ?: 0L,
			id = file.id,
			modifiedTime = file.modifiedTime?.value ?: 0L,
			name = file.name,
			sha256Checksum = file.sha256Checksum,
		)

	private fun fileToDetailedItem(file: DriveFile) =
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

	suspend fun trash(ids: List<String>) =
		withContext(Dispatchers.IO) {
			val service = DriveService.getService()
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
				updateState(id, newId, name, createdTime, modifiedTime)
			} catch (exception: Exception) {
				exception.printStackTrace()
			}
		}

	private suspend fun updateState(
		id: String,
		newId: String,
		name: String,
		createdTime: Long,
		modifiedTime: Long,
	) =
		withContext(Dispatchers.Main) {
			val iter = State.items.listIterator()
			while (iter.hasNext()) {
				val item = iter.next()
				if (item.id == id) {
					iter.set(
						item.copy(
							id = newId,
							name = name,
							createdTime = createdTime,
							modifiedTime = modifiedTime,
						)
					)
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
					iter.set(item.copy(subItems = updatedSubItems))
					File(State.cacheDirectoryPath, item.id).delete()
				}
			}
		}

	suspend fun relink(id: String, newId: String) =
		withContext(Dispatchers.IO) {
			val chatItems = State.items.filter { it.subItems.any { subItem -> subItem.id == id } }
			if (chatItems.isEmpty()) return@withContext
			val matches =
				chatItems.map { chatItem ->
					val subItem = chatItem.subItems.first { it.id == id }
					val newSubItem = subItem.copy(id = newId)
					DuplicateMatch(chatItem, subItem, newSubItem)
				}
			RelinkManager.relink(matches)
			withContext(Dispatchers.Main) {
				State.items.forEachIndexed { index, chatItem ->
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