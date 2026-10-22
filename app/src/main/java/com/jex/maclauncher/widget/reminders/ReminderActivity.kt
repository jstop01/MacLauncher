package com.jex.maclauncher.widget.reminders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jex.maclauncher.ui.theme.MacLauncherTheme

/**
 * 미리 알림 위젯 탭 처리. 완료 토글/지우기는 화면 없이 바로 끝나고,
 * 추가/수정은 입력창을 띄운다.
 */
class ReminderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_ID = "id"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_EDIT = "edit"
        const val ACTION_ADD = "add"
        const val ACTION_CLEAR = "clear"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_ID, -1)
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_TOGGLE -> return done { ReminderStore.toggle(this, id) }
            ACTION_CLEAR -> return done { ReminderStore.clearCompleted(this) }
        }
        val editing = ReminderStore.get(this, id)
        setContent {
            MacLauncherTheme {
                ReminderDialog(
                    initial = editing?.title.orEmpty(),
                    isEdit = editing != null,
                    onSave = { title ->
                        done { if (editing != null) ReminderStore.rename(this, id, title) else ReminderStore.add(this, title) }
                    },
                    onDelete = { done { ReminderStore.delete(this, id) } },
                    onCancel = ::finish,
                )
            }
        }
    }

    private fun done(change: () -> Unit) {
        change()
        RemindersWidget.updateAll(this)
        finish()
    }
}

@Composable
private fun ReminderDialog(
    initial: String,
    isEdit: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val save = { if (text.isNotBlank()) onSave(text.trim()) }
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
                .background(Color(0xFF2C2C2E), RoundedCornerShape(16.dp))
                .clickable(interactionSource = noRipple, indication = null) { /* 창 안쪽 탭은 무시 */ }
                .padding(20.dp),
        ) {
            Text(
                if (isEdit) "미리 알림 수정" else "새로운 미리 알림",
                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("할 일") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEdit) TextButton(onClick = onDelete) { Text("삭제", color = Color(0xFFFF453A)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("취소") }
                TextButton(onClick = save, enabled = text.isNotBlank()) {
                    Text(if (isEdit) "저장" else "추가", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
