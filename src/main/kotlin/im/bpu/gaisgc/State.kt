package im.bpu.gaisgc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import java.util.Properties
import kotlin.math.max
import kotlin.math.min

data class Item(
	val id: String,
	val name: String,
	val subItems: List<Item> = emptyList(),
	val isNotFound: Boolean = false,
	val createdTime: Long = 0L,
	val mimeType: String = "",
)

enum class Screen {
	MAIN,
	UNLINKED,
}

enum class FilterMimeType {
	ALL,
	DOCUMENT,
	PHOTO,
	PDF,
	VIDEO,
	AUDIO,
	OTHER,
}

enum class Sort {
	DATE_DESC,
	DATE_ASC,
	NAME_ASC,
	NAME_DESC,
}

object State {
	private const val CONFIG_FILE_PATH = "config.properties"
	private const val GAIS_PATH_PROPERTY = "gaisPath"
	private const val DEFAULT_GAIS_PATH = "Google AI Studio"
	private val properties = Properties()
	private val configFile = File(CONFIG_FILE_PATH)
	var screen by mutableStateOf(Screen.MAIN)
	var gaisPath by mutableStateOf(DEFAULT_GAIS_PATH)
	val items = mutableStateListOf<Item>()
	val unlinkedItems = mutableStateListOf<Item>()
	var selectedDocument by mutableStateOf<String?>(null)
	var selectedImage by mutableStateOf<ImageBitmap?>(null)
	var selectedPdf by mutableStateOf<List<ImageBitmap>?>(null)
	var filterName by mutableStateOf("")
	var filterMimeType by mutableStateOf(FilterMimeType.ALL)
	var sort by mutableStateOf(Sort.DATE_DESC)
	val selectedIds = mutableStateListOf<String>()
	var isShiftPressed by mutableStateOf(false)
	var lastSelectedId by mutableStateOf<String?>(null)
	private val shiftRangeIds = mutableListOf<String>()

	init {
		if (configFile.exists()) {
			configFile.inputStream().use { properties.load(it) }
			gaisPath = properties.getProperty(GAIS_PATH_PROPERTY, DEFAULT_GAIS_PATH)
		}
	}

	fun savePath(path: String) {
		gaisPath = path
		properties.setProperty(GAIS_PATH_PROPERTY, path)
		configFile.outputStream().use { properties.store(it, null) }
	}

	fun getFilteredUnlinkedItems(): List<Item> {
		return unlinkedItems
			.filter { it.name.contains(filterName, ignoreCase = true) }
			.filter { matchesMimeType(it.mimeType) }
			.sortedWith(getSortComparator())
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

	private fun isVideo(mimeType: String) = mimeType.startsWith("video/")

	private fun isAudio(mimeType: String) = mimeType.startsWith("audio/")

	private fun isOther(mimeType: String) =
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
		} else {
			selectedIds.add(id)
		}
		lastSelectedId = id
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
		shiftRangeIds.clear()
		selectedDocument = null
		selectedImage = null
		selectedPdf = null
	}
}