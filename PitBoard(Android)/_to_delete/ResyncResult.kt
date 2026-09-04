package com.pitboard.app.importer

data class ResyncResult(
    val calendarName: String,
    val previousCount: Int,
    val newCount: Int
) {
    fun feedbackMessage(): String = when {
        newCount == previousCount -> "«$calendarName»: sin cambios ($newCount eventos)"
        newCount > previousCount ->
            "«$calendarName»: $newCount eventos (+${newCount - previousCount} nuevos)"
        else ->
            "«$calendarName»: $newCount eventos (${previousCount - newCount} menos)"
    }
}
