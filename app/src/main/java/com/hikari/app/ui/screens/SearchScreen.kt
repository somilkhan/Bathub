package com.hikari.app.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.hikari.app.HikariApp
import com.hikari.app.data.ContentRepository
import com.hikari.app.data.MediaItem
import com.hikari.app.providers.ContentProvider
import com.hikari.app.ui.Artwork
import com.hikari.app.ui.PosterLoader
import com.hikari.app.ui.components.EmptyState
import com.hikari.app.ui.components.GlassSearchField
import com.hikari.app.ui.navigation.Routes
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
private fun FilterDropdown(label: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    app: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(app) {
    private val manager = (app as HikariApp).providers
    private val repo = ContentRepository(manager)

    // Query + selection live in SavedStateHandle so they survive the activity
    // being recreated while the video player runs. Without this, watching a
    // stream from the search results and coming back found an empty screen (the
    // ViewModel was recreated and the query lost) — forcing a re-search after
    // every video. On recreation the saved query is restored, so the search
    // re-runs automatically and the results come straight back.
    private val _query = MutableStateFlow(savedState.get<String>("query") ?: "")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Selected provider ids to search in. EMPTY = search ALL providers. */
    private val _selectedProviders =
        MutableStateFlow(savedState.get<ArrayList<String>>("providers")?.toSet() ?: emptySet())
    val selectedProviders: StateFlow<Set<String>> = _selectedProviders.asStateFlow()

    val providers: StateFlow<List<ContentProvider>> = manager.providers

    /**
     * Results + status live in the process-wide [SearchSession], NOT here, so
     * the multi-page scan survives this ViewModel being recreated (it is, every
     * time the user watches something and comes back). Returning to Search then
     * shows the results already collected and resumes the same scan — instead
     * of restarting from page 1.
     */
    val results: StateFlow<List<MediaItem>> = SearchSession.results
    val searching: StateFlow<Boolean> = SearchSession.searching

    init {
        viewModelScope.launch {
            combine(_query.debounce(400).distinctUntilChanged(), _selectedProviders) { q, _ -> q }
                .collectLatest { q ->
                    if (q.isBlank()) {
                        SearchSession.clear()
                        return@collectLatest
                    }
                    SearchSession.search(repo, q, _selectedProviders.value)
                }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
        savedState["query"] = q
    }

    /** "All sources" — empty set means every provider. */
    fun selectAll() {
        _selectedProviders.value = emptySet()
        savedState["providers"] = ArrayList<String>()
    }

    /** Selects exactly one provider — used by Home's "Search this extension"
     *  entry point, which scopes the search to the catalog you were browsing. */
    fun selectProvider(id: String) {
        if (id.isBlank()) return
        _selectedProviders.value = setOf(id)
        savedState["providers"] = ArrayList(listOf(id))
    }

    /** Toggle one provider in/out of the multi-select. Refuses to empty the
     *  selection (which would silently become "All"); use selectAll() for that. */
    fun toggleProvider(id: String) {
        val cur = _selectedProviders.value
        val next = if (id in cur) cur - id else cur + id
        if (next.isNotEmpty()) {
            _selectedProviders.value = next
            savedState["providers"] = ArrayList(next)
        }
    }
}

@Composable
fun SearchScreen(
    nav: NavHostController,
    initialQuery: String = "",
    initialProvider: String = "",
) {
    val vm: SearchViewModel = viewModel()
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val searching by vm.searching.collectAsState()
    val selected by vm.selectedProviders.collectAsState()
    val providers by vm.providers.collectAsState()
    var typeFilter by rememberSaveable { mutableStateOf("All types") }
    var sourceMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }

    // Search-bar translator state: the text the user typed before translating
    // (null while showing English), the current target language, the language
    // menu, and whether a translation is in flight.
    var translatedFrom by remember { mutableStateOf<String?>(null) }
    var targetLang by rememberSaveable { mutableStateOf("zh-CN") }
    var langMenu by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank()) vm.setQuery(initialQuery)
        if (initialProvider.isNotBlank()) vm.selectProvider(initialProvider)
    }

    // One tap EN -> target language (Chinese by default), tap again to restore
    // the original English the user typed. The source language is whatever the
    // user wrote, auto-detected by the translator, so the same button also
    // turns a Chinese title back into English when it was already translated.
    fun toggleTranslate() {
        if (translating) return
        val restore = translatedFrom
        if (restore != null) {
            vm.setQuery(restore)
            translatedFrom = null
            return
        }
        val src = query.trim()
        if (src.isEmpty()) return
        scope.launch {
            translating = true
            val out = com.hikari.app.data.Translator.translateTo(src, targetLang)
            translating = false
            if (out.isNotBlank() && out != src) {
                translatedFrom = src
                vm.setQuery(out)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        GlassSearchField(
            value = query,
            onValueChange = { vm.setQuery(it); translatedFrom = null },
            placeholder = if (selected.isEmpty()) "Search across all providers…"
            else "Search in ${selected.size} selected provider${if (selected.size == 1) "" else "s"}…",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            trailing = {
                Box {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { toggleTranslate() },
                                    onLongPress = { langMenu = true },
                                )
                            }
                            .padding(10.dp)
                    ) {
                        if (translating) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.Translate,
                                contentDescription = if (translatedFrom != null)
                                    "Show original English" else "Translate to $targetLang",
                                tint = if (translatedFrom != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = langMenu,
                        onDismissRequest = { langMenu = false },
                    ) {
                        com.hikari.app.data.Translator.LANGUAGES.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(if (code == targetLang) "$label  ✓" else label)
                                },
                                onClick = {
                                    targetLang = code
                                    langMenu = false
                                    translatedFrom = null
                                },
                            )
                        }
                    }
                }
            }
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                FilterDropdown(label = typeFilter, onClick = { typeMenu = true })
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    listOf("All types", "Movie", "TV", "Anime").forEach { type ->
                        DropdownMenuItem(text = { Text(type) }, onClick = { typeFilter = type; typeMenu = false })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                val sourceLabel = if (selected.isEmpty()) "All sources" else providers.firstOrNull { it.config.id in selected }?.config?.name ?: "Selected sources"
                FilterDropdown(label = sourceLabel, onClick = { sourceMenu = true })
                DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                    DropdownMenuItem(text = { Text("All sources") }, onClick = { vm.selectAll(); sourceMenu = false })
                    providers.forEach { provider ->
                        DropdownMenuItem(text = { Text(provider.config.name) }, onClick = { vm.selectProvider(provider.config.id); sourceMenu = false })
                    }
                }
            }
        }
        if (searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (query.isBlank() && results.isEmpty()) {
            EmptyState(
                title = "Search",
                subtitle = "Type something to search across every provider.",
                actionLabel = null,
                action = null
            )
        } else if (!searching && results.isEmpty()) {
            EmptyState(
                title = "No results",
                subtitle = "Nothing matched \"$query\". Try a different title, or deselect providers in the row above.",
                actionLabel = null,
                action = null
            )
        } else {
            val filteredResults = results.filter { item ->
                when (typeFilter) {
                    "Movie" -> item.type == com.hikari.app.data.MediaType.MOVIE
                    "TV" -> item.type == com.hikari.app.data.MediaType.SERIES
                    "Anime" -> item.rawType.contains("anime", ignoreCase = true) || item.genres.any { it.contains("anime", ignoreCase = true) }
                    else -> true
                }
            }
            val namesById = providers.associateBy({ it.config.id }, { it.config.name })
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredResults, key = { it.uniqueId }) { item ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                Routes.safeNavigate(
                                    nav,
                                    Routes.detail(item.providerId, item.type, item.id, item.title, item.posterUrl, item.rawType)
                                )
                            }
                    ) {
                        AsyncImage(
                            model = Artwork.model(item),
                            contentDescription = item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        namesById[item.providerId]?.let { name ->
                            Text(
                                name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
