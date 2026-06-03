package to.kuudere.anisuge.screens.w2g

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.W2gRoomCreateRequest
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

    var roomName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Create a room",
                color = AppColors.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "You will join as host and can pick the anime after the room opens.",
                color = AppColors.textMuted,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = roomName,
                onValueChange = {
                    roomName = it
                    error = null
                },
                label = { Text("Room Name") },
                leadingIcon = {
                    Icon(Icons.Outlined.Group, null, tint = AppColors.textMuted)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = createRoomFieldColors(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                label = { Text("Password (optional)") },
                supportingText = { Text("Leave blank for a public room.") },
                leadingIcon = {
                    Icon(Icons.Outlined.Lock, null, tint = AppColors.textMuted)
                },
                trailingIcon = {
                    if (password.isNotEmpty()) {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = AppColors.textMuted,
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                colors = createRoomFieldColors(),
            )

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = AppColors.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (roomName.isBlank()) {
                        error = "Room name is required"
                        return@Button
                    }
                    isLoading = true
                    error = null
                    scope.launch {
                        val request = W2gRoomCreateRequest(
                            roomName = roomName.trim(),
                            password = password.takeIf { it.isNotBlank() },
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent,
                    contentColor = AppColors.onAccent,
                    disabledContainerColor = AppColors.accent.copy(alpha = 0.45f),
                    disabledContentColor = AppColors.onAccent.copy(alpha = 0.72f),
                ),
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.onAccent,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isLoading) "Creating..." else "Create room", fontSize = 16.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun createRoomFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppColors.text,
    unfocusedTextColor = AppColors.text,
    focusedLabelColor = AppColors.accent,
    unfocusedLabelColor = AppColors.textMuted,
    focusedBorderColor = AppColors.accent,
    unfocusedBorderColor = AppColors.border,
    focusedSupportingTextColor = AppColors.textDim,
    unfocusedSupportingTextColor = AppColors.textDim,
    cursorColor = AppColors.accent,
)
