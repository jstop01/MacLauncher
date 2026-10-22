package com.jex.maclauncher

import android.Manifest
import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.jex.maclauncher.widget.CalendarWidget
import com.jex.maclauncher.widget.MonitorService
import com.jex.maclauncher.widget.MonitorWidget
import com.jex.maclauncher.widget.dock.DockConfigActivity
import com.jex.maclauncher.widget.dock.DockWidget
import com.jex.maclauncher.widget.finder.FinderWidget

private val Panel = Color(0xFF1E1E20)
private val Sub = Color(0x99FFFFFF)

/** 위젯을 홈 화면에 추가하는 안내 화면 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 앱 화면(포그라운드)에서는 서비스 시작이 항상 허용됨
        if (MonitorWidget.hasWidgets(this)) MonitorService.start(this)
        setContent {
            MacLauncherTheme {
                WidgetGuide(
                    onAddMonitor = { pin(MonitorWidget::class.java) },
                    onAddCalendar = { pin(CalendarWidget::class.java) },
                    onAddFinder = { pin(FinderWidget::class.java) },
                    onAddDock = ::pinDock,
                    onEditDock = ::editDock,
                )
            }
        }
    }

    // '모든 파일 접근'을 켜고 돌아왔을 수 있으니 Finder 위젯 새로고침
    override fun onResume() {
        super.onResume()
        FinderWidget.refreshAll(this)
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

@Composable
private fun WidgetGuide(onAddMonitor: () -> Unit, onAddCalendar: () -> Unit, onAddFinder: () -> Unit, onAddDock: () -> Unit, onEditDock: () -> Unit) {
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
            Spacer(Modifier.height(24.dp))
            WidgetCard(
                title = "시스템 모니터",
                detail = "CPU · 메모리 사용량을 1초마다 갱신해요. 갱신하는 동안 알림창에 '시스템 모니터' 알림이 떠 있고, 화면이 꺼지면 멈춥니다.",
                onAdd = onAddMonitor,
            )
            Spacer(Modifier.height(12.dp))
            WidgetCard(
                title = "달력",
                detail = "◀ ▶로 달을 넘기고, 년·월을 누르면 이번 달로 돌아와요. 날짜를 누르면 캘린더 앱이 열립니다.",
                onAdd = onAddCalendar,
            )
            Spacer(Modifier.height(12.dp))
            WidgetCard(
                title = "Finder",
                detail = "폴더를 탭하면 위젯 안에서 들어가고, 파일을 탭하면 바로 열려요. 처음 한 번 '모든 파일 접근' 권한을 켜야 합니다 (위젯을 탭하면 설정으로 이동).",
                onAdd = onAddFinder,
            )
            Spacer(Modifier.height(12.dp))
            WidgetCard(
                title = "Dock",
                detail = "자주 쓰는 앱을 골라 Mac Dock처럼 한 줄로 놓고 탭해서 실행해요. 추가하면 바로 앱 고르기 화면이 뜨고, 나중에 '앱 고르기'로 바꿀 수 있어요.",
                onAdd = onAddDock,
                secondary = "앱 고르기" to onEditDock,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "추가 버튼이 안 되면: 홈 화면 빈 곳을 길게 누르기 → 위젯 → Mac 위젯",
                color = Sub, fontSize = 12.sp,
            )
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
