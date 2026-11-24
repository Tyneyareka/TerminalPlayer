package dev.terminalplayer.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.terminalplayer.data.AppSettings
import dev.terminalplayer.i18n.AppLanguage
import dev.terminalplayer.i18n.LocalStrings
import dev.terminalplayer.ui.components.GlyphButton
import dev.terminalplayer.ui.components.MetaLabel
import dev.terminalplayer.ui.components.SectionHeader
import dev.terminalplayer.ui.components.TerminalCard
import dev.terminalplayer.ui.motion.LocalMotionEnabled
import dev.terminalplayer.ui.motion.Reveal
import dev.terminalplayer.ui.motion.revealDelay
import dev.terminalplayer.ui.motion.scaleLayer
import dev.terminalplayer.ui.theme.Accent
import dev.terminalplayer.ui.theme.StrokeSoft
import dev.terminalplayer.ui.theme.SurfaceCard
import dev.terminalplayer.ui.theme.TextMuted
import dev.terminalplayer.ui.theme.TextPrimary
import dev.terminalplayer.ui.theme.TextSecondary
import dev.terminalplayer.ui.theme.TerminalType

/**
 * SETTINGS: the language switch (Системный / Русский / English) and the animation switch.
 *
 * Both change the live composition - no restart, no playback interruption. The screen itself uses
 * the same staggered [Reveal] and crossfading selection states as the rest of the shell, so turning
 * animations off here also proves the switch works.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onLanguageSelected: (AppLanguage) -> Unit,
    onAnimationsChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val resolved = remember(settings.language) { settings.language.resolve() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SettingsHeader(
            title = strings.settingsTitle,
            subtitle = strings.settingsSubtitle,
            onBack = onBack,
        )
        Spacer(Modifier.height(16.dp))

        Reveal(delayMillis = revealDelay(0)) {
            Column {
                SectionHeader(
                    title = strings.languageSection,
                    trailing = strings.languageApplied,
                )
                Spacer(Modifier.height(10.dp))
                AppLanguage.entries.forEach { language ->
                    LanguageOption(
                        label = strings.languageName(language),
                        hint = if (language == AppLanguage.SYSTEM) {
                            strings.languageNow(resolved)
                        } else {
                            null
                        },
                        selected = settings.language == language,
                        onClick = { onLanguageSelected(language) },
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Reveal(delayMillis = revealDelay(1)) {
            Column {
                SectionHeader(title = strings.animationsSection)
                Spacer(Modifier.height(10.dp))
                AnimationsOption(
                    title = strings.animationsTitle,
                    body = strings.animationsBody,
                    on = strings.animationsOn,
                    off = strings.animationsOff,
                    enabled = settings.animationsEnabled,
                    onToggle = { onAnimationsChanged(!settings.animationsEnabled) },
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Reveal(delayMillis = revealDelay(2)) {
            Column {
                SectionHeader(title = strings.aboutSection)
                Spacer(Modifier.height(10.dp))
                AboutCard(versionLabel = strings.versionLabel, body = strings.aboutBody)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            glyph = "<",
            onClick = onBack,
            size = 40.dp,
            shape = CircleShape,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TerminalType.headline,
                color = TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TerminalType.label,
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LanguageOption(
    label: String,
    hint: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val motion = LocalMotionEnabled.current
    val shape = RoundedCornerShape(16.dp)

    val fill by animateColorAsState(
        targetValue = if (selected) Accent.copy(alpha = 0.16f) else SurfaceCard,
        animationSpec = tween(if (motion) 200 else 0),
        label = "languageFill",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) Accent else StrokeSoft,
        animationSpec = tween(if (motion) 200 else 0),
        label = "languageOutline",
    )
    val markerScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.82f,
        animationSpec = if (motion) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            )
        } else {
            tween(durationMillis = 0)
        },
        label = "languageMarker",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(fill, shape)
            .border(1.dp, outline, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) "[x]" else "[ ]",
            style = TerminalType.glyph.copy(
                fontSize = 14.sp,
                color = if (selected) Accent else TextMuted,
            ),
            maxLines = 1,
            modifier = Modifier.scaleLayer(markerScale),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = TerminalType.bodyStrong,
                color = if (selected) TextPrimary else TextSecondary,
                maxLines = 1,
            )
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = hint,
                    style = TerminalType.small,
                    color = TextMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AnimationsOption(
    title: String,
    body: String,
    on: String,
    off: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val motion = LocalMotionEnabled.current
    val shape = RoundedCornerShape(16.dp)
    val outline by animateColorAsState(
        targetValue = if (enabled) Accent.copy(alpha = 0.65f) else StrokeSoft,
        animationSpec = tween(if (motion) 200 else 0),
        label = "animationsOutline",
    )
    val stateColor by animateColorAsState(
        targetValue = if (enabled) Accent else TextMuted,
        animationSpec = tween(if (motion) 200 else 0),
        label = "animationsState",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, shape)
            .border(1.dp, outline, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onClick = onToggle,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TerminalType.bodyStrong,
                color = TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = body,
                style = TerminalType.small,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Crossfade(
            targetState = enabled,
            animationSpec = tween(if (motion) 180 else 0),
            label = "animationsSwitch",
        ) { isOn ->
            Text(
                text = if (isOn) on else off,
                style = TerminalType.label.copy(fontSize = 11.sp),
                color = stateColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AboutCard(versionLabel: String, body: String) {
    TerminalCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Text(
                text = "TERMINALPLAYER // v1.0.0",
                style = TerminalType.bodyStrong,
                color = TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = TerminalType.small,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaLabel(text = versionLabel, color = TextMuted)
                Spacer(Modifier.width(8.dp))
                MetaLabel(text = "1.0.0", color = TextSecondary)
            }
        }
    }
}
