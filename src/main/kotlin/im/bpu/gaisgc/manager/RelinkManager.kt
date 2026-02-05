package im.bpu.gaisgc.manager

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.GenericJson
import com.google.api.services.drive.model.File as DriveFile
import com.google.gson.JsonParser
import im.bpu.gaisgc.State
import im.bpu.gaisgc.model.Constants
import im.bpu.gaisgc.model.DuplicateMatch
import im.bpu.gaisgc.model.RelinkMethod
import im.bpu.gaisgc.service.DriveService
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RelinkManager {

	suspend fun relink(matches: List<DuplicateMatch>) =
		withContext(Dispatchers.IO) {
			val service = DriveService.getService()
			val matchesByChat = matches.groupBy { it.chat.id }
			val relinkMethod = if (State.devMode) State.relinkMethod else RelinkMethod.DIRECT
			matchesByChat.forEach { (chatId, chatMatches) ->
				try {
					val baos = ByteArrayOutputStream()
					service.files().get(chatId).executeMediaAndDownloadTo(baos)
					var content = baos.toString("UTF-8")
					var modified = false
					when (relinkMethod) {
						RelinkMethod.DIRECT -> {
							chatMatches.forEach { match ->
								val pattern = "\"id\": \"${match.original.id}\""
								val replacement = "\"id\": \"${match.duplicate.id}\""
								if (content.contains(pattern)) {
									content = content.replace(pattern, replacement)
									modified = true
								}
							}
						}
						RelinkMethod.REGEX -> {
							chatMatches.forEach { match ->
								val pattern = Regex("\"id\"\\s*:\\s*\"${match.original.id}\"")
								val replacement = "\"id\": \"${match.duplicate.id}\""
								if (pattern.containsMatchIn(content)) {
									content = content.replace(pattern, replacement)
									modified = true
								}
							}
						}
						RelinkMethod.PRETTY,
						RelinkMethod.JS_BEAUTIFY -> {
							val chat =
								DriveService.getJSONFactory()
									.fromString(content, GenericJson::class.java)
							val chunkedPrompt = chat["chunkedPrompt"] as? MutableMap<String, Any?>
							val chunks = chunkedPrompt?.get("chunks") as? List<*>
							chunks?.forEach { chunk ->
								val chunkMap = chunk as? MutableMap<String, Any?>
								if (chunkMap != null) {
									listOf("driveDocument", "driveImage", "driveVideo").forEach {
										key ->
										val driveReference =
											chunkMap[key] as? MutableMap<String, Any?>
										val driveReferenceId = driveReference?.get("id") as? String
										if (driveReferenceId != null) {
											val match =
												chatMatches.find {
													it.original.id == driveReferenceId
												}
											if (match != null) {
												driveReference["id"] = match.duplicate.id
												modified = true
											}
										}
									}
								}
							}
							if (modified) {
								val contentString = DriveService.getJSONFactory().toString(chat)
								content =
									if (relinkMethod == RelinkMethod.PRETTY) {
										val json = JsonParser.parseString(contentString)
										CacheManager.gson.toJson(json)
									} else {
										setJsBeautifyPrinting(contentString)
									}
							}
						}
					}
					if (modified) {
						val mediaContent =
							ByteArrayContent.fromString(Constants.MIME_PROMPT, content)
						val file = service.files().get(chatId).setFields("modifiedTime").execute()
						val chatModifiedTime = file.modifiedTime
						val metadataContent = DriveFile()
						metadataContent.modifiedTime = chatModifiedTime
						service.files().update(chatId, metadataContent, mediaContent).execute()
						File(State.cacheDirectoryPath, chatId).delete()
					}
					withContext(Dispatchers.Main) {
						State.duplicateItems.removeAll(chatMatches)
						val matchIds = chatMatches.map { "${it.chat.id}|${it.original.id}" }
						State.selectedIds.removeAll(matchIds)
					}
				} catch (exception: Exception) {
					exception.printStackTrace()
				}
			}
		}

	private fun setJsBeautifyPrinting(json: String): String {
		val chatFile = File.createTempFile("gaisgc", ".json")
		return try {
			chatFile.writeText(json)
			val osName = System.getProperty("os.name").lowercase()
			val isWindows = osName.contains("win")
			val cmd =
				if (isWindows) {
					listOf("cmd.exe", "/c", "js-beautify", "-s", "2", "-r", chatFile.absolutePath)
				} else {
					listOf("js-beautify", "-s", "2", "-r", chatFile.absolutePath)
				}
			ProcessBuilder(cmd).start().waitFor()
			val jsonFormatted = chatFile.readText()
			jsonFormatted.ifEmpty { json }
		} catch (exception: Exception) {
			json
		} finally {
			if (chatFile.exists()) chatFile.delete()
		}
	}
}