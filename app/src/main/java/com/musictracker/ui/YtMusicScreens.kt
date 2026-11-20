@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musictracker.player.MusicPlayer

private const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
    "&continue=https%3A%2F%2Fmusic.youtube.com%2F"

/** Google's own sign-in page; when it lands on YouTube Music, its cookies are kept for the library. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtSignInScreen(onSignedIn: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                    Text("Sign in to YouTube Music", style = MaterialTheme.typography.titleMedium)
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        CookieManager.getInstance().removeAllCookies(null)
                        var done = false // the page can finish loading more than once
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    if (!url.startsWith("https://music.youtube.com")) return
                                    val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
                                    if (!done && com.musictracker.online.YtMusic.isSignedIn(cookie)) {
                                        done = true
                                        onSignedIn(cookie)
                                    }
                                }
                            }
                            loadUrl(SIGN_IN_URL)
                        }
                    },
                )
            }
        }
    }
}

/** A YouTube Music playlist: cover, Shuffle and Play, then its songs. */
@Composable
fun PlaylistScreen(state: PlaylistState, vm: MainViewModel, onDismiss: () -> Unit, onOpenPlayer: () -> Unit) {
    val context = LocalContext.current
    fun play(index: Int, shuffle: Boolean) {
        MusicPlayer.play(context, state.songs, index, shuffle)
        onDismiss()
        onOpenPlayer()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Artwork(state.thumbnail, 200.dp, seed = state.title)
                        Text(
                            state.title,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        if (!state.loading) {
                            Text(
                                "${state.songs.size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                if (state.songs.isNotEmpty()) {
                    item {
                        ShufflePlayButtons(
                            onShuffle = { play(state.songs.indices.random(), shuffle = true) },
                            onPlay = { play(0, shuffle = false) },
                        )
                    }
                }
                if (state.loading) {
                    item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
                state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                itemsIndexed(state.songs, key = { i, s -> "$i-${s.videoId}" }) { i, song ->
                    SongRow(
                        padding = NoPadding,
                        title = song.title,
                        subtitle = song.artist.ifBlank { "Unknown artist" },
                        artUrl = song.thumbnail,
                        onClick = { play(i, shuffle = false) },
                        trailing = {
                            IconButton(onClick = {
                                MusicPlayer.addToQueue(context, song)
                                vm.toast("Added to queue")
                            }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to queue") }
                        },
                    )
                }
            }
        }
    }
}
