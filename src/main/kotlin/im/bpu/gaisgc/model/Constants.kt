package im.bpu.gaisgc.model

object Constants {
	const val APPLICATION_NAME = "GAISGC"
	const val LUMINANCE_THRESHOLD = 0.128
	const val MAX_VIDEO_SIZE = 25 * 1024 * 1024
	const val PORT = 8888
	const val TIMEOUT_MS = 0
	const val USER_ID = "user"

	const val CACHE_PROPERTY = "cache"
	const val DUPLICATES_PATH_PROPERTY = "duplicatesPath"
	const val GAIS_PATH_PROPERTY = "gaisPath"
	const val MIDDLE_FRAME_PROPERTY = "middleFrame"
	const val RELINK_METHOD_PROPERTY = "relinkMethod"

	const val CONFIG_FILE_PATH = "config.properties"
	const val CREDENTIALS_FILE_PATH = "credentials.json"
	const val DEFAULT_GAIS_PATH = "Google AI Studio"
	const val DEFAULT_DUPLICATES_PATH = DEFAULT_GAIS_PATH
	const val TOKENS_DIRECTORY_PATH = "tokens"

	const val DEFAULT_HEIGHT = 800
	const val DEFAULT_WIDTH = 1200
	const val MIN_HEIGHT = 600
	const val MIN_WIDTH = 1000
	const val PREVIEW_PANE_WIDTH_DP = 560f

	const val MIME_FOLDER = "application/vnd.google-apps.folder"
	const val MIME_PROMPT = "application/vnd.google-makersuite.prompt"
}