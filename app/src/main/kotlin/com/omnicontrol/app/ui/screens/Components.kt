package com.omnicontrol.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicontrol.app.ui.theme.Palette

@Composable
fun Card(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.Card, shape)
            .border(1.dp, accent?.copy(alpha = 0.4f) ?: Palette.Stroke, shape)
            .padding(18.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp, top = 6.dp))
}

@Composable
fun PrimaryButton(text: String, color: Color = Palette.Violet, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (enabled) color else Palette.CardHigh, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.White else Palette.TextDim, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun GhostButton(text: String, color: Color = Palette.Cyan, onClick: () -> Unit) {
    Text(
        text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
fun CheckRow(title: String, subtitle: String, done: Boolean, optional: Boolean = false, action: String = "Allow", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).background(
                if (done) Palette.Green.copy(alpha = 0.18f) else (if (optional) Palette.CardHigh else Palette.Amber.copy(alpha = 0.16f)), CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(if (done) "✓" else if (optional) "·" else "!", color = if (done) Palette.Green else if (optional) Palette.TextDim else Palette.Amber, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Palette.TextDim, fontSize = 12.sp)
        }
        if (!done) GhostButton(action, onClick = onClick)
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Palette.TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Violet, checkedThumbColor = Color.White)
        )
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Text("‹", color = Palette.Text, fontSize = 32.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp, bottom = 4.dp))
        }
        Text(title, color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = Palette.Cyan, fontSize = 13.sp)
        Text(text, color = Palette.TextDim, fontSize = 13.sp)
    }
}
