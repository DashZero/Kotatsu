
package org.koitharu.kotatsu.comments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource // Placeholder for image loading
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import org.koitharu.kotatsu.R // Assuming R is available for resources

@Composable
fun CommentScreen(viewModel: CommentViewModel) {
    val comments by viewModel.comments.collectAsState()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600

    Column(modifier = Modifier.fillMaxSize()) {
        if (comments.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Be the first to comment!")
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(comments) { comment ->
                    CommentItem(comment, isTablet, viewModel)
                }
            }
        }
        CommentInputBar(viewModel)
    }
}

@Composable
fun CommentItem(comment: Comment, isTablet: Boolean, viewModel: CommentViewModel) {
    val avatarSize = if (isTablet) 32.dp else 24.dp

    Row(modifier = Modifier.padding(8.dp)) {
        // Placeholder for avatar
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground), // Replace with actual avatar loading
            contentDescription = "Avatar",
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.userName, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "• ${java.text.SimpleDateFormat.getDateTimeInstance().format(java.util.Date(comment.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (comment.deleted) {
                Text("(This comment has been removed)", fontStyle = FontStyle.Italic)
            } else {
                Text(comment.text)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        var menuExpanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_vert), // Replace with your more_vert icon
                    contentDescription = "More options"
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(onClick = {
                    viewModel.reportComment(comment)
                    menuExpanded = false
                }, text = { Text("Report") })
                DropdownMenuItem(onClick = {
                    // Implement block user
                    menuExpanded = false
                }, text = { Text("Block User") })
                DropdownMenuItem(onClick = {
                    viewModel.deleteComment(comment)
                    menuExpanded = false
                }, text = { Text("Delete") })
            }
        }
    }
}

@Composable
fun CommentInputBar(viewModel: CommentViewModel) {
    var text by remember { mutableStateOf("") }
    val (userId, userName, avatarUrl) = "testUser" to "Test User" to null // Replace with actual user data from settings

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placeholder for user avatar
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground), // Replace with actual avatar loading
                contentDescription = "Your Avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add a comment...") },
                maxLines = 3
            )

            IconButton(onClick = {
                if (text.isNotBlank()) {
                    viewModel.sendComment(text, userId, userName, avatarUrl)
                    text = ""
                }
            }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_send), // Replace with your send icon
                    contentDescription = "Send"
                )
            }
        }
    }
}
