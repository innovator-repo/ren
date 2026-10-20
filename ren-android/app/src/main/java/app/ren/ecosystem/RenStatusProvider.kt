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

/**
 * Read-only `content://app.ren.japanese.provider/status`: one row with due, retention (0-100),
 * streak and doneToday (0/1). Guarded by the ecosystem signature permission in the manifest.
 */
class RenStatusProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (uri.lastPathSegment != "status") return null
        val context = context ?: return null
        val status = runCatching {
            val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            runBlocking {
                RenStatus.compute(
                    entry.studyDao().observeCards().first(),
                    entry.preferences().current(),
                    System.currentTimeMillis(),
                    LocalDate.now().toEpochDay(),
                )
            }
        }.getOrNull() ?: RenStatus(0, 100, 0, false)
        return MatrixCursor(COLUMNS, 1).apply {
            addRow(arrayOf<Any>(status.due, status.retention, status.streak, if (status.doneToday) 1 else 0))
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.app.ren.status"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        val COLUMNS = arrayOf("due", "retention", "streak", "doneToday")
    }
}
