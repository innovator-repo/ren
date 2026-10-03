package app.ren.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class FuriganaMode { FADE, ALWAYS, NEVER }

/**
 * Learner settings and lightweight daily state (spec §1: Proto DataStore).
 * Stored in protobuf wire format; field numbers are frozen, so only ever add new ones.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UserPreferences(
    @ProtoNumber(1) val onboarded: Boolean = false,
    @ProtoNumber(2) val startEpochDay: Long = 0,
    @ProtoNumber(3) val newUnitsPerDay: Int = 3,
    @ProtoNumber(4) val reviewGoal: Int = 30,
    @ProtoNumber(5) val minutesGoal: Int = 15,
    @ProtoNumber(6) val reminderEnabled: Boolean = true,
    @ProtoNumber(7) val reminderMinuteOfDay: Int = 20 * 60,
    @ProtoNumber(8) val showRomaji: Boolean = true,
    @ProtoNumber(9) val furigana: FuriganaMode = FuriganaMode.FADE,
    @ProtoNumber(10) val minedAudioFirst: Boolean = true,
    @ProtoNumber(11) val hapticsEnabled: Boolean = true,
    @ProtoNumber(12) val soundEnabled: Boolean = true,
    @ProtoNumber(13) val theme: ThemeMode = ThemeMode.SYSTEM,
    @ProtoNumber(14) val unlockAllStages: Boolean = false,
    @ProtoNumber(15) val streakDays: Int = 0,
    @ProtoNumber(16) val lastActiveEpochDay: Long = -1,
    @ProtoNumber(17) val streakFreezes: Int = 0,
    @ProtoNumber(18) val studyEpochDay: Long = -1,
    @ProtoNumber(19) val studyMsToday: Long = 0,
    @ProtoNumber(20) val longestStreak: Int = 0,
    @ProtoNumber(21) val speechRate: Float = 0.9f,
)
