package com.fixmoney.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fixmoney.resources.Res
import com.fixmoney.resources.avatar_1
import com.fixmoney.resources.avatar_2
import com.fixmoney.resources.avatar_3
import com.fixmoney.resources.avatar_4
import com.fixmoney.resources.avatar_5
import com.fixmoney.resources.avatar_6
import com.fixmoney.resources.avatar_7
import com.fixmoney.resources.avatar_8
import com.fixmoney.resources.avatar_9
import com.fixmoney.resources.nd_check_circle
import com.fixmoney.shared.onboarding.AppPrefs
import com.fixmoney.ui.Design
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.designType
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/** The claymation avatars from the Stitch "Choose Avatar" design. Ids are stored; never reorder. */
internal enum class ProfileAvatarOption(val id: Int, val label: String, val res: DrawableResource) {
    RedCap(1, "Red Cap", Res.drawable.avatar_1),
    SunnyCurls(2, "Sunny Curls", Res.drawable.avatar_2),
    WavyStache(3, "Wavy Stache", Res.drawable.avatar_3),
    PeachHijab(4, "Peach Hijab", Res.drawable.avatar_4),
    RetroDreads(5, "Retro Dreads", Res.drawable.avatar_5),
    GentleBeard(6, "Gentle Beard", Res.drawable.avatar_6),
    SunHat(7, "Sun Hat", Res.drawable.avatar_7),
    PurpleHoodie(8, "Purple Hoodie", Res.drawable.avatar_8),
    RainbowBraids(9, "Rainbow Braids", Res.drawable.avatar_9);

    companion object {
        fun byId(id: Int): ProfileAvatarOption? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The chosen avatar as app-wide state, so Home's app bar, Settings and Edit
 * Profile all change the moment one is applied, without a restart.
 */
internal object CurrentAvatar {
    var id by mutableIntStateOf(-1)
}

@Composable
internal fun rememberAvatarId(): Int {
    val prefs: AppPrefs = koinInject()
    if (CurrentAvatar.id < 0) CurrentAvatar.id = prefs.avatarId()
    return CurrentAvatar.id
}

/**
 * The profile picture: the chosen avatar, or the initial on the accent
 * gradient when none has been picked. Used everywhere a profile appears.
 */
@Composable
fun ProfileAvatar(name: String?, size: Dp, modifier: Modifier = Modifier, shapeRadius: Dp? = null) {
    val colors = Design.colors
    val t = designType()
    val shape = shapeRadius?.let { RoundedCornerShape(it) } ?: CircleShape
    val option = ProfileAvatarOption.byId(rememberAvatarId())
    if (option != null) {
        Image(
            painter = painterResource(option.res),
            contentDescription = "Profile avatar",
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape)
        )
    } else {
        Box(
            modifier.size(size).clip(shape)
                .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name?.trim()?.firstOrNull()?.uppercase() ?: "?",
                style = t.headlineLg.copy(fontSize = (size.value * 0.42f).sp),
                color = Color.White
            )
        }
    }
}

/** "Choose Avatar" sheet: a preview, the grid, Apply and Cancel. Nothing is saved until Apply. */
@Composable
internal fun AvatarPickerSheet(name: String?, onDismiss: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    val prefs: AppPrefs = koinInject()
    val current = rememberAvatarId()
    var picked by remember { mutableIntStateOf(current.coerceAtLeast(0)) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.stroke, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape).background(colors.surfaceHigh)
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose Avatar", style = t.headlineSm, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(colors.surfaceHigh).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) { Text("✕", style = t.titleMd, color = colors.textMuted) }
                }

                // Preview of what Apply will set.
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(colors.surface).border(1.dp, colors.stroke, RoundedCornerShape(20.dp))
                        .padding(14.dp)
                ) {
                    val previewOption = ProfileAvatarOption.byId(picked)
                    Box(Modifier.size(60.dp).clip(CircleShape).border(2.dp, colors.accent, CircleShape)) {
                        if (previewOption != null) {
                            Image(painterResource(previewOption.res), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name?.takeIf { it.isNotBlank() } ?: "Your profile", style = t.titleMd, color = colors.textPrimary)
                        Text(previewOption?.label ?: "Pick an avatar below", style = t.bodySm, color = colors.accent)
                    }
                }

                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                ) {
                    items(ProfileAvatarOption.entries) { option ->
                        val selected = option.id == picked
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clip(RoundedCornerShape(18.dp))
                                .background(colors.surface)
                                .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.stroke, RoundedCornerShape(18.dp))
                                .clickable { picked = option.id }
                                .padding(vertical = 12.dp, horizontal = 6.dp)
                        ) {
                            Box {
                                Image(
                                    painterResource(option.res), option.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(CircleShape)
                                )
                                if (selected) {
                                    Box(
                                        Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(colors.accent),
                                        contentAlignment = Alignment.Center
                                    ) { LineIcon(Res.drawable.nd_check_circle, Color.White, 16.dp) }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                option.label, style = t.bodySm, maxLines = 1,
                                color = if (selected) colors.textPrimary else colors.textMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().clip(CircleShape)
                        .background(
                            if (picked > 0) Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd))
                            else Brush.linearGradient(listOf(colors.surfaceHigh, colors.surfaceHigh))
                        )
                        .clickable(enabled = picked > 0) {
                            prefs.setAvatarId(picked)
                            CurrentAvatar.id = picked
                            onDismiss()
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Apply Avatar", style = t.titleMd, color = Color.White) }
                Text(
                    "Cancel", style = t.titleMd, color = colors.textMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable(onClick = onDismiss).padding(vertical = 12.dp)
                )
            }
        }
    }
}
