package app.it.fast4x.rimusic.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import org.jetbrains.compose.resources.painterResource
import rimusic.composeapp.generated.resources.Res
import rimusic.composeapp.generated.resources.total_plays

@Composable
internal fun CubicProfilePage(
    username: String,
    libraryCount: Int,
    downloadCount: Int,
    favoriteCount: Int,
    totalPlays: Int,
    onOpenSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Profile", color = CubicColors.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CubicColors.PanelRaised).padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            CubicLogo(66.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(username.ifBlank { "Cubic Music listener" }, color = CubicColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Local desktop profile • $totalPlays plays", color = CubicColors.TextSecondary, fontSize = 12.sp)
            }
            Image(
                painter = painterResource(Res.drawable.total_plays),
                contentDescription = "Total plays",
                modifier = Modifier.size(54.dp),
                contentScale = ContentScale.Fit
            )
            CubicActionTile("Settings", Icons.Rounded.Settings, onOpenSettings)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CubicStatCard("Library", "$libraryCount tracks", Icons.Rounded.LibraryMusic, Modifier.weight(1f))
            CubicStatCard("Favorites", "$favoriteCount tracks", Icons.Rounded.FavoriteBorder, Modifier.weight(1f))
            CubicStatCard("Downloaded", "$downloadCount offline", Icons.Rounded.DownloadDone, Modifier.weight(1f))
            CubicStatCard("Total plays", "$totalPlays sessions", Icons.Rounded.PlaylistPlay, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun CubicSettingsPage(
    downloadCount: Int,
    username: String,
    keepSidebarExpanded: Boolean,
    onUsernameSave: (String) -> Unit,
    onKeepSidebarExpandedChange: (Boolean) -> Unit,
    onClearData: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    var draftName by remember(username) { mutableStateOf(username) }
    var saved by remember(username) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", color = CubicColors.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        CubicSettingsCard("Profile", Icons.Rounded.Person) {
            Text("Your profile is stored locally on this computer.", color = CubicColors.TextSecondary, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = draftName,
                    onValueChange = { draftName = it; saved = false },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Display name") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CubicColors.Selection,
                        unfocusedContainerColor = CubicColors.Selection,
                        focusedIndicatorColor = CubicColors.Accent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = CubicColors.Text,
                        unfocusedTextColor = CubicColors.Text
                    )
                )
                Button(onClick = { onUsernameSave(draftName); saved = true }) { Text(if (saved) "Saved" else "Save") }
            }
        }
        CubicSettingsCard("Appearance", Icons.Rounded.Palette) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Keep navigation expanded", color = CubicColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Keep labels visible instead of waiting for hover.", color = CubicColors.TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = keepSidebarExpanded, onCheckedChange = onKeepSidebarExpandedChange)
            }
        }
        CubicSettingsCard("Offline music", Icons.Rounded.DownloadDone) {
            Text("$downloadCount downloaded tracks", color = CubicColors.TextSecondary, fontSize = 12.sp)
            Text(CubicDownloadStore.directory.absolutePath, color = CubicColors.TextMuted, fontSize = 11.sp)
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(CubicColors.AccentSoft).clickable {
                    runCatching { Desktop.getDesktop().open(CubicDownloadStore.directory) }
                }.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, null, tint = CubicColors.Accent, modifier = Modifier.size(18.dp))
                Text("Open download folder", color = CubicColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        CubicSettingsCard("Desktop data", Icons.Rounded.DeleteSweep) {
            Text("Clear listening history, favorites, playlists and offline downloads from this computer.", color = CubicColors.TextSecondary, fontSize = 11.sp)
            Text(
                "Clear desktop data",
                color = CubicColors.Danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(CubicColors.Danger.copy(alpha = 0.10f))
                    .clickable { confirmClear = true }.padding(horizontal = 14.dp, vertical = 11.dp)
            )
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear desktop data?") },
        text = { Text("This removes Cubic Music history, favorites, playlists and downloaded files on this computer. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClearData() }) { Text("Clear", color = CubicColors.Danger) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
    )
}

@Composable
private fun CubicSettingsCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CubicColors.PanelRaised).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(icon, null, tint = CubicColors.Accent, modifier = Modifier.size(19.dp))
            Text(title, color = CubicColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun CubicStatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(20.dp)).background(CubicColors.PanelRaised).padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.size(42.dp).background(CubicColors.AccentSoft, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = CubicColors.Accent, modifier = Modifier.size(21.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = CubicColors.TextMuted, fontSize = 10.sp)
            Text(value, color = CubicColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CubicActionTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = CubicColors.Accent, modifier = Modifier.size(19.dp))
        Text(label, color = CubicColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
