package com.jex.maclauncher.widget.finder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jex.maclauncher.ui.AppPicker
import com.jex.maclauncher.ui.theme.MacLauncherTheme
import java.util.UUID

/**
 * Finder '즐겨찾는 앱' 편집: 맨 위 앱 고르기, 폴더 만들기/이름·색 바꾸기/삭제, 폴더별 앱 고르기.
 * 폴더 id를 받고 열리면 그 폴더의 앱 고르기부터 보여준다.
 */
class FavoritesActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_FOLDER = "folder"

        fun intent(context: Context, folderId: String?): Intent =
            Intent(context, FavoritesActivity::class.java)
                .putExtra(EXTRA_FOLDER, folderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val folderId = intent.getStringExtra(EXTRA_FOLDER)
        enableEdgeToEdge()
        setContent {
            MacLauncherTheme {
                FavoritesEditor(startPicking = folderId?.let { PickTarget.Folder(it) }, closeAfterPick = folderId != null, onClose = ::finish)
            }
        }
    }
}

private sealed interface PickTarget {
    data object Root : PickTarget
    data class Folder(val id: String) : PickTarget
}

@Composable
private fun FavoritesEditor(startPicking: PickTarget?, closeAfterPick: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    var favorites by remember { mutableStateOf(FavoritesStore.load(context)) }
    var picking by remember { mutableStateOf(startPicking) }
    var editing by remember { mutableStateOf<FavoriteFolder?>(null) }   // 새 폴더도 여기로

    fun update(new: Favorites) {
        favorites = new
        FavoritesStore.save(context, new)
    }
    fun updateFolder(id: String, change: (FavoriteFolder) -> FavoriteFolder) =
        update(favorites.copy(folders = favorites.folders.map { if (it.id == id) change(it) else it }))

    picking?.let { target ->
        val folder = (target as? PickTarget.Folder)?.let { t -> favorites.folders.firstOrNull { it.id == t.id } }
        val done = { packages: List<String> ->
            if (folder != null) updateFolder(folder.id) { it.copy(apps = packages) }
            else update(favorites.copy(apps = packages))
            if (closeAfterPick) onClose() else picking = null
        }
        BackHandler { if (closeAfterPick) onClose() else picking = null }
        AppPicker(
            title = if (folder != null) "'${folder.name}'에 넣을 앱" else "맨 위에 둘 앱",
            subtitle = "누른 순서대로 놓여요 · 최대 ${FavoritesStore.MAX_APPS}개",
            max = FavoritesStore.MAX_APPS,
            initial = folder?.apps ?: favorites.apps,
            onDone = done,
        )
        return
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Text("즐겨찾는 앱", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Finder 위젯의 '즐겨찾는 앱'에 보여요. 폴더로 나눠 담을 수 있어요.", color = Sub, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))

            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("맨 위에 둘 앱", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("앱 ${favorites.apps.size}개", color = Sub, fontSize = 13.sp)
                    }
                    Button(onClick = { picking = PickTarget.Root }) { Text("앱 고르기") }
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("폴더", color = Sub, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            Panel {
                if (favorites.folders.isEmpty()) {
                    Text("아직 폴더가 없어요", color = Sub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
                favorites.folders.forEach { folder ->
                    Row(
                        Modifier.fillMaxWidth().clickable { picking = PickTarget.Folder(folder.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(painterResource(folder.color.icon), contentDescription = null, modifier = Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.name, color = Color.White, fontSize = 15.sp)
                            Text("앱 ${folder.apps.size}개 · 눌러서 앱 넣기", color = Sub, fontSize = 12.sp)
                        }
                        TextButton(onClick = { editing = folder }) { Text("이름·색") }
                        TextButton(onClick = { update(favorites.copy(folders = favorites.folders - folder)) }) {
                            Text("삭제", color = Color(0xFFFF453A))
                        }
                    }
                }
                TextButton(onClick = { editing = FavoriteFolder(UUID.randomUUID().toString(), "") }) { Text("+ 새 폴더") }
            }
        }
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Button(onClick = onClose) { Text("완료") }
        }
    }

    editing?.let { folder ->
        val isNew = favorites.folders.none { it.id == folder.id }
        FolderDialog(
            initial = folder,
            isNew = isNew,
            onDismiss = { editing = null },
            onConfirm = { name, color ->
                if (isNew) update(favorites.copy(folders = favorites.folders + folder.copy(name = name, color = color)))
                else updateFolder(folder.id) { it.copy(name = name, color = color) }
                editing = null
            },
        )
    }
}

private val Sub = Color(0x99FFFFFF)

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF1E1E20), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
    ) { content() }
}

/** 폴더 이름 + 색 (macOS 태그 색 7가지) */
@Composable
private fun FolderDialog(initial: FavoriteFolder, isNew: Boolean, onDismiss: () -> Unit, onConfirm: (String, FolderColor) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableStateOf(initial.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "새 폴더" else "폴더 이름·색") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("폴더 이름") })
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FolderColor.entries.forEach { c ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .then(if (c == color) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                .padding(4.dp)
                                .background(Color(c.argb), CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(color.label, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim(), color) }, enabled = name.isNotBlank()) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
