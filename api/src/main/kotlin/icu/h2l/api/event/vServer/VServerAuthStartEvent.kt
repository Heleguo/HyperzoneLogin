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

package icu.h2l.api.event.vServer

import com.velocitypowered.api.proxy.Player
import icu.h2l.api.player.HyperZonePlayer

/**
 * 玩家进入等待区实现并开始认证流程前触发的事件。
 *
 * @property proxyPlayer 当前代理层玩家对象
 * @property hyperZonePlayer 当前登录态玩家对象
 */
class VServerAuthStartEvent(
    val proxyPlayer: Player,
    val hyperZonePlayer: HyperZonePlayer
) {
    /**
     * 监听器可设为 `true`，表示当前等待区实现已接受并开始认证流程。
     */
    var pass: Boolean = false
}
