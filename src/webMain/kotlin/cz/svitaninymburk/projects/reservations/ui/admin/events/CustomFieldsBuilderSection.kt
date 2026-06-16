package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.*
import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.form.check.checkBox
import dev.kilua.form.number.numeric
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.html.*
import web.html.HTMLSelectElement

/**
 * Sdílený builder pro custom fields — používán ve všech admin formulářích pro eventy.
 *
 * @param customFields aktuální seznam polí
 * @param onCustomFieldsChange callback volaný při jakékoliv změně (přidání, odebrání, úprava)
 */
@Composable
fun IComponent.CustomFieldsBuilderSection(
    customFields: List<CustomFieldDefinition>,
    onCustomFieldsChange: (List<CustomFieldDefinition>) -> Unit,
) {
    val currentStrings by strings

    fun updateField(index: Int, newField: CustomFieldDefinition) {
        onCustomFieldsChange(customFields.toMutableList().apply { set(index, newField) })
    }

    div(className = "card bg-base-100 shadow-sm") {
        div(className = "card-body") {
            div(className = "flex justify-between items-center mb-4") {
                h2(className = "card-title text-lg") { +currentStrings.customFieldsHeading }

                div(className = "dropdown dropdown-end") {
                    div(className = "btn btn-sm btn-outline btn-secondary") {
                        attribute("tabindex", "0")
                        attribute("role", "button")
                        span(className = "icon-[heroicons--plus] size-4")
                        +currentStrings.addFieldButton
                    }
                    ul(className = "dropdown-content z-[1] menu p-2 shadow bg-base-100 rounded-box w-52") {
                        attribute("tabindex", "0")
                        li {
                            a {
                                onClick {
                                    onCustomFieldsChange(customFields + TextFieldDefinition("field_${customFields.size}", "Nové textové pole"))
                                }
                                +currentStrings.addTextField
                            }
                        }
                        li {
                            a {
                                onClick {
                                    onCustomFieldsChange(customFields + NumberFieldDefinition("field_${customFields.size}", "Nové číselné pole"))
                                }
                                +currentStrings.addNumberField
                            }
                        }
                        li {
                            a {
                                onClick {
                                    onCustomFieldsChange(customFields + BooleanFieldDefinition("field_${customFields.size}", "Nové zaškrtávací pole"))
                                }
                                +currentStrings.addBooleanField
                            }
                        }
                        li {
                            a {
                                onClick {
                                    onCustomFieldsChange(customFields + TimeRangeFieldDefinition("field_${customFields.size}", "Nový časový úsek"))
                                }
                                +currentStrings.addTimeRangeField
                            }
                        }
                    }
                }
            }

            if (customFields.isEmpty()) {
                p(className = "text-base-content/50 italic text-sm") { +currentStrings.noCustomFieldsMessage }
            } else {
                div(className = "flex flex-col gap-4") {
                    customFields.forEachIndexed { index, field ->
                        div(className = "flex gap-4 items-start p-4 bg-base-200/50 rounded-lg border border-base-300 relative") {

                            button(className = "btn btn-circle btn-ghost btn-xs absolute top-2 right-2 text-error") {
                                onClick {
                                    onCustomFieldsChange(customFields.toMutableList().apply { removeAt(index) })
                                }
                                span(className = "icon-[heroicons--trash] size-4")
                            }

                            div(className = "flex-1 grid grid-cols-1 md:grid-cols-2 gap-3") {
                                // Klíč pole
                                div(className = "form-control") {
                                    label(className = "label py-1") { span(className = "label-text text-xs") { +currentStrings.fieldKeyLabel } }
                                    text(value = field.key, className = "input input-sm input-bordered") {
                                        onInput {
                                            val newKey = value ?: ""
                                            updateField(index, when (field) {
                                                is TextFieldDefinition -> field.copy(key = newKey)
                                                is NumberFieldDefinition -> field.copy(key = newKey)
                                                is BooleanFieldDefinition -> field.copy(key = newKey)
                                                is TimeRangeFieldDefinition -> field.copy(key = newKey)
                                            })
                                        }
                                    }
                                }

                                // Popisek pole
                                div(className = "form-control") {
                                    label(className = "label py-1") { span(className = "label-text text-xs") { +currentStrings.fieldLabelLabel } }
                                    text(value = field.label, className = "input input-sm input-bordered") {
                                        onInput {
                                            val newLabel = value ?: ""
                                            updateField(index, when (field) {
                                                is TextFieldDefinition -> field.copy(label = newLabel)
                                                is NumberFieldDefinition -> field.copy(label = newLabel)
                                                is BooleanFieldDefinition -> field.copy(label = newLabel)
                                                is TimeRangeFieldDefinition -> field.copy(label = newLabel)
                                            })
                                        }
                                    }
                                }

                                // Povinné + typ badge
                                div(className = "form-control md:col-span-2") {
                                    label(className = "cursor-pointer label justify-start gap-2 py-1") {
                                        checkBox(value = field.isRequired, className = "checkbox checkbox-xs") {
                                            onChange {
                                                val req = value
                                                updateField(index, when (field) {
                                                    is TextFieldDefinition -> field.copy(isRequired = req)
                                                    is NumberFieldDefinition -> field.copy(isRequired = req)
                                                    is BooleanFieldDefinition -> field.copy(isRequired = req)
                                                    is TimeRangeFieldDefinition -> field.copy(isRequired = req)
                                                })
                                            }
                                        }
                                        span(className = "label-text text-xs") { +currentStrings.fieldRequired }

                                        when (field) {
                                            is TextFieldDefinition -> span(className = "badge badge-neutral badge-sm ml-4") { +currentStrings.fieldTypeText }
                                            is NumberFieldDefinition -> span(className = "badge badge-neutral badge-sm ml-4") { +currentStrings.fieldTypeNumber }
                                            is BooleanFieldDefinition -> span(className = "badge badge-neutral badge-sm ml-4") { +currentStrings.fieldTypeBoolean }
                                            is TimeRangeFieldDefinition -> span(className = "badge badge-neutral badge-sm ml-4") { +currentStrings.fieldTypeTimeRange }
                                        }
                                    }
                                }

                                // Specifické nastavení podle typu pole
                                when (field) {
                                    is TimeRangeFieldDefinition -> {
                                        div(className = "form-control md:col-span-2") {
                                            label(className = "cursor-pointer label justify-start gap-2 py-1") {
                                                checkBox(value = field.priceModifier is PriceModifier.TimeMultiplier, className = "checkbox checkbox-xs checkbox-accent") {
                                                    onChange {
                                                        updateField(index, field.copy(priceModifier = if (value) PriceModifier.TimeMultiplier else null))
                                                    }
                                                }
                                                span(className = "label-text text-xs") { +currentStrings.fieldTimeMultiplierToggle }
                                            }
                                        }
                                    }
                                    is BooleanFieldDefinition -> {
                                        div(className = "form-control md:col-span-2") {
                                            label(className = "cursor-pointer label justify-start gap-2 py-1") {
                                                checkBox(value = field.priceModifier is PriceModifier.FixedAmount, className = "checkbox checkbox-xs checkbox-accent") {
                                                    onChange {
                                                        updateField(index, field.copy(priceModifier = if (value) PriceModifier.FixedAmount(0.0) else null))
                                                    }
                                                }
                                                span(className = "label-text text-xs") { +currentStrings.fieldPriceModifierEnabled }
                                            }
                                        }
                                        if (field.priceModifier is PriceModifier.FixedAmount) {
                                            val fixedAmount = field.priceModifier as PriceModifier.FixedAmount
                                            div(className = "form-control") {
                                                label(className = "label py-1") { span(className = "label-text text-xs") { +currentStrings.fieldFlatFeeLabel } }
                                                numeric(value = fixedAmount.amount.takeIf { it > 0 }, min = 0, className = "input input-sm input-bordered w-full") {
                                                    onInput {
                                                        updateField(index, field.copy(priceModifier = PriceModifier.FixedAmount(value?.toDouble() ?: 0.0)))
                                                    }
                                                }
                                                p(className = "text-xs text-base-content/50 mt-1") { +currentStrings.fieldFlatFeeFormula }
                                            }
                                        }
                                    }
                                    is NumberFieldDefinition -> {
                                        // Modifier type selector
                                        div(className = "form-control md:col-span-2") {
                                            label(className = "label py-1") { span(className = "label-text text-xs") { +currentStrings.fieldPriceModifierEnabled } }
                                            select(className = "select select-sm select-bordered w-full") {
                                                option(value = "", label = currentStrings.fieldModifierNone) {
                                                    if (field.priceModifier == null) attribute("selected", "true")
                                                }
                                                option(value = "per_unit", label = currentStrings.fieldModifierPerUnit) {
                                                    if (field.priceModifier is PriceModifier.PerUnit) attribute("selected", "true")
                                                }
                                                option(value = "tiered", label = currentStrings.fieldModifierTiered) {
                                                    if (field.priceModifier is PriceModifier.TieredAmount) attribute("selected", "true")
                                                }
                                                onChange { event ->
                                                    val selectedValue = (event.target as? HTMLSelectElement)?.value
                                                    updateField(index, field.copy(priceModifier = when (selectedValue) {
                                                        "per_unit" -> PriceModifier.PerUnit(0.0)
                                                        "tiered" -> PriceModifier.TieredAmount(emptyList())
                                                        else -> null
                                                    }))
                                                }
                                            }
                                        }

                                        // PerUnit configuration (unchanged)
                                        if (field.priceModifier is PriceModifier.PerUnit) {
                                            val perUnit = field.priceModifier as PriceModifier.PerUnit
                                            div(className = "form-control") {
                                                label(className = "label py-1") { span(className = "label-text text-xs") { +currentStrings.fieldPerUnitPriceLabel } }
                                                numeric(value = perUnit.pricePerUnit.takeIf { it > 0 }, min = 0, className = "input input-sm input-bordered w-full") {
                                                    onInput {
                                                        updateField(index, field.copy(priceModifier = PriceModifier.PerUnit(value?.toDouble() ?: 0.0)))
                                                    }
                                                }
                                                p(className = "text-xs text-base-content/50 mt-1") { +currentStrings.fieldPerUnitFormula }
                                            }
                                        }

                                        // TieredAmount configuration
                                        if (field.priceModifier is PriceModifier.TieredAmount) {
                                            val tiered = field.priceModifier as PriceModifier.TieredAmount

                                            div(className = "md:col-span-2") {
                                                if (tiered.tiers.isNotEmpty()) {
                                                    div(className = "flex gap-2 mb-1") {
                                                        span(className = "label-text text-xs w-16") { +currentStrings.fieldTieredCountLabel }
                                                        span(className = "label-text text-xs flex-1") { +currentStrings.fieldTieredPriceLabel }
                                                    }
                                                    tiered.tiers.forEachIndexed { tierIndex, tier ->
                                                        div(className = "flex gap-2 items-center mb-1") {
                                                            numeric(value = tier.count.toDouble(), min = 1, className = "input input-xs input-bordered w-16") {
                                                                onInput {
                                                                    val newTiers = tiered.tiers.toMutableList().apply {
                                                                        set(tierIndex, tier.copy(count = value?.toInt() ?: 1))
                                                                    }
                                                                    updateField(index, field.copy(priceModifier = tiered.copy(tiers = newTiers)))
                                                                }
                                                            }
                                                            numeric(value = tier.price.takeIf { it > 0 }, min = 0, className = "input input-xs input-bordered flex-1") {
                                                                onInput {
                                                                    val newTiers = tiered.tiers.toMutableList().apply {
                                                                        set(tierIndex, tier.copy(price = value?.toDouble() ?: 0.0))
                                                                    }
                                                                    updateField(index, field.copy(priceModifier = tiered.copy(tiers = newTiers)))
                                                                }
                                                            }
                                                            button(className = "btn btn-ghost btn-xs text-error") {
                                                                onClick {
                                                                    val newTiers = tiered.tiers.toMutableList().apply { removeAt(tierIndex) }
                                                                    updateField(index, field.copy(priceModifier = tiered.copy(tiers = newTiers)))
                                                                }
                                                                span(className = "icon-[heroicons--x-mark] size-3")
                                                            }
                                                        }
                                                    }
                                                }

                                                button(className = "btn btn-xs btn-outline btn-secondary mt-1") {
                                                    onClick {
                                                        val nextCount = (tiered.tiers.maxOfOrNull { it.count } ?: 0) + 1
                                                        val newTiers = tiered.tiers + PriceModifier.TieredAmount.Tier(nextCount, 0.0)
                                                        updateField(index, field.copy(priceModifier = tiered.copy(tiers = newTiers)))
                                                    }
                                                    +currentStrings.fieldTieredAddTier
                                                }
                                            }

                                            div(className = "form-control") {
                                                label(className = "label py-1") { span(className = "label-text text-xs") { +currentStrings.fieldTieredFallbackLabel } }
                                                numeric(value = tiered.fallbackPerUnit.takeIf { it > 0 }, min = 0, className = "input input-sm input-bordered w-full") {
                                                    onInput {
                                                        updateField(index, field.copy(priceModifier = tiered.copy(fallbackPerUnit = value?.toDouble() ?: 0.0)))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
