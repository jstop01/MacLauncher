package com.jex.maclauncher

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
        handlePermissionRequest(intent)
        setContent {
            MacLauncherTheme {
                WidgetGuide(cards())
                if (pickingFinderMini) {
                    MiniPositionPicker(
                        current = FinderWidget.miniPosition(this),
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
            secondary = "아이콘 위치" to { pickingFinderMini = true },
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
            ThemePicker()
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
private fun MiniPositionPicker(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("최소화 아이콘 위치") },
        text = {
            Column {
                Text("Finder를 🟡로 접었을 때 아이콘이 남을 자리를 골라요. 화면 아래쪽에 놓은 위젯이면 아래 줄을 고르면 위로 열리는 느낌이 나요.", fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
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
                                        .clickable { onPick(pos) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        painterResource(R.drawable.ic_finder_face), contentDescription = null,
                                        modifier = Modifier.size(28.dp).alpha(if (selected) 1f else 0.35f),
                                    )
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
private fun ThemePicker() {
    val context = LocalContext.current
    var light by remember { mutableStateOf(WidgetTheme.isLight(context)) }
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("위젯 테마", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("바꾸면 홈 화면의 위젯이 모두 바로 바뀌어요", color = Sub, fontSize = 13.sp)
        }
        Row(Modifier.background(Color(0xFF2C2C2E), RoundedCornerShape(10.dp)).padding(3.dp)) {
            listOf(false to "다크", true to "라이트").forEach { (value, label) ->
                val selected = light == value
                Text(
                    label,
                    color = if (selected) Color.White else Sub,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .background(if (selected) Color(0xFF636366) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable {
                            light = value
                            WidgetTheme.setLight(context, value)
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
    }
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
