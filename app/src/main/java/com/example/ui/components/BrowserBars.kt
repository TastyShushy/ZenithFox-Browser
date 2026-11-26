package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BrowserTab
import com.example.data.model.PerformanceStats
import com.example.data.model.VideoTelemetry
import com.example.engine.UrlUtils
import com.example.ui.theme.StatusSuccess

@Composable
fun BrowserAddressBar(
  tab: BrowserTab?,
  isBookmarked: Boolean,
  blockedCount: Int,
  adBlockEnabled: Boolean,
  suggestions: List<String>,
  onQueryChange: (String) -> Unit,
  onNavigate: (String) -> Unit,
  onReloadOrStop: () -> Unit,
  onToggleBookmark: () -> Unit,
  onOpenPrivacy: () -> Unit,
  modifier: Modifier = Modifier
) {
  val focusManager = LocalFocusManager.current
  var isFocused by remember { mutableStateOf(false) }
  var inputText by remember(tab?.id, tab?.url) {
    mutableStateOf(if (tab?.isHomePage == true) "" else tab?.url.orEmpty())
  }

  LaunchedEffect(tab?.url, isFocused) {
    if (!isFocused) {
      inputText = if (tab?.isHomePage == true) "" else UrlUtils.displayHostOrUrl(tab?.url.orEmpty())
    }
  }

  Column(modifier = modifier.fillMaxWidth()) {
    Surface(
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
      contentColor = MaterialTheme.colorScheme.onSurface,
      tonalElevation = 4.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Ad Shield Pill
          Surface(
            onClick = onOpenPrivacy,
            shape = CircleShape,
            color = if (adBlockEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (adBlockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("address_shield_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = "Privacy & Ad Shield",
                tint = if (adBlockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
              )
              if (blockedCount > 0) {
                Text(
                  text = if (blockedCount > 99) "99+" else blockedCount.toString(),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
          }

          // Glass URL Omnibox
          Row(
            modifier = Modifier
              .weight(1f)
              .height(46.dp)
              .clip(RoundedCornerShape(24.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
              .border(
                width = 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(24.dp)
              )
              .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            val isHome = tab?.isHomePage != false
            val isSecureSite = tab?.isSecure == true
            Icon(
              imageVector = when {
                isHome || isFocused -> Icons.Filled.Search
                isSecureSite -> Icons.Filled.Lock
                else -> Icons.Outlined.LockOpen
              },
              contentDescription = if (isSecureSite) "Secure HTTPS" else "Search",
              tint = when {
                !isHome && !isFocused && isSecureSite -> StatusSuccess
                isFocused -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
              },
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
              if (inputText.isEmpty()) {
                Text(
                  text = if (tab?.isPrivate == true) "Private search or URL…" else "Search Google or type URL…",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              BasicTextField(
                value = inputText,
                onValueChange = {
                  inputText = it
                  onQueryChange(it)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                  color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                  onGo = {
                    val query = inputText.trim()
                    if (query.isNotEmpty()) {
                      onNavigate(query)
                      focusManager.clearFocus()
                    }
                  }
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("url_input_field")
                  .onFocusChanged { state ->
                    isFocused = state.isFocused
                    if (state.isFocused && tab != null && !tab.isHomePage) {
                      inputText = tab.url
                    }
                  }
              )
            }
            if (isFocused && inputText.isNotEmpty()) {
              IconButton(
                onClick = {
                  inputText = ""
                  onQueryChange("")
                },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = "Clear URL",
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            } else if (tab != null && !tab.isHomePage) {
              IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier
                  .size(32.dp)
                  .testTag("bookmark_toggle_button")
              ) {
                Icon(
                  imageVector = if (isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                  contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark page",
                  tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp)
                )
              }
              IconButton(
                onClick = onReloadOrStop,
                modifier = Modifier
                  .size(32.dp)
                  .testTag("reload_stop_button")
              ) {
                Icon(
                  imageVector = if (tab.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                  contentDescription = if (tab.isLoading) "Stop loading" else "Reload page",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }

        // Loading progress bar
        if (tab?.isLoading == true && tab.progress in 1..99) {
          LinearProgressIndicator(
            progress = { tab.progress / 100f },
            modifier = Modifier
              .fillMaxWidth()
              .height(2.5.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent
          )
        } else {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }
      }
    }

    // Search suggestions dropdown
    AnimatedVisibility(visible = isFocused && suggestions.isNotEmpty()) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column {
          suggestions.forEach { suggestion ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  inputText = suggestion
                  onNavigate(suggestion)
                  focusManager.clearFocus()
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = suggestion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
      }
    }
  }
}

@Composable
fun BrowserBottomNavBar(
  canGoBack: Boolean,
  canGoForward: Boolean,
  tabCount: Int,
  isPrivateMode: Boolean,
  isHome: Boolean,
  onHomeClick: () -> Unit,
  onBackClick: () -> Unit,
  onForwardClick: () -> Unit,
  onTabsClick: () -> Unit,
  onMenuClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = 8.dp,
    modifier = modifier.fillMaxWidth()
  ) {
    Column {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Home
        IconButton(
          onClick = onHomeClick,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("nav_home_button")
        ) {
          Icon(
            imageVector = Icons.Filled.Home,
            contentDescription = "Home",
            tint = if (isHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
          )
        }

        // Back
        IconButton(
          onClick = onBackClick,
          enabled = canGoBack || !isHome,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("nav_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = if (canGoBack || !isHome) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
          )
        }

        // Forward
        IconButton(
          onClick = onForwardClick,
          enabled = canGoForward,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("nav_forward_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Forward",
            tint = if (canGoForward) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
          )
        }

        // Tabs badge button
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onTabsClick)
            .testTag("nav_tabs_button")
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(26.dp)
              .border(
                width = 2.dp,
                color = if (isPrivateMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(7.dp)
              )
          ) {
            Text(
              text = if (tabCount > 99) "∞" else tabCount.toString(),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = if (isPrivateMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
          }
        }

        // Menu
        IconButton(
          onClick = onMenuClick,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("nav_menu_button")
        ) {
          Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = "Browser Menu",
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }
  }
}

@Composable
fun FindInPageBar(
  query: String,
  onQueryChange: (String) -> Unit,
  onFindPrev: () -> Unit,
  onFindNext: () -> Unit,
  onClose: () -> Unit
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = 6.dp,
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = Icons.Filled.Search,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp)
      )
      Box(modifier = Modifier.weight(1f)) {
        if (query.isEmpty()) {
          Text(
            text = "Find in page…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("find_in_page_input")
        )
      }
      IconButton(onClick = onFindPrev) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
      }
      IconButton(onClick = onFindNext) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
      }
      IconButton(onClick = onClose) {
        Icon(Icons.Filled.Close, contentDescription = "Close find in page")
      }
    }
  }
}

@Composable
fun PerformanceHudChip(
  stats: PerformanceStats,
  videoTelemetry: VideoTelemetry,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = Color.Black.copy(alpha = 0.80f),
    contentColor = Color.White,
    border = BorderStroke(
      1.dp,
      Brush.horizontalGradient(
        listOf(
          MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
          MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        )
      )
    ),
    modifier = modifier
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
      Text(
        text = "${stats.displayRefreshRateHz}Hz • Heap ${stats.appRamMb}MB • Free ${stats.availableDeviceRamMb}MB • ${stats.thermalStatusLabel}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary
      )
      if (videoTelemetry.hasVideo) {
        val fpsSummary = when {
          !videoTelemetry.isPlaying -> "Paused (0 FPS)"
          videoTelemetry.frameGenActive && videoTelemetry.sourceFps > 0 && videoTelemetry.outputFps > videoTelemetry.sourceFps ->
            "${videoTelemetry.sourceFps}→${videoTelemetry.outputFps} FPS"
          videoTelemetry.outputFps > 0 -> "${videoTelemetry.outputFps} FPS"
          videoTelemetry.sourceFps > 0 -> "${videoTelemetry.sourceFps} FPS"
          else -> "Measuring FPS…"
        }
        Text(
          text = "Video ${videoTelemetry.resolutionLabel} • $fpsSummary • ${videoTelemetry.statusHumanLabel}",
          style = MaterialTheme.typography.labelSmall,
          color = Color.White.copy(alpha = 0.92f)
        )
      }
    }
  }
}
