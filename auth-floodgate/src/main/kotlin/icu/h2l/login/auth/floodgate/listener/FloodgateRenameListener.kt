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

package icu.h2l.login.auth.floodgate.listener

import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.event.auth.LoginRenameEvent
import icu.h2l.login.auth.floodgate.credential.FloodgateHyperZoneCredential

/**
 * 响应 Rename 事件，销毁旧 Floodgate 凭证并以新名称重新提交。
 *
 * Floodgate UUID 由 Mojang 分配，不受 rename 影响，因此无需处理 ReUuid 事件。
 * 该监听器以 [PostOrder.EARLY] 运行，确保在核心 `LoginRenameListener` 之前完成凭证替换。
 *
 * ── 设计约定：rename 对 Floodgate 数据库匹配键无影响 ──
 *
 * Floodgate 认证的核心身份标识是 `xuid`（基岩版玩家的全局唯一 ID）：
 * - `floodgate_auth` 表以 `xuid` 为主匹配键，映射到 `profile_id`；
 * - rename 仅更新凭证的"注册展示名"，该名字只在首次写入 Profile 表时作为
 *   `profile.name` 落库，不会改变 `floodgate_auth` 表中 xuid → profile_id 的映射；
 * - 后续登录时同样通过 xuid 在 `floodgate_auth` 表中查找 `profile_id`，
 *   与 Profile 表的 `name` 列无关，因此 rename 不影响 Floodgate 的身份解析路径。
 *
 * 与离线模块的对比：
 * - 离线模块以规范化用户名（normalizedName）为匹配键，rename 会同步修改
 *   `offline_auth` 表中用于查找的 `name` 字段，是唯一"rename 影响 DB 匹配键"的模块。
 */
class FloodgateRenameListener {

    @Subscribe(order = PostOrder.EARLY)
    fun onRename(event: LoginRenameEvent) {
        val player = event.hyperZonePlayer
        val oldCredential = player.getSubmittedCredentials()
            .filterIsInstance<FloodgateHyperZoneCredential>()
            .firstOrNull() ?: return

        // withNewName 只更新 getRegistrationName() 的返回值（Profile 展示名），
        // xuid 字段不变，floodgate_auth 表的匹配键因此不受影响。
        player.destroyCredential(oldCredential.channelId)
        player.submitCredential(oldCredential.withNewName(event.newRegistrationName))
    }
}
