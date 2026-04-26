package to.kuudere.anisuge.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anisurge.composeapp.generated.resources.Res
import anisurge.composeapp.generated.resources.logo_txt
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.painterResource
import to.kuudere.anisuge.platform.TvPairingQrCode
import to.kuudere.anisuge.platform.isAndroidTvPlatform
import to.kuudere.anisuge.theme.Border
import to.kuudere.anisuge.theme.Muted
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onLoginSuccess()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearInfo()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints {
            val isDesktop = maxWidth > 800.dp

            if (isAndroidTvPlatform) {
                TvQrAuthLayout(state = state, viewModel = viewModel)
            } else if (isDesktop) {
                // Desktop background — Frieren poster image
                coil3.compose.AsyncImage(
                    model = "https://artworks.thetvdb.com/banners/v4/series/424536/posters/64e6a8b95dfad.jpg",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Black.copy(alpha = 0.9f),
                                )
                            )
                        )
                )
                DesktopAuthLayout(state, viewModel)
            } else {
                // Linux Mobile background exactly like Flutter (Gradient + Dripping)
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0F0F0F),
                                    Color(0xFF0C0C0C),
                                    Color(0xFF080808),
                                    Color(0xFF050505),
                                    Color(0xFF000000),
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize().blur(10.dp)) {
                        DrippingBackground()
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.02f)))
                }
                MobileAuthLayout(state, viewModel)
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xFFBF80FF),
                contentColor = Color.White,
                shape = RoundedCornerShape(8.dp),
            )
        }
    }
}

@Composable
private fun TvQrAuthLayout(state: AuthUiState, viewModel: AuthViewModel) {
    var nowMillis by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    val remainingSeconds = ((state.tvPairingExpiresAtMillis - nowMillis).coerceAtLeast(0L) / 1000L).toInt()

    LaunchedEffect(Unit) {
        viewModel.startTvPairing()
    }

    LaunchedEffect(state.tvPairingExpiresAtMillis) {
        while (state.tvPairingExpiresAtMillis > 0L && Clock.System.now().toEpochMilliseconds() < state.tvPairingExpiresAtMillis) {
            nowMillis = Clock.System.now().toEpochMilliseconds()
            delay(1000)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopTvPairing() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .widthIn(max = 760.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(horizontal = 48.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.logo_txt),
                contentDescription = "Anisurge Logo",
                modifier = Modifier.height(56.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(28.dp))

            // Title changes based on state
            Text(
                when {
                    state.isSuccess -> "Welcome!"
                    state.tvPairingConnected -> "Connecting..."
                    else -> "Scan to sign in"
                },
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    state.isSuccess -> "Login successful. Redirecting..."
                    state.tvPairingConnected -> "Authenticating your account..."
                    else -> "Open Anisurge on your phone, go to Profile, and scan this TV QR."
                },
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(28.dp))

            // QR Code or Loading State
            when {
                state.isSuccess -> {
                    // Success state - show checkmark or loading
                    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color(0xFF4CAF50),
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
                state.tvPairingConnected -> {
                    // Connected state - show loading animation
                    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color(0xFFBF80FF),
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
                else -> {
                    // Waiting for scan
                    val payload = state.tvPairingPayload
                    if (payload == null) {
                        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    } else {
                        TvPairingQrCode(
                            payload = payload,
                            modifier = Modifier.size(280.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                state.errorMessage ?: state.tvPairingStatus,
                color = when {
                    state.errorMessage != null -> Color(0xFFFF6B6B)
                    state.isSuccess -> Color(0xFF4CAF50)
                    state.tvPairingConnected -> Color(0xFFBF80FF)
                    else -> Color.White.copy(alpha = 0.78f)
                },
                fontSize = 15.sp,
                fontWeight = if (state.tvPairingConnected || state.isSuccess) FontWeight.Medium else FontWeight.Normal,
            )
            Spacer(Modifier.height(8.dp))

            // Only show expiry when waiting for scan
            if (!state.tvPairingConnected && !state.isSuccess) {
                Text(
                    if (remainingSeconds > 0) "Expires in ${remainingSeconds}s" else "QR expired. Generate a new code.",
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 13.sp,
                )
            }

            // Only show regenerate button when not connected
            if (!state.tvPairingConnected && !state.isSuccess) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.startTvPairing() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Text("Regenerate QR", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Shared Desktop Layout ──────────────────────────────────────────────────
@Composable
private fun DesktopAuthLayout(state: AuthUiState, viewModel: AuthViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left side — promo content + carousel
        Row(
            modifier = Modifier.weight(3f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 60.dp, vertical = 40.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.logo_txt),
                    contentDescription = "Anisuge Logo",
                    modifier = Modifier.height(60.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(40.dp))
                Text(
                    text = "Stream, Discover &\nDownload",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontSize = 42.sp,
                    lineHeight = 46.sp,
                    letterSpacing = (-1.5).sp
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Find and download torrents, watch trailers, manage your list, search, browse and discover anime, watch together with friends and more, all in the same interface.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF999999),
                    fontSize = 16.sp,
                    lineHeight = 25.sp,
                )
            }
            Spacer(Modifier.width(60.dp))
            AnimeCarousel()
        }

        // Right side — angled dark form
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
                // custom shape for slanted edge using the exact math from flutter
                .graphicsLayer {
                    shadowElevation = 10f
                    clip = true
                    shape = object : Shape {
                        override fun createOutline(
                            size: Size,
                            layoutDirection: LayoutDirection,
                            density: Density
                        ): Outline {
                            val angle = 0.12f
                            val tanAngle = tan(angle)
                            val slantWidth = (size.height * tanAngle)
                            val path = Path().apply {
                                moveTo(slantWidth, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            return Outline.Generic(path)
                        }
                    }
                }
                .background(Color(0xFF000000)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .padding(horizontal = 50.dp, vertical = 32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AuthForm(state = state, viewModel = viewModel, centered = true)
            }
        }
    }
}

// ── Mobile layout ────────────────────────────────────────────────────────────
@Composable
private fun MobileAuthLayout(state: AuthUiState, viewModel: AuthViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Image(
                painter = painterResource(Res.drawable.logo_txt),
                contentDescription = "AnisugeLogo",
                modifier = Modifier.height(48.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(32.dp))
            AuthForm(state = state, viewModel = viewModel, centered = false)
        }
    }
}

// ── Form Content ─────────────────────────────────────────────────────────────
@Composable
private fun AuthForm(state: AuthUiState, viewModel: AuthViewModel, centered: Boolean) {
    val passwordFocus = remember { FocusRequester() }
    val resetCodeFocus = remember { FocusRequester() }

    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        AnimatedContent(
            targetState = state.mode,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "auth_title",
        ) { mode ->
            Text(
                text = when (mode) {
                    AuthMode.LOGIN -> "Welcome back"
                    AuthMode.REGISTER -> "Create account"
                    AuthMode.FORGOT_PASSWORD -> "Forgot password"
                    AuthMode.VERIFY_CODE -> "Verify code"
                    AuthMode.RESET_PASSWORD -> "Reset password"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = (-0.5).sp
            )
        }
        Spacer(Modifier.height(6.dp))
        AnimatedContent(
            targetState = state.mode,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "auth_subtitle",
        ) { mode ->
            Text(
                text = when (mode) {
                    AuthMode.LOGIN -> "Sign in to continue watching"
                    AuthMode.REGISTER -> "Join our streaming platform"
                    AuthMode.FORGOT_PASSWORD -> "Enter your email to reset your password"
                    AuthMode.VERIFY_CODE -> "Enter the 6-digit code sent to your email"
                    AuthMode.RESET_PASSWORD -> "Enter your new password"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF999999),
                fontSize = 14.sp,
                fontWeight = FontWeight.W400,
            )
        }
    }

    Spacer(Modifier.height(28.dp))

    // REGISTER mode fields
    if (state.mode == AuthMode.REGISTER) {
        AnisugTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = "Username",
            placeholder = "Enter your Username",
            imeAction = ImeAction.Next,
            onImeAction = { /* Focus next */ },
        )
        Spacer(Modifier.height(16.dp))
    }

    // Email field
    if (state.mode != AuthMode.RESET_PASSWORD && state.mode != AuthMode.VERIFY_CODE) {
        AnisugTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = "Email",
            placeholder = "Enter your email address",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            onImeAction = { passwordFocus.requestFocus() },
        )
        Spacer(Modifier.height(16.dp))
    }

    // VERIFY_CODE mode fields
    if (state.mode == AuthMode.VERIFY_CODE) {
        AnisugTextField(
            value = state.resetCode,
            onValueChange = viewModel::onResetCodeChange,
            label = "Reset Code",
            placeholder = "Enter 6-digit code",
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            onImeAction = { viewModel.submit() },
            focusRequester = resetCodeFocus,
        )
        Spacer(Modifier.height(16.dp))
        
        LaunchedEffect(Unit) {
            resetCodeFocus.requestFocus()
        }
    }

    // Password field
    if (state.mode == AuthMode.LOGIN || state.mode == AuthMode.REGISTER || state.mode == AuthMode.RESET_PASSWORD) {
        AnisugTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = if (state.mode == AuthMode.RESET_PASSWORD) "New Password" else "Password",
            placeholder = "Enter your password",
            isPassword = true,
            keyboardType = KeyboardType.Password,
            imeAction = if (state.mode == AuthMode.RESET_PASSWORD) ImeAction.Next else ImeAction.Done,
            onImeAction = { if (state.mode == AuthMode.RESET_PASSWORD) { /* focus confirm */ } else viewModel.submit() },
            focusRequester = passwordFocus,
        )
        Spacer(Modifier.height(16.dp))
    }

    // Confirm Password field
    if (state.mode == AuthMode.RESET_PASSWORD) {
        AnisugTextField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = "Confirm Password",
            placeholder = "Confirm your new password",
            isPassword = true,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { viewModel.submit() },
        )
        Spacer(Modifier.height(16.dp))
    }

    // Forgot password link
    if (state.mode == AuthMode.LOGIN) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(
                    onClick = { viewModel.setMode(AuthMode.FORGOT_PASSWORD) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Forgot password?",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W500,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    Spacer(Modifier.height(8.dp))

    Button(
        onClick = viewModel::submit,
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.6f),
            disabledContentColor = Color.Black,
        ),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
        } else {
            Text(
                text = when (state.mode) {
                    AuthMode.LOGIN -> "Sign in"
                    AuthMode.REGISTER -> "Create account"
                    AuthMode.FORGOT_PASSWORD -> "Send Reset Code"
                    AuthMode.VERIFY_CODE -> "Verify Code"
                    AuthMode.RESET_PASSWORD -> "Reset Password"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = 0.5.sp,
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        val text = when (state.mode) {
            AuthMode.LOGIN -> "Don't have an account? "
            AuthMode.REGISTER -> "Already have an account? "
            else -> ""
        }
        val actionText = when (state.mode) {
            AuthMode.LOGIN -> "Sign up"
            AuthMode.REGISTER -> "Sign in"
            else -> "Back to sign in"
        }
        
        if (text.isNotEmpty()) {
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
            )
        }
        TextButton(
            onClick = {
                if (state.mode == AuthMode.LOGIN || state.mode == AuthMode.REGISTER) viewModel.toggleMode()
                else viewModel.setMode(AuthMode.LOGIN)
            },
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Text(
                text = actionText,
                fontWeight = FontWeight.W600,
                fontSize = 13.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun AnisugTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    var showPassword by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = 0.2.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.W400,
            ),
            singleLine = true,
            visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            interactionSource = interactionSource,
            cursorBrush = SolidColor(Color.White),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .border(
                            width = if (isFocused) 1.5.dp else 1.dp,
                            color = if (isFocused) Color.White else Color(0xFF000000),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder, 
                                color = Color.White.copy(alpha = 0.7f), 
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W400,
                            )
                        }
                        innerTextField()
                    }
                    if (isPassword) {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = if (isFocused) Color.White else Color(0xFF666666),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

// ── Carousel ────────────────────────────────────────────────────────────────
private val animeList = listOf(
    "https://artworks.thetvdb.com/banners/v4/series/424536/posters/68d6d5b36aa2f.jpg",
    "https://artworks.thetvdb.com/banners/v4/series/377543/posters/655f6f3591801.jpg",
    "https://artworks.thetvdb.com/banners/v4/series/355480/posters/68aa38e36a087.jpg",
    "https://artworks.thetvdb.com/banners/v4/series/421069/posters/67026a480c6d1.jpg",
    "https://artworks.thetvdb.com/banners/v4/series/414221/posters/639d9b966b354.jpg",
)

@Composable
private fun AnimeCarousel() {
    val listState = rememberLazyListState()
    
    // Auto-scroll loop
    LaunchedEffect(Unit) {
        while (true) {
            if (listState.layoutInfo.totalItemsCount > 0) {
                // smooth continuous scrolling, very slow
                listState.animateScrollBy(
                    value = 300000f,
                    animationSpec = tween(durationMillis = 10000000, easing = LinearEasing)
                )
            } else {
                kotlinx.coroutines.delay(100)
            }
        }
    }

    Box(
        modifier = Modifier
            .width(150.dp)
            .fillMaxHeight()
            .graphicsLayer {
                rotationZ = Math.toDegrees(0.12).toFloat()
            }
    ) {
        // large number of elements to simulate infinite scrolling
        LazyColumn(
            state = listState,
            userScrollEnabled = false,
        ) {
            items(count = 1000) { index ->
                val url = animeList[index % animeList.size]
                Box(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    coil3.compose.AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                        error = painterResource(Res.drawable.logo_txt) // fallback missing icon easily
                    )
                }
            }
        }
    }
}

// ── Dripping background ──────────────────────────────────────────────────────
@Composable
private fun DrippingBackground() {
    val transition = rememberInfiniteTransition()
    val value by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val v = value
        
        val paintColor = Color.White.copy(alpha = 0.9f)
        val path = Path().apply {
            moveTo(0f, 0f)
            for (xi in 0..width.toInt()) {
                val x = xi.toFloat()
                val y = 40f +
                    (sin((x / width * 4 * PI) + (v * 2 * PI)) * 20f).toFloat() +
                    (sin((x / width * 8 * PI) + (v * 4 * PI)) * 10f).toFloat() +
                    max(0.0, sin((x / width * 10 * PI) + (v * 2 * PI)) * 60.0).toFloat()
                lineTo(x, y)
            }
            lineTo(width, 0f)
            close()
        }
        drawPath(path, paintColor)

        // Detached drops
        for (i in 0 until 5) {
            val dropX = width * (0.2f + i * 0.15f)
            val dropProgress = ((v * 2f + i * 0.3f) % 1.0f)
            val dropY = 60f + dropProgress * 200f

            if (dropY > 80f) {
                val dropSize = 4f + 4f * dropProgress
                drawOval(
                    color = Color.White.copy(alpha = 0.8f),
                    topLeft = Offset(dropX - dropSize, dropY - dropSize * 2),
                    size = Size(dropSize * 2, dropSize * 4)
                )
            }
        }
    }
}

// ── BoxWithConstraints shim ──────────────────────────────────────────────────
@Composable
private fun BoxWithConstraints(content: @Composable androidx.compose.foundation.layout.BoxWithConstraintsScope.() -> Unit) = 
    androidx.compose.foundation.layout.BoxWithConstraints(content = content)
