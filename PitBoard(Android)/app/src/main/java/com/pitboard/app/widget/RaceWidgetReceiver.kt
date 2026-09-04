package com.pitboard.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 04/09/2026: un único receiver — antes había 3 (Small/Medium/Large), uno por cada
 * AppWidgetProviderInfo con un rango de tamaño fijo, porque así se hacía cuando el widget
 * solo admitía unos pocos tamaños "preset" en el selector de Samsung. Desde Android 12
 * (API 31) un solo AppWidgetProviderInfo puede declarar minWidth/minHeight hasta
 * maxResizeWidth/maxResizeHeight con resizeMode="horizontal|vertical" (ver
 * race_widget_info.xml) y el usuario arrastra las esquinas para agrandarlo/encogerlo — RaceWidget
 * ya leía el tamaño real con LocalSize.current para decidir el diseño (mini fila/hero/lista),
 * así que fusionar a un único receiver no cambia nada del contenido, solo simplifica el
 * manifest. En Android 8–11 (pre-API 31) el widget se coloca fijo al tamaño mínimo
 * declarado — sin el arrastre libre, pero funcional.
 */
class RaceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RaceWidget.instance
}
