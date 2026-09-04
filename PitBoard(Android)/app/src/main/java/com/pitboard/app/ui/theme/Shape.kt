package com.pitboard.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Antes cada pantalla elegía su propio radio de esquina a mano (8, 10, 12, 14, 16, 20, 24dp
 * repartidos sin un criterio claro entre tarjetas con un papel similar). Estos 5 niveles son
 * los que Material 3 ya usa por defecto en Card/Button/ModalBottomSheet cuando no se pasa un
 * shape explícito, así que fijarlos aquí ordena buena parte de la app sin tocar cada pantalla.
 * Donde una tarjeta necesitaba un shape explícito, ahora referencia uno de estos niveles en
 * vez de un RoundedCornerShape(Ndp) suelto.
 */
val PitBoardShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
