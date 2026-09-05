package com.pitboard.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Catálogo de textos traducidos, por clave — FASE 1 de la internacionalización (04/09/2026):
 * cubre el selector de idioma de primer arranque y la pantalla de Ajustes, como prueba de que
 * el mecanismo entero funciona de extremo a extremo. El resto de pantallas (Eventos,
 * Clasificaciones...) sigue con texto fijo en español por ahora — se migran en fases
 * siguientes añadiendo más entradas aquí y cambiando cada `Text("literal")` por `Text(tr("clave"))`
 * en la pantalla correspondiente, exactamente igual que aquí.
 *
 * Se usa un mapa propio en vez de los `strings.xml` por idioma de Android a propósito: el
 * usuario elige el idioma A MANO la primera vez (no tiene por qué coincidir con el idioma del
 * sistema, ver LanguagePickerScreen), y aplicar eso con los recursos nativos de Android exige
 * o bien AppCompatDelegate.setApplicationLocales (una dependencia nueva, AppCompat, en una app
 * que hasta ahora es 100% Compose sin ella) o recrear la Activity a mano con un Context
 * reconfigurado — ambas más frágiles de verificar en este entorno (sin emulador) que un mapa
 * Kotlin plano, que se comprueba con un test unitario normal y corriente.
 */
object Strings {

    private val entries: Map<String, Map<AppLanguage, String>> = mapOf(
        // --- Selector de idioma (primer arranque) ---
        "language_picker_title" to mapOf(
            AppLanguage.SPANISH to "Elige tu idioma",
            AppLanguage.ENGLISH to "Choose your language",
            AppLanguage.CATALAN to "Tria el teu idioma",
            AppLanguage.FRENCH to "Choisissez votre langue",
            AppLanguage.GERMAN to "Wähle deine Sprache"
        ),
        "language_picker_subtitle" to mapOf(
            AppLanguage.SPANISH to "Podrás cambiarlo luego desde Ajustes.",
            AppLanguage.ENGLISH to "You can change it later in Settings.",
            AppLanguage.CATALAN to "Podràs canviar-lo després des de Configuració.",
            AppLanguage.FRENCH to "Vous pourrez le modifier plus tard dans les Paramètres.",
            AppLanguage.GERMAN to "Du kannst sie später in den Einstellungen ändern."
        ),
        "language_picker_continue" to mapOf(
            AppLanguage.SPANISH to "Continuar",
            AppLanguage.ENGLISH to "Continue",
            AppLanguage.CATALAN to "Continua",
            AppLanguage.FRENCH to "Continuer",
            AppLanguage.GERMAN to "Weiter"
        ),

        // --- Ajustes: general ---
        "settings_title" to mapOf(
            AppLanguage.SPANISH to "Ajustes",
            AppLanguage.ENGLISH to "Settings",
            AppLanguage.CATALAN to "Configuració",
            AppLanguage.FRENCH to "Paramètres",
            AppLanguage.GERMAN to "Einstellungen"
        ),
        "settings_footer" to mapOf(
            AppLanguage.SPANISH to "PitBoard v0.1.0\n🏁 Hecho para fans del motor",
            AppLanguage.ENGLISH to "PitBoard v0.1.0\n🏁 Made for motorsport fans",
            AppLanguage.CATALAN to "PitBoard v0.1.0\n🏁 Fet per a fans del motor",
            AppLanguage.FRENCH to "PitBoard v0.1.0\n🏁 Fait pour les fans de sport automobile",
            AppLanguage.GERMAN to "PitBoard v0.1.0\n🏁 Gemacht für Motorsport-Fans"
        ),

        // --- Ajustes: Notificaciones ---
        "settings_notifications_title" to mapOf(
            AppLanguage.SPANISH to "Notificaciones",
            AppLanguage.ENGLISH to "Notifications",
            AppLanguage.CATALAN to "Notificacions",
            AppLanguage.FRENCH to "Notifications",
            AppLanguage.GERMAN to "Benachrichtigungen"
        ),
        "settings_notifications_enable" to mapOf(
            AppLanguage.SPANISH to "Activar avisos",
            AppLanguage.ENGLISH to "Enable alerts",
            AppLanguage.CATALAN to "Activa els avisos",
            AppLanguage.FRENCH to "Activer les alertes",
            AppLanguage.GERMAN to "Erinnerungen aktivieren"
        ),
        "settings_notifications_subtitle" to mapOf(
            AppLanguage.SPANISH to "Avisar antes de las sesiones",
            AppLanguage.ENGLISH to "Get notified before sessions",
            AppLanguage.CATALAN to "Avisa abans de les sessions",
            AppLanguage.FRENCH to "Être averti avant les sessions",
            AppLanguage.GERMAN to "Vor Sessions benachrichtigen"
        ),
        "settings_notifications_when" to mapOf(
            AppLanguage.SPANISH to "¿Cuándo avisar?",
            AppLanguage.ENGLISH to "When to notify?",
            AppLanguage.CATALAN to "Quan avisar?",
            AppLanguage.FRENCH to "Quand prévenir ?",
            AppLanguage.GERMAN to "Wann benachrichtigen?"
        ),
        "settings_notifications_which_sessions" to mapOf(
            AppLanguage.SPANISH to "¿Qué sesiones?",
            AppLanguage.ENGLISH to "Which sessions?",
            AppLanguage.CATALAN to "Quines sessions?",
            AppLanguage.FRENCH to "Quelles sessions ?",
            AppLanguage.GERMAN to "Welche Sessions?"
        ),
        "settings_notifications_competitive" to mapOf(
            AppLanguage.SPANISH to "Competición (Carrera, Clasif., Sprint)",
            AppLanguage.ENGLISH to "Competitive (Race, Qualifying, Sprint)",
            AppLanguage.CATALAN to "Competició (Cursa, Classificació, Sprint)",
            AppLanguage.FRENCH to "Compétition (Course, Qualification, Sprint)",
            AppLanguage.GERMAN to "Wettbewerb (Rennen, Qualifying, Sprint)"
        ),
        "settings_notifications_practice" to mapOf(
            AppLanguage.SPANISH to "Entrenamientos (Libres)",
            AppLanguage.ENGLISH to "Practice sessions",
            AppLanguage.CATALAN to "Entrenaments (Lliures)",
            AppLanguage.FRENCH to "Essais libres",
            AppLanguage.GERMAN to "Trainings (Freies Training)"
        ),
        "settings_notifications_active_series" to mapOf(
            AppLanguage.SPANISH to "Series activas",
            AppLanguage.ENGLISH to "Active series",
            AppLanguage.CATALAN to "Sèries actives",
            AppLanguage.FRENCH to "Séries actives",
            AppLanguage.GERMAN to "Aktive Serien"
        ),
        "settings_notifications_all_series" to mapOf(
            AppLanguage.SPANISH to "Todas las series",
            AppLanguage.ENGLISH to "All series",
            AppLanguage.CATALAN to "Totes les sèries",
            AppLanguage.FRENCH to "Toutes les séries",
            AppLanguage.GERMAN to "Alle Serien"
        ),
        "settings_notifications_series_count" to mapOf(
            AppLanguage.SPANISH to "%d series seleccionadas",
            AppLanguage.ENGLISH to "%d series selected",
            AppLanguage.CATALAN to "%d sèries seleccionades",
            AppLanguage.FRENCH to "%d séries sélectionnées",
            AppLanguage.GERMAN to "%d Serien ausgewählt"
        ),
        "settings_notifications_permission_title" to mapOf(
            AppLanguage.SPANISH to "Permiso de notificaciones",
            AppLanguage.ENGLISH to "Notification permission",
            AppLanguage.CATALAN to "Permís de notificacions",
            AppLanguage.FRENCH to "Autorisation de notifications",
            AppLanguage.GERMAN to "Benachrichtigungsberechtigung"
        ),
        "settings_notifications_permission_body" to mapOf(
            AppLanguage.SPANISH to "Android tiene bloqueados los avisos de PitBoard, así que la app ya no puede volver a pedirte el permiso desde aquí. Abre los ajustes del sistema y activa las notificaciones para PitBoard.",
            AppLanguage.ENGLISH to "Android has PitBoard's alerts blocked, so the app can no longer ask you for permission from here. Open the system settings and turn notifications on for PitBoard.",
            AppLanguage.CATALAN to "Android té bloquejats els avisos de PitBoard, així que l'app ja no pot tornar a demanar-te el permís des d'aquí. Obre la configuració del sistema i activa les notificacions per a PitBoard.",
            AppLanguage.FRENCH to "Android a bloqué les alertes de PitBoard, l'application ne peut donc plus vous demander l'autorisation depuis ici. Ouvrez les paramètres système et activez les notifications pour PitBoard.",
            AppLanguage.GERMAN to "Android hat die Erinnerungen von PitBoard blockiert, daher kann die App die Berechtigung nicht mehr von hier aus anfordern. Öffne die Systemeinstellungen und aktiviere Benachrichtigungen für PitBoard."
        ),
        "settings_open_settings" to mapOf(
            AppLanguage.SPANISH to "Abrir ajustes",
            AppLanguage.ENGLISH to "Open settings",
            AppLanguage.CATALAN to "Obre la configuració",
            AppLanguage.FRENCH to "Ouvrir les paramètres",
            AppLanguage.GERMAN to "Einstellungen öffnen"
        ),
        "settings_not_now" to mapOf(
            AppLanguage.SPANISH to "Ahora no",
            AppLanguage.ENGLISH to "Not now",
            AppLanguage.CATALAN to "Ara no",
            AppLanguage.FRENCH to "Pas maintenant",
            AppLanguage.GERMAN to "Jetzt nicht"
        ),
        "settings_series_with_alerts_title" to mapOf(
            AppLanguage.SPANISH to "Series con avisos",
            AppLanguage.ENGLISH to "Series with alerts",
            AppLanguage.CATALAN to "Sèries amb avisos",
            AppLanguage.FRENCH to "Séries avec alertes",
            AppLanguage.GERMAN to "Serien mit Erinnerungen"
        ),
        "settings_series_with_alerts_subtitle" to mapOf(
            AppLanguage.SPANISH to "Desactiva aquellas series de las que no quieras recibir notificaciones.",
            AppLanguage.ENGLISH to "Turn off any series you don't want notifications for.",
            AppLanguage.CATALAN to "Desactiva les sèries de les quals no vulguis rebre notificacions.",
            AppLanguage.FRENCH to "Désactivez les séries pour lesquelles vous ne voulez pas de notifications.",
            AppLanguage.GERMAN to "Deaktiviere die Serien, für die du keine Benachrichtigungen möchtest."
        ),
        "settings_done" to mapOf(
            AppLanguage.SPANISH to "Listo",
            AppLanguage.ENGLISH to "Done",
            AppLanguage.CATALAN to "Fet",
            AppLanguage.FRENCH to "Terminé",
            AppLanguage.GERMAN to "Fertig"
        ),

        // --- Ajustes: Clasificaciones ---
        "settings_standings_title" to mapOf(
            AppLanguage.SPANISH to "Clasificaciones",
            AppLanguage.ENGLISH to "Standings",
            AppLanguage.CATALAN to "Classificacions",
            AppLanguage.FRENCH to "Classements",
            AppLanguage.GERMAN to "Wertungen"
        ),
        "settings_standings_enable" to mapOf(
            AppLanguage.SPANISH to "Activar clasificaciones",
            AppLanguage.ENGLISH to "Enable standings",
            AppLanguage.CATALAN to "Activa les classificacions",
            AppLanguage.FRENCH to "Activer les classements",
            AppLanguage.GERMAN to "Wertungen aktivieren"
        ),
        "settings_standings_subtitle" to mapOf(
            AppLanguage.SPANISH to "F1, MotoGP, NASCAR y más. Al activarlas aparece su pestaña en la barra de abajo; se actualizan cada lunes a las 12:00 y necesitan wifi o datos móviles.",
            AppLanguage.ENGLISH to "F1, MotoGP, NASCAR and more. Turning this on adds its tab to the bottom bar; it updates every Monday at 12:00 and needs Wi-Fi or mobile data.",
            AppLanguage.CATALAN to "F1, MotoGP, NASCAR i més. En activar-les apareix la seva pestanya a la barra inferior; s'actualitzen cada dilluns a les 12:00 i necessiten wifi o dades mòbils.",
            AppLanguage.FRENCH to "F1, MotoGP, NASCAR et plus. En l'activant, son onglet apparaît dans la barre du bas ; mise à jour chaque lundi à 12h00, Wi-Fi ou données mobiles nécessaires.",
            AppLanguage.GERMAN to "F1, MotoGP, NASCAR und mehr. Beim Aktivieren erscheint der Tab in der unteren Leiste; Aktualisierung jeden Montag um 12:00 Uhr, WLAN oder mobile Daten nötig."
        ),

        // --- Ajustes: Apariencia ---
        "settings_appearance_title" to mapOf(
            AppLanguage.SPANISH to "Apariencia",
            AppLanguage.ENGLISH to "Appearance",
            AppLanguage.CATALAN to "Aparença",
            AppLanguage.FRENCH to "Apparence",
            AppLanguage.GERMAN to "Erscheinungsbild"
        ),
        "settings_appearance_subtitle" to mapOf(
            AppLanguage.SPANISH to "Elige el tema de la aplicación",
            AppLanguage.ENGLISH to "Choose the app's theme",
            AppLanguage.CATALAN to "Tria el tema de l'aplicació",
            AppLanguage.FRENCH to "Choisissez le thème de l'application",
            AppLanguage.GERMAN to "Wähle das App-Design"
        ),
        "settings_theme_light" to mapOf(
            AppLanguage.SPANISH to "☀️ Claro",
            AppLanguage.ENGLISH to "☀️ Light",
            AppLanguage.CATALAN to "☀️ Clar",
            AppLanguage.FRENCH to "☀️ Clair",
            AppLanguage.GERMAN to "☀️ Hell"
        ),
        "settings_theme_dark" to mapOf(
            AppLanguage.SPANISH to "🌙 Oscuro",
            AppLanguage.ENGLISH to "🌙 Dark",
            AppLanguage.CATALAN to "🌙 Fosc",
            AppLanguage.FRENCH to "🌙 Sombre",
            AppLanguage.GERMAN to "🌙 Dunkel"
        ),
        "settings_theme_auto" to mapOf(
            AppLanguage.SPANISH to "📱 Auto",
            AppLanguage.ENGLISH to "📱 Auto",
            AppLanguage.CATALAN to "📱 Auto",
            AppLanguage.FRENCH to "📱 Auto",
            AppLanguage.GERMAN to "📱 Auto"
        ),

        // --- Ajustes: Zona horaria ---
        "settings_timezone_title" to mapOf(
            AppLanguage.SPANISH to "Zona horaria",
            AppLanguage.ENGLISH to "Time zone",
            AppLanguage.CATALAN to "Fus horari",
            AppLanguage.FRENCH to "Fuseau horaire",
            AppLanguage.GERMAN to "Zeitzone"
        ),
        "settings_timezone_subtitle" to mapOf(
            AppLanguage.SPANISH to "Qué hora se ve de un vistazo en la lista de Eventos. El detalle de cada evento (al tocarlo) siempre enseña las dos.",
            AppLanguage.ENGLISH to "Which time shows at a glance in the Events list. Each event's detail (when tapped) always shows both.",
            AppLanguage.CATALAN to "Quina hora es veu d'una ullada a la llista d'Esdeveniments. El detall de cada esdeveniment (en tocar-lo) sempre mostra les dues.",
            AppLanguage.FRENCH to "Quelle heure s'affiche en un coup d'œil dans la liste des Événements. Le détail de chaque événement (au toucher) affiche toujours les deux.",
            AppLanguage.GERMAN to "Welche Uhrzeit auf einen Blick in der Ereignisliste angezeigt wird. Die Details jedes Ereignisses (beim Antippen) zeigen immer beide."
        ),
        "settings_timezone_device" to mapOf(
            AppLanguage.SPANISH to "Mi hora",
            AppLanguage.ENGLISH to "My time",
            AppLanguage.CATALAN to "La meva hora",
            AppLanguage.FRENCH to "Mon heure",
            AppLanguage.GERMAN to "Meine Zeit"
        ),
        "settings_timezone_track" to mapOf(
            AppLanguage.SPANISH to "Hora del circuito",
            AppLanguage.ENGLISH to "Track time",
            AppLanguage.CATALAN to "Hora del circuit",
            AppLanguage.FRENCH to "Heure du circuit",
            AppLanguage.GERMAN to "Streckenzeit"
        ),

        // --- Ajustes: ayuda (dontkillmyapp) ---
        "settings_battery_help_title" to mapOf(
            AppLanguage.SPANISH to "¿Los avisos o el widget no van?",
            AppLanguage.ENGLISH to "Alerts or widget not working?",
            AppLanguage.CATALAN to "Els avisos o el widget no van?",
            AppLanguage.FRENCH to "Les alertes ou le widget ne fonctionnent pas ?",
            AppLanguage.GERMAN to "Erinnerungen oder Widget funktionieren nicht?"
        ),
        "settings_battery_help_subtitle" to mapOf(
            AppLanguage.SPANISH to "Muchos fabricantes (Samsung, Xiaomi, Huawei...) matan la app en segundo plano para ahorrar batería, y eso puede retrasar avisos y el widget. dontkillmyapp.com explica, paso a paso para tu marca de móvil, cómo evitarlo.",
            AppLanguage.ENGLISH to "Many manufacturers (Samsung, Xiaomi, Huawei...) kill the app in the background to save battery, which can delay alerts and the widget. dontkillmyapp.com explains, step by step for your phone brand, how to avoid it.",
            AppLanguage.CATALAN to "Molts fabricants (Samsung, Xiaomi, Huawei...) maten l'app en segon pla per estalviar bateria, i això pot retardar els avisos i el widget. dontkillmyapp.com explica, pas a pas per a la teva marca de mòbil, com evitar-ho.",
            AppLanguage.FRENCH to "De nombreux fabricants (Samsung, Xiaomi, Huawei...) tuent l'application en arrière-plan pour économiser la batterie, ce qui peut retarder les alertes et le widget. dontkillmyapp.com explique, étape par étape pour votre marque de téléphone, comment l'éviter.",
            AppLanguage.GERMAN to "Viele Hersteller (Samsung, Xiaomi, Huawei...) beenden die App im Hintergrund, um Akku zu sparen, was Erinnerungen und das Widget verzögern kann. dontkillmyapp.com erklärt Schritt für Schritt für deine Handymarke, wie du das vermeidest."
        ),
        "settings_battery_help_button" to mapOf(
            AppLanguage.SPANISH to "Abrir dontkillmyapp.com",
            AppLanguage.ENGLISH to "Open dontkillmyapp.com",
            AppLanguage.CATALAN to "Obre dontkillmyapp.com",
            AppLanguage.FRENCH to "Ouvrir dontkillmyapp.com",
            AppLanguage.GERMAN to "dontkillmyapp.com öffnen"
        ),

        // --- FASE 2 (04/09/2026): pantalla de Eventos ---
        "events_title" to mapOf(
            AppLanguage.SPANISH to "Eventos",
            AppLanguage.ENGLISH to "Events",
            AppLanguage.CATALAN to "Esdeveniments",
            AppLanguage.FRENCH to "Événements",
            AppLanguage.GERMAN to "Termine"
        ),
        "events_search_placeholder" to mapOf(
            AppLanguage.SPANISH to "Buscar por palabra clave…",
            AppLanguage.ENGLISH to "Search by keyword…",
            AppLanguage.CATALAN to "Cerca per paraula clau…",
            AppLanguage.FRENCH to "Rechercher par mot-clé…",
            AppLanguage.GERMAN to "Nach Stichwort suchen…"
        ),
        "events_clear_search" to mapOf(
            AppLanguage.SPANISH to "Borrar búsqueda",
            AppLanguage.ENGLISH to "Clear search",
            AppLanguage.CATALAN to "Esborra la cerca",
            AppLanguage.FRENCH to "Effacer la recherche",
            AppLanguage.GERMAN to "Suche löschen"
        ),
        "events_filter_all_series" to mapOf(
            AppLanguage.SPANISH to "Todas",
            AppLanguage.ENGLISH to "All",
            AppLanguage.CATALAN to "Totes",
            AppLanguage.FRENCH to "Toutes",
            AppLanguage.GERMAN to "Alle"
        ),
        "events_filter_all_sessions" to mapOf(
            AppLanguage.SPANISH to "Todas las sesiones",
            AppLanguage.ENGLISH to "All sessions",
            AppLanguage.CATALAN to "Totes les sessions",
            AppLanguage.FRENCH to "Toutes les sessions",
            AppLanguage.GERMAN to "Alle Sessions"
        ),
        "events_empty_filtered_title" to mapOf(
            AppLanguage.SPANISH to "Ningún evento de estas series",
            AppLanguage.ENGLISH to "No events for these series",
            AppLanguage.CATALAN to "Cap esdeveniment d'aquestes sèries",
            AppLanguage.FRENCH to "Aucun événement pour ces séries",
            AppLanguage.GERMAN to "Keine Termine für diese Serien"
        ),
        "events_empty_filtered_message" to mapOf(
            AppLanguage.SPANISH to "No hay eventos guardados para las series que has elegido. Prueba a quitar el filtro o a actualizar.",
            AppLanguage.ENGLISH to "No events saved for the series you picked. Try removing the filter or refreshing.",
            AppLanguage.CATALAN to "No hi ha esdeveniments guardats per a les sèries que has triat. Prova de treure el filtre o d'actualitzar.",
            AppLanguage.FRENCH to "Aucun événement enregistré pour les séries choisies. Essayez de retirer le filtre ou d'actualiser.",
            AppLanguage.GERMAN to "Keine Termine für die ausgewählten Serien gespeichert. Versuche, den Filter zu entfernen oder zu aktualisieren."
        ),
        "events_remove_filter" to mapOf(
            AppLanguage.SPANISH to "Quitar filtro",
            AppLanguage.ENGLISH to "Remove filter",
            AppLanguage.CATALAN to "Treu el filtre",
            AppLanguage.FRENCH to "Retirer le filtre",
            AppLanguage.GERMAN to "Filter entfernen"
        ),
        "events_refresh" to mapOf(
            AppLanguage.SPANISH to "Actualizar",
            AppLanguage.ENGLISH to "Refresh",
            AppLanguage.CATALAN to "Actualitza",
            AppLanguage.FRENCH to "Actualiser",
            AppLanguage.GERMAN to "Aktualisieren"
        ),
        "events_empty_offline_title" to mapOf(
            AppLanguage.SPANISH to "Necesitas conexión",
            AppLanguage.ENGLISH to "You need a connection",
            AppLanguage.CATALAN to "Necessites connexió",
            AppLanguage.FRENCH to "Connexion nécessaire",
            AppLanguage.GERMAN to "Verbindung erforderlich"
        ),
        "events_empty_offline_message" to mapOf(
            AppLanguage.SPANISH to "Todavía no se ha guardado ningún evento en este dispositivo. Conéctate a wifi o datos móviles al menos una vez.",
            AppLanguage.ENGLISH to "No events have been saved on this device yet. Connect to Wi-Fi or mobile data at least once.",
            AppLanguage.CATALAN to "Encara no s'ha guardat cap esdeveniment en aquest dispositiu. Connecta't a wifi o dades mòbils almenys una vegada.",
            AppLanguage.FRENCH to "Aucun événement n'a encore été enregistré sur cet appareil. Connectez-vous au Wi-Fi ou aux données mobiles au moins une fois.",
            AppLanguage.GERMAN to "Auf diesem Gerät wurden noch keine Termine gespeichert. Verbinde dich mindestens einmal mit WLAN oder mobilen Daten."
        ),
        "events_empty_title" to mapOf(
            AppLanguage.SPANISH to "Sin eventos",
            AppLanguage.ENGLISH to "No events",
            AppLanguage.CATALAN to "Sense esdeveniments",
            AppLanguage.FRENCH to "Aucun événement",
            AppLanguage.GERMAN to "Keine Termine"
        ),
        "events_empty_message" to mapOf(
            AppLanguage.SPANISH to "Todavía no se ha sincronizado ningún calendario. Prueba a tocar Actualizar.",
            AppLanguage.ENGLISH to "No calendar has synced yet. Try tapping Refresh.",
            AppLanguage.CATALAN to "Encara no s'ha sincronitzat cap calendari. Prova de tocar Actualitza.",
            AppLanguage.FRENCH to "Aucun calendrier n'a encore été synchronisé. Essayez d'appuyer sur Actualiser.",
            AppLanguage.GERMAN to "Es wurde noch kein Kalender synchronisiert. Tippe auf Aktualisieren."
        ),
        "events_empty_no_match_title" to mapOf(
            AppLanguage.SPANISH to "Ningún evento coincide",
            AppLanguage.ENGLISH to "No events match",
            AppLanguage.CATALAN to "Cap esdeveniment coincideix",
            AppLanguage.FRENCH to "Aucun événement ne correspond",
            AppLanguage.GERMAN to "Keine Treffer"
        ),
        "events_empty_no_match_message" to mapOf(
            AppLanguage.SPANISH to "Prueba con otra palabra clave, serie o tipo de sesión.",
            AppLanguage.ENGLISH to "Try another keyword, series or session type.",
            AppLanguage.CATALAN to "Prova amb una altra paraula clau, sèrie o tipus de sessió.",
            AppLanguage.FRENCH to "Essayez un autre mot-clé, une autre série ou un autre type de session.",
            AppLanguage.GERMAN to "Versuche es mit einem anderen Stichwort, einer anderen Serie oder Session-Art."
        ),
        "events_clear_search_and_filters" to mapOf(
            AppLanguage.SPANISH to "Borrar búsqueda y filtros",
            AppLanguage.ENGLISH to "Clear search and filters",
            AppLanguage.CATALAN to "Esborra la cerca i els filtres",
            AppLanguage.FRENCH to "Effacer la recherche et les filtres",
            AppLanguage.GERMAN to "Suche und Filter löschen"
        ),
        "events_later_section" to mapOf(
            AppLanguage.SPANISH to "MÁS ADELANTE",
            AppLanguage.ENGLISH to "LATER",
            AppLanguage.CATALAN to "MÉS ENDAVANT",
            AppLanguage.FRENCH to "PLUS TARD",
            AppLanguage.GERMAN to "SPÄTER"
        ),
        "events_search_and_filter" to mapOf(
            AppLanguage.SPANISH to "Buscar y filtrar",
            AppLanguage.ENGLISH to "Search and filter",
            AppLanguage.CATALAN to "Cerca i filtra",
            AppLanguage.FRENCH to "Rechercher et filtrer",
            AppLanguage.GERMAN to "Suchen und filtern"
        ),
        "events_edit_series" to mapOf(
            AppLanguage.SPANISH to "Editar series",
            AppLanguage.ENGLISH to "Edit series",
            AppLanguage.CATALAN to "Edita les sèries",
            AppLanguage.FRENCH to "Modifier les séries",
            AppLanguage.GERMAN to "Serien bearbeiten"
        ),
        "events_offline_toast" to mapOf(
            AppLanguage.SPANISH to "Sin conexión — no se puede actualizar ahora",
            AppLanguage.ENGLISH to "No connection — can't refresh right now",
            AppLanguage.CATALAN to "Sense connexió — ara mateix no es pot actualitzar",
            AppLanguage.FRENCH to "Pas de connexion — impossible d'actualiser maintenant",
            AppLanguage.GERMAN to "Keine Verbindung — jetzt nicht aktualisierbar"
        ),
        "events_edit_series_subtitle" to mapOf(
            AppLanguage.SPANISH to "Toca una serie para cambiar sus iniciales o su color.",
            AppLanguage.ENGLISH to "Tap a series to change its initials or color.",
            AppLanguage.CATALAN to "Toca una sèrie per canviar-ne les inicials o el color.",
            AppLanguage.FRENCH to "Touchez une série pour changer ses initiales ou sa couleur.",
            AppLanguage.GERMAN to "Tippe auf eine Serie, um Kürzel oder Farbe zu ändern."
        ),
        "events_tag_label" to mapOf(
            AppLanguage.SPANISH to "Tag corto (máx. 5)",
            AppLanguage.ENGLISH to "Short tag (max. 5)",
            AppLanguage.CATALAN to "Etiqueta curta (màx. 5)",
            AppLanguage.FRENCH to "Étiquette courte (max. 5)",
            AppLanguage.GERMAN to "Kurzes Tag (max. 5)"
        ),
        "events_color_label" to mapOf(
            AppLanguage.SPANISH to "Color (#RRGGBB)",
            AppLanguage.ENGLISH to "Color (#RRGGBB)",
            AppLanguage.CATALAN to "Color (#RRGGBB)",
            AppLanguage.FRENCH to "Couleur (#RRGGBB)",
            AppLanguage.GERMAN to "Farbe (#RRGGBB)"
        ),
        "events_preview_label" to mapOf(
            AppLanguage.SPANISH to "Vista previa: ",
            AppLanguage.ENGLISH to "Preview: ",
            AppLanguage.CATALAN to "Vista prèvia: ",
            AppLanguage.FRENCH to "Aperçu : ",
            AppLanguage.GERMAN to "Vorschau: "
        ),
        "events_save" to mapOf(
            AppLanguage.SPANISH to "Guardar",
            AppLanguage.ENGLISH to "Save",
            AppLanguage.CATALAN to "Desa",
            AppLanguage.FRENCH to "Enregistrer",
            AppLanguage.GERMAN to "Speichern"
        ),
        "events_cancel" to mapOf(
            AppLanguage.SPANISH to "Cancelar",
            AppLanguage.ENGLISH to "Cancel",
            AppLanguage.CATALAN to "Cancel·la",
            AppLanguage.FRENCH to "Annuler",
            AppLanguage.GERMAN to "Abbrechen"
        ),
        "events_close" to mapOf(
            AppLanguage.SPANISH to "Cerrar",
            AppLanguage.ENGLISH to "Close",
            AppLanguage.CATALAN to "Tanca",
            AppLanguage.FRENCH to "Fermer",
            AppLanguage.GERMAN to "Schließen"
        ),
        "events_detail_your_time" to mapOf(
            AppLanguage.SPANISH to "Tu hora local",
            AppLanguage.ENGLISH to "Your local time",
            AppLanguage.CATALAN to "La teva hora local",
            AppLanguage.FRENCH to "Votre heure locale",
            AppLanguage.GERMAN to "Deine Ortszeit"
        ),
        "events_detail_track_time" to mapOf(
            AppLanguage.SPANISH to "Hora local del circuito (%s)",
            AppLanguage.ENGLISH to "Local track time (%s)",
            AppLanguage.CATALAN to "Hora local del circuit (%s)",
            AppLanguage.FRENCH to "Heure locale du circuit (%s)",
            AppLanguage.GERMAN to "Lokale Streckenzeit (%s)"
        ),
        "events_detail_series" to mapOf(
            AppLanguage.SPANISH to "Serie",
            AppLanguage.ENGLISH to "Series",
            AppLanguage.CATALAN to "Sèrie",
            AppLanguage.FRENCH to "Série",
            AppLanguage.GERMAN to "Serie"
        ),
        "events_detail_session_type" to mapOf(
            AppLanguage.SPANISH to "Tipo de sesión",
            AppLanguage.ENGLISH to "Session type",
            AppLanguage.CATALAN to "Tipus de sessió",
            AppLanguage.FRENCH to "Type de session",
            AppLanguage.GERMAN to "Session-Art"
        ),
        "events_detail_weather" to mapOf(
            AppLanguage.SPANISH to "Clima en el circuito",
            AppLanguage.ENGLISH to "Weather at the track",
            AppLanguage.CATALAN to "Clima al circuit",
            AppLanguage.FRENCH to "Météo sur le circuit",
            AppLanguage.GERMAN to "Wetter an der Strecke"
        ),
        "events_detail_weather_value" to mapOf(
            AppLanguage.SPANISH to "%d°C · %d%% de probabilidad de lluvia",
            AppLanguage.ENGLISH to "%d°C · %d%% chance of rain",
            AppLanguage.CATALAN to "%d°C · %d%% de probabilitat de pluja",
            AppLanguage.FRENCH to "%d°C · %d%% de probabilité de pluie",
            AppLanguage.GERMAN to "%d°C · %d%% Regenwahrscheinlichkeit"
        ),
        "events_weekend_today" to mapOf(
            AppLanguage.SPANISH to "Hoy",
            AppLanguage.ENGLISH to "Today",
            AppLanguage.CATALAN to "Avui",
            AppLanguage.FRENCH to "Aujourd'hui",
            AppLanguage.GERMAN to "Heute"
        ),
        "events_weekend_this" to mapOf(
            AppLanguage.SPANISH to "Este fin de semana",
            AppLanguage.ENGLISH to "This weekend",
            AppLanguage.CATALAN to "Aquest cap de setmana",
            AppLanguage.FRENCH to "Ce week-end",
            AppLanguage.GERMAN to "Dieses Wochenende"
        ),
        "events_weekend_next" to mapOf(
            AppLanguage.SPANISH to "Próximo fin de semana",
            AppLanguage.ENGLISH to "Next weekend",
            AppLanguage.CATALAN to "Pròxim cap de setmana",
            AppLanguage.FRENCH to "Le prochain week-end",
            AppLanguage.GERMAN to "Nächstes Wochenende"
        ),
        "events_weekend_upcoming" to mapOf(
            AppLanguage.SPANISH to "Próxima cita",
            AppLanguage.ENGLISH to "Coming up",
            AppLanguage.CATALAN to "Pròxima cita",
            AppLanguage.FRENCH to "Prochain rendez-vous",
            AppLanguage.GERMAN to "Nächster Termin"
        ),
        "session_race" to mapOf(
            AppLanguage.SPANISH to "Carrera",
            AppLanguage.ENGLISH to "Race",
            AppLanguage.CATALAN to "Cursa",
            AppLanguage.FRENCH to "Course",
            AppLanguage.GERMAN to "Rennen"
        ),
        "session_qualy" to mapOf(
            AppLanguage.SPANISH to "Clasificación",
            AppLanguage.ENGLISH to "Qualifying",
            AppLanguage.CATALAN to "Classificació",
            AppLanguage.FRENCH to "Qualification",
            AppLanguage.GERMAN to "Qualifying"
        ),
        "session_sprint" to mapOf(
            AppLanguage.SPANISH to "Sprint",
            AppLanguage.ENGLISH to "Sprint",
            AppLanguage.CATALAN to "Sprint",
            AppLanguage.FRENCH to "Sprint",
            AppLanguage.GERMAN to "Sprint"
        ),
        "session_practice" to mapOf(
            AppLanguage.SPANISH to "Libres",
            AppLanguage.ENGLISH to "Practice",
            AppLanguage.CATALAN to "Lliures",
            AppLanguage.FRENCH to "Essais libres",
            AppLanguage.GERMAN to "Freies Training"
        ),
        "session_other" to mapOf(
            AppLanguage.SPANISH to "Otros",
            AppLanguage.ENGLISH to "Other",
            AppLanguage.CATALAN to "Altres",
            AppLanguage.FRENCH to "Autre",
            AppLanguage.GERMAN to "Sonstige"
        ),
        "events_series_tag_prefix" to mapOf(
            AppLanguage.SPANISH to "Tag: %s",
            AppLanguage.ENGLISH to "Tag: %s",
            AppLanguage.CATALAN to "Etiqueta: %s",
            AppLanguage.FRENCH to "Étiquette : %s",
            AppLanguage.GERMAN to "Tag: %s"
        ),
        "events_edit" to mapOf(
            AppLanguage.SPANISH to "Editar",
            AppLanguage.ENGLISH to "Edit",
            AppLanguage.CATALAN to "Edita",
            AppLanguage.FRENCH to "Modifier",
            AppLanguage.GERMAN to "Bearbeiten"
        ),

        // --- FASE 3 (04/09/2026): pantallas de Clasificaciones ---
        "standings_title" to mapOf(
            AppLanguage.SPANISH to "Clasificaciones",
            AppLanguage.ENGLISH to "Standings",
            AppLanguage.CATALAN to "Classificacions",
            AppLanguage.FRENCH to "Classements",
            AppLanguage.GERMAN to "Wertungen"
        ),
        "standings_empty_disabled_title" to mapOf(
            AppLanguage.SPANISH to "Clasificaciones desactivadas",
            AppLanguage.ENGLISH to "Standings disabled",
            AppLanguage.CATALAN to "Classificacions desactivades",
            AppLanguage.FRENCH to "Classements désactivés",
            AppLanguage.GERMAN to "Wertungen deaktiviert"
        ),
        "standings_empty_disabled_message" to mapOf(
            AppLanguage.SPANISH to "Actívalas en Ajustes para ver la clasificación de F1, MotoGP y más — necesita conexión a internet.",
            AppLanguage.ENGLISH to "Turn them on in Settings to see F1, MotoGP and more standings — needs an internet connection.",
            AppLanguage.CATALAN to "Activa-les a Configuració per veure la classificació de F1, MotoGP i més — necessita connexió a internet.",
            AppLanguage.FRENCH to "Activez-les dans les Paramètres pour voir les classements F1, MotoGP et plus — connexion internet nécessaire.",
            AppLanguage.GERMAN to "Aktiviere sie in den Einstellungen, um F1-, MotoGP- und weitere Wertungen zu sehen — benötigt Internetverbindung."
        ),
        "standings_empty_offline_message" to mapOf(
            AppLanguage.SPANISH to "Todavía no hay ninguna clasificación guardada. Conéctate a wifi o datos móviles al menos una vez.",
            AppLanguage.ENGLISH to "No standings saved yet. Connect to Wi-Fi or mobile data at least once.",
            AppLanguage.CATALAN to "Encara no hi ha cap classificació guardada. Connecta't a wifi o dades mòbils almenys una vegada.",
            AppLanguage.FRENCH to "Aucun classement enregistré pour le moment. Connectez-vous au Wi-Fi ou aux données mobiles au moins une fois.",
            AppLanguage.GERMAN to "Noch keine Wertungen gespeichert. Verbinde dich mindestens einmal mit WLAN oder mobilen Daten."
        ),
        "standings_leading" to mapOf(
            AppLanguage.SPANISH to "Lidera %s · %s pts",
            AppLanguage.ENGLISH to "%s leads · %s pts",
            AppLanguage.CATALAN to "Lidera %s · %s pts",
            AppLanguage.FRENCH to "%s est en tête · %s pts",
            AppLanguage.GERMAN to "%s führt · %s Pkt."
        ),
        "standings_no_data_yet" to mapOf(
            AppLanguage.SPANISH to "Sin datos todavía",
            AppLanguage.ENGLISH to "No data yet",
            AppLanguage.CATALAN to "Sense dades encara",
            AppLanguage.FRENCH to "Pas encore de données",
            AppLanguage.GERMAN to "Noch keine Daten"
        ),
        "standings_sync_result" to mapOf(
            AppLanguage.SPANISH to "Sincronización: %d de %d OK",
            AppLanguage.ENGLISH to "Sync: %d of %d OK",
            AppLanguage.CATALAN to "Sincronització: %d de %d OK",
            AppLanguage.FRENCH to "Synchronisation : %d sur %d OK",
            AppLanguage.GERMAN to "Synchronisierung: %d von %d OK"
        ),
        "standings_drivers" to mapOf(
            AppLanguage.SPANISH to "Pilotos",
            AppLanguage.ENGLISH to "Drivers",
            AppLanguage.CATALAN to "Pilots",
            AppLanguage.FRENCH to "Pilotes",
            AppLanguage.GERMAN to "Fahrer"
        ),
        "standings_teams" to mapOf(
            AppLanguage.SPANISH to "Equipos",
            AppLanguage.ENGLISH to "Teams",
            AppLanguage.CATALAN to "Equips",
            AppLanguage.FRENCH to "Écuries",
            AppLanguage.GERMAN to "Teams"
        ),
        "standings_back" to mapOf(
            AppLanguage.SPANISH to "Volver",
            AppLanguage.ENGLISH to "Back",
            AppLanguage.CATALAN to "Enrere",
            AppLanguage.FRENCH to "Retour",
            AppLanguage.GERMAN to "Zurück"
        ),
        "standings_last_updated" to mapOf(
            AppLanguage.SPANISH to "Actualizado: %s",
            AppLanguage.ENGLISH to "Updated: %s",
            AppLanguage.CATALAN to "Actualitzat: %s",
            AppLanguage.FRENCH to "Mis à jour : %s",
            AppLanguage.GERMAN to "Aktualisiert: %s"
        ),
        "standings_no_driver_data" to mapOf(
            AppLanguage.SPANISH to "Sin datos de pilotos todavía para este coche",
            AppLanguage.ENGLISH to "No driver data yet for this car",
            AppLanguage.CATALAN to "Encara no hi ha dades de pilots per a aquest cotxe",
            AppLanguage.FRENCH to "Pas encore de données de pilotes pour cette voiture",
            AppLanguage.GERMAN to "Noch keine Fahrerdaten für dieses Auto"
        ),
        "standings_no_category_data" to mapOf(
            AppLanguage.SPANISH to "Sin datos todavía para esta categoría",
            AppLanguage.ENGLISH to "No data yet for this category",
            AppLanguage.CATALAN to "Encara no hi ha dades per a aquesta categoria",
            AppLanguage.FRENCH to "Pas encore de données pour cette catégorie",
            AppLanguage.GERMAN to "Noch keine Daten für diese Kategorie"
        ),
        "standings_points" to mapOf(
            AppLanguage.SPANISH to "%s pts",
            AppLanguage.ENGLISH to "%s pts",
            AppLanguage.CATALAN to "%s pts",
            AppLanguage.FRENCH to "%s pts",
            AppLanguage.GERMAN to "%s Pkt."
        ),
        "standings_offline_alert_title" to mapOf(
            AppLanguage.SPANISH to "Sin conexión",
            AppLanguage.ENGLISH to "No connection",
            AppLanguage.CATALAN to "Sense connexió",
            AppLanguage.FRENCH to "Pas de connexion",
            AppLanguage.GERMAN to "Keine Verbindung"
        ),
        "standings_offline_alert_message" to mapOf(
            AppLanguage.SPANISH to "No se puede actualizar ahora.",
            AppLanguage.ENGLISH to "Can't refresh right now.",
            AppLanguage.CATALAN to "Ara mateix no es pot actualitzar.",
            AppLanguage.FRENCH to "Impossible d'actualiser maintenant.",
            AppLanguage.GERMAN to "Jetzt nicht aktualisierbar."
        ),
        "standings_offline_alert_ok" to mapOf(
            AppLanguage.SPANISH to "Vale",
            AppLanguage.ENGLISH to "OK",
            AppLanguage.CATALAN to "D'acord",
            AppLanguage.FRENCH to "OK",
            AppLanguage.GERMAN to "OK"
        ),
        "standings_sync_all_ok" to mapOf(
            AppLanguage.SPANISH to "Todo actualizado correctamente.",
            AppLanguage.ENGLISH to "Everything updated successfully.",
            AppLanguage.CATALAN to "Tot actualitzat correctament.",
            AppLanguage.FRENCH to "Tout a été mis à jour avec succès.",
            AppLanguage.GERMAN to "Alles erfolgreich aktualisiert."
        ),

        // --- FASE 4 (04/09/2026): widget de la pantalla de inicio ---
        // "Sin eventos" reutiliza la clave "events_empty_title" (mismo texto exacto).
        "widget_days_prefix" to mapOf(
            AppLanguage.SPANISH to "D-",
            AppLanguage.ENGLISH to "D-",
            AppLanguage.CATALAN to "D-",
            // En francés la cuenta atrás se dice "J-7" (Jour), no "D-7" — igual que en
            // alemán se dice "T-7" (Tag); son las abreviaturas reales que usa cada idioma
            // para "días para", no una traducción literal de la letra.
            AppLanguage.FRENCH to "J-",
            AppLanguage.GERMAN to "T-"
        ),
        "widget_track_time" to mapOf(
            AppLanguage.SPANISH to "Pista: %s",
            AppLanguage.ENGLISH to "Track: %s",
            AppLanguage.CATALAN to "Circuit: %s",
            AppLanguage.FRENCH to "Circuit : %s",
            AppLanguage.GERMAN to "Strecke: %s"
        ),

        // --- Accesibilidad (Fase 2, 05/09/2026): onClickLabel/contentDescription de
        // elementos que ya usan tr() en su pantalla — los widgets (RaceWidgetConfigActivity,
        // StandingsWidgetConfigActivity) no migran a tr() todavía, eso es Fase 3 (i18n). ---
        "cd_view_photo" to mapOf(
            AppLanguage.SPANISH to "Ver foto grande",
            AppLanguage.ENGLISH to "View larger photo",
            AppLanguage.CATALAN to "Veure foto gran",
            AppLanguage.FRENCH to "Voir la photo en grand",
            AppLanguage.GERMAN to "Foto vergrößern ansehen"
        ),
        "cd_edit_active_series" to mapOf(
            AppLanguage.SPANISH to "Editar series activas",
            AppLanguage.ENGLISH to "Edit active series",
            AppLanguage.CATALAN to "Edita les sèries actives",
            AppLanguage.FRENCH to "Modifier les séries actives",
            AppLanguage.GERMAN to "Aktive Serien bearbeiten"
        ),

        // --- i18n (Fase 3, 05/09/2026): últimos textos fijos en español que quedaban fuera
        // de Eventos/Standings/CategoryStandings/Ajustes (ya cubiertos desde Fase 1). ---
        "startup_loading_message" to mapOf(
            AppLanguage.SPANISH to "Actualizando calendario y clasificaciones…",
            AppLanguage.ENGLISH to "Updating calendar and standings…",
            AppLanguage.CATALAN to "Actualitzant calendari i classificacions…",
            AppLanguage.FRENCH to "Mise à jour du calendrier et des classements…",
            AppLanguage.GERMAN to "Kalender und Wertungen werden aktualisiert…"
        ),
        "offline_banner_message" to mapOf(
            AppLanguage.SPANISH to "Sin conexión — mostrando la última actualización guardada",
            AppLanguage.ENGLISH to "Offline — showing the last saved update",
            AppLanguage.CATALAN to "Sense connexió — mostrant l'última actualització desada",
            AppLanguage.FRENCH to "Hors ligne — affichage de la dernière mise à jour enregistrée",
            AppLanguage.GERMAN to "Offline — letzte gespeicherte Aktualisierung wird angezeigt"
        )
    )

    /** El texto de [key] en [language] — si falta esa traducción concreta, cae al español
     *  (siempre completo, es el idioma de origen) antes que enseñar la propia clave en
     *  pantalla. */
    fun get(key: String, language: AppLanguage): String =
        entries[key]?.get(language)
            ?: entries[key]?.get(AppLanguage.SPANISH)
            ?: key
}

/** Atajo de composable: lee el idioma activo de [LocalAppLanguage] y devuelve el texto de
 *  [key] en ese idioma — ej. `Text(tr("settings_title"))`. */
@Composable
@ReadOnlyComposable
fun tr(key: String): String = Strings.get(key, LocalAppLanguage.current)
