package im.bpu.gaisgc.model

import java.io.File

object Constants {
	const val APPLICATION_NAME = "GAISGC"
	const val LUMINANCE_THRESHOLD = 0.128
	const val MAX_VIDEO_SIZE = 25 * 1024 * 1024
	const val PORT = 0
	const val TIMEOUT_MS = 0
	const val USER_ID = "user"

	const val CACHE_PROPERTY = "cache"
	const val DEV_MODE_PROPERTY = "devMode"
	const val DUPLICATES_PATH_PROPERTY = "duplicatesPath"
	const val GAIS_PATH_PROPERTY = "gaisPath"
	const val MIDDLE_FRAME_PROPERTY = "middleFrame"
	const val CREATED_TIME_MODIFICATION_PROPERTY = "createdTimeModification"
	const val RELINK_METHOD_PROPERTY = "relinkMethod"

	private val APP_DIRECTORY = File(System.getProperty("user.home"), ".gaisgc").apply { mkdirs() }
	val CONFIG_FILE_PATH = File(APP_DIRECTORY, "config.properties").absolutePath!!
	val CREDENTIALS_FILE_PATH = File(APP_DIRECTORY, "credentials.json").absolutePath!!
	val TOKENS_DIRECTORY_PATH = File(APP_DIRECTORY, "tokens").absolutePath!!
	val CACHE_DIRECTORY_PATH = File(APP_DIRECTORY, "cache").absolutePath!!

	const val CREDENTIALS_JSON_PATH = "CREDENTIALS_JSON_PATH"
	const val DEFAULT_GAIS_PATH = "Google AI Studio"
	const val DEFAULT_DUPLICATES_PATH = DEFAULT_GAIS_PATH

	const val DEFAULT_HEIGHT = 800
	const val DEFAULT_WIDTH = 1200
	const val MIN_HEIGHT = 600
	const val MIN_WIDTH = 1000
	const val PREVIEW_PANE_WIDTH_DP = 560f
	const val DEV_MODE_CLICKS = 7
	const val DEV_MODE_MS = 2000

	const val MIME_FOLDER = "application/vnd.google-apps.folder"
	const val MIME_PROMPT = "application/vnd.google-makersuite.prompt"
}