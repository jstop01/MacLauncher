package com.jex.maclauncher.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LaunchableApp(val packageName: String, val label: String, val icon: ImageBitmap)

/**
 * 설치된 앱 중에서 고르는 화면 (Dock, Finder 즐겨찾기가 같이 씀).
 * 누른 순서가 곧 배치 순서이고, max개까지.
 */
@Composable
fun AppPicker(title: String, subtitle: String, max: Int, initial: List<String>, onDone: (List<String>) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<LaunchableApp>?>(null) {
        value = withContext(Dispatchers.IO) { loadApps(context) }
    }
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var query by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f)) {
            Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0x99FFFFFF), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("검색") }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            val shown = apps?.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }.orEmpty()
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.packageName }) { app ->
                    val order = selected.indexOf(app.packageName)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (order >= 0) selected.remove(app.packageName)
                                else if (selected.size < max) selected.add(app.packageName)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        Text(app.label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        OrderBadge(order)
                    }
                }
            }
        }
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selected.clear() }) { Text("모두 해제") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onDone(selected.toList()) }) { Text("완료 (${selected.size}/$max)") }
        }
    }
}

/** 선택 순서 번호 (선택 안 됐으면 빈 원) */
@Composable
private fun OrderBadge(order: Int) {
    val picked = order >= 0
    Box(
        Modifier
            .size(26.dp)
            .background(if (picked) Color(0xFF0A84FF) else Color(0x33FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (picked) Text("${order + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun loadApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map {
            LaunchableApp(
                packageName = it.activityInfo.packageName,
                label = it.loadLabel(pm).toString(),
                icon = it.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
            )
        }
        .sortedBy { it.label.lowercase() }
}
