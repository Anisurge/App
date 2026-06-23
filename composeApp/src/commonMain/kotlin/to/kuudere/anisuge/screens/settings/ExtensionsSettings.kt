package to.kuudere.anisuge.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.extensions.CloudflareBypassDialog
import to.kuudere.anisuge.extensions.CloudflareBypassButton
import to.kuudere.anisuge.extensions.ExtensionEngine
import to.kuudere.anisuge.extensions.ExtensionManager
import to.kuudere.anisuge.extensions.ExtensionRepository
import to.kuudere.anisuge.extensions.ExtensionSource
import to.kuudere.anisuge.extensions.ExtensionRuntimeState
import to.kuudere.anisuge.theme.AppColors

private val EngineAccent = mapOf(
    ExtensionEngine.ANIYOMI to Color(0xFF3F51B5),
    ExtensionEngine.CLOUDSTREAM to Color(0xFF42A5F5),
    ExtensionEngine.MANGAYOMI to Color(0xFFE53935),
    ExtensionEngine.SORA to Color(0xFF8D6E63),
)

@Composable
fun ExtensionsSettings(modifier: Modifier = Modifier) {
    val manager = AppComponent.extensionManager
    val config by manager.config.collectAsState()
    val available by manager.available.collectAsState()
    val installed by manager.installed.collectAsState()
    val runtime by manager.runtime.state.collectAsState()
    val busy by manager.busy.collectAsState()
    val error by manager.error.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showRepoDialog by remember { mutableStateOf(false) }
    var showRuntimeDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showBypassDialog by remember { mutableStateOf(false) }
    var filterEngine by remember { mutableStateOf<ExtensionEngine?>(null) }
    var filterInstalled by remember { mutableStateOf<Boolean?>(null) }
    var showOnlyNsfw by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }

    fun guarded(action: suspend () -> Unit) {
        scope.launch {
            operationError = null
            operationMessage = null
            runCatching { action() }.onFailure { e ->
                val cause = generateSequence(e) { it.cause }.last()
                operationError = cause.message?.takeIf { it.isNotBlank() }
                    ?: cause::class.simpleName ?: "Extension operation failed"
            }
        }
    }

    if (showRepoDialog) {
        RepoManagementDialog(
            config = config.repositories,
            runtimeState = runtime,
            manager = manager,
            onDismiss = { showRepoDialog = false },
            onRemoveRepo = { repo -> runCatching { manager.removeRepository(repo) } },
            onInstallRuntime = { guarded { manager.runtime.install(force = runtime.installed) } },
            onRemoveRuntime = { guarded { manager.runtime.remove() } },
        )
    }

    if (showRuntimeDialog) {
        RuntimeStatusDialog(
            state = runtime,
            onDismiss = { showRuntimeDialog = false },
            onInstall = { guarded { manager.runtime.install(force = runtime.installed) } },
            onRemove = { guarded { manager.runtime.remove() } },
            onBypass = { showBypassDialog = true },
            logs = if (runtime.ready) manager.runtime.logs() else emptyList(),
        )
    }

    if (showBypassDialog) {
        CloudflareBypassDialog(
            onDismiss = { showBypassDialog = false },
            onBypass = { url ->
                showBypassDialog = false
                guarded { manager.runtime.openCloudflareBypass(url) }
            },
        )
    }

    if (showSortDialog) {
        SortFilterDialog(
            currentEngine = filterEngine,
            currentInstalled = filterInstalled,
            showNsfw = showOnlyNsfw,
            onDismiss = { showSortDialog = false },
            onApply = { engine, installedFilter, nsfw ->
                filterEngine = engine
                filterInstalled = installedFilter
                showOnlyNsfw = nsfw
                showSortDialog = false
            },
        )
    }

    Column(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Extensions",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            HeaderActionButton(Icons.AutoMirrored.Filled.Sort, "Sort & Filter", onClick = { showSortDialog = true })
            Spacer(Modifier.width(6.dp))
            HeaderActionButton(Icons.Default.Build, "Runtime", onClick = { showRuntimeDialog = true })
            Spacer(Modifier.width(6.dp))
            HeaderActionButton(Icons.Default.Settings, "Repositories", onClick = { showRepoDialog = true })
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = { Text("Search extensions", color = AppColors.textMuted) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.textMuted) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, "Clear", tint = AppColors.textMuted)
                    }
                }
            } else null,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.accent,
                unfocusedBorderColor = AppColors.border,
                cursorColor = AppColors.accent,
                focusedTextColor = AppColors.text,
                unfocusedTextColor = AppColors.text,
                focusedPlaceholderColor = AppColors.textMuted,
                unfocusedPlaceholderColor = AppColors.textMuted,
            ),
        )

        val filtered = available.filter { source ->
            val matchesQuery = query.isBlank() || source.name.contains(query, ignoreCase = true) ||
                source.language.contains(query, ignoreCase = true) ||
                source.engine.displayName.contains(query, ignoreCase = true)
            val matchesEngine = filterEngine == null || source.engine == filterEngine
            val matchesInstalled = filterInstalled == null || source.installed == filterInstalled
            val matchesNsfw = !showOnlyNsfw || source.isNsfw
            matchesQuery && matchesEngine && matchesInstalled && matchesNsfw
        }.sortedWith(compareByDescending<ExtensionSource> { it.installed }.thenBy { it.name })

        if (busy && filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.accent)
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Extension, null, tint = AppColors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (config.repositories.isEmpty()) "Add a repository to discover extensions."
                        else "No extensions found.",
                        color = AppColors.textMuted,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { "${it.engine.name}:${it.id}" }) { source ->
                    ExtensionSourceCard(
                        source = source,
                        isBusy = busy,
                        onInstall = { guarded { manager.install(source) } },
                        onUninstall = { guarded { manager.uninstall(source) } },
                        onTest = { guarded { operationMessage = manager.testSource(source) } },
                    )
                }
            }
        }

        operationError?.let {
            Text(it, color = AppColors.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        operationMessage?.let {
            Text(it, color = AppColors.accent, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun HeaderActionButton(icon: ImageVector, contentDesc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.accent.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Icon(icon, contentDesc, tint = AppColors.accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ExtensionSourceCard(
    source: ExtensionSource,
    isBusy: Boolean = false,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onTest: () -> Unit,
) {
    val accent = EngineAccent[source.engine] ?: AppColors.accent
    val engineName = source.engine.displayName

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExtensionIcon(source = source, accent = accent, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    source.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(text = engineName, color = accent)
                    InfoChip(text = source.language.uppercase(), color = AppColors.textMuted)
                    if (source.version.isNotBlank()) {
                        InfoChip(
                            text = if (source.version.startsWith("v")) source.version else "v${source.version}",
                            color = AppColors.accent,
                        )
                    }
                    if (source.isNsfw) {
                        InfoChip(text = "18+", color = Color(0xFFE53935))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (source.installed) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (source.hasUpdate) {
                        ActionIconButton(
                            icon = Icons.Default.Update,
                            containerColor = AppColors.accent.copy(alpha = 0.2f),
                            iconColor = AppColors.accent,
                            onClick = onInstall,
                            enabled = !isBusy,
                        )
                    }
                    ActionIconButton(
                        icon = Icons.Default.Build,
                        containerColor = AppColors.surfaceVariant,
                        iconColor = AppColors.text,
                        onClick = onTest,
                        enabled = !isBusy,
                    )
                    ActionIconButton(
                        icon = Icons.Default.Delete,
                        containerColor = Color(0xFFE53935).copy(alpha = 0.2f),
                        iconColor = Color(0xFFE53935),
                        onClick = onUninstall,
                        enabled = !isBusy,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (source.engine.nativeRuntime) AppColors.accent.copy(alpha = 0.2f)
                            else AppColors.surfaceVariant
                        ),
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = AppColors.accent,
                        )
                    } else {
                        IconButton(
                            onClick = onInstall,
                            enabled = source.engine.nativeRuntime,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.Download, null, tint =
                                if (source.engine.nativeRuntime) AppColors.accent else AppColors.textMuted,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionIcon(source: ExtensionSource, accent: Color, modifier: Modifier = Modifier) {
    val outerSize = 48.dp
    val innerSize = 44.dp
    val badgeSize = 14.dp
    val dotSize = 8.dp

    Box(
        modifier = modifier.size(outerSize).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(innerSize).clip(RoundedCornerShape(10.dp)).background(AppColors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (!source.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = source.iconUrl,
                    contentDescription = source.name,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Default.Extension, null, tint = AppColors.textMuted, modifier = Modifier.size(22.dp))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(dotSize).clip(CircleShape).background(AppColors.surface),
                )
            }
        }
    }
}

@Composable
private fun InfoChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionIconButton(
    icon: ImageVector,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 36.dp,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RepoManagementDialog(
    config: List<ExtensionRepository>,
    runtimeState: ExtensionRuntimeState,
    manager: ExtensionManager,
    onDismiss: () -> Unit,
    onRemoveRepo: suspend (ExtensionRepository) -> Unit,
    onInstallRuntime: () -> Unit,
    onRemoveRuntime: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var repoError by remember { mutableStateOf<String?>(null) }
    var showWarning by remember { mutableStateOf(false) }
    var pendingUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (showWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("Third-party extension warning") },
            text = {
                Text(
                    "Extensions execute third-party code and may access provider websites. " +
                        "Only add repositories you trust. Anisurge does not provide, review, or endorse their content.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showWarning = false
                    scope.launch {
                        adding = true
                        repoError = null
                        try {
                            manager.acceptWarning()
                            manager.addRepositoryAutoDetect(pendingUrl.trim())
                            url = ""
                            onDismiss()
                        } catch (e: Throwable) {
                            val cause = generateSequence(e) { it.cause }.last()
                            repoError = cause.message?.takeIf { it.isNotBlank() }
                                ?: cause::class.simpleName ?: "Failed to add repository"
                        } finally {
                            adding = false
                        }
                    }
                }) { Text("I understand") }
            },
            dismissButton = { TextButton(onClick = { showWarning = false }) { Text("Cancel") } },
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.background),
        ) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()).width(400.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repositories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = AppColors.textMuted)
                    }
                }

                RuntimeStatusInline(state = runtimeState, onInstall = onInstallRuntime, onRemove = onRemoveRuntime)

                Text("ADD REPOSITORY", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AppColors.textMuted)

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://raw.githubusercontent.com/...", color = AppColors.textMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = AppColors.border,
                        cursorColor = AppColors.accent,
                        focusedTextColor = AppColors.text,
                        unfocusedTextColor = AppColors.text,
                    ),
                )

                Button(
                    onClick = {
                        val repoUrl = url.trim()
                        if (!repoUrl.startsWith("https://") && !repoUrl.startsWith("http://")) {
                            repoError = "Enter a valid HTTP/HTTPS URL"
                            return@Button
                        }
                        if (config.any { it.url == repoUrl }) {
                            repoError = "Repository already added"
                            return@Button
                        }
                        if (!manager.config.value.acceptedWarning) {
                            pendingUrl = repoUrl
                            showWarning = true
                            return@Button
                        }
                        adding = true
                        repoError = null
                        scope.launch {
                            try {
                                manager.addRepositoryAutoDetect(repoUrl)
                                url = ""
                                onDismiss()
                            } catch (e: Throwable) {
                                val cause = generateSequence(e) { it.cause }.last()
                                repoError = cause.message?.takeIf { it.isNotBlank() }
                                    ?: cause::class.simpleName ?: "Failed to add repository"
                            } finally {
                                adding = false
                            }
                        }
                    },
                    enabled = url.isNotBlank() && !adding,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Repository")
                }
                if (adding) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                repoError?.let {
                    Text(it, color = AppColors.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Text(
                    "Engine type is detected automatically from the repository content.",
                    color = AppColors.textMuted, fontSize = 11.sp,
                )

                if (config.isEmpty()) {
                    Text("No repositories yet. Add one to discover extensions.", color = AppColors.textMuted, fontSize = 13.sp)
                } else {
                    config.forEach { repo ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.surface)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(AppColors.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Extension, null, tint = AppColors.textMuted, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    repo.name ?: repo.engine.displayName,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    repo.url,
                                    color = AppColors.textMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = {
                                scope.launch { onRemoveRepo(repo) }
                            }) {
                                Icon(Icons.Default.Delete, "Remove", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeStatusInline(
    state: ExtensionRuntimeState,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (state.installed) AppColors.accent.copy(alpha = 0.2f)
                            else Color(0xFFE53935).copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = AppColors.text,
                        )
                    } else {
                        Icon(
                            if (state.installed) Icons.Default.Download else Icons.Default.Warning,
                            null,
                            tint = if (state.installed) AppColors.accent else Color(0xFFE53935),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.busy) "Downloading Plugin..."
                        else if (state.installed) "Plugin Installed"
                        else "Plugin Not Installed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        if (state.busy) state.status
                        else if (state.installed) "Aniyomi & CloudStream ready"
                        else "Download plugin to unlock Aniyomi & CloudStream",
                        color = AppColors.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            if (state.busy && state.progress > 0f) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = AppColors.accent,
                    trackColor = AppColors.surfaceVariant,
                )
            }
            state.error?.let {
                Text(it, color = Color(0xFFE53935), fontSize = 12.sp)
            }
            if (state.installed && !state.busy) {
                state.version?.let { v ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Version", color = AppColors.textMuted, fontSize = 12.sp)
                        Text(v, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeStatusDialog(
    state: ExtensionRuntimeState,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    onBypass: () -> Unit,
    logs: List<String>,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.background),
        ) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()).width(400.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Runtime Helper", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = AppColors.textMuted)
                    }
                }

                RuntimeStatusInline(state = state, onInstall = onInstall, onRemove = onRemove)

                if (state.installed && !state.busy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onInstall,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                        ) {
                            Text("Repair / Update", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onRemove,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Remove", fontSize = 12.sp, color = Color(0xFFE53935))
                        }
                    }
                } else if (!state.installed && !state.busy) {
                    Button(
                        onClick = onInstall,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Install Plugin")
                    }
                }

                if (logs.isNotEmpty()) {
                    Text("LOGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AppColors.textMuted)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            logs.takeLast(6).forEach { line ->
                                Text(line, fontSize = 11.sp, color = AppColors.textMuted, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                if (state.installed && !state.busy) {
                    CloudflareBypassButton(onBypass = onBypass)
                }

                Text(
                    "Aniyomi and CloudStream use the helper. Mangayomi and Sora repositories can be inspected, " +
                        "but their Dart engines are not executable in this Kotlin build yet.",
                    color = AppColors.textMuted,
                    fontSize = 11.sp,
                )

                Text(state.status, color = AppColors.textMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SortFilterDialog(
    currentEngine: ExtensionEngine?,
    currentInstalled: Boolean?,
    showNsfw: Boolean,
    onDismiss: () -> Unit,
    onApply: (ExtensionEngine?, Boolean?, Boolean) -> Unit,
) {
    var selectedEngine by remember { mutableStateOf(currentEngine) }
    var selectedInstalled by remember { mutableStateOf(currentInstalled) }
    var selectedNsfw by remember { mutableStateOf(showNsfw) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.background),
        ) {
            Column(
                Modifier.padding(20.dp).width(350.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort & Filter", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = AppColors.textMuted)
                    }
                }

                Text("SOURCE TYPE", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AppColors.textMuted)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = selectedEngine == null,
                        onClick = { selectedEngine = null },
                        label = { Text("All", fontSize = 12.sp) },
                    )
                    ExtensionEngine.entries.forEach { value ->
                        FilterChip(
                            selected = selectedEngine == value,
                            onClick = { selectedEngine = value },
                            label = { Text(value.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = (EngineAccent[value] ?: AppColors.accent).copy(alpha = 0.3f),
                            ),
                        )
                    }
                }

                Text("STATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AppColors.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selectedInstalled == null, onClick = { selectedInstalled = null }, label = { Text("All", fontSize = 12.sp) })
                    FilterChip(selected = selectedInstalled == true, onClick = { selectedInstalled = true }, label = { Text("Installed", fontSize = 12.sp) })
                    FilterChip(selected = selectedInstalled == false, onClick = { selectedInstalled = false }, label = { Text("Available", fontSize = 12.sp) })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = selectedNsfw,
                        onCheckedChange = { selectedNsfw = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AppColors.accent),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Show 18+ only", fontSize = 13.sp)
                }

                Button(
                    onClick = { onApply(selectedEngine, selectedInstalled, selectedNsfw) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                ) {
                    Text("Apply")
                }
            }
        }
    }
}
