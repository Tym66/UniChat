package com.unichat.app.data

/**
 * 消息/联系人摄入的共享逻辑。
 *
 * 供两条数据源复用:
 * 1. UniChatProvider(Hook 跨进程写入)
 * 2. DbSyncManager(直接读微信/抖音数据库)
 */
object IngestHelper {

    /** 摄入一条消息:归并联系人 -> 去重插入 -> 更新未读/统计 */
    suspend fun ingestMessage(
        db: AppDatabase,
        msg: Message,
        peer: String,
        peerName: String = peer,
        phone: String = ""
    ): Boolean {
        if (peer.isBlank()) return false
        val contactId = resolveContactId(db, msg, peer, peerName, phone)
        if (contactId <= 0) return false
        val m = msg.copy(contactId = contactId)
        val id = db.messageDao().insert(m)
        if (id > 0 && m.direction == Direction.IN) {
            db.contactDao().bump(contactId, m.content.take(50), m.timestamp, System.currentTimeMillis())
        }
        bumpSync(db, msg.platform)
        return id > 0
    }

    /** 摄入联系人资料(按平台 ID / 手机号归并) */
    suspend fun ingestContact(db: AppDatabase, c: Contact) {
        val dao = db.contactDao()
        val existing = findMatchingContact(dao, c)
        if (existing == null) {
            dao.insert(c)
        } else {
            dao.update(mergeContact(existing, c))
        }
        bumpSync(db, c.platforms)
    }

    /** 根据平台 peer id 解析聚合联系人 id(已读同步用) */
    suspend fun resolveContactIdForPeer(db: AppDatabase, peer: String): Long {
        if (peer.isBlank()) return 0L
        return db.contactDao().findByPlatformId(peer)?.id ?: 0L
    }

    /** 根据消息解析/归并联系人,返回聚合联系人 id */
    suspend fun resolveContactId(
        db: AppDatabase,
        msg: Message,
        peer: String,
        peerName: String,
        phone: String
    ): Long {
        val dao = db.contactDao()

        // 1. 该平台 ID 已存在 -> 直接命中
        dao.findByPlatformId(peer)?.let { return it.id }

        // 2. 携带手机号 -> 跨平台归并
        if (phone.isNotBlank()) {
            val byPhone = dao.findByPhone(phone)
            if (byPhone != null) {
                val updated = if (msg.platform == Platform.WECHAT)
                    byPhone.copy(wechatId = peer, platforms = mergePlatforms(byPhone.platforms, msg.platform))
                else
                    byPhone.copy(douyinId = peer, platforms = mergePlatforms(byPhone.platforms, msg.platform))
                dao.update(updated)
                return byPhone.id
            }
        }

        // 3. 平台 ID 本身是手机号 -> 按手机号归并
        if (isPhoneLike(peer)) {
            val byPhone = dao.findByPhone(peer)
            if (byPhone != null) {
                val updated = if (msg.platform == Platform.WECHAT)
                    byPhone.copy(wechatId = peer, platforms = mergePlatforms(byPhone.platforms, msg.platform))
                else
                    byPhone.copy(douyinId = peer, platforms = mergePlatforms(byPhone.platforms, msg.platform))
                dao.update(updated)
                return byPhone.id
            }
        }

        // 4. 创建新联系人
        val contact = Contact(
            name = peerName.ifBlank { peer },
            phone = phone,
            wechatId = if (msg.platform == Platform.WECHAT) peer else "",
            douyinId = if (msg.platform == Platform.DOUYIN) peer else "",
            platforms = msg.platform,
            lastTime = msg.timestamp
        )
        return dao.insert(contact)
    }

    private suspend fun findMatchingContact(dao: ContactDao, c: Contact): Contact? {
        val platformId = if (c.wechatId.isNotBlank()) c.wechatId else c.douyinId
        if (platformId.isNotBlank()) {
            dao.findByPlatformId(platformId)?.let { return it }
        }
        if (c.phone.isNotBlank()) {
            dao.findByPhone(c.phone)?.let { return it }
        }
        return null
    }

    private fun mergeContact(old: Contact, incoming: Contact): Contact {
        return old.copy(
            name = incoming.name.ifBlank { old.name },
            phone = incoming.phone.ifBlank { old.phone },
            wechatId = incoming.wechatId.ifBlank { old.wechatId },
            douyinId = incoming.douyinId.ifBlank { old.douyinId },
            remark = incoming.remark.ifBlank { old.remark },
            avatarPath = incoming.avatarPath.ifBlank { old.avatarPath },
            platforms = mergePlatforms(old.platforms, incoming.platforms),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun mergePlatforms(old: String, new: String): String {
        return (old.split(",") + new.split(","))
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")
    }

    private fun isPhoneLike(s: String): Boolean {
        val digits = s.filter { it.isDigit() }
        return digits.isNotEmpty() && digits == s && digits.length in 7..15
    }

    /** 记录平台同步统计 */
    suspend fun bumpSync(db: AppDatabase, platform: String) {
        if (platform.isBlank()) return
        val dao = db.syncStatDao()
        val cur = dao.get(platform)
        dao.upsert(
            SyncStat(
                platform = platform,
                hookInstalled = cur?.hookInstalled ?: true,
                lastSyncAt = System.currentTimeMillis(),
                msgCount = (cur?.msgCount ?: 0) + 1
            )
        )
    }
}
