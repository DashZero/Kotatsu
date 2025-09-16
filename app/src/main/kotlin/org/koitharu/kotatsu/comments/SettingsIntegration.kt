
package org.koitharu.kotatsu.comments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Placeholder for DataStore
interface AppSettings {
    val commentsEnabled: State<Boolean>
    val moderationEnabled: State<Boolean>
    val displayName: State<String>
    val avatarUrl: State<String>
    val peerUrl: State<String>

    fun setCommentsEnabled(enabled: Boolean)
    fun setModerationEnabled(enabled: Boolean)
    fun setDisplayName(name: String)
    fun setAvatarUrl(url: String)
    fun setPeerUrl(url: String)
}

@Composable
fun CommentsSettings(settings: AppSettings) {
    var commentsEnabled by remember { mutableStateOf(settings.commentsEnabled.value) }
    var moderationEnabled by remember { mutableStateOf(settings.moderationEnabled.value) }
    var displayName by remember { mutableStateOf(settings.displayName.value) }
    var avatarUrl by remember { mutableStateOf(settings.avatarUrl.value) }
    var peerUrl by remember { mutableStateOf(settings.peerUrl.value) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Comments Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        SettingSwitch(
            title = "Enable Comments",
            checked = commentsEnabled,
            onCheckedChange = {
                commentsEnabled = it
                settings.setCommentsEnabled(it)
            }
        )

        SettingSwitch(
            title = "Enable Moderation",
            checked = moderationEnabled,
            onCheckedChange = {
                moderationEnabled = it
                settings.setModerationEnabled(it)
            }
        )

        SettingTextField(
            label = "Display Name",
            value = displayName,
            onValueChange = {
                displayName = it
                settings.setDisplayName(it)
            }
        )

        SettingTextField(
            label = "Avatar URL",
            value = avatarUrl,
            onValueChange = {
                avatarUrl = it
                settings.setAvatarUrl(it)
            }
        )

        SettingTextField(
            label = "Gun Peer URL",
            value = peerUrl,
            onValueChange = {
                peerUrl = it
                settings.setPeerUrl(it)
            }
        )
    }
}

@Composable
fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}
