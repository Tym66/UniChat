package com.unichat.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unichat.app.data.Contact
import com.unichat.app.data.Message
import com.unichat.app.data.ModuleCategory
import com.unichat.app.data.ModuleInfo
import com.unichat.app.data.AppDatabase
import com.unichat.app.data.SyncStat
import com.unichat.app.data.repo.ModuleRepoService
import com.unichat.app.sync.DbSyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(private val db: AppDatabase) : ViewModel() {

    private val query = MutableStateFlow("")
    val queryState: StateFlow<String> = query

    val contacts: StateFlow<List<Contact>> = query
        .flatMapLatest { kw -> if (kw.isBlank()) db.contactDao().observeAll() else db.contactDao().search(kw) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(kw: String) { query.value = kw }
}

class ChatDetailViewModel(private val db: AppDatabase) : ViewModel() {

    private val contactId = MutableStateFlow(-1L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<Message>> = contactId
        .flatMapLatest { id -> if (id <= 0) flow { emit(emptyList<Message>()) } else db.messageDao().observeByContact(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun load(contactId: Long) { this.contactId.value = contactId }

    fun markRead(contactId: Long) {
        viewModelScope.launch {
            db.messageDao().markAllRead(contactId)
            db.contactDao().markRead(contactId, System.currentTimeMillis())
        }
    }
}

/** 平台接入状态 + 直接读库同步 */
class SyncViewModel(private val db: AppDatabase, app: Application) : ViewModel() {
    val stats: StateFlow<List<SyncStat>> = db.syncStatDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val syncManager = DbSyncManager(app.applicationContext)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage

    /** 触发一次直接读库同步 */
    fun sync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try {
                val r = syncManager.syncAll(db)
                _syncMessage.value = r.summary
            } catch (t: Throwable) {
                _syncMessage.value = "同步失败: ${t.message}"
            } finally {
                _syncing.value = false
            }
        }
    }
}

class ModuleViewModel(
    private val db: AppDatabase,
    private val repo: ModuleRepoService = ModuleRepoService.instance
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow(ModuleCategory.LSPOSED)
    val queryState: StateFlow<String> = query
    val categoryState: StateFlow<String> = category

    val modules: StateFlow<List<ModuleInfo>> = combine(query, category) { kw, cat -> kw to cat }
        .flatMapLatest { (kw, cat) ->
            if (kw.isNotBlank()) db.moduleDao().search(kw)
            else db.moduleDao().observeByCategory(cat)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun setQuery(kw: String) { query.value = kw }
    fun setCategory(cat: String) { category.value = cat }

    /** 从 GitHub 拉取并缓存 */
    fun refresh() {
        if (loading.value) return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val list = repo.searchCategory(category.value)
                if (list.isNotEmpty()) {
                    db.moduleDao().upsertAll(list)
                }
            } catch (t: Throwable) {
                error.value = "拉取失败: ${t.message}"
            } finally {
                loading.value = false
            }
        }
    }
}
