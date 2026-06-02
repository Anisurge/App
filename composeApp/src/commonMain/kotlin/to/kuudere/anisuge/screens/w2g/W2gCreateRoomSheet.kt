package to.kuudere.anisuge.screens.w2g

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.ImeAction
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
        ) {
            Text("Create a Room", color = Color.White, fontSize = 20.sp)

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = roomName,
                onValueChange = { roomName = it },
                label = { Text("Room Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = AppColors.accent,
                    unfocusedLabelColor = Color.Gray,
                    focusedBorderColor = AppColors.accent,
                    unfocusedBorderColor = Color.Gray,
                ),
            )

            Spacer(Modifier.height(12.dp))

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
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = Color.Red, fontSize = 13.sp)
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
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                enabled = !isLoading,
            ) {
                Text(if (isLoading) "Creating..." else "Create Room", fontSize = 16.sp)
            }
        }
    }
}
