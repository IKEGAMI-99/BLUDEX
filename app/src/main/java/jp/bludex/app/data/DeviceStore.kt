package jp.bludex.app.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Entirely local history and favorites. Advertised MAC addresses may rotate, so favorites
 * are address-specific and may not automatically match the same physical device later.
 */
class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("bludex_devices", Context.MODE_PRIVATE)
    private val entries = LinkedHashMap<String, StoredDevice>()
    private val _history = MutableStateFlow<List<StoredDevice>>(emptyList())
    val history = _history.asStateFlow()
    private var lastWriteAt = 0L

    init {
        runCatching {
            val json = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val item = StoredDevice(
                    address = obj.getString("address"),
                    name = obj.optString("name"),
                    manufacturer = obj.optString("manufacturer", "メーカー不明"),
                    model = obj.optString("model").ifBlank { null },
                    category = runCatching {
                        DeviceCategory.valueOf(obj.optString("category", "OTHER"))
                    }.getOrDefault(DeviceCategory.OTHER),
                    lastRssi = obj.optInt("rssi", -100),
                    firstSeen = obj.optLong("firstSeen"),
                    lastSeen = obj.optLong("lastSeen"),
                    favorite = obj.optBoolean("favorite"),
                    nickname = obj.optString("nickname").ifBlank { null },
                    packetCount = obj.optInt("packets", 1)
                )
                entries[item.address] = item
            }
        }
        publish()
    }

    fun observe(devices: List<BleDevice>) {
        if (devices.isEmpty()) return
        for (device in devices) {
            val old = entries[device.address]
            entries[device.address] = StoredDevice(
                address = device.address,
                name = device.name,
                manufacturer = device.manufacturer,
                model = device.model,
                category = device.category,
                lastRssi = device.rssi,
                firstSeen = old?.firstSeen ?: device.firstSeen,
                lastSeen = device.lastSeen,
                favorite = old?.favorite ?: false,
                nickname = old?.nickname,
                packetCount = (old?.packetCount ?: 0).coerceAtLeast(device.packetCount)
            )
        }
        prune()
        publish()
        val now = SystemClock.elapsedRealtime()
        if (now - lastWriteAt > 6_000L) persist()
    }

    fun toggleFavorite(address: String, current: BleDevice? = null) {
        if (entries[address] == null && current != null) observe(listOf(current))
        entries[address]?.let { entries[address] = it.copy(favorite = !it.favorite) }
        publish()
        persist()
    }

    fun rename(address: String, nickname: String) {
        entries[address]?.let { entries[address] = it.copy(nickname = nickname.trim().take(60)) }
        publish()
        persist()
    }

    fun clearNonFavorites() {
        entries.entries.removeAll { !it.value.favorite }
        publish()
        persist()
    }

    fun flush() = persist()

    private fun prune() {
        if (entries.size <= 500) return
        val removed = entries.values.filterNot { it.favorite }
            .sortedBy { it.lastSeen }.take(entries.size - 500)
        removed.forEach { entries.remove(it.address) }
    }

    private fun publish() {
        _history.value = entries.values.sortedByDescending { it.lastSeen }
    }

    private fun persist() {
        val array = JSONArray()
        entries.values.forEach { item ->
            array.put(JSONObject().apply {
                put("address", item.address)
                put("name", item.name)
                put("manufacturer", item.manufacturer)
                put("model", item.model ?: "")
                put("category", item.category.name)
                put("rssi", item.lastRssi)
                put("firstSeen", item.firstSeen)
                put("lastSeen", item.lastSeen)
                put("favorite", item.favorite)
                put("nickname", item.nickname ?: "")
                put("packets", item.packetCount)
            })
        }
        prefs.edit().putString("history", array.toString()).apply()
        lastWriteAt = SystemClock.elapsedRealtime()
    }
}
