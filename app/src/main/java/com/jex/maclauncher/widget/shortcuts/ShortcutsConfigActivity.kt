package com.jex.maclauncher.widget.shortcuts

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jex.maclauncher.ui.theme.MacLauncherTheme

/** 단축어 위젯에 넣을 타일 고르기. 누른 순서대로 왼쪽 위부터 놓인다 */
class ShortcutsConfigActivity : ComponentActivity() {

    companion object {
        fun intent(context: Context, widgetId: Int): Intent =
            Intent(context, ShortcutsConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        enableEdgeToEdge()
        setContent {
            MacLauncherTheme {
                ActionPicker(ShortcutsWidget.actions(this, id)) { picked ->
                    ShortcutsWidget.save(this, id, picked)
                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    finish()
                }
            }
        }
    }
}

@Composable
private fun ActionPicker(initial: List<ShortcutAction>, onDone: (List<ShortcutAction>) -> Unit) {
    val selected = remember { mutableStateListOf<ShortcutAction>().apply { addAll(initial) } }
    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f)) {
            Text("단축어 타일", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("누른 순서대로 왼쪽 위부터 놓여요 · 최대 ${ShortcutAction.MAX}개", color = Color(0x99FFFFFF), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(ShortcutAction.entries.toList()) { action ->
                    val order = selected.indexOf(action)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (order >= 0) selected.remove(action)
                                else if (selected.size < ShortcutAction.MAX) selected.add(action)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(40.dp).background(Color(action.color), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(action.icon), contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                        Text(action.label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(26.dp).background(if (order >= 0) Color(0xFF0A84FF) else Color(0x33FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (order >= 0) Text("${order + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selected.clear() }) { Text("모두 해제") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onDone(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text("완료 (${selected.size}/${ShortcutAction.MAX})")
            }
        }
    }
}
