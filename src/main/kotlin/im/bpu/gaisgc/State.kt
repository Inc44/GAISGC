package im.bpu.gaisgc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import java.util.Properties

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
	var selectedImage by mutableStateOf<ImageBitmap?>(null)
	var filterName by mutableStateOf("")
	var filterMimeType by mutableStateOf(FilterMimeType.ALL)
	var sort by mutableStateOf(Sort.DATE_DESC)
	val selectedIds = mutableStateListOf<String>()
	var isShiftPressed by mutableStateOf(false)
	var lastSelectedId by mutableStateOf<String?>(null)
	val shiftRangeIds = mutableListOf<String>()

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
			.filter {
				val mimeType = it.mimeType.lowercase()
				when (filterMimeType) {
					FilterMimeType.ALL -> true
					FilterMimeType.DOCUMENT ->
						mimeType.startsWith("text/") ||
							mimeType.contains("document") ||
							mimeType.contains("sheet") ||
							mimeType.contains("presentation") ||
							mimeType.contains("word") ||
							mimeType.contains("excel") ||
							mimeType.contains("powerpoint")
					FilterMimeType.PHOTO -> mimeType.startsWith("image/")
					FilterMimeType.PDF -> mimeType == "application/pdf"
					FilterMimeType.VIDEO -> mimeType.startsWith("video/")
					FilterMimeType.AUDIO -> mimeType.startsWith("audio/")
					FilterMimeType.OTHER ->
						mimeType.startsWith("text/") ||
							mimeType.contains("document") ||
							mimeType.contains("sheet") ||
							mimeType.contains("presentation") ||
							mimeType.contains("word") ||
							mimeType.contains("excel") ||
							mimeType.contains("powerpoint") ||
							mimeType.startsWith("image/") ||
							mimeType == "application/pdf" ||
							mimeType.startsWith("video/") ||
							mimeType.startsWith("audio/")
				}
			}
			.sortedWith { item, nextItem ->
				when (sort) {
					Sort.DATE_DESC -> nextItem.createdTime.compareTo(item.createdTime)
					Sort.DATE_ASC -> item.createdTime.compareTo(nextItem.createdTime)
					Sort.NAME_ASC -> item.name.compareTo(nextItem.name, ignoreCase = true)
					Sort.NAME_DESC -> nextItem.name.compareTo(item.name, ignoreCase = true)
				}
			}
	}
}