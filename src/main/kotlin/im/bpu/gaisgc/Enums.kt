package im.bpu.gaisgc

enum class Screen {
	MAIN,
	UNLINKED,
	RELINKER,
	SETTINGS,
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

enum class RelinkMethod {
	DIRECT,
	REGEX,
	PRETTY,
	JS_BEAUTIFY,
}