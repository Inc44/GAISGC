package im.bpu.gaisgc.parser

import com.google.api.services.drive.Drive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream
import java.io.InputStreamReader

object ChatParser {
	fun getChatSubItems(service: Drive, id: String): List<String> {
		return service.files().get(id).executeMediaAsInputStream().use { extractSubItemIds(it) }
	}

	private fun extractSubItemIds(ins: InputStream): List<String> {
		val ids = mutableListOf<String>()
		val reader = JsonReader(InputStreamReader(ins, "UTF-8"))
		reader.use {
			if (it.peek() == JsonToken.BEGIN_OBJECT) {
				parseObject(it, ids)
			}
		}
		return ids
	}

	private fun parseObject(reader: JsonReader, ids: MutableList<String>) {
		reader.beginObject()
		while (reader.hasNext()) {
			val name = reader.nextName()
			when {
				name == "driveDocument" || name == "driveImage" || name == "driveVideo" -> {
					parseIdContainer(reader, ids)
				}
				reader.peek() == JsonToken.BEGIN_OBJECT -> parseObject(reader, ids)
				reader.peek() == JsonToken.BEGIN_ARRAY -> parseArray(reader, ids)
				else -> reader.skipValue()
			}
		}
		reader.endObject()
	}

	private fun parseArray(reader: JsonReader, ids: MutableList<String>) {
		reader.beginArray()
		while (reader.hasNext()) {
			when (reader.peek()) {
				JsonToken.BEGIN_OBJECT -> parseObject(reader, ids)
				JsonToken.BEGIN_ARRAY -> parseArray(reader, ids)
				else -> reader.skipValue()
			}
		}
		reader.endArray()
	}

	private fun parseIdContainer(reader: JsonReader, ids: MutableList<String>) {
		reader.beginObject()
		while (reader.hasNext()) {
			val name = reader.nextName()
			if (name == "id" && reader.peek() == JsonToken.STRING) {
				ids.add(reader.nextString())
			} else {
				reader.skipValue()
			}
		}
		reader.endObject()
	}
}