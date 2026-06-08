package cz.svitaninymburk.projects.reservations.android.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorTest {

    @Test
    fun `light primary is Svitani orange`() {
        assertEquals(Color(0xFF8B4200), SvitaniLightColorScheme.primary)
    }

    @Test
    fun `dark primary is light orange for dark surfaces`() {
        assertEquals(Color(0xFFFFB68E), SvitaniDarkColorScheme.primary)
    }

    @Test
    fun `light background is near-white`() {
        assertEquals(Color(0xFFFFFBFF), SvitaniLightColorScheme.background)
    }

    @Test
    fun `dark background is warm dark`() {
        assertEquals(Color(0xFF201A17), SvitaniDarkColorScheme.background)
    }
}
