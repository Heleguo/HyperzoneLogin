/*
 * This file is part of HyperZoneLogin, licensed under the GNU Affero General Public License v3.0 or later.
 *
 * Copyright (C) ksqeib (庆灵) <ksqeib@qq.com>
 * Copyright (C) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package icu.h2l.api.profile

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 离线→正版升级待办状态管理器。
 *
 * 当离线玩家执行 /upgrade 时，系统记录其 Profile 的升级意向；
 * 随后玩家以 Yggdrasil 模式重新连接并认证成功后，
 * YggdrasilAuthModule 可消费该待办事项，
 * 将 Yggdrasil 凭证绑定到现有 Profile 并移除离线绑定。
 */
object PendingUpgradeManager {

    data class PendingUpgrade(
        val offlineProfileId: UUID,
        val yggdrasilEntryId: String,
        val yggdrasilName: String,
        val yggdrasilUuid: UUID
    )

    private val pendingUpgrades = ConcurrentHashMap<UUID, PendingUpgrade>()

    /**
     * 记录一个待办升级。
     */
    fun addPending(offlineProfileId: UUID, entryId: String = "__pending__", name: String, uuid: UUID) {
        pendingUpgrades[offlineProfileId] = PendingUpgrade(offlineProfileId, entryId, name, uuid)
    }

    /**
     * 消费并移除一个待办升级。
     */
    fun consumePending(profileId: UUID): PendingUpgrade? {
        return pendingUpgrades.remove(profileId)
    }

    /**
     * 检查指定 Profile 是否有待办升级。
     */
    fun hasPending(profileId: UUID): Boolean {
        return pendingUpgrades.containsKey(profileId)
    }
}
