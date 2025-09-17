package org.koitharu.kotatsu.comments

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Composable
fun CommentScreen(mangaId: String) {
    val context = LocalContext.current
    val gunClient = remember { GunClient(context) }
    val commentRepository = remember { CommentRepository(context, gunClient, rememberCoroutineScope()) }
    val viewModel: CommentViewModel = viewModel(factory = CommentViewModel.Factory(context, mangaId, commentRepository))

    val comments by viewModel.comments.collectAsState()
    val inputMessage by viewModel.inputMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val localMutedUsers by viewModel.localMutedUsers.collectAsState()

    val isCommentsEnabled by CommentsSettings.isEnabled.collectAsState()
    val isUserBanned = CommentsSettings.isUserBanned()

    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        gunClient.start()
        CommentsSettings.init(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            gunClient.stop()
        }
    }

    if (!isCommentsEnabled) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Comments are disabled in settings.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Comments (${comments.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            IconButton(onClick = { showSettingsDialog = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        // Offline banner
        if (isOffline) {
            Text(
                text = "You\'re offline. Showing cached comments.",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 12.sp
            )
        }

        // Comments List
        if (comments.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Be the first to comment!", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(comments) {
                    CommentItem(comment = it, viewModel = viewModel, localMutedUsers = localMutedUsers)
                }
            }
        }

        // Input Bar
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }
            if (isUserBanned) {
                Text(
                    text = "You are banned until ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(CommentsSettings.getBannedUntil()))} due to community reports.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                // User Avatar (from settings)
                val userAvatarUrl by CommentsSettings.avatarUrl.collectAsState()
                val userName by CommentsSettings.displayName.collectAsState()
                val finalAvatarUrl = userAvatarUrl ?: CommentsSettings.getDiceBearAvatarUrl(userName)

                Image(
                    painter = rememberAsyncImagePainter(finalAvatarUrl),
                    contentDescription = "Your Avatar",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = viewModel::onInputMessageChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a comment...") },
                    maxLines = 3,
                    enabled = !isSending && !isUserBanned
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = viewModel::sendComment,
                    enabled = inputMessage.isNotBlank() && !isSending && !isUserBanned
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        CommentsSettingsDialog(onDismiss = { showSettingsDialog = false })
    }
}

@Composable
fun CommentItem(comment: Comment, viewModel: CommentViewModel, localMutedUsers: Set<String>) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val currentUserId = CommentsSettings.userId.collectAsState().value

    if (localMutedUsers.contains(comment.userId)) {
        // Do not render muted user's comments
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (comment.userId == currentUserId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = rememberAsyncImagePainter(comment.avatarUrl ?: CommentsSettings.getDiceBearAvatarUrl(comment.userName)),
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(28.dp) // Slightly larger for comment items
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = comment.userName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(comment.timestamp)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (comment.userId == currentUserId) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { viewModel.deleteComment(comment); showMenu = false })
                    }
                    DropdownMenuItem(text = { Text("Report") }, onClick = { viewModel.reportComment(comment); showMenu = false })
                    DropdownMenuItem(text = { Text("Block User") }, onClick = { viewModel.toggleMuteUser(comment.userId); showMenu = false })
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (comment.deleted) {
                Text(
                    text = "(This comment has been removed)",
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                MarkdownText(text = comment.text) { // Handle URL clicks
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                    context.startActivity(intent)
                }
            }
        }
    }
}

@Composable
fun MarkdownText(text: String, onUrlClick: (String) -> Unit) {
    val annotatedString = buildAnnotatedString {
        val boldPattern = Pattern.compile("\\*\\*(.*?)\\*\\*")
        val italicPattern = Pattern.compile("\\*(.*?)\\*")
        val linkPattern = Pattern.compile("\[(.*?)\]\\((.*?)\\)")
        val imagePattern = Pattern.compile("!\\[(.*?)\]\\((.*?)\\)")

        var currentIndex = 0
        val matches = mutableListOf<Pair<Int, Matcher>>()

        boldPattern.matcher(text).results().forEach { matches.add(it.range().first to it) }
        italicPattern.matcher(text).results().forEach { matches.add(it.range().first to it) }
        linkPattern.matcher(text).results().forEach { matches.add(it.range().first to it) }
        imagePattern.matcher(text).results().forEach { matches.add(it.range().first to it) }

        matches.sortBy { it.first }

        for ((_, match) in matches) {
            append(text.substring(currentIndex, match.start()))
            when (match.pattern()) {
                boldPattern -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(match.group(1))
                    pop()
                }
                italicPattern -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(match.group(1))
                    pop()
                }
                linkPattern -> {
                    val linkText = match.group(1)
                    val url = match.group(2)
                    pushStringAnnotation(tag = "URL", annotation = url)
                    pushStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
                    append(linkText)
                    pop()
                    pop()
                }
                imagePattern -> {
                    // For images, just render as a clickable link for now to avoid complex image loading without Coil dependency
                    val imageUrl = match.group(2)
                    val altText = match.group(1)
                    pushStringAnnotation(tag = "URL", annotation = imageUrl)
                    pushStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
                    append("[Image: $altText]")
                    pop()
                    pop()
                }
            }
            currentIndex = match.end()
        }
        append(text.substring(currentIndex, text.length))
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onUrlClick(annotation.item)
                }
        }
    )
}

@Composable
fun CommentsSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isEnabled by CommentsSettings.isEnabled.collectAsState()
    var displayName by remember { mutableStateOf(CommentsSettings.displayName.value) }
    var avatarUrl by remember { mutableStateOf(CommentsSettings.avatarUrl.value ?: "") }
    val enableModeration by CommentsSettings.enableModeration.collectAsState()
    var relayUrlsString by remember { mutableStateOf(CommentsSettings.relayUrls.value.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comment Settings") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Comments")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = isEnabled, onCheckedChange = { CommentsSettings.setEnabled(it) })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it },
                    label = { Text("Avatar URL (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Generate avatar automatically (DiceBear)")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = avatarUrl.isBlank(),
                        onCheckedChange = {
                            avatarUrl = if (it) "" else CommentsSettings.avatarUrl.value ?: ""
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Moderation")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = enableModeration, onCheckedChange = { CommentsSettings.setEnableModeration(it) })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = relayUrlsString,
                    onValueChange = { relayUrlsString = it },
                    label = { Text("Gun Relay URLs (comma-separated)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                CommentsSettings.setDisplayName(displayName)
                CommentsSettings.setAvatarUrl(avatarUrl.ifBlank { null })
                CommentsSettings.setRelayUrl(relayUrl)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Helper to remember CoroutineScope in a Composable
@Composable
fun rememberCoroutineScope(): CoroutineScope {
    return remember(Unit) { CoroutineScope(Dispatchers.Default) }
}
