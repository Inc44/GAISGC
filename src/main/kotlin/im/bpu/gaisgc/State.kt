package im.bpu.gaisgc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import im.bpu.gaisgc.model.Constants
import im.bpu.gaisgc.model.DuplicateMatch
import im.bpu.gaisgc.model.FilterMimeType
import im.bpu.gaisgc.model.Item
import im.bpu.gaisgc.model.PdfDocument
import im.bpu.gaisgc.model.RelinkMethod
import im.bpu.gaisgc.model.Screen
import im.bpu.gaisgc.model.Sort
import java.io.File
import java.util.Properties
import kotlin.math.max
import kotlin.math.min

object State {
	private val properties = Properties()
	private val configFile = File(Constants.CONFIG_FILE_PATH)
	private val shiftRangeIds = mutableListOf<String>()
	val cacheDirectoryPath = File(System.getProperty("user.home"), ".gaisgc/cache")
	var isConnected by mutableStateOf(false)
	var screen by mutableStateOf(Screen.MAIN)
	var gaisPath by mutableStateOf(Constants.DEFAULT_GAIS_PATH)
	var duplicatesPath by mutableStateOf(Constants.DEFAULT_DUPLICATES_PATH)
	var middleFrame by mutableStateOf(false)
	var cache by mutableStateOf(false)
	var relinkMethod by mutableStateOf(RelinkMethod.DIRECT)
	val items = mutableStateListOf<Item>()
	val unlinkedItems = mutableStateListOf<Item>()
	val duplicateItems = mutableStateListOf<DuplicateMatch>()
	val selectedDocument = mutableStateListOf<String>()
	var selectedImage by mutableStateOf<ImageBitmap?>(null)
	var selectedPdf by mutableStateOf<PdfDocument?>(null)
	var filterName by mutableStateOf("")
	var filterMimeType by mutableStateOf(FilterMimeType.ALL)
	var sort by mutableStateOf(Sort.DATE_DESC)
	val selectedIds = mutableStateListOf<String>()
	var isShiftPressed by mutableStateOf(false)
	var lastSelectedId by mutableStateOf<String?>(null)
	var previewId by mutableStateOf<String?>(null)
	var editId by mutableStateOf<String?>(null)

	init {
		if (configFile.exists()) {
			configFile.inputStream().use { properties.load(it) }
			gaisPath =
				properties.getProperty(Constants.GAIS_PATH_PROPERTY, Constants.DEFAULT_GAIS_PATH)
			duplicatesPath =
				properties.getProperty(
					Constants.DUPLICATES_PATH_PROPERTY,
					Constants.DEFAULT_DUPLICATES_PATH,
				)
			middleFrame =
				properties.getProperty(Constants.MIDDLE_FRAME_PROPERTY, "false").toBoolean()
			cache = properties.getProperty(Constants.CACHE_PROPERTY, "false").toBoolean()
			relinkMethod =
				try {
					RelinkMethod.valueOf(
						properties.getProperty(Constants.RELINK_METHOD_PROPERTY, "DIRECT")
					)
				} catch (exception: Exception) {
					RelinkMethod.PRETTY
				}
		}
		isConnected =
			File(Constants.TOKENS_DIRECTORY_PATH).exists() &&
				File(Constants.TOKENS_DIRECTORY_PATH).walk().any { it.name == "StoredCredential" }
		if (!cacheDirectoryPath.exists()) cacheDirectoryPath.mkdirs()
	}

	fun saveGaisPath(path: String) {
		gaisPath = path
		properties.setProperty(Constants.GAIS_PATH_PROPERTY, path)
		saveProperties()
	}

	fun saveDuplicatesPath(path: String) {
		duplicatesPath = path
		properties.setProperty(Constants.DUPLICATES_PATH_PROPERTY, path)
		saveProperties()
	}

	fun saveSettings(newMiddleFrame: Boolean, newCache: Boolean, newRelinkMethod: RelinkMethod) {
		middleFrame = newMiddleFrame
		cache = newCache
		relinkMethod = newRelinkMethod
		properties.setProperty(Constants.MIDDLE_FRAME_PROPERTY, middleFrame.toString())
		properties.setProperty(Constants.CACHE_PROPERTY, cache.toString())
		properties.setProperty(Constants.RELINK_METHOD_PROPERTY, relinkMethod.name)
		saveProperties()
	}

	private fun saveProperties() {
		configFile.outputStream().use { properties.store(it, null) }
	}

	fun getFilteredUnlinkedItems(): List<Item> {
		return unlinkedItems
			.filter { it.name.contains(filterName, ignoreCase = true) }
			.filter { matchesMimeType(it.mimeType) }
			.sortedWith(getSortComparator())
	}

	fun getFilteredDuplicateItems(): List<DuplicateMatch> {
		return duplicateItems
			.filter {
				it.chat.name.contains(filterName, ignoreCase = true) ||
					it.duplicate.name.contains(filterName, ignoreCase = true)
			}
			.filter { matchesMimeType(it.duplicate.mimeType) }
			.sortedWith { a, b -> getSortComparator().compare(a.chat, b.chat) }
	}

	private fun matchesMimeType(mimeType: String): Boolean {
		val lowercaseMimeType = mimeType.lowercase()
		return when (filterMimeType) {
			FilterMimeType.ALL -> true
			FilterMimeType.DOCUMENT -> isDocument(lowercaseMimeType)
			FilterMimeType.PHOTO -> isPhoto(lowercaseMimeType)
			FilterMimeType.PDF -> isPdf(lowercaseMimeType)
			FilterMimeType.VIDEO -> isVideo(lowercaseMimeType)
			FilterMimeType.AUDIO -> isAudio(lowercaseMimeType)
			FilterMimeType.OTHER -> isOther(lowercaseMimeType)
		}
	}

	fun isDocument(mimeType: String) =
		mimeType.startsWith("text/") ||
			mimeType.contains("document") ||
			mimeType.contains("sheet") ||
			mimeType.contains("presentation") ||
			mimeType.contains("word") ||
			mimeType.contains("excel") ||
			mimeType.contains("powerpoint")

	fun isPhoto(mimeType: String) = mimeType.startsWith("image/")

	fun isPdf(mimeType: String) = mimeType == "application/pdf"

	fun isVideo(mimeType: String) = mimeType.startsWith("video/")

	fun isAudio(mimeType: String) = mimeType.startsWith("audio/")

	fun isOther(mimeType: String) =
		!isDocument(mimeType) &&
			!isPhoto(mimeType) &&
			!isPdf(mimeType) &&
			!isVideo(mimeType) &&
			!isAudio(mimeType)

	private fun getSortComparator(): Comparator<Item> {
		return when (sort) {
			Sort.DATE_DESC -> compareByDescending { it.createdTime }
			Sort.DATE_ASC -> compareBy { it.createdTime }
			Sort.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
			Sort.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
		}
	}

	fun toggleSelection(id: String, ids: List<String>) {
		if (isShiftPressed && lastSelectedId != null) {
			selectRange(lastSelectedId!!, id, ids)
		} else {
			selectSingle(id)
		}
	}

	private fun selectSingle(id: String) {
		if (id in selectedIds) {
			selectedIds.remove(id)
			if (lastSelectedId == id) lastSelectedId = null
		} else {
			selectedIds.add(id)
			lastSelectedId = id
		}
		shiftRangeIds.clear()
	}

	private fun selectRange(startId: String, endId: String, ids: List<String>) {
		val startIndex = ids.indexOf(startId)
		val endIndex = ids.indexOf(endId)
		if (startIndex == -1 || endIndex == -1) return
		val rangeStart = min(startIndex, endIndex)
		val rangeEnd = max(startIndex, endIndex)
		val rangeIds = (rangeStart..rangeEnd).map { ids[it] }.toSet()
		val idsToRemove = shiftRangeIds.filter { it !in rangeIds }
		selectedIds.removeAll(idsToRemove)
		val idsToAdd = rangeIds.filter { it !in selectedIds }
		selectedIds.addAll(idsToAdd)
		shiftRangeIds.clear()
		shiftRangeIds.addAll(rangeIds)
	}

	fun selectAll(ids: List<String>) {
		val allSelected = ids.all { it in selectedIds }
		if (allSelected) {
			selectedIds.removeAll(ids)
		} else {
			val idsToAdd = ids.filter { it !in selectedIds }
			selectedIds.addAll(idsToAdd)
		}
	}

	fun clearSelection() {
		selectedIds.clear()
		lastSelectedId = null
		previewId = null
		editId = null
		shiftRangeIds.clear()
		selectedDocument.clear()
		selectedImage = null
		selectedPdf?.close()
		selectedPdf = null
	}
}