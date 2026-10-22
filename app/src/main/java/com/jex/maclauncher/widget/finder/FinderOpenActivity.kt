package com.jex.maclauncher.widget.finder

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Finder 위젯 격자 탭을 받는 중간 액티비티 (화면 없이 바로 끝남).
 * 폴더면 위젯 위치를 바꾸고, 파일이면 알맞은 앱으로 연다.
 * 위젯 목록 항목은 하나의 PendingIntent 템플릿만 쓸 수 있고, 파일을 열려면 액티비티에서 시작해야 해서 이렇게 한다.
 */
class FinderOpenActivity : Activity() {

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_IS_DIR = "is_dir"
        private const val EXTRA_PERMISSION = "permission"

        /** 격자 항목용 템플릿. 항목마다 fill-in으로 경로를 채우므로 MUTABLE */
        fun template(context: Context, widgetId: Int): PendingIntent = PendingIntent.getActivity(
            context, widgetId,
            Intent(context, FinderOpenActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** 권한 안내 탭: 이미 허용됐으면 새로고침, 아니면 설정 화면 */
        fun permission(context: Context, widgetId: Int): PendingIntent = PendingIntent.getActivity(
            context, -widgetId - 1,
            Intent(context, FinderOpenActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(EXTRA_PERMISSION, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val path = intent.getStringExtra(EXTRA_PATH)
        when {
            intent.getBooleanExtra(EXTRA_PERMISSION, false) -> requestAccess(widgetId)
            path == null -> Unit
            intent.getBooleanExtra(EXTRA_IS_DIR, false) -> FinderWidget.go(this, widgetId, path)
            else -> openFile(File(path))
        }
        finish()
    }

    private fun requestAccess(widgetId: Int) {
        if (FinderFiles.hasAccess(this)) {
            FinderWidget.refresh(this, widgetId)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 별도 태스크로: 같은 태스크에 남으면 다음 위젯 탭 때 설정 화면이 다시 앞으로 나온다
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, FinderFiles.mimeType(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        // 기본 앱이 정해져 있으면 바로, 아니면 시스템이 앱 선택 창을 띄움
        try {
            startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "이 파일을 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show()
        }
    }
}
