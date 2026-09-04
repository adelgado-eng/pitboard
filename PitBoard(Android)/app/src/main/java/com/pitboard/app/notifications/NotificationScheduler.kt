package com.pitboard.app.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pitboard.app.data.AppSettingsRepository
import com.pitboard.app.data.EventDao
import com.pitboard.app.data.EventEntity
import com.pitboard.app.data.SessionBadgeType
import com.pitboard.app.util.SeasonWindow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class NotificationScheduler(
    private val context: Context,
    private val eventDao: EventDao,
    private val appSettingsRepository: AppSettingsRepository
) {

    suspend fun rescheduleAllUpcoming() {
        cancelAllPending()

        if (!appSettingsRepository.isNotificationsEnabledNow()) return

        val competitiveEnabled = appSettingsRepository.competitiveNotificationsEnabled.first()
        val practiceEnabled = appSettingsRepository.practiceNotificationsEnabled.first()
        val minutesBefore = appSettingsRepository.notificationMinutesBefore.first()
        val disabledSeries = appSettingsRepository.notificationDisabledSeriesNow()
        val nowUtc = System.currentTimeMillis()
        val upcoming = eventDao.getAllUpcoming(nowUtc, SeasonWindow.endOfCurrentYearUtc(nowUtc))

        upcoming.forEach { event ->
            if (event.series in disabledSeries) return@forEach

            val badge = event.inferredBadge
            val isCompetitive = badge == SessionBadgeType.RACE || badge == SessionBadgeType.QUALY || badge == SessionBadgeType.SPRINT
            val isPractice = badge == SessionBadgeType.PRACTICE

            val allowed = when {
                isCompetitive -> competitiveEnabled
                isPractice -> practiceEnabled
                else -> competitiveEnabled // por defecto igual que competitivas
            }

            if (!allowed) return@forEach

            scheduleReminder(event, minutesBefore)
        }
    }

    private fun scheduleReminder(event: EventEntity, minutesBefore: Int) {
        val reminderTimeUtc = event.startTimeUtc - minutesBefore * ONE_MINUTE_MILLIS
        val delay = reminderTimeUtc - System.currentTimeMillis()

        if (delay <= 0) return

        val inputData = Data.Builder()
            .putLong(EventReminderWorker.KEY_EVENT_ID, event.id)
            .putString(EventReminderWorker.KEY_EVENT_TITLE, event.fullTitle)
            .putInt(EventReminderWorker.KEY_MINUTES_BEFORE, minutesBefore)
            .putString(EventReminderWorker.KEY_BADGE, event.inferredBadge)
            .putLong(EventReminderWorker.KEY_START_TIME_UTC, event.startTimeUtc)
            .build()

        val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag(REMINDER_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(event.uid),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun cancelAllPending() {
        WorkManager.getInstance(context).cancelAllWorkByTag(REMINDER_TAG)
    }

    companion object {
        private const val ONE_MINUTE_MILLIS = 60 * 1000L
        private const val REMINDER_TAG = "event_reminder"
        private fun uniqueWorkName(uid: String) = "event_reminder_$uid"
    }
}