package app.ren.data.prefs

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import app.ren.domain.model.UserPreferences
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalSerializationApi::class)
object UserPreferencesSerializer : Serializer<UserPreferences> {
    override val defaultValue = UserPreferences()

    override suspend fun readFrom(input: InputStream): UserPreferences = try {
        ProtoBuf.decodeFromByteArray<UserPreferences>(input.readBytes())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable preferences", e)
    }

    override suspend fun writeTo(t: UserPreferences, output: OutputStream) {
        output.write(ProtoBuf.encodeToByteArray(t))
    }
}

fun createPreferencesStore(context: Context): DataStore<UserPreferences> =
    DataStoreFactory.create(
        serializer = UserPreferencesSerializer,
        corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { UserPreferences() },
        produceFile = { context.dataStoreFile("user_prefs.pb") },
    )
