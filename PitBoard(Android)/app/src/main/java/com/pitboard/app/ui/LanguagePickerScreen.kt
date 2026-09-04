package com.pitboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pitboard.app.i18n.AppLanguage
import com.pitboard.app.i18n.LocalAppLanguage
import com.pitboard.app.i18n.tr

/**
 * Selector de idioma de primer arranque — se enseña ANTES que cualquier otra pantalla mientras
 * AppSettingsRepository.appLanguage sea null (ver PitBoardApp() en MainActivity.kt), igual de
 * prioritario que NotificationPermissionOnboarding. Los 5 nombres de idioma se enseñan siempre
 * en su propio idioma (nunca traducidos) — quien no lee español encuentra "English" sin tener
 * que entender antes "Inglés".
 *
 * El texto de "Elige tu idioma"/"Continuar" SÍ usa `tr()` con el idioma seleccionado en el
 * momento — según se va tocando una opción, el propio selector cambia de idioma delante del
 * usuario, la mejor confirmación posible de que ha elegido bien antes de continuar.
 */
@Composable
fun LanguagePickerScreen(onLanguageChosen: (AppLanguage) -> Unit) {
    var selected by remember { mutableStateOf(AppLanguage.SPANISH) }

    // El propio selector cambia de idioma en vivo según se toca una opción (usa la selección
    // TODAVÍA NO GUARDADA, no la persistida) — la mejor confirmación posible de que se ha
    // elegido bien antes de continuar.
    CompositionLocalProvider(LocalAppLanguage provides selected) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Text(
                tr("language_picker_title"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                tr("language_picker_subtitle"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(AppLanguage.entries) { language ->
                    LanguageRow(
                        language = language,
                        selected = language == selected,
                        onClick = { selected = language }
                    )
                }
            }

            Button(
                onClick = { onLanguageChosen(selected) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text(tr("language_picker_continue"))
            }
        }
    }
    }
}

@Composable
private fun LanguageRow(language: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                language.nativeName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(end = 32.dp)
            )
            if (selected) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

