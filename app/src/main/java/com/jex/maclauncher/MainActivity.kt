package com.jex.maclauncher

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
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
                )
            }
        }
    }

    /** One UI는 "홈 화면에 추가" 확인 창을 띄워준다 */
    private fun pin(provider: Class<*>) {
        AppWidgetManager.getInstance(this).requestPinAppWidget(ComponentName(this, provider), null, null)
    }
}

@Composable
private fun WidgetGuide(onAddMonitor: () -> Unit, onAddCalendar: () -> Unit) {
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
            Spacer(Modifier.height(20.dp))
            Text(
                "추가 버튼이 안 되면: 홈 화면 빈 곳을 길게 누르기 → 위젯 → Mac 위젯",
                color = Sub, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun WidgetCard(title: String, detail: String, onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button(onClick = onAdd) { Text("홈 화면에 추가") }
        }
        Spacer(Modifier.height(8.dp))
        Text(detail, color = Sub, fontSize = 13.sp)
    }
}
