package im.bpu.gaisgc.manager

import im.bpu.gaisgc.model.DuplicateMatch
import im.bpu.gaisgc.model.Item

object DuplicateManager {

	private fun isValidSubItem(item: Item): Boolean {
		return !item.isNotFound &&
			item.fileExtension != null &&
			item.size != 0L &&
			item.sha256Checksum != null
	}

	private fun findDuplicate(subItem: Item, folderItems: List<Item>): Item? {
		return folderItems.firstOrNull { folderItem ->
			folderItem.id != subItem.id &&
				folderItem.fileExtension == subItem.fileExtension &&
				folderItem.size == subItem.size &&
				folderItem.sha256Checksum == subItem.sha256Checksum
		}
	}

	fun findDuplicates(chatItems: List<Item>, folderItems: List<Item>): List<DuplicateMatch> {
		val duplicateItems = mutableListOf<DuplicateMatch>()
		for (chatItem in chatItems) {
			for (subItem in chatItem.subItems) {
				if (isValidSubItem(subItem)) {
					val duplicateItem = findDuplicate(subItem, folderItems)
					if (duplicateItem != null) {
						duplicateItems.add(DuplicateMatch(chatItem, subItem, duplicateItem))
					}
				}
			}
		}
		return duplicateItems
	}
}