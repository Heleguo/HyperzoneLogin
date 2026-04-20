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

package icu.h2l.login.auth.offline.listener

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.event.auth.LoginRenameEvent
import icu.h2l.api.event.auth.LoginReUuidEvent
import icu.h2l.login.auth.offline.service.OfflineHyperZoneCredential

/**
 * 响应 Rename / ReUuid 事件，销毁旧离线凭证并以不可变新实例重新提交。
 *
 * 该监听器以 [PostOrder.EARLY] 运行，确保在核心
 * `LoginRenameListener` / `LoginReUuidListener`（[PostOrder.NORMAL]）执行前
 * 完成凭证替换，使核心读取到的凭证已反映最新状态。
 *
 * ── 设计约定：离线模块是唯一一个"rename 会影响写入数据库的匹配键"的模块 ──
 *
 * 离线认证的核心身份标识是"规范化用户名"（normalizedName）：
 * - `offline_auth` 表以 `name`（规范化用户名）为主匹配键，映射到 `profile_id`；
 * - Profile 表中的 `name` 同样取自注册名；
 * - 因此 rename 必须同时更新凭证的注册名 AND 将 [PendingOfflineRegistrationManager]
 *   中待绑定记录的 `normalizedName` 同步为新值，否则最终写入 `offline_auth` 表的
 *   记录将与玩家实际使用的名字不一致，导致后续无法通过用户名查找到对应 Profile。
 *
 * 与其他模块的对比：
 * - Floodgate 以 `xuid` 为匹配键，rename 只影响 Profile 的展示名，
 *   Floodgate 表记录的查找路径不受影响；
 * - Yggdrasil 以 Mojang 认证 UUID 为匹配键，rename 仅修改 Profile 展示名，
 *   入口表记录的查找路径同样不受影响。
 */
class OfflineRenameReUuidListener {

    @Subscribe(order = PostOrder.EARLY)
    fun onRename(event: LoginRenameEvent) {
        val player = event.hyperZonePlayer
        val oldCredential = player.getSubmittedCredentials()
            .filterIsInstance<OfflineHyperZoneCredential>()
            .firstOrNull() ?: return

        // withNewName 内部会同步更新 PendingOfflineRegistrationManager 中对��记录的
        // normalizedName，确保最终 offline_auth 表写入的是 rename 后的规范化用户名。
        player.destroyCredential(oldCredential.channelId)
        player.submitCredential(oldCredential.withNewName(event.newRegistrationName))
    }

    @Subscribe(order = PostOrder.EARLY)
    fun onReUuid(event: LoginReUuidEvent) {
        val player = event.hyperZonePlayer
        val oldCredential = player.getSubmittedCredentials()
            .filterIsInstance<OfflineHyperZoneCredential>()
            .firstOrNull() ?: return

        // withReUuid 将 passProfileCreateUuid 置为 false，使 getSuggestedProfileCreateUuid()
        // 返回 null，从而让核心 ReUuid 逻辑（createWithReUuid）自行生成带随机前缀的新 UUID，
        // 避免再次建议与已有记录冲突的正常离线 UUID。
        // 注意：名字（normalizedName）保持不变，offline_auth 表的匹配键不受影响。
        player.destroyCredential(oldCredential.channelId)
        player.submitCredential(oldCredential.withReUuid())
    }
}
