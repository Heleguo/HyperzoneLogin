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

package icu.h2l.login.vServer.backend.compat

import icu.h2l.login.HyperZoneLoginMain

/**
 * backend 模式专用（已简化）：
 * 临时 GameProfile 功能已移除，玩家直接使用客户端原始档案。
 * 该监听器保留为空壳以维持注册结构兼容。
 */
class BackendProfileLayerCompatListener {
    companion object {
        const val PLUGIN_CONFLICT_MESSAGE = "登录失败：检测到插件冲突。"
    }

    private fun isEnabled(): Boolean {
        return HyperZoneLoginMain.getInstance().serverAdapter?.needsBackendInitialProfileCompat() == true
    }
}
