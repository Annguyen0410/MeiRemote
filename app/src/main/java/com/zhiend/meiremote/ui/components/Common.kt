package com.zhiend.meiremote.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhiend.meiremote.data.ConnStatus
import com.zhiend.meiremote.ui.theme.LocalMeiColors

@Composable
fun MeiSectionCard(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalMeiColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.cardBg, RoundedCornerShape(16.dp))
            .border(1.dp, c.borderSoft, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = c.textMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
fun MeiFieldLabel(text: String) {
    val c = LocalMeiColors.current
    Text(text, color = c.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun MeiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    minLines: Int = 1,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    val c = LocalMeiColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder, color = c.textMuted) },
        minLines = minLines,
        singleLine = singleLine && minLines == 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.inputFocus,
            unfocusedBorderColor = c.inputBorder,
            focusedLabelColor = c.accentHover,
            unfocusedLabelColor = c.textMuted,
            cursorColor = c.accentHover,
            focusedContainerColor = c.inputBg,
            unfocusedContainerColor = c.inputBg,
            focusedTextColor = c.text,
            unfocusedTextColor = c.text,
        ),
    )
}

@Composable
fun MeiPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalMeiColors.current
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent,
            contentColor = c.buttonText,
            disabledContainerColor = c.mainBgAlt,
            disabledContentColor = c.textMuted,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.width(18.dp).height(18.dp),
                strokeWidth = 2.dp,
                color = c.buttonText,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MeiChipRow(
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val c = LocalMeiColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option, fontSize = 12.sp) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = c.mainBgAlt,
                    labelColor = c.textMuted,
                    selectedContainerColor = c.accentSoft,
                    selectedLabelColor = c.accentHover,
                ),
                border = BorderStroke(
                    1.dp,
                    if (option == selected) c.accent else c.borderSoft,
                ),
            )
        }
    }
}

@Composable
fun MeiStatusBanner(
    status: ConnStatus,
    statusText: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalMeiColors.current
    val (bg, fg, glyph) = when (status) {
        ConnStatus.ONLINE -> Triple(c.success, c.cardBg, "●")
        ConnStatus.CHECKING -> Triple(c.accentHover, c.cardBg, "◌")
        else -> Triple(c.danger, c.cardBg, "○")
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(
            statusText,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text("Kiểm tra", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MeiMessageBanner(message: String?, modifier: Modifier = Modifier) {
    if (message.isNullOrBlank()) return
    val c = LocalMeiColors.current
    Text(
        message,
        color = c.accentHover,
        fontSize = 12.sp,
        modifier = modifier
            .fillMaxWidth()
            .background(c.accentSoft, RoundedCornerShape(10.dp))
            .border(1.dp, c.inputBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
    )
}

@Composable
fun MeiPageHeader(title: String, subtitle: String = "") {
    val c = LocalMeiColors.current
    Column {
        Text(title, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = c.textMuted, fontSize = 12.sp)
        }
    }
}
