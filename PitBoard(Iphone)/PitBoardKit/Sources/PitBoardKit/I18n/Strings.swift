import Foundation

/// Catálogo de textos traducidos, por clave — equivalente exacto de `Strings.kt`, mismas
/// claves y mismas traducciones (para que ambas apps digan lo mismo en cada idioma). FASE 1
/// de la internacionalización (04/09/2026): cubre el selector de idioma de primer arranque y
/// la pantalla de Ajustes, como prueba de que el mecanismo entero funciona de extremo a
/// extremo — el resto de pantallas sigue con texto fijo en español por ahora, se migran en
/// fases siguientes añadiendo más entradas aquí.
public enum Strings {

    private static let entries: [String: [AppLanguage: String]] = [
        // --- Selector de idioma (primer arranque) ---
        "language_picker_title": [
            .spanish: "Elige tu idioma",
            .english: "Choose your language",
            .catalan: "Tria el teu idioma",
            .french: "Choisissez votre langue",
            .german: "Wähle deine Sprache"
        ],
        "language_picker_subtitle": [
            .spanish: "Podrás cambiarlo luego desde Ajustes.",
            .english: "You can change it later in Settings.",
            .catalan: "Podràs canviar-lo després des de Configuració.",
            .french: "Vous pourrez le modifier plus tard dans les Paramètres.",
            .german: "Du kannst sie später in den Einstellungen ändern."
        ],
        "language_picker_continue": [
            .spanish: "Continuar",
            .english: "Continue",
            .catalan: "Continua",
            .french: "Continuer",
            .german: "Weiter"
        ],

        // --- Ajustes: general ---
        "settings_title": [
            .spanish: "Ajustes",
            .english: "Settings",
            .catalan: "Configuració",
            .french: "Paramètres",
            .german: "Einstellungen"
        ],
        "settings_footer": [
            .spanish: "PitBoard v0.1.0\n🏁 Hecho para fans del motor",
            .english: "PitBoard v0.1.0\n🏁 Made for motorsport fans",
            .catalan: "PitBoard v0.1.0\n🏁 Fet per a fans del motor",
            .french: "PitBoard v0.1.0\n🏁 Fait pour les fans de sport automobile",
            .german: "PitBoard v0.1.0\n🏁 Gemacht für Motorsport-Fans"
        ],

        // --- Ajustes: Notificaciones ---
        "settings_notifications_title": [
            .spanish: "Notificaciones",
            .english: "Notifications",
            .catalan: "Notificacions",
            .french: "Notifications",
            .german: "Benachrichtigungen"
        ],
        "settings_notifications_enable": [
            .spanish: "Activar avisos",
            .english: "Enable alerts",
            .catalan: "Activa els avisos",
            .french: "Activer les alertes",
            .german: "Erinnerungen aktivieren"
        ],
        "settings_notifications_subtitle": [
            .spanish: "Avisar antes de las sesiones",
            .english: "Get notified before sessions",
            .catalan: "Avisa abans de les sessions",
            .french: "Être averti avant les sessions",
            .german: "Vor Sessions benachrichtigen"
        ],
        "settings_notifications_when": [
            .spanish: "¿Cuándo avisar?",
            .english: "When to notify?",
            .catalan: "Quan avisar?",
            .french: "Quand prévenir ?",
            .german: "Wann benachrichtigen?"
        ],
        "settings_notifications_which_sessions": [
            .spanish: "¿Qué sesiones?",
            .english: "Which sessions?",
            .catalan: "Quines sessions?",
            .french: "Quelles sessions ?",
            .german: "Welche Sessions?"
        ],
        "settings_notifications_competitive": [
            .spanish: "Competición (Carrera, Clasif., Sprint)",
            .english: "Competitive (Race, Qualifying, Sprint)",
            .catalan: "Competició (Cursa, Classificació, Sprint)",
            .french: "Compétition (Course, Qualification, Sprint)",
            .german: "Wettbewerb (Rennen, Qualifying, Sprint)"
        ],
        "settings_notifications_practice": [
            .spanish: "Entrenamientos (Libres)",
            .english: "Practice sessions",
            .catalan: "Entrenaments (Lliures)",
            .french: "Essais libres",
            .german: "Trainings (Freies Training)"
        ],
        "settings_notifications_active_series": [
            .spanish: "Series activas",
            .english: "Active series",
            .catalan: "Sèries actives",
            .french: "Séries actives",
            .german: "Aktive Serien"
        ],
        "settings_notifications_all_series": [
            .spanish: "Todas las series",
            .english: "All series",
            .catalan: "Totes les sèries",
            .french: "Toutes les séries",
            .german: "Alle Serien"
        ],
        "settings_notifications_series_count": [
            .spanish: "%d series seleccionadas",
            .english: "%d series selected",
            .catalan: "%d sèries seleccionades",
            .french: "%d séries sélectionnées",
            .german: "%d Serien ausgewählt"
        ],
        "settings_notifications_permission_title": [
            .spanish: "Permiso de notificaciones",
            .english: "Notification permission",
            .catalan: "Permís de notificacions",
            .french: "Autorisation de notifications",
            .german: "Benachrichtigungsberechtigung"
        ],
        "settings_notifications_permission_body": [
            .spanish: "iOS tiene bloqueados los avisos de PitBoard, así que la app ya no puede volver a pedirte el permiso desde aquí. Abre los ajustes del sistema y activa las notificaciones para PitBoard.",
            .english: "iOS has PitBoard's alerts blocked, so the app can no longer ask you for permission from here. Open the system settings and turn notifications on for PitBoard.",
            .catalan: "iOS té bloquejats els avisos de PitBoard, així que l'app ja no pot tornar a demanar-te el permís des d'aquí. Obre la configuració del sistema i activa les notificacions per a PitBoard.",
            .french: "iOS a bloqué les alertes de PitBoard, l'application ne peut donc plus vous demander l'autorisation depuis ici. Ouvrez les paramètres système et activez les notifications pour PitBoard.",
            .german: "iOS hat die Erinnerungen von PitBoard blockiert, daher kann die App die Berechtigung nicht mehr von hier aus anfordern. Öffne die Systemeinstellungen und aktiviere Benachrichtigungen für PitBoard."
        ],
        "settings_open_settings": [
            .spanish: "Abrir ajustes",
            .english: "Open settings",
            .catalan: "Obre la configuració",
            .french: "Ouvrir les paramètres",
            .german: "Einstellungen öffnen"
        ],
        "settings_not_now": [
            .spanish: "Ahora no",
            .english: "Not now",
            .catalan: "Ara no",
            .french: "Pas maintenant",
            .german: "Jetzt nicht"
        ],
        "settings_series_with_alerts_title": [
            .spanish: "Series con avisos",
            .english: "Series with alerts",
            .catalan: "Sèries amb avisos",
            .french: "Séries avec alertes",
            .german: "Serien mit Erinnerungen"
        ],
        "settings_series_with_alerts_subtitle": [
            .spanish: "Desactiva aquellas series de las que no quieras recibir notificaciones.",
            .english: "Turn off any series you don't want notifications for.",
            .catalan: "Desactiva les sèries de les quals no vulguis rebre notificacions.",
            .french: "Désactivez les séries pour lesquelles vous ne voulez pas de notifications.",
            .german: "Deaktiviere die Serien, für die du keine Benachrichtigungen möchtest."
        ],
        "settings_done": [
            .spanish: "Listo",
            .english: "Done",
            .catalan: "Fet",
            .french: "Terminé",
            .german: "Fertig"
        ],

        // --- Ajustes: Clasificaciones ---
        "settings_standings_title": [
            .spanish: "Clasificaciones",
            .english: "Standings",
            .catalan: "Classificacions",
            .french: "Classements",
            .german: "Wertungen"
        ],
        "settings_standings_enable": [
            .spanish: "Activar clasificaciones",
            .english: "Enable standings",
            .catalan: "Activa les classificacions",
            .french: "Activer les classements",
            .german: "Wertungen aktivieren"
        ],
        "settings_standings_subtitle": [
            .spanish: "F1, MotoGP, NASCAR y más. Al activarlas aparece su pestaña en la barra de abajo; se actualizan cada lunes a las 12:00 y necesitan wifi o datos móviles.",
            .english: "F1, MotoGP, NASCAR and more. Turning this on adds its tab to the bottom bar; it updates every Monday at 12:00 and needs Wi-Fi or mobile data.",
            .catalan: "F1, MotoGP, NASCAR i més. En activar-les apareix la seva pestanya a la barra inferior; s'actualitzen cada dilluns a les 12:00 i necessiten wifi o dades mòbils.",
            .french: "F1, MotoGP, NASCAR et plus. En l'activant, son onglet apparaît dans la barre du bas ; mise à jour chaque lundi à 12h00, Wi-Fi ou données mobiles nécessaires.",
            .german: "F1, MotoGP, NASCAR und mehr. Beim Aktivieren erscheint der Tab in der unteren Leiste; Aktualisierung jeden Montag um 12:00 Uhr, WLAN oder mobile Daten nötig."
        ],

        // --- Ajustes: Apariencia ---
        "settings_appearance_title": [
            .spanish: "Apariencia",
            .english: "Appearance",
            .catalan: "Aparença",
            .french: "Apparence",
            .german: "Erscheinungsbild"
        ],
        "settings_appearance_subtitle": [
            .spanish: "Elige el tema de la aplicación",
            .english: "Choose the app's theme",
            .catalan: "Tria el tema de l'aplicació",
            .french: "Choisissez le thème de l'application",
            .german: "Wähle das App-Design"
        ],
        "settings_theme_light": [
            .spanish: "☀️ Claro",
            .english: "☀️ Light",
            .catalan: "☀️ Clar",
            .french: "☀️ Clair",
            .german: "☀️ Hell"
        ],
        "settings_theme_dark": [
            .spanish: "🌙 Oscuro",
            .english: "🌙 Dark",
            .catalan: "🌙 Fosc",
            .french: "🌙 Sombre",
            .german: "🌙 Dunkel"
        ],
        "settings_theme_auto": [
            .spanish: "📱 Auto",
            .english: "📱 Auto",
            .catalan: "📱 Auto",
            .french: "📱 Auto",
            .german: "📱 Auto"
        ],

        // --- Ajustes: Zona horaria ---
        "settings_timezone_title": [
            .spanish: "Zona horaria",
            .english: "Time zone",
            .catalan: "Fus horari",
            .french: "Fuseau horaire",
            .german: "Zeitzone"
        ],
        "settings_timezone_subtitle": [
            .spanish: "Qué hora se ve de un vistazo en la lista de Eventos. El detalle de cada evento (al tocarlo) siempre enseña las dos.",
            .english: "Which time shows at a glance in the Events list. Each event's detail (when tapped) always shows both.",
            .catalan: "Quina hora es veu d'una ullada a la llista d'Esdeveniments. El detall de cada esdeveniment (en tocar-lo) sempre mostra les dues.",
            .french: "Quelle heure s'affiche en un coup d'œil dans la liste des Événements. Le détail de chaque événement (au toucher) affiche toujours les deux.",
            .german: "Welche Uhrzeit auf einen Blick in der Ereignisliste angezeigt wird. Die Details jedes Ereignisses (beim Antippen) zeigen immer beide."
        ],
        "settings_timezone_device": [
            .spanish: "Mi hora",
            .english: "My time",
            .catalan: "La meva hora",
            .french: "Mon heure",
            .german: "Meine Zeit"
        ],
        "settings_timezone_track": [
            .spanish: "Hora del circuito",
            .english: "Track time",
            .catalan: "Hora del circuit",
            .french: "Heure du circuit",
            .german: "Streckenzeit"
        ],

        // --- Ajustes: ayuda (avisos/widget que llegan tarde) ---
        "settings_battery_help_title": [
            .spanish: "¿Los avisos o el widget no van?",
            .english: "Alerts or widget not working?",
            .catalan: "Els avisos o el widget no van?",
            .french: "Les alertes ou le widget ne fonctionnent pas ?",
            .german: "Erinnerungen oder Widget funktionieren nicht?"
        ],
        "settings_battery_help_subtitle": [
            .spanish: "Si iOS tiene desactivada la 'Actualización en segundo plano' para PitBoard (o el Modo de bajo consumo está activado), los avisos y el widget pueden llegar tarde. Actívala aquí.",
            .english: "If iOS has 'Background App Refresh' turned off for PitBoard (or Low Power Mode is on), alerts and the widget may arrive late. Turn it on here.",
            .catalan: "Si iOS té desactivada l'\"Actualització en segon pla\" per a PitBoard (o el Mode de baix consum està activat), els avisos i el widget poden arribar tard. Activa-la aquí.",
            .french: "Si iOS a désactivé l'« Actualisation en arrière-plan » pour PitBoard (ou si le Mode Économie d'énergie est actif), les alertes et le widget peuvent arriver en retard. Activez-la ici.",
            .german: "Wenn iOS die 'Hintergrundaktualisierung' für PitBoard deaktiviert hat (oder der Stromsparmodus aktiv ist), können Erinnerungen und das Widget verspätet ankommen. Aktiviere sie hier."
        ],
        "settings_battery_help_button": [
            .spanish: "Abrir Ajustes de PitBoard",
            .english: "Open PitBoard Settings",
            .catalan: "Obre la Configuració de PitBoard",
            .french: "Ouvrir les Réglages de PitBoard",
            .german: "PitBoard-Einstellungen öffnen"
        ],

        // --- FASE 2 (04/09/2026): pantalla de Eventos ---
        "events_title": [
            .spanish: "Eventos",
            .english: "Events",
            .catalan: "Esdeveniments",
            .french: "Événements",
            .german: "Termine"
        ],
        "events_search_placeholder": [
            .spanish: "Buscar por palabra clave…",
            .english: "Search by keyword…",
            .catalan: "Cerca per paraula clau…",
            .french: "Rechercher par mot-clé…",
            .german: "Nach Stichwort suchen…"
        ],
        "events_clear_search": [
            .spanish: "Borrar búsqueda",
            .english: "Clear search",
            .catalan: "Esborra la cerca",
            .french: "Effacer la recherche",
            .german: "Suche löschen"
        ],
        "events_filter_all_series": [
            .spanish: "Todas",
            .english: "All",
            .catalan: "Totes",
            .french: "Toutes",
            .german: "Alle"
        ],
        "events_filter_all_sessions": [
            .spanish: "Todas las sesiones",
            .english: "All sessions",
            .catalan: "Totes les sessions",
            .french: "Toutes les sessions",
            .german: "Alle Sessions"
        ],
        "events_empty_filtered_title": [
            .spanish: "Ningún evento de estas series",
            .english: "No events for these series",
            .catalan: "Cap esdeveniment d'aquestes sèries",
            .french: "Aucun événement pour ces séries",
            .german: "Keine Termine für diese Serien"
        ],
        "events_empty_filtered_message": [
            .spanish: "No hay eventos guardados para las series que has elegido. Prueba a quitar el filtro o a actualizar.",
            .english: "No events saved for the series you picked. Try removing the filter or refreshing.",
            .catalan: "No hi ha esdeveniments guardats per a les sèries que has triat. Prova de treure el filtre o d'actualitzar.",
            .french: "Aucun événement enregistré pour les séries choisies. Essayez de retirer le filtre ou d'actualiser.",
            .german: "Keine Termine für die ausgewählten Serien gespeichert. Versuche, den Filter zu entfernen oder zu aktualisieren."
        ],
        "events_remove_filter": [
            .spanish: "Quitar filtro",
            .english: "Remove filter",
            .catalan: "Treu el filtre",
            .french: "Retirer le filtre",
            .german: "Filter entfernen"
        ],
        "events_refresh": [
            .spanish: "Actualizar",
            .english: "Refresh",
            .catalan: "Actualitza",
            .french: "Actualiser",
            .german: "Aktualisieren"
        ],
        "events_empty_offline_title": [
            .spanish: "Necesitas conexión",
            .english: "You need a connection",
            .catalan: "Necessites connexió",
            .french: "Connexion nécessaire",
            .german: "Verbindung erforderlich"
        ],
        "events_empty_offline_message": [
            .spanish: "Todavía no se ha guardado ningún evento en este dispositivo. Conéctate a wifi o datos móviles al menos una vez.",
            .english: "No events have been saved on this device yet. Connect to Wi-Fi or mobile data at least once.",
            .catalan: "Encara no s'ha guardat cap esdeveniment en aquest dispositiu. Connecta't a wifi o dades mòbils almenys una vegada.",
            .french: "Aucun événement n'a encore été enregistré sur cet appareil. Connectez-vous au Wi-Fi ou aux données mobiles au moins une fois.",
            .german: "Auf diesem Gerät wurden noch keine Termine gespeichert. Verbinde dich mindestens einmal mit WLAN oder mobilen Daten."
        ],
        "events_empty_title": [
            .spanish: "Sin eventos",
            .english: "No events",
            .catalan: "Sense esdeveniments",
            .french: "Aucun événement",
            .german: "Keine Termine"
        ],
        "events_empty_message": [
            .spanish: "Todavía no se ha sincronizado ningún calendario. Prueba a tocar Actualizar.",
            .english: "No calendar has synced yet. Try tapping Refresh.",
            .catalan: "Encara no s'ha sincronitzat cap calendari. Prova de tocar Actualitza.",
            .french: "Aucun calendrier n'a encore été synchronisé. Essayez d'appuyer sur Actualiser.",
            .german: "Es wurde noch kein Kalender synchronisiert. Tippe auf Aktualisieren."
        ],
        "events_empty_no_match_title": [
            .spanish: "Ningún evento coincide",
            .english: "No events match",
            .catalan: "Cap esdeveniment coincideix",
            .french: "Aucun événement ne correspond",
            .german: "Keine Treffer"
        ],
        "events_empty_no_match_message": [
            .spanish: "Prueba con otra palabra clave, serie o tipo de sesión.",
            .english: "Try another keyword, series or session type.",
            .catalan: "Prova amb una altra paraula clau, sèrie o tipus de sessió.",
            .french: "Essayez un autre mot-clé, une autre série ou un autre type de session.",
            .german: "Versuche es mit einem anderen Stichwort, einer anderen Serie oder Session-Art."
        ],
        "events_clear_search_and_filters": [
            .spanish: "Borrar búsqueda y filtros",
            .english: "Clear search and filters",
            .catalan: "Esborra la cerca i els filtres",
            .french: "Effacer la recherche et les filtres",
            .german: "Suche und Filter löschen"
        ],
        "events_later_section": [
            .spanish: "MÁS ADELANTE",
            .english: "LATER",
            .catalan: "MÉS ENDAVANT",
            .french: "PLUS TARD",
            .german: "SPÄTER"
        ],
        "events_search_and_filter": [
            .spanish: "Buscar y filtrar",
            .english: "Search and filter",
            .catalan: "Cerca i filtra",
            .french: "Rechercher et filtrer",
            .german: "Suchen und filtern"
        ],
        "events_edit_series": [
            .spanish: "Editar series",
            .english: "Edit series",
            .catalan: "Edita les sèries",
            .french: "Modifier les séries",
            .german: "Serien bearbeiten"
        ],
        "events_offline_toast": [
            .spanish: "Sin conexión — no se puede actualizar ahora",
            .english: "No connection — can't refresh right now",
            .catalan: "Sense connexió — ara mateix no es pot actualitzar",
            .french: "Pas de connexion — impossible d'actualiser maintenant",
            .german: "Keine Verbindung — jetzt nicht aktualisierbar"
        ],
        "events_edit_series_subtitle": [
            .spanish: "Toca una serie para cambiar sus iniciales o su color.",
            .english: "Tap a series to change its initials or color.",
            .catalan: "Toca una sèrie per canviar-ne les inicials o el color.",
            .french: "Touchez une série pour changer ses initiales ou sa couleur.",
            .german: "Tippe auf eine Serie, um Kürzel oder Farbe zu ändern."
        ],
        "events_tag_label": [
            .spanish: "Tag corto (máx. 5)",
            .english: "Short tag (max. 5)",
            .catalan: "Etiqueta curta (màx. 5)",
            .french: "Étiquette courte (max. 5)",
            .german: "Kurzes Tag (max. 5)"
        ],
        "events_color_label": [
            .spanish: "Color (#RRGGBB)",
            .english: "Color (#RRGGBB)",
            .catalan: "Color (#RRGGBB)",
            .french: "Couleur (#RRGGBB)",
            .german: "Farbe (#RRGGBB)"
        ],
        "events_preview_label": [
            .spanish: "Vista previa: ",
            .english: "Preview: ",
            .catalan: "Vista prèvia: ",
            .french: "Aperçu : ",
            .german: "Vorschau: "
        ],
        "events_save": [
            .spanish: "Guardar",
            .english: "Save",
            .catalan: "Desa",
            .french: "Enregistrer",
            .german: "Speichern"
        ],
        "events_cancel": [
            .spanish: "Cancelar",
            .english: "Cancel",
            .catalan: "Cancel·la",
            .french: "Annuler",
            .german: "Abbrechen"
        ],
        "events_close": [
            .spanish: "Cerrar",
            .english: "Close",
            .catalan: "Tanca",
            .french: "Fermer",
            .german: "Schließen"
        ],
        "events_detail_your_time": [
            .spanish: "Tu hora local",
            .english: "Your local time",
            .catalan: "La teva hora local",
            .french: "Votre heure locale",
            .german: "Deine Ortszeit"
        ],
        "events_detail_track_time": [
            .spanish: "Hora local del circuito (%@)",
            .english: "Local track time (%@)",
            .catalan: "Hora local del circuit (%@)",
            .french: "Heure locale du circuit (%@)",
            .german: "Lokale Streckenzeit (%@)"
        ],
        "events_detail_series": [
            .spanish: "Serie",
            .english: "Series",
            .catalan: "Sèrie",
            .french: "Série",
            .german: "Serie"
        ],
        "events_detail_session_type": [
            .spanish: "Tipo de sesión",
            .english: "Session type",
            .catalan: "Tipus de sessió",
            .french: "Type de session",
            .german: "Session-Art"
        ],
        "events_detail_weather": [
            .spanish: "Clima en el circuito",
            .english: "Weather at the track",
            .catalan: "Clima al circuit",
            .french: "Météo sur le circuit",
            .german: "Wetter an der Strecke"
        ],
        "events_detail_weather_value": [
            .spanish: "%d°C · %d%% de probabilidad de lluvia",
            .english: "%d°C · %d%% chance of rain",
            .catalan: "%d°C · %d%% de probabilitat de pluja",
            .french: "%d°C · %d%% de probabilité de pluie",
            .german: "%d°C · %d%% Regenwahrscheinlichkeit"
        ],
        "events_weekend_today": [
            .spanish: "Hoy",
            .english: "Today",
            .catalan: "Avui",
            .french: "Aujourd'hui",
            .german: "Heute"
        ],
        "events_weekend_this": [
            .spanish: "Este fin de semana",
            .english: "This weekend",
            .catalan: "Aquest cap de setmana",
            .french: "Ce week-end",
            .german: "Dieses Wochenende"
        ],
        "events_weekend_next": [
            .spanish: "Próximo fin de semana",
            .english: "Next weekend",
            .catalan: "Pròxim cap de setmana",
            .french: "Le prochain week-end",
            .german: "Nächstes Wochenende"
        ],
        "events_weekend_upcoming": [
            .spanish: "Próxima cita",
            .english: "Coming up",
            .catalan: "Pròxima cita",
            .french: "Prochain rendez-vous",
            .german: "Nächster Termin"
        ],
        "session_race": [
            .spanish: "Carrera",
            .english: "Race",
            .catalan: "Cursa",
            .french: "Course",
            .german: "Rennen"
        ],
        "session_qualy": [
            .spanish: "Clasificación",
            .english: "Qualifying",
            .catalan: "Classificació",
            .french: "Qualification",
            .german: "Qualifying"
        ],
        "session_sprint": [
            .spanish: "Sprint",
            .english: "Sprint",
            .catalan: "Sprint",
            .french: "Sprint",
            .german: "Sprint"
        ],
        "session_practice": [
            .spanish: "Libres",
            .english: "Practice",
            .catalan: "Lliures",
            .french: "Essais libres",
            .german: "Freies Training"
        ],
        "session_other": [
            .spanish: "Otros",
            .english: "Other",
            .catalan: "Altres",
            .french: "Autre",
            .german: "Sonstige"
        ],
        "events_series_tag_prefix": [
            .spanish: "Tag: %@",
            .english: "Tag: %@",
            .catalan: "Etiqueta: %@",
            .french: "Étiquette : %@",
            .german: "Tag: %@"
        ],
        "events_edit": [
            .spanish: "Editar",
            .english: "Edit",
            .catalan: "Edita",
            .french: "Modifier",
            .german: "Bearbeiten"
        ],

        // --- FASE 3 (04/09/2026): pantallas de Clasificaciones ---
        "standings_title": [
            .spanish: "Clasificaciones",
            .english: "Standings",
            .catalan: "Classificacions",
            .french: "Classements",
            .german: "Wertungen"
        ],
        "standings_empty_disabled_title": [
            .spanish: "Clasificaciones desactivadas",
            .english: "Standings disabled",
            .catalan: "Classificacions desactivades",
            .french: "Classements désactivés",
            .german: "Wertungen deaktiviert"
        ],
        "standings_empty_disabled_message": [
            .spanish: "Actívalas en Ajustes para ver la clasificación de F1, MotoGP y más — necesita conexión a internet.",
            .english: "Turn them on in Settings to see F1, MotoGP and more standings — needs an internet connection.",
            .catalan: "Activa-les a Configuració per veure la classificació de F1, MotoGP i més — necessita connexió a internet.",
            .french: "Activez-les dans les Paramètres pour voir les classements F1, MotoGP et plus — connexion internet nécessaire.",
            .german: "Aktiviere sie in den Einstellungen, um F1-, MotoGP- und weitere Wertungen zu sehen — benötigt Internetverbindung."
        ],
        "standings_empty_offline_message": [
            .spanish: "Todavía no hay ninguna clasificación guardada. Conéctate a wifi o datos móviles al menos una vez.",
            .english: "No standings saved yet. Connect to Wi-Fi or mobile data at least once.",
            .catalan: "Encara no hi ha cap classificació guardada. Connecta't a wifi o dades mòbils almenys una vegada.",
            .french: "Aucun classement enregistré pour le moment. Connectez-vous au Wi-Fi ou aux données mobiles au moins une fois.",
            .german: "Noch keine Wertungen gespeichert. Verbinde dich mindestens einmal mit WLAN oder mobilen Daten."
        ],
        "standings_leading": [
            .spanish: "Lidera %@ · %@ pts",
            .english: "%@ leads · %@ pts",
            .catalan: "Lidera %@ · %@ pts",
            .french: "%@ est en tête · %@ pts",
            .german: "%@ führt · %@ Pkt."
        ],
        "standings_no_data_yet": [
            .spanish: "Sin datos todavía",
            .english: "No data yet",
            .catalan: "Sense dades encara",
            .french: "Pas encore de données",
            .german: "Noch keine Daten"
        ],
        "standings_sync_result": [
            .spanish: "Sincronización: %d de %d OK",
            .english: "Sync: %d of %d OK",
            .catalan: "Sincronització: %d de %d OK",
            .french: "Synchronisation : %d sur %d OK",
            .german: "Synchronisierung: %d von %d OK"
        ],
        "standings_drivers": [
            .spanish: "Pilotos",
            .english: "Drivers",
            .catalan: "Pilots",
            .french: "Pilotes",
            .german: "Fahrer"
        ],
        "standings_teams": [
            .spanish: "Equipos",
            .english: "Teams",
            .catalan: "Equips",
            .french: "Écuries",
            .german: "Teams"
        ],
        "standings_back": [
            .spanish: "Volver",
            .english: "Back",
            .catalan: "Enrere",
            .french: "Retour",
            .german: "Zurück"
        ],
        "standings_last_updated": [
            .spanish: "Actualizado: %@",
            .english: "Updated: %@",
            .catalan: "Actualitzat: %@",
            .french: "Mis à jour : %@",
            .german: "Aktualisiert: %@"
        ],
        "standings_no_driver_data": [
            .spanish: "Sin datos de pilotos todavía para este coche",
            .english: "No driver data yet for this car",
            .catalan: "Encara no hi ha dades de pilots per a aquest cotxe",
            .french: "Pas encore de données de pilotes pour cette voiture",
            .german: "Noch keine Fahrerdaten für dieses Auto"
        ],
        "standings_no_category_data": [
            .spanish: "Sin datos todavía para esta categoría",
            .english: "No data yet for this category",
            .catalan: "Encara no hi ha dades per a aquesta categoria",
            .french: "Pas encore de données pour cette catégorie",
            .german: "Noch keine Daten für diese Kategorie"
        ],
        "standings_points": [
            .spanish: "%@ pts",
            .english: "%@ pts",
            .catalan: "%@ pts",
            .french: "%@ pts",
            .german: "%@ Pkt."
        ],
        "standings_offline_alert_title": [
            .spanish: "Sin conexión",
            .english: "No connection",
            .catalan: "Sense connexió",
            .french: "Pas de connexion",
            .german: "Keine Verbindung"
        ],
        "standings_offline_alert_message": [
            .spanish: "No se puede actualizar ahora.",
            .english: "Can't refresh right now.",
            .catalan: "Ara mateix no es pot actualitzar.",
            .french: "Impossible d'actualiser maintenant.",
            .german: "Jetzt nicht aktualisierbar."
        ],
        "standings_offline_alert_ok": [
            .spanish: "Vale",
            .english: "OK",
            .catalan: "D'acord",
            .french: "OK",
            .german: "OK"
        ],
        "standings_sync_all_ok": [
            .spanish: "Todo actualizado correctamente.",
            .english: "Everything updated successfully.",
            .catalan: "Tot actualitzat correctament.",
            .french: "Tout a été mis à jour avec succès.",
            .german: "Alles erfolgreich aktualisiert."
        ],

        // --- FASE 4 (04/09/2026): widget de la pantalla de inicio ---
        // "Sin eventos" reutiliza la clave "events_empty_title" (mismo texto exacto).
        "widget_days_prefix": [
            .spanish: "D-",
            .english: "D-",
            .catalan: "D-",
            // En francés la cuenta atrás se dice "J-7" (Jour), no "D-7" — igual que en
            // alemán se dice "T-7" (Tag); son las abreviaturas reales que usa cada idioma
            // para "días para", no una traducción literal de la letra.
            .french: "J-",
            .german: "T-"
        ],
        "widget_track_time": [
            .spanish: "Pista: %@",
            .english: "Track: %@",
            .catalan: "Circuit: %@",
            .french: "Circuit : %@",
            .german: "Strecke: %@"
        ],

        // --- Accesibilidad (Fase 2, 05/09/2026): accessibilityLabel de botones solo-icono
        // — mismas claves y traducciones que "cd_view_photo" en Strings.kt (Android). ---
        "cd_refresh": [
            .spanish: "Actualizar",
            .english: "Refresh",
            .catalan: "Actualitza",
            .french: "Actualiser",
            .german: "Aktualisieren"
        ],
        "cd_filter_events": [
            .spanish: "Filtrar eventos",
            .english: "Filter events",
            .catalan: "Filtra esdeveniments",
            .french: "Filtrer les événements",
            .german: "Ereignisse filtern"
        ],
        "cd_edit_series": [
            .spanish: "Editar series",
            .english: "Edit series",
            .catalan: "Edita les sèries",
            .french: "Modifier les séries",
            .german: "Serien bearbeiten"
        ],
        "cd_clear_search": [
            .spanish: "Borrar búsqueda",
            .english: "Clear search",
            .catalan: "Esborra la cerca",
            .french: "Effacer la recherche",
            .german: "Suche löschen"
        ],
        "cd_view_photo": [
            .spanish: "Ver foto grande",
            .english: "View larger photo",
            .catalan: "Veure foto gran",
            .french: "Voir la photo en grand",
            .german: "Foto vergrößern ansehen"
        ],
        "cd_close_preview": [
            .spanish: "Cerrar vista previa",
            .english: "Close preview",
            .catalan: "Tanca la previsualització",
            .french: "Fermer l'aperçu",
            .german: "Vorschau schließen"
        ],
        "cd_edit_active_series": [
            .spanish: "Editar series activas",
            .english: "Edit active series",
            .catalan: "Edita les sèries actives",
            .french: "Modifier les séries actives",
            .german: "Aktive Serien bearbeiten"
        ],

        // --- i18n (Fase 3, 05/09/2026): mismas claves/traducciones que Strings.kt. ---
        "startup_loading_message": [
            .spanish: "Actualizando calendario y clasificaciones…",
            .english: "Updating calendar and standings…",
            .catalan: "Actualitzant calendari i classificacions…",
            .french: "Mise à jour du calendrier et des classements…",
            .german: "Kalender und Wertungen werden aktualisiert…"
        ],
        "offline_banner_message": [
            .spanish: "Sin conexión — mostrando la última actualización guardada",
            .english: "Offline — showing the last saved update",
            .catalan: "Sense connexió — mostrant l'última actualització desada",
            .french: "Hors ligne — affichage de la dernière mise à jour enregistrée",
            .german: "Offline — letzte gespeicherte Aktualisierung wird angezeigt"
        ]
    ]

    /// El texto de `key` en `language` — si falta esa traducción concreta, cae al español
    /// (siempre completo, es el idioma de origen) antes que enseñar la propia clave en
    /// pantalla. Equivalente exacto de `Strings.get()` en Android.
    public static func get(_ key: String, language: AppLanguage) -> String {
        entries[key]?[language] ?? entries[key]?[.spanish] ?? key
    }
}
