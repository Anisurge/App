package to.kuudere.anisuge.screens.w2g

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.W2gCreateRoomRequest
import to.kuudere.anisuge.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun W2gCreateRoomSheet(
    viewModel: W2gViewModel,
    onRoomCreated: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var animeId by remember { mutableStateOf("") }
    var episodeNumber by remember { mutableStateOf("1") }
    var server by remember { mutableStateOf("suzu") }
    var language by remember { mutableStateOf("sub") }
    var quality by remember { mutableStateOf("1080p") }
    var password by remember { mutableStateOf("") }
    var animeTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Create a Room", color = Color.White, fontSize = 20.sp)

            OutlinedTextField(
                value = animeId,
                onValueChange = { animeId = it },
                label = { Text("Anime ID / Slug") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = AppColors.accent,
                    unfocusedLabelColor = Color.Gray,
                    focusedBorderColor = AppColors.accent,
                    unfocusedBorderColor = Color.Gray,
                ),
            )

            OutlinedTextField(
                value = animeTitle,
                onValueChange = { animeTitle = it },
                label = { Text("Anime Title (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = AppColors.accent,
                    unfocusedLabelColor = Color.Gray,
                    focusedBorderColor = AppColors.accent,
                    unfocusedBorderColor = Color.Gray,
                ),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = episodeNumber,
                    onValueChange = { episodeNumber = it.filter { c -> c.isDigit() } },
                    label = { Text("Episode") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = AppColors.accent,
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = Color.Gray,
                    ),
                )

                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Server") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = AppColors.accent,
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = Color.Gray,
                    ),
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Lang (sub/dub)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = AppColors.accent,
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = Color.Gray,
                    ),
                )

                OutlinedTextField(
                    value = quality,
                    onValueChange = { quality = it },
                    label = { Text("Quality") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = AppColors.accent,
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = Color.Gray,
                    ),
                )
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (optional, leave blank for public)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = AppColors.accent,
                    unfocusedLabelColor = Color.Gray,
                    focusedBorderColor = AppColors.accent,
                    unfocusedBorderColor = Color.Gray,
                ),
            )

            if (error != null) {
                Text(error!!, color = Color.Red, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    val ep = episodeNumber.toIntOrNull() ?: 1
                    if (animeId.isBlank()) {
                        error = "Anime ID is required"
                        return@Button
                    }
                    isLoading = true
                    error = null
                    scope.launch {
                        val request = W2gCreateRoomRequest(
                            animeId = animeId.trim(),
                            episodeNumber = ep,
                            server = server.trim(),
                            language = language.takeIf { it.isNotBlank() },
                            quality = quality.takeIf { it.isNotBlank() },
                            password = password.takeIf { it.isNotBlank() },
                            animeTitle = animeTitle.takeIf { it.isNotBlank() },
                        )
                        val code = viewModel.createRoom(request)
                        isLoading = false
                        if (code != null) {
                            onRoomCreated(code)
                        } else {
                            error = "Failed to create room"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                enabled = !isLoading,
            ) {
                Text(if (isLoading) "Creating..." else "Create Room", fontSize = 16.sp)
            }
        }
    }
}
