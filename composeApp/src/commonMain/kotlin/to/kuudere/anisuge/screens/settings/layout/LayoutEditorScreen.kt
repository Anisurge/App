package to.kuudere.anisuge.screens.settings.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.i18n.LocalAppStrings
import to.kuudere.anisuge.screens.settings.SettingsViewModel
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.ui.ConfirmDialog

@Composable
fun LayoutEditorScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val uiState by settingsViewModel.uiState.collectAsState()
    var pendingLayout by remember { mutableStateOf(uiState.homeLayout) }
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.homeLayout) {
        pendingLayout = uiState.homeLayout
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = strings.homeLayoutResetConfirmTitle,
            message = strings.homeLayoutResetConfirmMessage,
            confirmLabel = strings.resetToDefaults,
            onConfirm = {
                showResetConfirm = false
                settingsViewModel.resetHomeLayout()
            },
            onDismiss = { showResetConfirm = false },
            isDanger = false,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = AppColors.text)
            }
            Text(strings.homeLayout, color = AppColors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { showResetConfirm = true }) {
                Text(strings.resetToDefaults)
            }
        }

        uiState.layoutSaveError?.let {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF3A1212)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(strings.homeLayoutSaveFailed, color = AppColors.text, modifier = Modifier.weight(1f))
                Button(
                    onClick = settingsViewModel::retrySaveHomeLayout,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                ) { Text(strings.retry) }
                IconButton(onClick = settingsViewModel::dismissLayoutSaveError) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = AppColors.text)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            pendingLayout.rows.forEachIndexed { index, row ->
                LayoutEditorRow(
                    row = row,
                    index = index,
                    total = pendingLayout.rows.size,
                    onToggle = { visible ->
                        pendingLayout = pendingLayout.setVisible(row.id, visible)
                        settingsViewModel.setHomeLayout(pendingLayout)
                    },
                    onMoveUp = {
                        pendingLayout = pendingLayout.moveUp(row.id)
                        settingsViewModel.setHomeLayout(pendingLayout)
                    },
                    onMoveDown = {
                        pendingLayout = pendingLayout.moveDown(row.id)
                        settingsViewModel.setHomeLayout(pendingLayout)
                    },
                    onDragEnd = { y ->
                        val target = when {
                            y < -30f -> index - 1
                            y > 30f -> index + 1
                            else -> index
                        }.coerceIn(0, pendingLayout.rows.lastIndex)
                        if (target != index) {
                            pendingLayout = pendingLayout.reorder(index, target)
                            settingsViewModel.setHomeLayout(pendingLayout)
                        }
                    },
                )
            }
        }

        Box(Modifier.height(40.dp))
    }
}
