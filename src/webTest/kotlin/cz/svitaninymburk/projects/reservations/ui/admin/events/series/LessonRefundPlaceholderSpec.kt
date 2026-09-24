package cz.svitaninymburk.projects.reservations.ui.admin.events.series

import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.proportionalLessonRefundPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LessonRefundPlaceholderSpec {

    @Test
    fun dividesPriceByLessonsRoundingDown() {
        assertEquals(136, proportionalLessonRefundPreview(1500, 11), "1500 ÷ 11 = 136,36")
        assertEquals(150, proportionalLessonRefundPreview(1500.0, 10))
    }

    @Test
    fun nothingToShowWithoutPriceOrLessons() {
        assertNull(proportionalLessonRefundPreview(null, 10))
        assertNull(proportionalLessonRefundPreview(0, 10), "kurz zdarma nic nevrací")
        assertNull(proportionalLessonRefundPreview(1500, 0))
    }
}
