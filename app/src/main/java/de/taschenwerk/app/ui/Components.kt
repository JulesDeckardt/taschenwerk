package de.taschenwerk.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    radius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(radius)
    var m = modifier
        .clip(shape)
        .background(p.card)
    if (p.cardRing != Color.Transparent) m = m.border(BorderStroke(1.dp, p.cardRing), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

@Composable
fun TwIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = LocalPal.current.ink, size: Dp = 24.dp, desc: String? = null) {
    Icon(icon, contentDescription = desc, tint = tint, modifier = modifier.size(size))
}

@Composable
fun Segmented(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .then(if (sel) Modifier.shadow(1.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) p.card else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (sel) p.ink else p.sub,
                    fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun Toggle(checked: Boolean, onToggle: (Boolean) -> Unit) {
    val p = LocalPal.current
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "toggle")
    Box(
        Modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) p.fill else p.line)
            .clickable { onToggle(!checked) }
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(26.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(if (p.dark && !checked) Color(0xFFC9CAD0) else Color.White)
        )
    }
}

@Composable
fun CircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    bg: Color = LocalPal.current.card,
    fg: Color = LocalPal.current.ink,
    iconSize: Dp = 24.dp,
    desc: String? = null,
    elevated: Boolean = false
) {
    val p = LocalPal.current
    var m = modifier.size(size)
    if (elevated) m = m.shadow(10.dp, CircleShape, ambientColor = p.fill, spotColor = p.fill)
    m = m.clip(CircleShape).background(bg)
    if (bg == p.card && p.cardRing != Color.Transparent) m = m.border(1.dp, p.cardRing, CircleShape)
    Box(m.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        TwIcon(icon, tint = fg, size = iconSize, desc = desc)
    }
}

@Composable
fun Fab(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, desc: String? = null) {
    val p = LocalPal.current
    CircleButton(icon, onClick, modifier, size = 64.dp, bg = p.fill, fg = p.onFill, iconSize = 28.dp, desc = desc, elevated = true)
}

@Composable
fun PillButton(
    text: String,
    icon: ImageVector?,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = LocalPal.current
    var m = modifier
        .height(56.dp)
        .clip(RoundedCornerShape(28.dp))
        .background(if (filled) p.fill else p.card)
    if (!filled && p.cardRing != Color.Transparent) m = m.border(1.dp, p.cardRing, RoundedCornerShape(28.dp))
    Row(
        m.clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            TwIcon(icon, tint = if (filled) p.onFill else p.ink, size = 20.dp)
            Box(Modifier.width(8.dp))
        }
        Text(text, color = if (filled) p.onFill else p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SmallButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.track)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, color = p.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun Divider() {
    val p = LocalPal.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(p.line)
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    var m = modifier
        .height(38.dp)
        .clip(RoundedCornerShape(19.dp))
        .background(if (selected) p.fill else p.card)
    if (!selected && p.cardRing != Color.Transparent) m = m.border(1.dp, p.cardRing, RoundedCornerShape(19.dp))
    Box(m.clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(
            text, color = if (selected) p.onFill else p.ink, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1
        )
    }
}

@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = num(18.sp, FontWeight.SemiBold),
    align: TextAlign = TextAlign.Center,
    boxed: Boolean = true,
    color: Color = LocalPal.current.ink
) {
    val p = LocalPal.current
    val m = if (boxed) modifier
        .height(44.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(p.track)
        .padding(horizontal = 8.dp) else modifier
    Box(m, contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value = value,
            onValueChange = { s -> onChange(s.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = style.copy(color = color, textAlign = align),
            cursorBrush = SolidColor(p.ink),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun StepperBox(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = p.sub, fontSize = 12.sp)
        Text(value, style = num(20.sp), color = p.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircleButton(Ic.minus, onMinus, size = 38.dp, bg = p.track, iconSize = 18.dp, desc = "$label kürzer")
            CircleButton(Ic.plus, onPlus, size = 38.dp, bg = p.track, iconSize = 18.dp, desc = "$label länger")
        }
    }
}

@Composable
fun RowItem(
    title: String,
    value: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val p = LocalPal.current
    var m = modifier
        .fillMaxWidth()
        .height(54.dp)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = p.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = p.sub, fontSize = 15.sp)
        if (trailing != null) trailing()
        else if (onClick != null) TwIcon(Ic.chevRight, tint = p.sub, size = 18.dp)
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}
