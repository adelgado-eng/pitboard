package com.pitboard.app.standings

import androidx.room.Entity
import androidx.room.Index

/**
 * Un piloto de un coche de resistencia (3 por coche en ELMS, 2 en IMSA — a veces uno
 * más o menos si hay un cambio a mitad de temporada), para el desplegable que sale al
 * tocar un coche en Clasificaciones — dato que ni ElmsStandingsSource ni
 * ImsaStandingsSource traen, porque sus tablas de origen son de EQUIPOS/coches, no de
 * pilotos (ver ElmsDriversSource, ImsaDriversSource).
 *
 * `category` entra en la clave porque dos categorías distintas pueden compartir número
 * de coche (el #29 de ELMS y el #29 de IMSA no son el mismo coche) — originalmente esta
 * tabla era solo de ELMS (`ElmsCarDriverEntity`), generalizada al añadir IMSA con el
 * mismo tratamiento. No se guarda una "posición 1/2/3": se listan en el orden en que
 * los trae la página oficial de pilotos/equipo.
 */
@Entity(
    tableName = "car_drivers",
    primaryKeys = ["category", "standingsClass", "carNumber", "entryKey"],
    indices = [Index(value = ["category", "standingsClass", "carNumber"])]
)
data class CarDriverEntity(
    val category: StandingsCategory,
    val standingsClass: StandingsClass,
    /** Sin "#", mismo formato que el número de coche de StandingEntity.name en las
     *  fuentes de coches (ElmsStandingsSource, ImsaStandingsSource). */
    val carNumber: String,
    /** Nombre normalizado del piloto — solo para distinguir filas dentro del mismo coche. */
    val entryKey: String,
    val name: String,
    /** null = icono por defecto en la UI, igual que StandingEntity.photoUrl. */
    val photoUrl: String?,
    val updatedAtUtc: Long
)
