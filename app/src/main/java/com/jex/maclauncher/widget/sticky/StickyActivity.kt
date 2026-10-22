package com.jex.maclauncher.widget.sticky

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jex.maclauncher.ui.theme.MacLauncherTheme

/** 스티커 메모 수정: 메모지 모양 입력창 + 색 고르기 */
class StickyActivity : ComponentActivity() {

    companion object {
        fun intent(context: Context, widgetId: Int): Intent =
            Intent(context, StickyActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        setContent {
            MacLauncherTheme {
                StickyEditor(
                    initialText = StickyWidget.text(this, id),
                    initialColor = StickyWidget.color(this, id),
                    onSave = { text, color ->
                        StickyWidget.save(this, id, text, color)
                        finish()
                    },
                    onCancel = ::finish,
                )
            }
        }
    }
}

@Composable
private fun StickyEditor(
    initialText: String,
    initialColor: StickyColor,
    onSave: (String, StickyColor) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var color by remember { mutableStateOf(initialColor) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val noRipple = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = noRipple, indication = null, onClick = onCancel)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.9f)
                .clickable(interactionSource = noRipple, indication = null) { /* 안쪽 탭은 무시 */ },
        ) {
            // 메모지
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = Color(0xE6000000), fontSize = 17.sp, lineHeight = 24.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp)
                    .background(Color(color.argb), RoundedCornerShape(20.dp))
                    .padding(20.dp)
                    .focusRequester(focus),
                decorationBox = { field ->
                    if (text.isEmpty()) Text("메모를 입력하세요", color = Color(0x66000000), fontSize = 17.sp)
                    field()
                },
            )
            Spacer(Modifier.height(14.dp))
            // 색 고르기 / 버튼 (좁은 화면에서도 '저장'이 꺾이지 않게 두 줄)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C2C2E), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StickyColor.entries.forEach { c ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .then(if (c == color) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                .padding(3.dp)
                                .background(Color(c.argb), CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("취소") }
                    TextButton(onClick = { onSave(text.trim(), color) }) { Text("저장", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
