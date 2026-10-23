package jp.bludex.app.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.bludex.app.ExplorerViewModel
import jp.bludex.app.bluetooth.ScanState
import jp.bludex.app.data.BleDevice
import jp.bludex.app.data.DeviceCategory
import jp.bludex.app.data.StoredDevice
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class Page { SCAN, FAVORITES, HISTORY, SETTINGS }

private fun categoryIcon(category: DeviceCategory): ImageVector = when (category) {
    DeviceCategory.AUDIO -> Icons.Rounded.Headphones
    DeviceCategory.WATCH -> Icons.Rounded.Watch
    DeviceCategory.PHONE -> Icons.Rounded.Smartphone
    DeviceCategory.SENSOR -> Icons.Rounded.Sensors
    DeviceCategory.COMPUTER -> Icons.Rounded.Computer
    DeviceCategory.TAG -> Icons.Rounded.Bluetooth
    DeviceCategory.OTHER -> Icons.Rounded.DevicesOther
}

private fun dateTime(time: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.SHORT, DateFormat.SHORT, Locale.JAPAN
).format(Date(time))

private fun bondText(state: Int) = when (state) {
    BluetoothDevice.BOND_BONDED -> "ペアリング済み"
    BluetoothDevice.BOND_BONDING -> "ペアリング処理中"
    else -> "未ペアリング"
}

@Composable
fun BludexApp(
    viewModel: ExplorerViewModel,
    permissionsReady: Boolean,
    bluetoothReady: Boolean,
    onScanRequest: () -> Unit
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val bonds by viewModel.bonds.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(Page.SCAN) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val live = devices.firstOrNull { it.address == selected }
    val archive = history.firstOrNull { it.address == selected }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        val message = notice ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissNotice()
    }

    Scaffold(
        containerColor = Night,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selected == null) {
                NavigationBar(containerColor = Panel) {
                    listOf(
                        Triple(Page.SCAN, Icons.Rounded.BluetoothSearching, "スキャン"),
                        Triple(Page.FAVORITES, Icons.Rounded.Star, "お気に入り"),
                        Triple(Page.HISTORY, Icons.Rounded.History, "履歴"),
                        Triple(Page.SETTINGS, Icons.Rounded.Settings, "設定")
                    ).forEach { (tab, symbol, name) ->
                        NavigationBarItem(
                            selected = page == tab,
                            onClick = { page = tab },
                            icon = { Icon(symbol, null) },
                            label = { Text(name, fontSize = 10.sp) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (selected != null && (live != null || archive != null)) {
                Detail(
                    live = live, stored = archive,
                    bondState = selected?.let { bonds[it] } ?: live?.bondState ?: BluetoothDevice.BOND_NONE,
                    onBack = { selected = null },
                    onFavorite = { selected?.let(viewModel::toggleFavorite) },
                    onRename = { name -> selected?.let { viewModel.rename(it, name) } },
                    onPair = { selected?.let(viewModel::pair) }
                )
            } else {
                LaunchedEffect(selected) {
                    if (selected != null) selected = null
                }
                when (page) {
                    Page.SCAN -> ScanPage(
                        devices, history, scanState is ScanState.Scanning,
                        (scanState as? ScanState.Failed)?.explanation,
                        permissionsReady, bluetoothReady, viewModel.hasBluetooth,
                        onScanRequest, viewModel::stopScan,
                        onOpen = { selected = it }, onFavorite = viewModel::toggleFavorite
                    )
                    Page.FAVORITES -> ArchivePage(
                        "お気に入り", history.filter { it.favorite }, devices,
                        onOpen = { selected = it }, onFavorite = viewModel::toggleFavorite
                    )
                    Page.HISTORY -> ArchivePage(
                        "検出履歴", history, devices,
                        onOpen = { selected = it }, onFavorite = viewModel::toggleFavorite
                    )
                    Page.SETTINGS -> SettingsPage(
                        history.size, viewModel::clearNonFavorites, viewModel::clearNearby
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanPage(
    devices: List<BleDevice>,
    history: List<StoredDevice>,
    scanning: Boolean,
    error: String?,
    permissions: Boolean,
    bluetooth: Boolean,
    supported: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpen: (String) -> Unit,
    onFavorite: (String) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var identifiedOnly by rememberSaveable { mutableStateOf(false) }
    var signalSort by rememberSaveable { mutableStateOf(true) }
    val saved = history.associateBy { it.address }
    val matched = devices.filter {
        (!favoritesOnly || saved[it.address]?.favorite == true) &&
            (!identifiedOnly || it.manufacturer != "メーカー不明" || it.model != null) &&
            (search.isBlank() || listOf(it.title, it.manufacturer, it.address, it.model.orEmpty())
                .any { term -> term.contains(search.trim(), ignoreCase = true) })
    }.let { if (signalSort) it.sortedByDescending(BleDevice::rssi)
        else it.sortedBy(BleDevice::title) }

    Column {
        Header()
        LazyColumn(Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Stat(devices.size.toString(), "検出数", Modifier.weight(1f))
                    Stat(devices.count { it.manufacturer != "メーカー不明" || it.model != null }
                        .toString(), "識別済み", Modifier.weight(1f))
                    Stat(history.count { it.favorite }.toString(), "お気に入り", Modifier.weight(1f))
                }
            }
            item {
                val explanation = when {
                    !supported -> "この端末はBluetoothに対応していません"
                    !permissions -> "Bluetooth・位置情報の権限が必要です（GPS座標は取得しません）"
                    !bluetooth -> "Bluetoothがオフです"
                    error != null -> error
                    else -> null
                }
                if (explanation != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBlue)) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(explanation, fontSize = 13.sp)
                            Button(onClick = onStart, enabled = supported) { Text("スキャンを開始") }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = if (scanning) onStop else onStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(if (scanning) Icons.Rounded.BluetoothSearching else Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (scanning) "スキャン中 · タップして停止" else "スキャンを再開")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("機器名・メーカー・アドレス", fontSize = 13.sp) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp)
                )
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !favoritesOnly && !identifiedOnly,
                        onClick = { favoritesOnly = false; identifiedOnly = false },
                        label = { Text("すべて") }
                    )
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = { favoritesOnly = !favoritesOnly },
                        label = { Text("お気に入り") }
                    )
                    FilterChip(
                        selected = identifiedOnly,
                        onClick = { identifiedOnly = !identifiedOnly },
                        label = { Text("識別済み") }
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("デバイス  " + matched.size, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { signalSort = !signalSort }) {
                        Text(if (signalSort) "電波が強い順" else "名前順", fontSize = 12.sp)
                    }
                }
            }
            if (matched.isEmpty()) item {
                Empty(if (scanning) "デバイスを探索しています" else "スキャンを開始してください",
                    "機器がBLE広告を送信している必要があります")
            }
            items(matched, key = { it.address }) { device ->
                DeviceCard(
                    saved[device.address]?.title ?: device.title, device.manufacturer,
                    device.category, device.address, device.rssi,
                    saved[device.address]?.favorite == true, true,
                    { onOpen(device.address) }, { onFavorite(device.address) }
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Color(0xFF173B60), Night)))
            .padding(horizontal = 21.dp, vertical = 25.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
            .background(Sky.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Bluetooth, null, tint = Sky, modifier = Modifier.size(31.dp))
        }
        Column {
            Text("BLUDEX", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Nearby Bluetooth devices", fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(15.dp)).padding(14.dp)) {
        Text(value, color = Sky, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun DeviceCard(
    title: String, maker: String, category: DeviceCategory,
    address: String, rssi: Int, favorite: Boolean, nearby: Boolean,
    onClick: () -> Unit, onFavorite: () -> Unit
) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(15.dp)).background(CardBlue),
                contentAlignment = Alignment.Center) {
                Icon(categoryIcon(category), category.label, tint = Sky, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(maker, fontSize = 12.sp, color = Muted, maxLines = 1)
                Text((if (nearby) "" else "最終 ") + rssi + " dBm",
                    fontSize = 11.sp, color = if (nearby && rssi > -70) Green else Muted)
                Text(address, fontSize = 10.sp, color = Muted)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onFavorite) {
                    Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        if (favorite) "お気に入り解除" else "お気に入り登録",
                        tint = if (favorite) Amber else Muted)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Muted,
                    modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ArchivePage(
    title: String, items: List<StoredDevice>, live: List<BleDevice>,
    onOpen: (String) -> Unit, onFavorite: (String) -> Unit
) {
    val near = live.map { it.address }.toSet()
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(items.size.toString() + " 件", color = Sky, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        if (items.isEmpty()) item {
            Empty("まだ記録がありません", "スキャンで検出した機器が表示されます")
        }
        items(items, key = { it.address }) { device ->
            DeviceCard(device.title, device.manufacturer, device.category,
                device.address, device.lastRssi, device.favorite,
                device.address in near, { onOpen(device.address) },
                { onFavorite(device.address) })
        }
    }
}

@Composable
private fun Detail(
    live: BleDevice?, stored: StoredDevice?, bondState: Int,
    onBack: () -> Unit, onFavorite: () -> Unit,
    onRename: (String) -> Unit, onPair: () -> Unit
) {
    val title = stored?.title ?: live?.title ?: "Unknown device"
    val address = live?.address ?: stored?.address.orEmpty()
    val category = live?.category ?: stored?.category ?: DeviceCategory.OTHER
    var confirmPair by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var newName by remember(stored?.nickname) { mutableStateOf(stored?.nickname.orEmpty()) }
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "戻る") }
            Text("Device Details", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            IconButton(onClick = { editName = true }, enabled = stored != null) {
                Icon(Icons.Rounded.Edit, "表示名を編集")
            }
            IconButton(onClick = onFavorite) {
                Icon(if (stored?.favorite == true) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    "お気に入り", tint = if (stored?.favorite == true) Amber else Muted)
            }
        }
        Column(
            Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(Color(0xFF1D4262), Panel)),
                RoundedCornerShape(23.dp)
            ).padding(23.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(Modifier.size(98.dp).clip(CircleShape)
                .background(Sky.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                Icon(categoryIcon(category), null, tint = Sky, modifier = Modifier.size(58.dp))
            }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
            Text((live?.manufacturer ?: stored?.manufacturer ?: "メーカー不明")
                + " · " + category.label, color = Muted, fontSize = 12.sp)
            Text(bondText(bondState), color = if (bondState == BluetoothDevice.BOND_BONDED) Green else Muted)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(live?.rssi?.let { it.toString() + " dBm" } ?: "オフライン",
                "信号強度", Modifier.weight(1f))
            Stat((live?.packetCount ?: stored?.packetCount ?: 0).toString(),
                "広告受信数", Modifier.weight(1f))
        }
        Spacer(Modifier.height(23.dp))
        Heading("デバイス情報")
        DetailLine("メーカー", live?.manufacturer ?: stored?.manufacturer ?: "不明")
        DetailLine("型番", live?.model ?: stored?.model ?: "不明")
        DetailLine("MACアドレス", address) { clipboard.setText(AnnotatedString(address)) }
        DetailLine("接続可能", when (live?.connectable) {
            true -> "広告上は接続可能"
            false -> "広告上は接続不可"
            null -> "不明"
        })
        DetailLine("初回検出", dateTime(stored?.firstSeen ?: live?.firstSeen ?: 0))
        DetailLine("最終検出", dateTime(live?.lastSeen ?: stored?.lastSeen ?: 0))
        Spacer(Modifier.height(20.dp))
        Heading("Advertising Data")
        if (live == null) {
            Text("現在受信していません。再検出すると広告データが表示されます",
                color = Muted, fontSize = 13.sp)
        } else {
            DetailLine("RSSI", live.rssi.toString() + " dBm")
            DetailLine("TX Power", live.txPower?.let { it.toString() + " dBm" } ?: "データなし")
            DetailLine("Manufacturer ID", live.manufacturerId?.let { "0x%04X".format(it) } ?: "データなし")
            DetailLine("Service UUIDs", live.serviceUuids.joinToString(", ").ifEmpty { "なし" })
            live.manufacturerData.forEach { (key, value) ->
                HexSection("Manufacturer Data · " + key, value) {
                    clipboard.setText(AnnotatedString(value))
                }
            }
            live.serviceData.forEach { (key, value) ->
                HexSection("Service Data · " + key, value) {
                    clipboard.setText(AnnotatedString(value))
                }
            }
            HexSection("RAW Advertising Data", live.rawHex.ifBlank { "なし" }) {
                clipboard.setText(AnnotatedString(live.rawHex))
            }
        }
        Spacer(Modifier.height(23.dp))
        Heading("ペアリング")
        Button(
            onClick = { confirmPair = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = live != null && live.connectable != false &&
                bondState != BluetoothDevice.BOND_BONDED &&
                bondState != BluetoothDevice.BOND_BONDING
        ) {
            Icon(Icons.Rounded.BluetoothConnected, null)
            Spacer(Modifier.width(8.dp))
            Text(when {
                bondState == BluetoothDevice.BOND_BONDED -> "ペアリング済み"
                bondState == BluetoothDevice.BOND_BONDING -> "ペアリング中"
                live == null -> "近くにデバイスがありません"
                live.connectable == false -> "接続不可の広告"
                else -> "ペアリングを要求"
            })
        }
        Spacer(Modifier.height(9.dp))
        Text("Android標準のペアリング確認を使用します。すべてのBLE機器が対応するわけではありません。",
            color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(30.dp))
    }

    if (confirmPair) AlertDialog(
        onDismissRequest = { confirmPair = false },
        title = { Text("ペアリングを要求") },
        text = { Text("この機器にペアリング要求を送信しますか？ 相手側で承認操作が必要な場合があります。") },
        confirmButton = {
            TextButton(onClick = { confirmPair = false; onPair() }) { Text("要求する") }
        },
        dismissButton = {
            TextButton(onClick = { confirmPair = false }) { Text("キャンセル") }
        }
    )
    if (editName) AlertDialog(
        onDismissRequest = { editName = false },
        title = { Text("表示名を編集") },
        text = {
            OutlinedTextField(value = newName, onValueChange = { newName = it.take(60) },
                label = { Text("自分用の名前") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onRename(newName); editName = false }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = { editName = false }) { Text("キャンセル") }
        }
    )
}

@Composable
private fun Heading(name: String) {
    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
        modifier = Modifier.padding(bottom = 9.dp))
}

@Composable
private fun DetailLine(name: String, value: String, copy: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(name, Modifier.width(110.dp), fontSize = 12.sp, color = Muted)
        Text(value, Modifier.weight(1f), fontSize = 12.sp)
        if (copy != null) Icon(Icons.Rounded.ContentCopy, "コピー",
            modifier = Modifier.size(17.dp).clickable { copy() }, tint = Sky)
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
}

@Composable
private fun HexSection(name: String, data: String, onCopy: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onCopy) {
                    Icon(Icons.Rounded.ContentCopy, "コピー", tint = Sky,
                        modifier = Modifier.size(18.dp))
                }
            }
            Text(data, fontSize = 11.sp, lineHeight = 18.sp, color = Muted)
        }
    }
}

@Composable
private fun SettingsPage(count: Int, onClear: () -> Unit, onResetNearby: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("設定", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("BLUDEX 0.1.0", color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("プライバシー", fontWeight = FontWeight.SemiBold)
                Text("お気に入りと検出履歴は端末内のみに保存されます。"
                    + "位置情報権限はBLE探索のために使用しますが、GPS座標は取得しません。"
                    + "MACアドレスが変わる機器は別機器として表示される場合があります。",
                    color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("保存された履歴", fontWeight = FontWeight.SemiBold)
                Text(count.toString() + " 件", fontSize = 24.sp, color = Sky)
                OutlinedButton(onClick = onResetNearby, modifier = Modifier.fillMaxWidth()) {
                    Text("現在の検出一覧をクリア")
                }
                OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("お気に入り以外の履歴を削除")
                }
            }
        }
        Text("製品写真は未収録のため、現在はカテゴリー別アイコンを表示します。",
            color = Muted, fontSize = 12.sp)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("履歴を削除しますか？") },
        text = { Text("お気に入りは残します。") },
        confirmButton = {
            TextButton(onClick = { confirm = false; onClear() }) { Text("削除") }
        },
        dismissButton = {
            TextButton(onClick = { confirm = false }) { Text("キャンセル") }
        }
    )
}

@Composable
private fun Empty(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 51.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.BluetoothSearching, null, tint = Muted, modifier = Modifier.size(49.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(message, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
