package im.bpu.gaisgc.manager

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import im.bpu.gaisgc.State
import im.bpu.gaisgc.model.Item
import java.io.File

object CacheManager {
	val gson: Gson = GsonBuilder().setPrettyPrinting().create()

	fun loadFromCache(id: String, sha256Checksum: String?): Item? {
		if (sha256Checksum == null) return null
		val cacheFile = File(State.cacheDirectoryPath, id)
		if (!cacheFile.exists()) return null
		return try {
			val json = cacheFile.readText()
			val item = gson.fromJson(json, Item::class.java)
			if (item.sha256Checksum == sha256Checksum) item else null
		} catch (exception: Exception) {
			null
		}
	}

	fun saveToCache(
		createdTime: Long,
		id: String,
		name: String,
		sha256Checksum: String?,
		subItems: List<Item>,
	) {
		if (sha256Checksum == null) return
		try {
			val item =
				Item(
					createdTime = createdTime,
					id = id,
					name = name,
					sha256Checksum = sha256Checksum,
					subItems = subItems,
				)
			val json = gson.toJson(item)
			File(State.cacheDirectoryPath, id).writeText(json)
		} catch (exception: Exception) {
			exception.printStackTrace()
		}
	}

	fun clearCache() {
		if (State.cacheDirectoryPath.exists()) {
			State.cacheDirectoryPath.listFiles()?.forEach { it.delete() }
		}
	}
}