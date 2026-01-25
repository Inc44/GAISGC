package im.bpu.gaisgc

import androidx.compose.runtime.mutableStateListOf

data class Item(val id: String, val name: String, val subItems: List<Item> = emptyList())

object State {
	val items = mutableStateListOf<Item>()
}