package im.bpu.gaisgc

data class Item(
	val createdTime: Long = 0L,
	val fileExtension: String? = null,
	val id: String,
	val mimeType: String = "",
	val name: String,
	val sha256Checksum: String? = null,
	val size: Long = -1L,
	val isNotFound: Boolean = false,
	val subItems: List<Item> = emptyList(),
)

data class DuplicateMatch(val chat: Item, val original: Item, val duplicate: Item)