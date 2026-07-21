package cz.svitaninymburk.projects.reservations.ui.admin.events

import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomFieldsBuilderSectionSpec {

    @Test
    fun nextAvailableFieldKeyStartsAtZeroWhenEmpty() {
        assertEquals("field_0", nextAvailableFieldKey(emptyList()))
    }

    @Test
    fun nextAvailableFieldKeySkipsExistingKeys() {
        assertEquals("field_2", nextAvailableFieldKey(listOf("field_0", "field_1")))
    }

    @Test
    fun nextAvailableFieldKeyAvoidsCollisionAfterFieldWasDeleted() {
        // field_0 was deleted, only field_1 remains -> naive "size"-based generation ("field_${size}") would
        // produce "field_1" again and collide with the surviving field
        assertEquals("field_0", nextAvailableFieldKey(listOf("field_1")))
    }

    @Test
    fun deduplicateFieldKeysLeavesUniqueKeysUntouched() {
        val fields = listOf(
            BooleanFieldDefinition(key = "field_0", label = "Beru s sebou dítě/děti"),
            TextFieldDefinition(key = "field_1", label = "Pokud beru, napište prosím..."),
        )
        assertEquals(fields, deduplicateFieldKeys(fields))
    }

    @Test
    fun deduplicateFieldKeysReassignsLaterDuplicate() {
        val checkbox = BooleanFieldDefinition(key = "field_1", label = "Beru s sebou dítě/děti")
        val text = TextFieldDefinition(key = "field_1", label = "Pokud beru, napište prosím...")

        val deduped = deduplicateFieldKeys(listOf(checkbox, text))

        assertEquals("field_1", deduped[0].key)
        assertEquals("field_0", deduped[1].key)
        assertEquals(text.label, deduped[1].label)
    }

    @Test
    fun deduplicateFieldKeysAvoidsCollidingWithAnUnrelatedLaterField() {
        val a = BooleanFieldDefinition(key = "field_1", label = "A")
        val b = TextFieldDefinition(key = "field_1", label = "B")
        val c = TextFieldDefinition(key = "field_0", label = "C")

        val deduped = deduplicateFieldKeys(listOf(a, b, c))

        val keys = deduped.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "keys must stay unique: $keys")
    }
}
