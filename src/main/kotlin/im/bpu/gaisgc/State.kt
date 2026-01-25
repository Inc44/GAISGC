package im.bpu.gaisgc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Properties

data class Item(
	val id: String,
	val name: String,
	val subItems: List<Item> = emptyList(),
	val isNotFound: Boolean = false,
)

enum class Screen {
	MAIN,
	UNLINKED,
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
}