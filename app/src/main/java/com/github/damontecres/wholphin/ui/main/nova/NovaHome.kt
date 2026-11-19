package com.github.damontecres.wholphin.ui.main.nova

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.ui.components.TimeDisplay
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.DestinationContent
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.nav.NavDrawerViewModel
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.ui.playback.theater.TheaterColors
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.LocalClock

/**
 * Nova home: a full screen spotlight with a top tab bar in place of the nav drawer.
 * Compile time switch, so the fork can fall back to the upstream home and keep merging upstream changes.
 */
const val NOVA_HOME_ENABLED = true

/** The top bar, provided by [NovaHomeFrame] so [NovaHomeContent] can lay it out and hide it */
internal val LocalNovaTopBar = staticCompositionLocalOf<(@Composable (Modifier, FocusRequester) -> Unit)?> { null }

/** Title of the current page (a library, favorites) shown before its tabs; null when it has none */
internal val LocalNovaPageTitle = staticCompositionLocalOf<String?> { null }

/**
 * Hosts the pages that upstream shows in the nav drawer. Nova has no drawer: the home page has the top bar, every
 * other page is entered from it and left with back, showing its title before its tabs.
 */
@Composable
fun NovaFrame(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (destination is Destination.Home) {
        NovaHomeFrame(destination, preferences, user, server, onClearBackdrop, modifier)
    } else {
        NovaPageFrame(destination, preferences, user, server, onClearBackdrop, modifier)
    }
}

@Composable
private fun NovaPageFrame(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NavDrawerViewModel =
        hiltViewModel(
            LocalView.current.findViewTreeViewModelStoreOwner()!!,
            key = "${server.id}_${user.id}",
        ),
) {
    val context = LocalContext.current
    val serviceState by viewModel.serviceState.collectAsState()
    // The name the top bar showed for it; libraries matched by id, wherever they were opened from
    val title =
        remember(destination, serviceState, context) {
            when (destination) {
                Destination.Favorites -> {
                    NavDrawerItem.Favorites.name(context)
                }

                Destination.Discover -> {
                    NavDrawerItem.Discover.name(context)
                }

                else -> {
                    (serviceState.items + serviceState.moreItems)
                        .filterIsInstance<ServerNavDrawerItem>()
                        .firstOrNull {
                            it.destination == destination ||
                                (destination is Destination.MediaItem && destination.itemId == it.itemId)
                        }?.name
                }
            }
        }
    CompositionLocalProvider(LocalNovaPageTitle provides title) {
        Box(modifier = modifier.fillMaxSize()) {
            DestinationContent(
                destination = destination,
                preferences = preferences,
                onClearBackdrop = onClearBackdrop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = 32.dp, end = 16.dp),
            )
            if (preferences.appPreferences.interfacePreferences.showClock) {
                TimeDisplay()
            }
        }
    }
}

/**
 * A tabbed page's header: the page title (see [LocalNovaPageTitle]) then its [tabs]. Without a title, just the tabs.
 */
@Composable
fun NovaTabHeader(
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    val title = LocalNovaPageTitle.current
    if (title == null) {
        Box(modifier) { tabs() }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        // Not focusable: back is the remote's back key
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Box(
            Modifier
                .padding(start = 20.dp, end = 12.dp)
                .size(width = 1.dp, height = 24.dp)
                .drawBehind { drawRect(Color.White.copy(alpha = .25f)) },
        )
        tabs()
    }
}

/**
 * Hosts the home page without the nav drawer, giving it the top bar instead
 */
@Composable
private fun NovaHomeFrame(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topBar: @Composable (Modifier, FocusRequester) -> Unit =
        remember(preferences, user, server, onClearBackdrop) {
            { mod, focusRequester ->
                NovaTopBar(
                    user = user,
                    server = server,
                    showClock = preferences.appPreferences.interfacePreferences.showClock,
                    onClearBackdrop = onClearBackdrop,
                    homeFocusRequester = focusRequester,
                    modifier = mod,
                )
            }
        }
    CompositionLocalProvider(LocalNovaTopBar provides topBar) {
        DestinationContent(
            destination = destination,
            preferences = preferences,
            onClearBackdrop = onClearBackdrop,
            modifier = modifier.fillMaxSize(),
        )
    }
}

/**
 * Search, home and the nav drawer's libraries on the left; settings, the user and the clock on the right
 */
@Composable
internal fun NovaTopBar(
    user: JellyfinUser,
    server: JellyfinServer,
    showClock: Boolean,
    onClearBackdrop: () -> Unit,
    homeFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    // Same instance as the nav drawer's on the other pages
    viewModel: NavDrawerViewModel =
        hiltViewModel(
            LocalView.current.findViewTreeViewModelStoreOwner()!!,
            key = "${server.id}_${user.id}",
        ),
) {
    val context = LocalContext.current
    val serviceState by viewModel.serviceState.collectAsState()
    val drawerState by viewModel.state.collectAsState()
    val navigation = viewModel.navigationManager
    // Like the drawer: the pinned items, then "more" which expands into the others (shared expanded state)
    val moreExpanded = drawerState.moreExpanded
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(TopBarHeight)
                .padding(horizontal = 32.dp)
                .focusGroup()
                .focusProperties { onEnter = { homeFocusRequester.tryRequestFocus() } },
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                TopBarIcon(Icons.Default.Search, stringResource(R.string.search)) {
                    navigation.navigateToFromDrawer(Destination.Search())
                }
            }
            if (serviceState.nowPlayingEnabled) {
                item {
                    TopBarIcon(Icons.Default.PlayArrow, stringResource(R.string.now_playing)) {
                        navigation.navigateTo(Destination.NowPlaying)
                    }
                }
            }
            item {
                TopBarTab(
                    text = stringResource(R.string.home),
                    selected = true,
                    modifier = Modifier.focusRequester(homeFocusRequester),
                ) {
                    // Only shown on the home page: reload it
                    navigation.reloadHome()
                    onClearBackdrop.invoke()
                }
            }
            itemsIndexed(serviceState.items) { index, item ->
                TopBarTab(text = item.name(context), selected = false) {
                    viewModel.onClickDrawerItem(index, item)
                }
            }
            if (serviceState.moreItems.isNotEmpty()) {
                item {
                    TopBarTab(text = NavDrawerItem.More.name(context) + if (moreExpanded) " ‹" else " ›", selected = false) {
                        viewModel.setShowMore(!moreExpanded)
                    }
                }
                if (moreExpanded) {
                    itemsIndexed(serviceState.moreItems) { index, item ->
                        TopBarTab(text = item.name(context), selected = false) {
                            viewModel.onClickDrawerItem(serviceState.items.size + index, item)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        TopBarIcon(Icons.Default.Settings, stringResource(R.string.settings)) {
            navigation.navigateTo(Destination.Settings(PreferenceScreenOption.BASIC))
        }
        Spacer(Modifier.width(8.dp))
        val userImageUrl = remember(user) { viewModel.getUserImage(user) }
        Surface(
            onClick = { viewModel.navigateToSetup(SetupDestination.UserList(server)) },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(3.dp, TheaterColors.Text), shape = CircleShape),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            modifier = Modifier.size(36.dp),
        ) {
            UserIconCardImage(id = user.id, name = user.name, imageUrl = userImageUrl, modifier = Modifier.fillMaxSize())
        }
        if (showClock) {
            // A thin divider sets the clock apart from the buttons
            Box(
                Modifier
                    .padding(start = 20.dp, end = 16.dp)
                    .size(width = 1.dp, height = 24.dp)
                    .drawBehind { drawRect(Color.White.copy(alpha = .25f)) },
            )
            val timeString by LocalClock.current.timeString
            Text(text = timeString, fontSize = 18.sp, color = TheaterColors.Text)
        }
    }
}

internal val TopBarHeight = 64.dp

@Composable
private fun TopBarTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = TheaterColors.accent
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = novaPillColors(contentColor = if (selected) TheaterColors.Text else TheaterColors.Text.copy(alpha = .6f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .drawBehind {
                        // Underline the current tab; the focused pill already shows where focus is
                        if (selected && !focused) {
                            val y = size.height + 3.dp.toPx()
                            drawLine(accent, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                        }
                    },
        )
    }
}

@Composable
private fun TopBarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = novaPillColors(contentColor = TheaterColors.Text.copy(alpha = .7f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.size(40.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

/** Pill buttons of the Nova home, in the shared button style (see [TheaterColors.Focused]) */
@Composable
internal fun novaPillColors(
    containerColor: Color = Color.Transparent,
    contentColor: Color = TheaterColors.Text,
) = ClickableSurfaceDefaults.colors(
    containerColor = containerColor,
    contentColor = contentColor,
    focusedContainerColor = TheaterColors.Focused,
    focusedContentColor = TheaterColors.OnFocused,
)

/**
 * Favorites without any: how to add some, and a focusable way out instead of a lone line of text
 */
@Composable
fun NovaFavoritesEmpty(
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val accent = TheaterColors.accent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(60.dp)
                    .drawBehind { drawCircle(accent.copy(alpha = .18f)) },
        ) {
            Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
        }
        Text(
            text = stringResource(R.string.nova_favorites_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = stringResource(R.string.nova_favorites_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Surface(
            onClick = onGoHome,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = novaPillColors(containerColor = TheaterColors.Glass),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier =
                Modifier
                    .padding(top = 24.dp)
                    .focusRequester(focusRequester),
        ) {
            Text(
                text = stringResource(R.string.nova_go_home),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
}
