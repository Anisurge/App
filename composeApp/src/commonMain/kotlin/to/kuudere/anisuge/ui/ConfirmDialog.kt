package to.kuudere.anisuge.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import to.kuudere.anisuge.i18n.LocalAppStrings

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    dismissLabel: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDanger: Boolean = true,
) {
    val strings = LocalAppStrings.current
    val resolvedDismissLabel = if (dismissLabel == "Cancel") strings.cancel else dismissLabel
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
            Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(
                    onClick = onDismiss,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(220)) + scaleIn(
                    initialScale = 0.88f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                ),
                exit = fadeOut(tween(180)) + scaleOut(
                    targetScale = 0.88f,
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF000000))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                        .clickable(
                            onClick = {},
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                ) {
                    // Title + message
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = message,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center,
                        )
                    }

                    // Divider
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))

                    // Action row
                    Row(modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        DialogActionCell(
                            label = resolvedDismissLabel,
                            icon = Icons.Default.Close,
                            modifier = Modifier.weight(1f),
                            onClick = onDismiss,
                        )
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.08f)))
                        DialogActionCell(
                            label = confirmLabel,
                            icon = Icons.Default.Delete,
                            modifier = Modifier.weight(1f),
                            isPrimary = true,
                            onClick = onConfirm,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogActionCell(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (isPrimary) Color.White else Color.White.copy(alpha = 0.65f),
        animationSpec = tween(200),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
            Text(text = label, color = tint, fontSize = 13.sp, fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
