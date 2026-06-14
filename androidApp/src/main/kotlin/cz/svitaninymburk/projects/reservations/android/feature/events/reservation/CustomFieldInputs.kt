package cz.svitaninymburk.projects.reservations.android.feature.events.reservation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.svitaninymburk.projects.reservations.android.R
import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.BooleanValue
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.NumberFieldDefinition
import cz.svitaninymburk.projects.reservations.event.NumberValue
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextValue
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TimeRangeValue
import kotlinx.datetime.LocalTime

@Composable
fun CustomFieldInput(
    definition: CustomFieldDefinition,
    value: CustomFieldValue?,
    onChange: (CustomFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (definition) {
        is TextFieldDefinition -> OutlinedTextField(
            value = (value as? TextValue)?.value ?: "",
            onValueChange = { onChange(TextValue(fieldKey = definition.key, value = it)) },
            label = { Text(fieldLabel(definition)) },
            singleLine = !definition.isMultiline,
            minLines = if (definition.isMultiline) 3 else 1,
            modifier = modifier.fillMaxWidth(),
        )

        is NumberFieldDefinition -> OutlinedTextField(
            value = (value as? NumberValue)?.value?.let { formatNumber(it) } ?: "",
            onValueChange = { raw ->
                raw.replace(',', '.').toFloatOrNull()?.let { typed ->
                    val min = definition.min?.toFloat() ?: -Float.MAX_VALUE
                    val max = definition.max?.toFloat() ?: Float.MAX_VALUE
                    onChange(NumberValue(fieldKey = definition.key, value = typed.coerceIn(min, max)))
                }
            },
            label = { Text(fieldLabel(definition)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = modifier.fillMaxWidth(),
        )

        is BooleanFieldDefinition -> {
            val checked = (value as? BooleanValue)?.value == true
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = { onChange(BooleanValue(fieldKey = definition.key, value = it)) },
                    ),
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(fieldLabel(definition), style = MaterialTheme.typography.bodyMedium)
            }
        }

        is TimeRangeFieldDefinition -> TimeRangeInput(
            definition = definition,
            value = value as? TimeRangeValue,
            onChange = onChange,
            modifier = modifier,
        )
    }
}

@Composable
private fun TimeRangeInput(
    definition: TimeRangeFieldDefinition,
    value: TimeRangeValue?,
    onChange: (CustomFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingFrom by remember { mutableStateOf<Boolean?>(null) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            fieldLabel(definition),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { editingFrom = true }) {
            Text(value?.from?.let { formatTime(it) } ?: stringResource(R.string.reservation_form_time_from))
        }
        OutlinedButton(onClick = { editingFrom = false }) {
            Text(value?.to?.let { formatTime(it) } ?: stringResource(R.string.reservation_form_time_to))
        }
    }

    editingFrom?.let { isFrom ->
        key(isFrom) {
            val initial = (if (isFrom) value?.from else value?.to) ?: LocalTime(12, 0)
            val state = rememberTimePickerState(
                initialHour = initial.hour,
                initialMinute = initial.minute,
                is24Hour = true,
            )
            AlertDialog(
                onDismissRequest = { editingFrom = null },
                title = { Text(stringResource(R.string.reservation_form_time_pick)) },
                text = { TimeInput(state = state) },
                confirmButton = {
                    TextButton(onClick = {
                        val picked = LocalTime(state.hour, state.minute)
                        onChange(
                            if (isFrom) TimeRangeValue(definition.key, from = picked, to = value?.to ?: picked)
                            else TimeRangeValue(definition.key, from = value?.from ?: picked, to = picked)
                        )
                        editingFrom = null
                    }) { Text(stringResource(R.string.reservation_form_dialog_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { editingFrom = null }) {
                        Text(stringResource(R.string.reservation_form_dialog_cancel))
                    }
                },
            )
        }
    }
}

private fun fieldLabel(definition: CustomFieldDefinition): String =
    if (definition.isRequired) "${definition.label} *" else definition.label

private fun formatTime(time: LocalTime): String =
    "%d:%02d".format(time.hour, time.minute)

private fun formatNumber(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
