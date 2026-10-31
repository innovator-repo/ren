package app.ren.ecosystem

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import app.ren.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId

/**
 * Read-only `content://app.ren.japanese.provider/status`: one row with due, retention (0-100),
 * streak, doneToday (0/1), newToday and minutes (estimate). Guarded by the ecosystem signature permission in the manifest.
 */
class RenStatusProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (uri.lastPathSegment != "status") return null
        val context = context ?: return null
        val status = runCatching {
            val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            runBlocking {
                val day = LocalDate.now()
                RenStatus.compute(
                    entry.studyDao().observeCards().first(),
                    entry.preferences().current(),
                    System.currentTimeMillis(),
                    day.toEpochDay(),
                    units = entry.studyDao().observeUnits().first(),
                    dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                )
            }
        }.getOrNull() ?: RenStatus(0, 100, 0, false)
        return MatrixCursor(RenStatus.COLUMNS, 1).apply {
            addRow(status.toRow())
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.app.ren.status"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
