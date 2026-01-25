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
	private val properties = Properties()
	private val configFile = File("config.properties")
	var screen by mutableStateOf(Screen.MAIN)
	var gaisPath by mutableStateOf("Google AI Studio")
	val items = mutableStateListOf<Item>()
	val unlinkedItems = mutableStateListOf<Item>()

	init {
		if (configFile.exists()) {
			configFile.inputStream().use { properties.load(it) }
			gaisPath = properties.getProperty("gaisPath", "Google AI Studio")
		}
	}

	fun savePath(path: String) {
		gaisPath = path
		properties.setProperty("gaisPath", path)
		configFile.outputStream().use { properties.store(it, null) }
	}
}