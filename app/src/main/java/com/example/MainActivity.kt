package com.example

import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ToolbarPosition
import com.example.engine.UrlUtils
import com.example.ui.components.BrowserAddressBar
import com.example.ui.components.BrowserBottomNavBar
import com.example.ui.components.BrowserMenuBottomSheet
import com.example.ui.components.FindInPageBar
import com.example.ui.components.PerformanceHudChip
import com.example.ui.components.QrCodeDialog
import com.example.ui.components.ReaderModeSheet
import com.example.ui.components.SitePermissionPromptDialog
import com.example.ui.navigation.BrowserScreenRoute
import com.example.ui.screens.BookmarksScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.ExtensionsScreen
import com.example.ui.screens.FloatingVideoControlButton
import com.example.ui.screens.FloatingVideoControlPanel
import com.example.ui.screens.FullscreenToolsCornerPill
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PrivacyDashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SitePermissionsScreen
import com.example.ui.screens.TabSwitcherScreen
import com.example.ui.screens.VideoDownloaderSheet
import com.example.ui.screens.VideoEnhancementSheet
import com.example.ui.theme.AnimeBrowserTheme
import com.example.viewmodel.BrowserViewModel
import org.mozilla.geckoview.GeckoView

class MainActivity : ComponentActivity() {

  private val viewModel: BrowserViewModel by viewModels()
  private var isLandscapeLocked = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleIncomingBrowserIntent(intent)

    setContent {
      AnimeBrowserRoot(
        viewModel = viewModel,
        onToggleFullscreenWindow = ::applyImmersiveFullscreen,
        onToggleOrientation = ::toggleOrientation,
        onKeepScreenAwake = ::setKeepScreenAwake,
        onEnterPip = ::tryEnterPipMode
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIncomingBrowserIntent(intent)
  }

  private fun handleIncomingBrowserIntent(intent: Intent?) {
    if (intent?.action == Intent.ACTION_VIEW) {
      val dataUrl = intent.dataString
      if (!dataUrl.isNullOrBlank() && (dataUrl.startsWith("http://") || dataUrl.startsWith("https://"))) {
        viewModel.createNewTab(url = dataUrl, isPrivate = false)
      }
    }
  }

  private fun applyImmersiveFullscreen(fullscreen: Boolean, autoLandscape: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (fullscreen) {
      controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      controller.hide(WindowInsetsCompat.Type.systemBars())
      if (autoLandscape) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        isLandscapeLocked = true
      }
    } else {
      controller.show(WindowInsetsCompat.Type.systemBars())
      if (isLandscapeLocked) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        isLandscapeLocked = false
      }
    }
  }

  private fun toggleOrientation() {
    isLandscapeLocked = !isLandscapeLocked
    requestedOrientation = if (isLandscapeLocked) {
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
      ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
  }

  private fun setKeepScreenAwake(awake: Boolean) {
    if (awake) {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }

  private fun tryEnterPipMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val params = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .build()
      runCatching { enterPictureInPictureMode(params) }
        .onFailure {
          Toast.makeText(this, "Picture-in-Picture is unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    } else {
      Toast.makeText(this, "Picture-in-Picture requires Android 8.0+", Toast.LENGTH_SHORT).show()
    }
  }
}

@Composable
private fun AnimeBrowserRoot(
  viewModel: BrowserViewModel,
  onToggleFullscreenWindow: (fullscreen: Boolean, autoLandscape: Boolean) -> Unit,
  onToggleOrientation: () -> Unit,
  onKeepScreenAwake: (Boolean) -> Unit,
  onEnterPip: () -> Unit
) {
  val context = LocalContext.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val tabs by viewModel.tabs.collectAsStateWithLifecycle()
  val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
  val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
  val recentlyClosedTabs by viewModel.recentlyClosedTabs.collectAsStateWithLifecycle()
  val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
  val history by viewModel.history.collectAsStateWithLifecycle()
  val recentlyWatched by viewModel.recentlyWatched.collectAsStateWithLifecycle()
  val downloads by viewModel.downloads.collectAsStateWithLifecycle()
  val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
  val siteSettings by viewModel.siteSettings.collectAsStateWithLifecycle()
  val customBlockRules by viewModel.customBlockRules.collectAsStateWithLifecycle()
  val whitelistedHosts by viewModel.whitelistedHosts.collectAsStateWithLifecycle()
  val totalBlockedCount by viewModel.totalBlockedCount.collectAsStateWithLifecycle()
  val recentBlocked by viewModel.recentBlockedEvents.collectAsStateWithLifecycle()
  val videoTelemetry by viewModel.videoTelemetry.collectAsStateWithLifecycle()
  val detectedStreams by viewModel.detectedStreams.collectAsStateWithLifecycle()
  val hlsVariantsByUrl by viewModel.hlsVariantsByUrl.collectAsStateWithLifecycle()
  val inspectingHlsUrl by viewModel.inspectingHlsUrl.collectAsStateWithLifecycle()
  val enhancementConfig by viewModel.enhancementConfig.collectAsStateWithLifecycle()
  val scalingMode by viewModel.scalingMode.collectAsStateWithLifecycle()
  val customZoom by viewModel.customZoom.collectAsStateWithLifecycle()
  val isWebFullScreen by viewModel.isWebFullScreen.collectAsStateWithLifecycle()
  val isForceFullScreen by viewModel.isForceFullScreen.collectAsStateWithLifecycle()
  val extensions by viewModel.extensions.collectAsStateWithLifecycle()
  val suggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
  val perfStats by viewModel.performanceStats.collectAsStateWithLifecycle()
  val readerArticle by viewModel.readerArticle.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
  val blockedRedirectUrl by viewModel.blockedRedirectUrl.collectAsStateWithLifecycle()
  val pendingSitePermission by viewModel.pendingSitePermission.collectAsStateWithLifecycle()
  val pendingAndroidPermission by viewModel.pendingAndroidPermission.collectAsStateWithLifecycle()

  var currentRoute by remember { mutableStateOf(BrowserScreenRoute.BROWSER) }
  var showMenuSheet by remember { mutableStateOf(false) }
  var showEnhancerSheet by remember { mutableStateOf(false) }
  var showVideoControlPanel by remember { mutableStateOf(false) }
  var showVideoDownloaderSheet by remember { mutableStateOf(false) }
  var showFindInPage by remember { mutableStateOf(false) }
  var findQuery by remember { mutableStateOf("") }
  var showQrDialog by remember { mutableStateOf(false) }

  val snackbarHostState = remember { SnackbarHostState() }
  val isAnyFullscreen = isWebFullScreen || isForceFullScreen

  // Runtime Android Permission Launcher bridged to GeckoView PermissionDelegate
  val androidPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { resultMap ->
    val allGranted = resultMap.values.all { it }
    viewModel.resolvePendingAndroidPermission(allGranted)
  }

  LaunchedEffect(pendingAndroidPermission) {
    pendingAndroidPermission?.let { req ->
      androidPermissionLauncher.launch(req.permissions)
    }
  }

  LaunchedEffect(isAnyFullscreen, settings.autoLandscapeFullscreen) {
    onToggleFullscreenWindow(isAnyFullscreen, settings.autoLandscapeFullscreen)
  }

  LaunchedEffect(videoTelemetry.isPlaying, settings.keepScreenAwakeVideo) {
    onKeepScreenAwake(videoTelemetry.isPlaying && settings.keepScreenAwakeVideo)
  }

  LaunchedEffect(statusMessage) {
    statusMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  LaunchedEffect(blockedRedirectUrl) {
    blockedRedirectUrl?.let { blockedUrl ->
      val result = snackbarHostState.showSnackbar(
        message = "Blocked ad redirect to ${UrlUtils.displayHostOrUrl(blockedUrl)}",
        actionLabel = "Allow",
        duration = SnackbarDuration.Short
      )
      if (result == SnackbarResult.ActionPerformed) {
        viewModel.openBlockedUrlAnyway(blockedUrl)
      } else {
        viewModel.clearBlockedRedirectBanner()
      }
    }
  }

  // Handle back press when in fullscreen or browsing web pages
  BackHandler(enabled = currentRoute == BrowserScreenRoute.BROWSER && (isAnyFullscreen || showFindInPage || activeTab?.isHomePage == false)) {
    when {
      isAnyFullscreen -> viewModel.exitAnyFullscreen()
      showFindInPage -> {
        showFindInPage = false
        findQuery = ""
        viewModel.clearFindInPage()
      }
      else -> viewModel.goBack()
    }
  }

  val isBookmarked = remember(bookmarks, activeTab?.url) {
    val url = activeTab?.url.orEmpty()
    url.isNotBlank() && bookmarks.any { it.url == url }
  }

  AnimeBrowserTheme(
    themeMode = settings.themeMode,
    accentPalette = settings.accentPalette,
    dynamicColor = settings.dynamicColor,
    isPrivateMode = activeTab?.isPrivate == true
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
    when (currentRoute) {
      BrowserScreenRoute.TABS -> TabSwitcherScreen(
        tabs = tabs,
        activeTabId = activeTabId,
        recentlyClosedTabs = recentlyClosedTabs,
        performanceStats = perfStats,
        onSelectTab = viewModel::selectTab,
        onCloseTab = viewModel::closeTab,
        onNewTab = { isPriv, grp -> viewModel.createNewTab(url = "", isPrivate = isPriv, groupName = grp) },
        onRestoreClosed = viewModel::restoreRecentlyClosedTab,
        onCloseAll = viewModel::closeAllTabs,
        onAssignGroup = viewModel::setTabGroup,
        onBack = { currentRoute = BrowserScreenRoute.BROWSER }
      )

      BrowserScreenRoute.BOOKMARKS -> BookmarksScreen(
        bookmarks = bookmarks,
        onOpenUrl = { viewModel.navigateActiveTab(it) },
        onAddBookmark = viewModel::addBookmark,
        onDeleteBookmark = viewModel::deleteBookmark,
        onBack = { currentRoute = BrowserScreenRoute.BROWSER }
      )

      BrowserScreenRoute.HISTORY -> HistoryScreen(
        history = history,
        onOpenUrl = { viewModel.navigateActiveTab(it) },
        onDeleteItem = viewModel::deleteHistoryItem,
        onClearAll = viewModel::clearHistory,
        onBack = { currentRoute = BrowserScreenRoute.BROWSER }
      )

      BrowserScreenRoute.DOWNLOADS -> DownloadsScreen(
        downloads = downloads,
        downloadSubfolder = settings.downloadSubfolder,
        onStartManualDownload = { viewModel.startDownload(it) },
        onCancelDownload = viewModel::cancelDownload,
        onRetryDownload = viewModel::retryDownload,
        onOpenDownload = viewModel::openDownload,
        onDeleteDownload = viewModel::deleteDownload,
        onBack = { currentRoute = BrowserScreenRoute.BROWSER }
      )

      BrowserScreenRoute.EXTENSIONS -> ExtensionsScreen(
        extensions = extensions,
        onToggleExtension = viewModel::toggleExtension,
        onOpenEnhancer = {
          currentRoute = BrowserScreenRoute.BROWSER
          showEnhancerSheet = true
        },
        onOpenDownloader = {
          currentRoute = BrowserScreenRoute.BROWSER
          showVideoDownloaderSheet = true
        },
        onOpenPrivacy = { currentRoute = BrowserScreenRoute.PRIVACY },
        onBack = { currentRoute = BrowserScreenRoute.BROWSER }
      )

      BrowserScreenRoute.PRIVACY -> PrivacyDashboardScreen(
        settings = settings,
        totalBlockedCount = totalBlockedCount,
        recentBlocked = recentBlocked,
        customRules = customBlockRules,
        whitelistedHosts = whitelistedHosts,
        currentHost = UrlUtils.extractHost(activeTab?.url.orEmpty()),
        onUpdateSettings = viewModel::updateSettings,
        onAddCustomRule = viewModel::addCustomBlockDomain,
        onRemoveCustomRule = viewModel::removeCustomBlockDomain,
        onToggleWhitelist = viewModel::toggleHostWhitelist,
        onClearBrowsingData = viewModel::clearBrowsingData,
        onBack = { currentRoute = BrowserScreenRoute.BROWSER }
      )

      BrowserScreenRoute.SITE_PERMISSIONS -> SitePermissionsScreen(
        siteSettings = siteSettings,
        onUpdateSite = viewModel::updateSitePermission,
        onDeleteSite = viewModel::deleteSiteSetting,
        onBack = { currentRoute = BrowserScreenRoute.BROWSER }
      )

      BrowserScreenRoute.SETTINGS -> SettingsScreen(
        settings = settings,
        performanceStats = perfStats,
        onUpdateSettings = viewModel::updateSettings,
        onOpenPrivacy = { currentRoute = BrowserScreenRoute.PRIVACY },
        onOpenSitePermissions = { currentRoute = BrowserScreenRoute.SITE_PERMISSIONS },
        onOpenExtensions = { currentRoute = BrowserScreenRoute.EXTENSIONS },
        onBack = { currentRoute = BrowserScreenRoute.BROWSER }
      )

      BrowserScreenRoute.BROWSER -> {
        Scaffold(
          containerColor = MaterialTheme.colorScheme.background,
          snackbarHost = { SnackbarHost(snackbarHostState) },
          topBar = {
            if (!isAnyFullscreen && settings.toolbarPosition == ToolbarPosition.TOP) {
              Column(modifier = Modifier.statusBarsPadding()) {
                BrowserAddressBar(
                  tab = activeTab,
                  isBookmarked = isBookmarked,
                  blockedCount = totalBlockedCount,
                  adBlockEnabled = settings.adBlockEnabled,
                  suggestions = suggestions,
                  onQueryChange = viewModel::updateSearchQueryForSuggestions,
                  onNavigate = viewModel::navigateActiveTab,
                  onReloadOrStop = viewModel::reloadOrStop,
                  onToggleBookmark = viewModel::toggleBookmarkForActiveTab,
                  onOpenPrivacy = { currentRoute = BrowserScreenRoute.PRIVACY }
                )
                AnimatedVisibility(visible = showFindInPage) {
                  FindInPageBar(
                    query = findQuery,
                    onQueryChange = {
                      findQuery = it
                      viewModel.findInPage(it)
                    },
                    onFindPrev = { viewModel.findInPage(findQuery, backward = true) },
                    onFindNext = { viewModel.findInPage(findQuery, backward = false) },
                    onClose = {
                      showFindInPage = false
                      findQuery = ""
                      viewModel.clearFindInPage()
                    }
                  )
                }
              }
            }
          },
          bottomBar = {
            if (!isAnyFullscreen) {
              Column {
                if (settings.toolbarPosition == ToolbarPosition.BOTTOM) {
                  AnimatedVisibility(visible = showFindInPage) {
                    FindInPageBar(
                      query = findQuery,
                      onQueryChange = {
                        findQuery = it
                        viewModel.findInPage(it)
                      },
                      onFindPrev = { viewModel.findInPage(findQuery, backward = true) },
                      onFindNext = { viewModel.findInPage(findQuery, backward = false) },
                      onClose = {
                        showFindInPage = false
                        findQuery = ""
                        viewModel.clearFindInPage()
                      }
                    )
                  }
                  BrowserAddressBar(
                    tab = activeTab,
                    isBookmarked = isBookmarked,
                    blockedCount = totalBlockedCount,
                    adBlockEnabled = settings.adBlockEnabled,
                    suggestions = suggestions,
                    onQueryChange = viewModel::updateSearchQueryForSuggestions,
                    onNavigate = viewModel::navigateActiveTab,
                    onReloadOrStop = viewModel::reloadOrStop,
                    onToggleBookmark = viewModel::toggleBookmarkForActiveTab,
                    onOpenPrivacy = { currentRoute = BrowserScreenRoute.PRIVACY }
                  )
                }
                BrowserBottomNavBar(
                  canGoBack = activeTab?.canGoBack == true,
                  canGoForward = activeTab?.canGoForward == true,
                  tabCount = tabs.size,
                  isPrivateMode = activeTab?.isPrivate == true,
                  isHome = activeTab?.isHomePage != false,
                  onHomeClick = viewModel::goHome,
                  onBackClick = { viewModel.goBack() },
                  onForwardClick = viewModel::goForward,
                  onTabsClick = { currentRoute = BrowserScreenRoute.TABS },
                  onMenuClick = { showMenuSheet = true }
                )
              }
            }
          }
        ) { innerPadding ->
          Box(
            modifier = Modifier
              .fillMaxSize()
              .then(if (isAnyFullscreen) Modifier else Modifier.padding(innerPadding))
              .background(MaterialTheme.colorScheme.background)
          ) {
            val currentTab = activeTab
            if (currentTab == null || currentTab.isHomePage) {
              HomeScreen(
                isPrivateMode = currentTab?.isPrivate == true,
                searchEngine = settings.searchEngine,
                enhancementConfig = enhancementConfig,
                blockedCount = totalBlockedCount,
                shortcuts = shortcuts,
                openTabs = tabs,
                recentlyWatched = recentlyWatched,
                recentHistory = history,
                bookmarks = bookmarks,
                onNavigate = viewModel::navigateActiveTab,
                onSelectTab = viewModel::selectTab,
                onAddShortcut = viewModel::addCustomShortcut,
                onDeleteShortcut = viewModel::deleteShortcut,
                onOpenEnhancer = { showEnhancerSheet = true },
                onOpenDownloads = { currentRoute = BrowserScreenRoute.DOWNLOADS },
                onOpenPrivacy = { currentRoute = BrowserScreenRoute.PRIVACY },
                onOpenExtensions = { currentRoute = BrowserScreenRoute.EXTENSIONS }
              )
            } else {
              // Real Mozilla GeckoView rendering surface (session memoized by tabId so recompositions NEVER thrash GeckoSession!)
              GeckoTabWebView(
                tabId = currentTab.id,
                getSession = { viewModel.getSessionForTab(currentTab) },
                modifier = Modifier.fillMaxSize()
              )

              // Floating Video Controls & Video Downloader FAB column (when video or stream detected)
              if ((videoTelemetry.hasVideo || detectedStreams.isNotEmpty()) && !isAnyFullscreen) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier
                    .align(if (settings.floatingButtonOnRight) Alignment.BottomEnd else Alignment.BottomStart)
                    .padding(18.dp)
                ) {
                  if (detectedStreams.isNotEmpty()) {
                    SmallFloatingActionButton(
                      onClick = { showVideoDownloaderSheet = true },
                      shape = CircleShape,
                      containerColor = MaterialTheme.colorScheme.secondaryContainer,
                      contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                      BadgedBox(
                        badge = {
                          Badge { Text("${detectedStreams.size}") }
                        }
                      ) {
                        Icon(
                          Icons.Filled.Download,
                          contentDescription = "Download detected anime video",
                          modifier = Modifier.size(20.dp)
                        )
                      }
                    }
                  }
                  if (videoTelemetry.hasVideo) {
                    FloatingVideoControlButton(
                      telemetry = videoTelemetry,
                      enhancementConfig = enhancementConfig,
                      onClick = { showVideoControlPanel = true }
                    )
                  }
                }
              }

              // Non-blocking Fullscreen Floating Corner Pill (99% of screen passes clicks straight to the site's native video player!)
              if (isAnyFullscreen) {
                FullscreenToolsCornerPill(
                  telemetry = videoTelemetry,
                  scalingMode = scalingMode,
                  customZoom = customZoom,
                  detectedStreamCount = detectedStreams.size,
                  isForceFullscreen = isForceFullScreen,
                  onSetScalingMode = viewModel::setScalingMode,
                  onOpenEnhancer = { showEnhancerSheet = true },
                  onOpenDownloader = { showVideoDownloaderSheet = true },
                  onOpenControlPanel = { showVideoControlPanel = true },
                  onToggleOrientation = onToggleOrientation,
                  onEnterPip = onEnterPip,
                  onExitFullscreen = viewModel::exitAnyFullscreen,
                  modifier = Modifier.align(Alignment.TopEnd)
                )
              }
            }

            // Optional Live Performance HUD Overlay (works in BOTH normal & fullscreen modes!)
            if (settings.showPerformanceOverlay) {
              PerformanceHudChip(
                stats = perfStats,
                videoTelemetry = videoTelemetry,
                modifier = Modifier
                  .align(Alignment.TopStart)
                  .then(if (isAnyFullscreen) Modifier.statusBarsPadding() else Modifier)
                  .padding(10.dp)
              )
            }
          }
        }
      }
    }

    // --- Modals & Bottom Sheets ---
    if (showMenuSheet) {
      BrowserMenuBottomSheet(
        activeTab = activeTab,
        onDismiss = { showMenuSheet = false },
        onNewTab = { viewModel.createNewTab(url = "", isPrivate = false); currentRoute = BrowserScreenRoute.BROWSER },
        onNewPrivateTab = { viewModel.createNewTab(url = "", isPrivate = true); currentRoute = BrowserScreenRoute.BROWSER },
        onOpenBookmarks = { currentRoute = BrowserScreenRoute.BOOKMARKS },
        onOpenHistory = { currentRoute = BrowserScreenRoute.HISTORY },
        onOpenDownloads = { currentRoute = BrowserScreenRoute.DOWNLOADS },
        onOpenEnhancer = { showEnhancerSheet = true },
        onOpenExtensions = { currentRoute = BrowserScreenRoute.EXTENSIONS },
        onOpenPrivacy = { currentRoute = BrowserScreenRoute.PRIVACY },
        onOpenSitePermissions = { currentRoute = BrowserScreenRoute.SITE_PERMISSIONS },
        onOpenSettings = { currentRoute = BrowserScreenRoute.SETTINGS },
        onToggleDesktopMode = viewModel::toggleDesktopMode,
        onOpenFindInPage = { showFindInPage = true },
        onSharePage = {
          activeTab?.let { if (!it.isHomePage) viewModel.shareUrl(context, it.title, it.url) }
        },
        onCopyUrl = {
          activeTab?.url?.takeIf { it.isNotBlank() }?.let { url ->
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("URL", url))
            Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
          }
        },
        onShowQrCode = { showQrDialog = true },
        onTranslatePage = viewModel::translateCurrentPage,
        onReaderMode = viewModel::requestReaderMode,
        onSaveWebpage = viewModel::saveCurrentWebpage
      )
    }

    if (showEnhancerSheet) {
      VideoEnhancementSheet(
        config = enhancementConfig,
        telemetry = videoTelemetry,
        onDismiss = { showEnhancerSheet = false },
        onToggleMaster = viewModel::toggleEnhancementMaster,
        onSelectPreset = viewModel::applyEnhancementPreset,
        onUpdateCustom = viewModel::updateCustomEnhancement,
        onToggleFrameGen = viewModel::toggleFrameGeneration,
        onSelectTargetFps = viewModel::setTargetFps
      )
    }

    if (showVideoControlPanel) {
      FloatingVideoControlPanel(
        telemetry = videoTelemetry,
        scalingMode = scalingMode,
        customZoom = customZoom,
        enhancementConfig = enhancementConfig,
        isFullscreen = isAnyFullscreen,
        detectedStreamCount = detectedStreams.size,
        onDismiss = { showVideoControlPanel = false },
        onToggleForceFullscreen = { enable -> viewModel.setForceFullscreen(enable, hideSiteControls = true) },
        onSelectScalingMode = viewModel::setScalingMode,
        onOpenEnhancer = { showEnhancerSheet = true },
        onOpenDownloader = { showVideoDownloaderSheet = true },
        onEnterPip = onEnterPip
      )
    }

    if (showVideoDownloaderSheet) {
      VideoDownloaderSheet(
        pageTitle = activeTab?.title.orEmpty(),
        videoTelemetry = videoTelemetry,
        detectedStreams = detectedStreams,
        hlsVariantsByUrl = hlsVariantsByUrl,
        inspectingUrl = inspectingHlsUrl,
        onInspectHls = viewModel::inspectHlsQualities,
        onDownloadStream = { stream, fileName, variantUrl ->
          viewModel.downloadDetectedStream(stream, fileName, variantUrl)
          showVideoDownloaderSheet = false
        },
        onOpenDownloadsScreen = { currentRoute = BrowserScreenRoute.DOWNLOADS },
        onDismiss = { showVideoDownloaderSheet = false }
      )
    }

    if (showQrDialog && activeTab != null && !activeTab!!.isHomePage) {
      QrCodeDialog(
        url = activeTab!!.url,
        title = activeTab!!.title,
        onDismiss = { showQrDialog = false }
      )
    }

    readerArticle?.let { article ->
      ReaderModeSheet(
        article = article,
        onDismiss = viewModel::dismissReaderMode
      )
    }

    pendingSitePermission?.let { req ->
      SitePermissionPromptDialog(
        request = req,
        onDecision = viewModel::resolvePendingSitePermission
      )
    }
    }
  }
}

@Composable
private fun GeckoTabWebView(
  tabId: String,
  getSession: () -> org.mozilla.geckoview.GeckoSession,
  modifier: Modifier = Modifier
) {
  // Memoize session lookup strictly by tabId so frequent telemetry / slider recompositions
  // NEVER call session.setActive(true) or suspendInactiveSessions during video playback!
  val desiredSession = remember(tabId) { getSession() }
  var geckoViewRef by remember { mutableStateOf<GeckoView?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      geckoViewRef?.releaseSession()
    }
  }

  AndroidView(
    factory = { ctx ->
      GeckoView(ctx).apply {
        coverUntilFirstPaint(android.graphics.Color.BLACK)
        setSession(desiredSession)
        geckoViewRef = this
      }
    },
    update = { view ->
      if (view.session !== desiredSession) {
        view.releaseSession()
        view.setSession(desiredSession)
      }
    },
    modifier = modifier
  )
}
