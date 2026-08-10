package com.unichat.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unichat.app.data.Contact
import com.unichat.app.data.ModuleInfo
import com.unichat.app.ui.ChatDetailViewModel
import com.unichat.app.ui.ChatViewModel
import com.unichat.app.ui.ModuleViewModel
import com.unichat.app.ui.components.FloatingActionBar
import com.unichat.app.ui.screens.ChatDetailScreen
import com.unichat.app.ui.screens.ChatListScreen
import com.unichat.app.ui.screens.ModuleSearchScreen
import com.unichat.app.ui.theme.UniChatTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniChatTheme {
                MainScreen()
            }
        }
    }
}

private enum class Tab { CHAT, MODULE }

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val chatVm: ChatViewModel = viewModel { ChatViewModel(UniChatApp.instance.database) }
    val moduleVm: ModuleViewModel = viewModel { ModuleViewModel(UniChatApp.instance.database) }

    var tab by remember { mutableStateOf(Tab.CHAT) }
    var detailContactId by remember { mutableStateOf(-1L) }
    var inDetail by remember { mutableStateOf(false) }

    val detailVm: ChatDetailViewModel = viewModel { ChatDetailViewModel(UniChatApp.instance.database) }
    val contacts by chatVm.contacts.collectAsState()
    val modules by moduleVm.modules.collectAsState()
    val loading by moduleVm.loading.collectAsState()
    val error by moduleVm.error.collectAsState()

    LaunchedEffect(Unit) {
        moduleVm.refresh() // 首次进入自动拉取一次
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            inDetail -> {
                val contact = contacts.firstOrNull { it.id == detailContactId }
                LaunchedEffect(detailContactId) {
                    detailVm.load(detailContactId)
                    if (detailContactId > 0) detailVm.markRead(detailContactId)
                }
                val messages by detailVm.messages.collectAsState()
                ChatDetailScreen(
                    contact = contact,
                    messages = messages,
                    onBack = {
                        inDetail = false
                        detailContactId = -1L
                    },
                    onMarkRead = { detailVm.markRead(it) }
                )
            }
            tab == Tab.CHAT -> {
                val query by chatVm.queryState.collectAsState()
                ChatListScreen(
                    contacts = contacts,
                    query = query,
                    onQueryChange = { chatVm.setQuery(it) },
                    onContactClick = {
                        detailContactId = it.id
                        inDetail = true
                    }
                )
            }
            else -> {
                val query by moduleVm.queryState.collectAsState()
                val category by moduleVm.categoryState.collectAsState()
                ModuleSearchScreen(
                    modules = modules,
                    query = query,
                    onQueryChange = { moduleVm.setQuery(it) },
                    category = category,
                    onCategoryChange = { moduleVm.setCategory(it); moduleVm.refresh() },
                    loading = loading,
                    error = error,
                    onRefresh = { moduleVm.refresh() },
                    onModuleClick = { openGitHub(context, it.sourceUrl) }
                )
            }
        }

        // 底部悬浮操作栏
        if (!inDetail) {
            FloatingActionBar(
                onChatClick = { tab = Tab.CHAT },
                onModuleClick = { tab = Tab.MODULE },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .navigationBarsPadding()
            )
        }
    }
}

private fun openGitHub(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}
