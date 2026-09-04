package com.pitboard.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Un único receiver, redimensionable de extremo a extremo desde el primer día (ver el
 *  comentario en RaceWidgetReceiver.kt sobre por qué ya no hace falta uno por tamaño). */
class StandingsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StandingsWidget.instance
}
