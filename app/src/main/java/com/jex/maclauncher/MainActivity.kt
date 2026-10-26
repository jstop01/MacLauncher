package com.jex.maclauncher

import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import android.Manifest
import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jex.maclauncher.ui.theme.MacLauncherTheme
import com.jex.maclauncher.widget.finder.FinderIcon
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.result.PickVisualMediaRequest
import android.graphics.Bitmap
import com.jex.maclauncher.backup.WidgetBackup
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import com.jex.maclauncher.widget.BatteryWidget
import com.jex.maclauncher.widget.CalendarWidget
import com.jex.maclauncher.widget.ClockWidget
import com.jex.maclauncher.widget.MonitorService
import com.jex.maclauncher.widget.MonitorWidget
import com.jex.maclauncher.widget.PhotoWidget
import com.jex.maclauncher.widget.StorageWidget
import com.jex.maclauncher.widget.TimerWidget
import com.jex.maclauncher.widget.WeatherWidget
import com.jex.maclauncher.widget.WidgetRefresher
import com.jex.maclauncher.widget.WidgetTheme
import com.jex.maclauncher.widget.skin.Entitlements
import com.jex.maclauncher.widget.skin.PanelStyle
import com.jex.maclauncher.widget.skin.Skin
import com.jex.maclauncher.widget.skin.SkinBilling
import com.jex.maclauncher.widget.skin.SkinPack
import com.jex.maclauncher.widget.skin.SkinPrefs
import com.jex.maclauncher.widget.skin.Tier
import com.jex.maclauncher.widget.apps.AppsWidget
import com.jex.maclauncher.widget.dock.DockConfigActivity
import com.jex.maclauncher.widget.dock.DockWidget
import com.jex.maclauncher.widget.finder.FinderWidget
import com.jex.maclauncher.widget.music.MusicWidget
import com.jex.maclauncher.widget.network.NetworkWidget
import com.jex.maclauncher.widget.screentime.ScreenTimeWidget
import com.jex.maclauncher.widget.shortcuts.ShortcutsConfigActivity
import com.jex.maclauncher.widget.shortcuts.ShortcutsWidget
import com.jex.maclauncher.widget.reminders.RemindersWidget
import com.jex.maclauncher.widget.sticky.StickyWidget

private val Panel = Color(0xFF1E1E20)
private val Sub = Color(0x99FFFFFF)

/** 위젯을 홈 화면에 추가하는 안내 화면 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_REQUEST_PERMISSION = "request_permission"

        /** 위젯 탭 → 이 화면을 열어 권한 하나 요청 (권한 창은 액티비티에서만 띄울 수 있음), 끝나면 위젯 다시 그림 */
        fun requestPermissionIntent(context: Context, permission: String): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_REQUEST_PERMISSION, permission)
    }

    private val permissionOnly = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        WidgetRefresher.all(this)
    }

    private fun handlePermissionRequest(intent: Intent) {
        intent.getStringExtra(EXTRA_REQUEST_PERMISSION)?.let(permissionOnly::launch)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePermissionRequest(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 앱 화면(포그라운드)에서는 서비스 시작이 항상 허용됨
        if (MonitorService.needed(this)) MonitorService.start(this)
        finderIcon = FinderIcon.custom(this)
        handlePermissionRequest(intent)
        setContent {
            MacLauncherTheme {
                WidgetGuide(cards())
                if (pickingFinderMini) {
                    MiniPositionPicker(
                        current = FinderWidget.miniPosition(this),
                        iconOn = FinderWidget.miniIconEnabled(this),
                        onIconToggle = { FinderWidget.setMiniIconEnabled(this, it) },
                        customIcon = finderIcon,
                        onPickIcon = { finderIconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onResetIcon = {
                            FinderIcon.clear(this)
                            finderIcon = null
                        },
                        onPick = {
                            FinderWidget.setMiniPosition(this, it)
                            pickingFinderMini = false
                        },
                        onDismiss = { pickingFinderMini = false },
                    )
                }
            }
        }
    }

    // '모든 파일 접근'을 켜고 돌아왔을 수 있으니 Finder 위젯 새로고침
    override fun onResume() {
        super.onResume()
        FinderWidget.refreshAll(this)
        StorageWidget.refreshAll(this)
        AppsWidget.refreshAll(this) // 새로 설치/삭제한 앱 반영
        ScreenTimeWidget.refreshAll(this) // '사용 정보 접근'을 켜고 돌아왔을 수 있음
    }

    /** 앱 화면의 위젯 카드 목록 (위에서부터 이 순서로 보임, Finder는 항상 맨 위) */
    private fun cards() = listOf(
        WidgetCardSpec(
            "Finder",
            "폴더를 탭하면 위젯 안에서 들어가고, 파일을 탭하면 바로 열려요. 🟡 최소화, 🟢 최대화. 처음 한 번 '모든 파일 접근' 권한을 켜야 합니다 (위젯을 탭하면 설정으로 이동).",
            secondary = "아이콘 설정" to { pickingFinderMini = true },
        ) { pin(FinderWidget::class.java) },
        WidgetCardSpec(
            "응용 프로그램",
            "앱 전용 Finder예요. 전체 · 최근 설치 · 게임 · 소셜 · 사진·동영상 · 음악 · 생산성으로 나눠 보고 탭해서 실행해요.",
        ) { pin(AppsWidget::class.java) },
        WidgetCardSpec(
            "시스템 모니터",
            "CPU · 메모리 사용량을 1초마다 갱신하고, 탭하면 삼성 디바이스 케어(최적화)로 가요. 갱신하는 동안 알림창에 '시스템 모니터' 알림이 떠 있고, 화면이 꺼지면 멈춥니다.",
        ) { pin(MonitorWidget::class.java) },
        WidgetCardSpec(
            "스크린 타임",
            "Mac 스크린 타임처럼 오늘 사용 시간, 시간대별 막대(소셜·엔터테인먼트·기타), 많이 쓴 앱 3개를 보여줘요. 탭하면 새로고침. '사용 정보 접근' 권한이 필요해요.",
        ) { pin(ScreenTimeWidget::class.java) },
        WidgetCardSpec(
            "단축어",
            "Mac 단축어처럼 알록달록한 타일로 손전등·와이파이·블루투스·카메라·타이머·녹음·계산기·소리/진동 등을 바로 실행해요. 손전등과 소리/진동은 앱을 열지 않고 바로 바뀌어요.",
            secondary = "타일 고르기" to ::editShortcuts,
        ) { pin(ShortcutsWidget::class.java) },
        WidgetCardSpec(
            "네트워크",
            "Mac iStat처럼 실시간 다운로드·업로드 속도와 60초 그래프, 연결 종류(Wi-Fi·모바일), 오늘 데이터 사용량을 보여줘요. 시스템 모니터와 같은 1초 갱신을 써요.",
        ) { pin(NetworkWidget::class.java) },
        WidgetCardSpec(
            "시계",
            "Mac 아날로그 시계예요. 작게 놓으면 시계 하나, 넓게 놓으면 서울·뉴욕·런던·도쿄 세계 시계가 돼요. 탭하면 알람 앱이 열려요.",
        ) { pin(ClockWidget::class.java) },
        WidgetCardSpec(
            "타이머",
            "Mac 시계 타이머처럼 주황 링에 남은 시간을 보여줘요. 위젯에서 바로 1·5·10·25분을 시작하고 일시정지·취소할 수 있고, 끝나면 알람 소리로 알려줘요.",
        ) { pin(TimerWidget::class.java) },
        WidgetCardSpec(
            "달력",
            "◀ ▶로 달을 넘기고, 일정이 있는 날엔 점이 찍혀요. 작게 놓으면 다음 일정 2개를 보여줘요. 일정을 보려면 캘린더 권한이 필요해요.",
        ) { pinAfterPermission(CalendarWidget::class.java, Manifest.permission.READ_CALENDAR) },
        WidgetCardSpec(
            "Dock",
            "자주 쓰는 앱을 골라 Mac Dock처럼 한 줄로 놓고 탭해서 실행해요. 추가하면 바로 앱 고르기 화면이 뜨고, 나중에 '앱 고르기'로 바꿀 수 있어요.",
            secondary = "앱 고르기" to ::editDock,
            onAdd = ::pinDock,
        ),
        WidgetCardSpec(
            "저장 공간",
            "Mac의 'Macintosh HD'처럼 앱 · 사진 · 동영상 · 음악 · 문서별 사용량을 색 막대로 보여줘요. 탭하면 삼성 저장공간 화면, ⟳ 로 새로고침 (항목별 용량은 '모든 파일 접근' 권한 필요).",
        ) { pin(StorageWidget::class.java) },
        WidgetCardSpec(
            "미리 알림",
            "동그라미를 탭하면 완료, 글자를 탭하면 수정, + 로 바로 추가해요. 할 일은 폰 안에만 저장됩니다.",
        ) { pin(RemindersWidget::class.java) },
        WidgetCardSpec(
            "사진",
            "갤러리 사진을 한 장씩 꽉 차게 보여주고 1시간마다 바꿔요. 아래에 찍은 날짜가 나오고, 탭하면 갤러리에서 열려요. 사진 접근 권한이 필요해요.",
        ) { pinAfterPermission(PhotoWidget::class.java, PhotoWidget.permission) },
        WidgetCardSpec(
            "지금 재생 중",
            "재생 중인 음악의 앨범 아트·제목·가수와 ⏮ ⏯ ⏭ 버튼이에요. 유튜브 뮤직·멜론·스포티파이 등 어떤 앱이든 돼요. 처음 한 번 위젯을 탭해서 '알림 접근'을 켜주세요 (알림 내용은 읽지 않아요).",
        ) { pin(MusicWidget::class.java) },
        WidgetCardSpec(
            "메모",
            "Mac 스티커처럼 색깔 메모지에 메모를 써요. 탭하면 수정하고 색(노랑·파랑·초록·분홍·보라·회색)도 바꿀 수 있어요. 여러 개 놓으면 각각 따로 저장돼요.",
        ) { pin(StickyWidget::class.java) },
        WidgetCardSpec(
            "배터리",
            "폰과 연결된 블루투스 기기(버즈·워치 등)의 배터리를 링으로 보여줘요. 블루투스 기기는 '근처 기기' 권한이 필요하고, 기기에 따라 배터리를 알려주지 않을 수 있어요.",
        ) {
            // 블루투스 기기 배터리를 읽으려면 근처 기기 권한 (거부해도 폰 배터리는 표시)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) pinAfterPermission(BatteryWidget::class.java, Manifest.permission.BLUETOOTH_CONNECT)
            else pin(BatteryWidget::class.java)
        },
        WidgetCardSpec(
            "날씨",
            "현재 위치의 날씨와 4일 예보를 Mac 날씨처럼 보여줘요. 위치 권한이 필요하고(거부하면 탭해서 다시 허용), 30분마다 갱신돼요. 탭하면 바로 새로고침해요.",
        ) { pinAfterPermission(WeatherWidget::class.java, Manifest.permission.ACCESS_COARSE_LOCATION) },
    )

    /** Finder 🟡 최소화 아이콘 위치 고르기 창 */
    private var pickingFinderMini by mutableStateOf(false)

    /** 사용자가 등록한 Finder 아이콘 (없으면 기본 웃는 얼굴) */
    private var finderIcon by mutableStateOf<Bitmap?>(null)
    private val finderIconPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        if (FinderIcon.set(this, uri)) finderIcon = FinderIcon.custom(this)
        else Toast.makeText(this, "이미지를 불러오지 못했어요", Toast.LENGTH_SHORT).show()
    }

    // 권한 창과 위젯 추가 창이 겹치지 않게, 권한 응답 뒤에 추가 창을 띄운다 (거부해도 위젯은 추가)
    private var pinAfterGrant: Class<*>? = null
    private val permissionThenPin = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pinAfterGrant?.let(::pin)
        pinAfterGrant = null
    }

    private fun pinAfterPermission(provider: Class<*>, permission: String) {
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return pin(provider)
        pinAfterGrant = provider
        permissionThenPin.launch(permission)
    }

    /** Dock은 홈에 놓이자마자 앱 고르기 화면을 띄움 (런처가 새 위젯 id를 채워서 불러줌) */
    private fun pinDock() {
        // Android 14+: 런처가 이 PendingIntent로 우리 화면을 띄우려면 만든 쪽(우리)이 허용해야 함
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else null
        val pickApps = PendingIntent.getActivity(
            this, 0, Intent(this, DockConfigActivity::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT, options,
        )
        AppWidgetManager.getInstance(this).requestPinAppWidget(ComponentName(this, DockWidget::class.java), null, pickApps)
    }

    /** 이미 놓은 단축어 위젯의 타일 다시 고르기 (여러 개면 가장 최근 것) */
    private fun editShortcuts() {
        val ids = AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, ShortcutsWidget::class.java))
        if (ids.isEmpty()) {
            Toast.makeText(this, "먼저 단축어 위젯을 홈 화면에 추가하세요", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(ShortcutsConfigActivity.intent(this, ids.max()))
    }

    /** 이미 놓은 Dock의 앱 다시 고르기 (여러 개면 가장 최근 것) */
    private fun editDock() {
        val ids = AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, DockWidget::class.java))
        if (ids.isEmpty()) {
            Toast.makeText(this, "먼저 Dock 위젯을 홈 화면에 추가하세요", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(DockConfigActivity.intent(this, ids.max()))
    }

    /** One UI는 "홈 화면에 추가" 확인 창을 띄워준다 */
    private fun pin(provider: Class<*>) {
        AppWidgetManager.getInstance(this).requestPinAppWidget(ComponentName(this, provider), null, null)
    }
}

private data class WidgetCardSpec(
    val title: String,
    val detail: String,
    val secondary: Pair<String, () -> Unit>? = null,
    val onAdd: () -> Unit,
)

@Composable
private fun WidgetGuide(cards: List<WidgetCardSpec>) {
    // 실시간 갱신 알림을 보이려면 Android 13+에서 알림 권한 필요 (거부해도 위젯은 동작)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Text("Mac 위젯", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "홈 화면에 macOS 스타일 위젯을 추가하세요. 추가한 뒤에는 길게 눌러 크기를 바꾸거나 옮길 수 있어요.",
                color = Sub, fontSize = 14.sp,
            )
            Spacer(Modifier.height(20.dp))
            SkinPicker()
            Spacer(Modifier.height(12.dp))
            BackupPanel()
            cards.forEach { card ->
                Spacer(Modifier.height(12.dp))
                WidgetCard(card.title, card.detail, card.onAdd, card.secondary)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "추가 버튼이 안 되면: 홈 화면 빈 곳을 길게 누르기 → 위젯 → Mac 위젯",
                color = Sub, fontSize = 12.sp,
            )
        }
    }
}

/** 위젯 다크/라이트. 바꾸면 이미 놓인 위젯도 바로 다시 그려짐 */
/** 백업: Finder 즐겨찾기·Dock·메모·미리 알림·테마를 파일 하나로 내보내고 불러오기 */
@Composable
private fun BackupPanel() {
    val context = LocalContext.current
    val today = remember { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching { WidgetBackup.export(context, uri) }.isSuccess
        Toast.makeText(context, if (ok) "백업 파일을 저장했어요" else "저장하지 못했어요", Toast.LENGTH_SHORT).show()
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val msg = runCatching { WidgetBackup.import(context, uri) }.fold(
            onSuccess = { r ->
                buildString {
                    append("복원했어요")
                    if (r.pendingDock + r.pendingSticky > 0) {
                        append(" · 남은 Dock ${r.pendingDock}개, 메모 ${r.pendingSticky}개는 새로 추가할 때 채워져요")
                    }
                }
            },
            onFailure = { it.message ?: "불러오지 못했어요" },
        )
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("백업", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("즐겨찾기 · Dock · 메모 · 미리 알림 · 테마", color = Sub, fontSize = 13.sp)
        }
        TextButton(onClick = { importer.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) { Text("불러오기") }
        Button(onClick = { exporter.launch("mac-widgets-backup-$today.json") }) { Text("내보내기") }
    }
}

/** 최소화 아이콘 위치 9칸 (위젯 모양의 3x3 격자) */
@Composable
private fun MiniPositionPicker(
    current: Int,
    iconOn: Boolean,
    onIconToggle: (Boolean) -> Unit,
    customIcon: Bitmap?,
    onPickIcon: () -> Unit,
    onResetIcon: () -> Unit,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var showIcon by remember { mutableStateOf(iconOn) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finder 최소화 아이콘") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("최소화하면 아이콘 남기기", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(checked = showIcon, onCheckedChange = { showIcon = it; onIconToggle(it) })
                }
                Text(
                    if (showIcon) "Finder를 🟡로 접었을 때 아이콘이 남을 자리를 골라요. 화면 아래쪽에 놓은 위젯이면 아래 줄을 고르면 위로 열리는 느낌이 나요."
                    else "🟡로 접으면 Finder가 완전히 사라지고, 아래에 겹쳐 둔 위젯도 그대로 누를 수 있어요. Dock의 Finder 아이콘을 누르면 다시 펼쳐져요.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                // 아이콘 모양: 기본(직접 그린 웃는 얼굴) 또는 사용자가 고른 이미지
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FinderIconPreview(customIcon, Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(if (customIcon != null) "내 이미지" else "기본 아이콘", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (customIcon != null) TextButton(onClick = onResetIcon) { Text("기본으로") }
                    TextButton(onClick = onPickIcon) { Text("이미지 고르기") }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (showIcon) 1f else 0.35f)
                        .aspectRatio(1.3f)
                        .background(Color(0xFF1E1E20), RoundedCornerShape(16.dp))
                        .padding(8.dp),
                ) {
                    for (row in 0..2) {
                        Row(Modifier.weight(1f)) {
                            for (col in 0..2) {
                                val pos = row * 3 + col
                                val selected = pos == current
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(4.dp)
                                        .background(if (selected) Color(0xFF0A84FF) else Color(0xFF2C2C2E), RoundedCornerShape(10.dp))
                                        .clickable(enabled = showIcon) { onPick(pos) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FinderIconPreview(customIcon, Modifier.size(28.dp).alpha(if (selected) 1f else 0.35f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun FinderIconPreview(custom: Bitmap?, modifier: Modifier) {
    if (custom != null) Image(custom.asImageBitmap(), contentDescription = null, modifier = modifier.clip(RoundedCornerShape(22)))
    else Image(painterResource(R.drawable.ic_finder_face), contentDescription = null, modifier = modifier)
}

/**
 * 위젯 감성(테마) 고르기. 다크/라이트도 팩(맥 다크·맥 라이트)으로 통합됐다.
 * 팩을 고르면 아직 이관 안 된 위젯이 밝기라도 따라오게 WidgetTheme.isLight도 맞춘다.
 * 프리미엄 팩은 자물쇠로 표시하고 탭하면 구매 안내를 띄운다.
 */
@Composable
private fun SkinPicker() {
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) }
    val currentId = remember(version) { Skin.current(context).id }
    val unlocked = remember(version) { Entitlements.unlockedIds(context) }
    var purchasing by remember { mutableStateOf<SkinPack?>(null) }
    // 다른 기기에서 산 팩·환불된 팩을 Play 구매 내역으로 맞춘다
    LaunchedEffect(Unit) { SkinBilling.restore(context) { version++ } }

    fun apply(pack: SkinPack) {
        // 아직 이관 안 된 위젯(배터리·달력 등)이 밝기라도 맞게 isLight 동기화
        WidgetTheme.setLightQuietly(context, pack.isLight)
        SkinPrefs.select(context, pack.id)  // 내부에서 WidgetRefresher.all 호출
        version++
    }

    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(18.dp),
    ) {
        Text("위젯 감성", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("홈 화면 위젯의 감성을 골라요. 바꾸면 바로 적용돼요. (배경·그래프·강조색이 함께 바뀌어요)", color = Sub, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        SkinPreview(Skin.byId(currentId) ?: Skin.MAC_DARK)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Skin.BUILTIN.forEachIndexed { i, pack ->
                if (i > 0) Spacer(Modifier.width(14.dp))
                val locked = pack.tier == Tier.PREMIUM && pack.id !in unlocked
                SkinSwatch(pack, selected = pack.id == currentId, locked = locked) {
                    if (locked) purchasing = pack else apply(pack)
                }
            }
        }
    }

    purchasing?.let { pack ->
        PremiumDialog(
            pack = pack,
            onDismiss = { purchasing = null },
            onPurchase = {
                val activity = context as? android.app.Activity ?: return@PremiumDialog
                SkinBilling.purchase(activity, pack) { result ->
                    when (result) {
                        is SkinBilling.Result.Purchased -> { version++; apply(pack); purchasing = null }
                        is SkinBilling.Result.Canceled -> purchasing = null
                        is SkinBilling.Result.Unavailable ->
                            Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDebugUnlock = {
                if (SkinBilling.debugUnlock(context, pack)) { version++; apply(pack); purchasing = null }
            },
            debug = SkinBilling.isDebug(context),
        )
    }
}

/** 프리미엄 팩 구매 안내 */
@Composable
private fun PremiumDialog(
    pack: SkinPack,
    onDismiss: () -> Unit,
    onPurchase: () -> Unit,
    onDebugUnlock: () -> Unit,
    debug: Boolean,
) {
    val context = LocalContext.current
    var price by remember(pack.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(pack.id) { SkinBilling.price(context, pack) { price = it } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("‘${pack.name}’ 프리미엄 감성") },
        text = {
            Column {
                SkinPreview(pack)
                Spacer(Modifier.height(12.dp))
                Text("구매하면 로그인한 Google 계정의 모든 기기에서 쓸 수 있어요.", fontSize = 14.sp)
                if (debug) {
                    Spacer(Modifier.height(8.dp))
                    Text("(디버그 빌드: 결제 없이 ‘바로 해금’으로 테스트할 수 있어요)", color = Color.Gray, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onPurchase) { Text(price?.let { "구매 · $it" } ?: "구매") } },
        dismissButton = {
            if (debug) TextButton(onClick = onDebugUnlock) { Text("바로 해금") }
            else TextButton(onClick = onDismiss) { Text("닫기") }
        },
    )
}

/** 감성 스와치 한 칸: 팩 배경색 미리보기 + 이름 (+ 프리미엄 자물쇠). */
@Composable
private fun SkinSwatch(pack: SkinPack, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(pack.previewBrush())
                .border(
                    if (selected) 2.5.dp else 1.dp,
                    if (selected) Color(0xFF0A84FF) else Color(0x33FFFFFF),
                    RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (locked) Text("🔒", fontSize = 18.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            pack.name,
            color = if (selected) Color.White else Sub,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * 팩을 입힌 미니 위젯 미리보기: CPU 링·그래프(강조색), 제목·보조 글씨, 오늘 표시.
 * 실제 위젯과 같은 팔레트 값을 써서 사기 전에 느낌을 볼 수 있게 한다.
 */
@Composable
private fun SkinPreview(pack: SkinPack) {
    val p = pack.palette
    val text = Color(p.text)
    val sub = Color(p.subText)
    val accent = Color(pack.accent)
    Row(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(pack.cornerRadiusDp.dp))
            .background(pack.previewBrush())
            .border(1.dp, Color(pack.strokeColor()), RoundedCornerShape(pack.cornerRadiusDp.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.minDimension * 0.12f
                val inset = w / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(Color(p.track), 0f, 360f, false, tl, arcSize, style = Stroke(w))
                drawArc(accent, -90f, 360f * 0.42f, false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text("42%", color = text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("시스템 모니터", color = text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("메모리 6.2GB · 일정 2개", color = sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val ys = listOf(0.7f, 0.55f, 0.8f, 0.35f, 0.6f, 0.2f, 0.5f, 0.3f, 0.65f, 0.45f)
                val path = Path()
                ys.forEachIndexed { i, y ->
                    val x = size.width * i / (ys.size - 1)
                    if (i == 0) path.moveTo(x, size.height * y) else path.lineTo(x, size.height * y)
                }
                drawLine(Color(p.grid), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height))
                drawPath(path, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("10월", color = sub, fontSize = 11.sp)
            Box(
                Modifier.padding(top = 2.dp).size(30.dp).background(accent, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("26", color = Color(Skin.onAccent(pack)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 위젯 패널 테두리 색 (맥 다크처럼 앱 배경과 비슷한 팩도 미리보기 경계가 보이게) */
private fun SkinPack.strokeColor(): Int = when (val s = panel) {
    is PanelStyle.Solid -> s.stroke
    is PanelStyle.Gradient -> s.stroke
    is PanelStyle.Glass -> s.stroke
}

/** 팩 배경을 스와치용 Compose Brush로. 위젯에 실제로 깔리는 SkinGraphics와 같은 색을 쓴다. */
private fun SkinPack.previewBrush(): Brush = when (val s = panel) {
    is PanelStyle.Solid -> SolidColor(Color(s.color))
    is PanelStyle.Gradient -> Brush.verticalGradient(listOf(Color(s.from), Color(s.to)))
    is PanelStyle.Glass -> SolidColor(Color(s.tint))
}

@Composable
private fun WidgetCard(title: String, detail: String, onAdd: () -> Unit, secondary: Pair<String, () -> Unit>? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            secondary?.let { (label, action) -> TextButton(onClick = action) { Text(label) } }
            Button(onClick = onAdd) { Text("홈 화면에 추가") }
        }
        Spacer(Modifier.height(8.dp))
        Text(detail, color = Sub, fontSize = 13.sp)
    }
}
