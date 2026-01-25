package im.bpu.gaisgc

import androidx.compose.runtime.mutableStateListOf

data class Item(val id: String, val name: String)

object State {
	val items = mutableStateListOf<Item>()
}