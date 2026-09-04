package com.pitboard.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Tres receivers para las tres entradas que ahora aparecen en el selector de widgets de
 * Samsung (2×1, 2×2, 4×2) — Android exige un <receiver> distinto por cada tamaño que
 * quieras que aparezca como opción separada; no hay forma de declarar "un widget con 3
 * tamaños" en una sola entrada.
 *
 * Los tres apuntan a la MISMA clase RaceWidget (mismo contenido, misma lógica) — lo único
 * que cambia es el AppWidgetProviderInfo (race_widget_info_*.xml) que cada uno referencia
 * desde el manifest. RaceWidget decide qué diseño pintar leyendo el tamaño real con
 * LocalSize.current, así que no hace falta triplicar el composable ni la configuración.
 */
class RaceWidgetReceiverSmall : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RaceWidget.instance
}

class RaceWidgetReceiverMedium : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RaceWidget.instance
}

class RaceWidgetReceiverLarge : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RaceWidget.instance
}