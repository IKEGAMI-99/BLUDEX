package jp.bludex.app.data

import android.bluetooth.BluetoothDevice

enum class DeviceCategory(val label: String) {
    AUDIO("オーディオ"), WATCH("スマートウォッチ"), PHONE("スマートフォン"),
    SENSOR("センサー"), COMPUTER("PC"), TAG("タグ・ビーコン"),
    OTHER("その他")
}

data class BleDevice(
    val address: String,
    val name: String,
    val manufacturerId: Int?,
    val manufacturer: String,
    val model: String?,
    val category: DeviceCategory,
    val rssi: Int,
    val txPower: Int?,
    val connectable: Boolean?,
    val serviceUuids: List<String>,
    val serviceData: Map<String, String>,
    val manufacturerData: Map<String, String>,
    val rawHex: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val packetCount: Int,
    val bondState: Int = BluetoothDevice.BOND_NONE
) {
    val title: String get() = model ?: name.ifBlank { "Unknown device" }
    val shortAddress: String get() = address
}

data class StoredDevice(
    val address: String,
    val name: String,
    val manufacturer: String,
    val model: String?,
    val category: DeviceCategory,
    val lastRssi: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val favorite: Boolean,
    val nickname: String?,
    val packetCount: Int
) {
    val title: String get() = nickname?.takeIf { it.isNotBlank() }
        ?: model ?: name.ifBlank { "Unknown device" }
}

data class Identification(
    val manufacturer: String,
    val model: String?,
    val category: DeviceCategory
)

object DeviceClassifier {
    private val companyNames = mapOf(
        0x0006 to "Microsoft",
        0x004C to "Apple",
        0x0075 to "Samsung",
        0x00E0 to "Google",
        0x012D to "Sony",
        0x038F to "Xiaomi"
    )
    private val modelTokens = listOf(
        "WH-1000XM5", "WH-1000XM4", "WF-1000XM5", "WF-1000XM4",
        "Galaxy Buds3", "Galaxy Buds2", "AirPods Pro", "AirPods",
        "Mi Smart Band 8", "Mi Smart Band 7", "Pixel Watch"
    )

    fun identify(name: String, companyId: Int?, uuids: List<String>): Identification {
        val lower = name.lowercase()
        val model = modelTokens.firstOrNull { lower.contains(it.lowercase()) }
        val company = companyId?.let(companyNames::get)
            ?: when {
                lower.contains("sony") || lower.startsWith("wh-") || lower.startsWith("wf-") -> "Sony"
                lower.contains("galaxy") -> "Samsung"
                lower.contains("airpods") -> "Apple"
                lower.contains("pixel") -> "Google"
                lower.contains("xiaomi") || lower.contains("mi band") -> "Xiaomi"
                else -> companyId?.let { "Company ID 0x%04X".format(it) } ?: "メーカー不明"
            }
        val category = when {
            listOf("headphone", "earbud", "airpod", "buds", "sound", "speaker", "wh-", "wf-")
                .any(lower::contains) -> DeviceCategory.AUDIO
            listOf("watch", "band", "fitbit", "garmin").any(lower::contains) -> DeviceCategory.WATCH
            listOf("iphone", "pixel", "phone", "galaxy s").any(lower::contains) -> DeviceCategory.PHONE
            listOf("tile", "airtag", "beacon", "tracker", "tag").any(lower::contains) ->
                DeviceCategory.TAG
            listOf("macbook", "laptop", "desktop", "surface").any(lower::contains) ->
                DeviceCategory.COMPUTER
            listOf("sensor", "thermo", "heart", "scale", "meter", "temp").any(lower::contains) ||
                uuids.any { it.contains("180D", ignoreCase = true) ||
                    it.contains("181A", ignoreCase = true) } -> DeviceCategory.SENSOR
            else -> DeviceCategory.OTHER
        }
        return Identification(company, model, category)
    }
}

fun ByteArray.asHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
