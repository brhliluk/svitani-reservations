package cz.svitaninymburk.projects.reservations.event

/** Nejnižší volný klíč ve tvaru `field_N`, který se nekříží s [existingKeys]. */
fun nextAvailableFieldKey(existingKeys: Collection<String>): String {
    var i = 0
    while ("field_$i" in existingKeys) i++
    return "field_$i"
}

/**
 * Přejmenuje druhý a další výskyt téhož klíče tak, aby každé pole mělo unikátní `key`.
 *
 * Hodnoty vyplněné v rezervačním formuláři jsou mapované právě přes `key`, takže dvě
 * pole se stejným klíčem sdílí jednu hodnotu a uživatelsky se navzájem vylučují.
 * Pořadí polí ani labely se nemění, klíč si drží první výskyt.
 *
 * @param reservedKeys klíče, které se nesmí použít pro přejmenování, i když nejsou
 *   mezi [fields] — typicky klíče už uložené v hodnotách existujících rezervací,
 *   aby nové pole nezdědilo hodnotu po dávno smazaném poli.
 */
fun deduplicateFieldKeys(
    fields: List<CustomFieldDefinition>,
    reservedKeys: Set<String> = emptySet(),
): List<CustomFieldDefinition> {
    val usedKeys = fields.mapTo(mutableSetOf()) { it.key }.apply { addAll(reservedKeys) }
    val seenKeys = mutableSetOf<String>()
    return fields.map { field ->
        if (!seenKeys.add(field.key)) {
            val newKey = nextAvailableFieldKey(usedKeys)
            usedKeys += newKey
            when (field) {
                is TextFieldDefinition -> field.copy(key = newKey)
                is NumberFieldDefinition -> field.copy(key = newKey)
                is BooleanFieldDefinition -> field.copy(key = newKey)
                is TimeRangeFieldDefinition -> field.copy(key = newKey)
            }
        } else field
    }
}

/**
 * Přemapuje hodnoty jedné rezervace na klíče po [deduplicateFieldKeys].
 *
 * Dokud dvě pole sdílela jeden klíč, zapisovala do stejného slotu a uložila se jen ta
 * hodnota, která byla vyplněná naposled — pod klíčem, který si po deduplikaci drží
 * první z nich. Typ hodnoty ale prozradí, kterému poli patřila: hodnotu proto přesuneme
 * na klíč pole, jehož typ jí odpovídá.
 *
 * Nehádá: pokud typ vyhovuje víc než jednomu z původně kolidujících polí (obě byla
 * stejného typu) nebo žádnému, hodnota zůstane tam, kde je. Hodnoty pod klíči, které
 * kolizí nebyly zasažené, se nikdy nemění.
 */
fun remapValuesAfterKeyDeduplication(
    originalFields: List<CustomFieldDefinition>,
    dedupedFields: List<CustomFieldDefinition>,
    values: Map<String, CustomFieldValue>,
): Map<String, CustomFieldValue> {
    if (originalFields.size != dedupedFields.size) return values

    val candidatesByOldKey = originalFields.indices
        .groupBy({ originalFields[it].key }) { dedupedFields[it] }
        .filterValues { it.size > 1 }
    if (candidatesByOldKey.isEmpty()) return values

    return values.entries.associate { (key, value) ->
        val target = candidatesByOldKey[key]
            ?.singleOrNull { it.accepts(value) }
            ?.key
            ?.takeIf { it != key && it !in values }
        if (target == null) key to value else target to value.withFieldKey(target)
    }
}

private fun CustomFieldDefinition.accepts(value: CustomFieldValue): Boolean = when (this) {
    is TextFieldDefinition -> value is TextValue
    is NumberFieldDefinition -> value is NumberValue
    is BooleanFieldDefinition -> value is BooleanValue
    is TimeRangeFieldDefinition -> value is TimeRangeValue
}

private fun CustomFieldValue.withFieldKey(key: String): CustomFieldValue = when (this) {
    is TextValue -> copy(fieldKey = key)
    is NumberValue -> copy(fieldKey = key)
    is BooleanValue -> copy(fieldKey = key)
    is TimeRangeValue -> copy(fieldKey = key)
}
