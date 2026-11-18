package com.zadhron.adaa.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BrandPlum = Color(0xFF2D2029)
val BrandChampagne = Color(0xFFE8CFAB)
val BrandSage = Color(0xFF386652)

@Composable
fun BrandWordmark(modifier: Modifier = Modifier, inverse: Boolean = false) {
    Text("adaa", modifier, color = if (inverse) BrandChampagne else MaterialTheme.colorScheme.onBackground,
        fontFamily = FontFamily.Serif, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.6).sp)
}

@Composable
fun JourneyPage(title: String, subtitle: String, eyebrow: String? = null,
    onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Go back")
            }
            Spacer(Modifier.weight(1f)); BrandWordmark(); Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(top = 14.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            eyebrow?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp) }
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          content()
        }
    }
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true, busy: Boolean = false,
    modifier: Modifier = Modifier, arrow: Boolean = true, inverse: Boolean = false) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled && !busy,
        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 15.dp),
        colors = if (inverse) ButtonDefaults.buttonColors(containerColor = BrandChampagne, contentColor = BrandPlum) else ButtonDefaults.buttonColors()) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        else {
            Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (arrow) Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp))
        }
    }
}

@Composable
fun SecondaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 54.dp), enabled = enabled,
        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        icon?.let { Icon(it, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun FormField(value: String, onValueChange: (String) -> Unit, label: String,
    helper: String? = null, error: String? = null, password: Boolean = false,
    email: Boolean = false, singleLine: Boolean = true, enabled: Boolean = true, tag: String = label, inputType: KeyboardType? = null) {
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth().testTag(tag),
        enabled = enabled, label = { Text(label) }, isError = error != null, singleLine = singleLine,
        minLines = if (singleLine) 1 else 3, maxLines = if (singleLine) 1 else 6,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = inputType ?: when { password -> KeyboardType.Password; email -> KeyboardType.Email; else -> KeyboardType.Text }),
        visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password) ({ IconButton(onClick = { reveal = !reveal }) {
            Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                if (reveal) "Hide password" else "Show password")
        } }) else null,
        supportingText = if (helper != null || error != null) ({ Text(error ?: helper.orEmpty()) }) else null)
}

@Composable
fun InfoPanel(title: String, description: String, icon: ImageVector = Icons.Outlined.Info) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatusPill(status: String) {
    val label = when (status) { "published" -> "Live"; "draft" -> "Draft"; "suspended" -> "Suspended"; "unpublished" -> "Not published"; else -> status.replaceFirstChar { it.uppercase() } }
    val live = status == "published"
    Surface(shape = RoundedCornerShape(50), color = if (live) Color(0xFFE4F0E9) else MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (live) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                null, Modifier.size(14.dp), tint = if (live) BrandSage else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = if (live) BrandSage else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, icon: ImageVector? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        icon?.let { Icon(it, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun EmptyPanel(title: String, description: String, icon: ImageVector = Icons.Outlined.Storefront,
    action: String? = null, onAction: () -> Unit = {}, enabled: Boolean = true) {
    Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(Modifier.size(64.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.let { TextButton(onClick = onAction, enabled = enabled) { Text(it) } }
    }
}

@Composable
fun StorefrontArtwork(modifier: Modifier = Modifier) {
    // Decorative vector artwork scales sharply on every device; no remote images.
    Canvas(modifier) {
        val sx = size.width / 360f; val sy = size.height / 230f
        fun x(v: Float) = v * sx
        fun y(v: Float) = v * sy
        drawCircle(BrandChampagne.copy(alpha = 0.07f), x(110f), androidx.compose.ui.geometry.Offset(x(300f), y(40f)))
        drawCircle(BrandChampagne.copy(alpha = 0.06f), x(100f), androidx.compose.ui.geometry.Offset(x(30f), y(240f)))
        drawLine(BrandChampagne.copy(alpha = 0.22f), androidx.compose.ui.geometry.Offset(x(38f), y(197f)), androidx.compose.ui.geometry.Offset(x(322f), y(197f)), 1.dp.toPx())
        drawRoundRect(Color(0xFFE1C5AC), androidx.compose.ui.geometry.Offset(x(100f), y(65f)), androidx.compose.ui.geometry.Size(x(160f), y(130f)), androidx.compose.ui.geometry.CornerRadius(x(9f)))
        val roof = Path().apply { moveTo(x(88f), y(67f)); lineTo(x(104f), y(37f)); lineTo(x(256f), y(37f)); lineTo(x(272f), y(67f)); close() }
        drawPath(roof, Color(0xFFAA7179))
        repeat(6) { index ->
            drawRoundRect(if (index % 2 == 0) Color(0xFFB77982) else Color(0xFFF0DBC5),
                androidx.compose.ui.geometry.Offset(x(88f + index * 30.66f), y(66f)),
                androidx.compose.ui.geometry.Size(x(30.66f), y(24f)), androidx.compose.ui.geometry.CornerRadius(x(5f)))
        }
        drawRoundRect(BrandPlum, androidx.compose.ui.geometry.Offset(x(122f), y(111f)), androidx.compose.ui.geometry.Size(x(51f), y(84f)), androidx.compose.ui.geometry.CornerRadius(x(25f)))
        drawRect(BrandPlum, androidx.compose.ui.geometry.Offset(x(122f), y(154f)), androidx.compose.ui.geometry.Size(x(51f), y(41f)))
        drawRoundRect(Color(0xFFF7E9D8), androidx.compose.ui.geometry.Offset(x(191f), y(108f)), androidx.compose.ui.geometry.Size(x(48f), y(51f)), androidx.compose.ui.geometry.CornerRadius(x(4f)))
        drawLine(Color(0xFFBC9E89), androidx.compose.ui.geometry.Offset(x(215f), y(108f)), androidx.compose.ui.geometry.Offset(x(215f), y(159f)), x(2f))
        drawLine(Color(0xFFBC9E89), androidx.compose.ui.geometry.Offset(x(191f), y(133f)), androidx.compose.ui.geometry.Offset(x(239f), y(133f)), y(2f))
        drawCircle(BrandChampagne, x(2f), androidx.compose.ui.geometry.Offset(x(161f), y(160f)))
        drawRoundRect(Color(0xFFB87C66), androidx.compose.ui.geometry.Offset(x(276f), y(174f)), androidx.compose.ui.geometry.Size(x(24f), y(22f)), androidx.compose.ui.geometry.CornerRadius(x(3f)))
        drawOval(Color(0xFF8EAA91), androidx.compose.ui.geometry.Offset(x(276f), y(137f)), androidx.compose.ui.geometry.Size(x(15f), y(39f)))
        drawOval(Color(0xFF6F8E79), androidx.compose.ui.geometry.Offset(x(285f), y(144f)), androidx.compose.ui.geometry.Size(x(16f), y(32f)))
        drawCircle(BrandChampagne.copy(alpha = .6f), x(2f), androidx.compose.ui.geometry.Offset(x(63f), y(53f)))
        drawCircle(BrandChampagne.copy(alpha = .6f), x(3f), androidx.compose.ui.geometry.Offset(x(298f), y(109f)), style = Stroke(x(1f)))
    }
}

fun operatingModelLabel(model: String) = when (model) {
    "physical" -> "At a physical location"; "service_area" -> "Across a service area"; else -> "Online"
}
