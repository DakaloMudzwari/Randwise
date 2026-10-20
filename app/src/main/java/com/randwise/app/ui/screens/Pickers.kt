package com.randwise.app.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.randwise.app.data.model.Category
import com.randwise.app.ui.components.ColorDot
import com.randwise.app.ui.components.categoryColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T?,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable (T) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        OutlinedTextField(
            value = selected?.let(display) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            singleLine = true,
            shape = com.randwise.app.ui.components.FieldShape,
            colors = com.randwise.app.ui.components.rwFieldColors(),
        )
        ExposedDropdownMenu(open, { open = false }, containerColor = com.randwise.app.ui.theme.Palette.Navy800, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            leading?.invoke(opt)
                            if (leading != null) Spacer(Modifier.width(8.dp))
                            Text(display(opt))
                        }
                    },
                    onClick = { onSelect(opt); open = false },
                )
            }
        }
    }
}

@Composable
fun CategoryDropdown(categories: List<Category>, selectedId: Long?, onSelect: (Long?) -> Unit, modifier: Modifier = Modifier, label: String = "Category") {
    val none = Category(id = -1, name = "Uncategorised", bucket = com.randwise.app.data.model.Bucket.VARIABLE, colorArgb = 0xFF94A3B8)
    val options = listOf(none) + categories
    Dropdown(
        label = label,
        options = options,
        selected = options.firstOrNull { it.id == (selectedId ?: -1) },
        display = { it.name },
        onSelect = { onSelect(if (it.id == -1L) null else it.id) },
        modifier = modifier,
        leading = { ColorDot(categoryColor(it.colorArgb)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier, label: String = "Date") {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = date.toString(), onValueChange = {}, readOnly = true, label = { Text(label) },
        trailingIcon = { TextButton({ open = true }) { Text("Change") } },
        modifier = modifier.fillMaxWidth(), singleLine = true,
        shape = com.randwise.app.ui.components.FieldShape,
        colors = com.randwise.app.ui.components.rwFieldColors(),
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
