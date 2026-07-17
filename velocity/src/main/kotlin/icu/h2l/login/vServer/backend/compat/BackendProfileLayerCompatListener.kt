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

import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.event.profile.VerifyInitialGameProfileEvent
import icu.h2l.login.HyperZoneLoginMain

/**
 * backend 模式专用：
 * - 放行客户端初始 GameProfile（不再使用随机临时档案，无需 remap 校验）。
 *
 * outpre 全链路由自身桥接控制，不再复用这层兼容逻辑。
 */
class BackendProfileLayerCompatListener {
    companion object {
        const val PLUGIN_CONFLICT_MESSAGE = "登录失败：检测到插件冲突。"
    }

    @Subscribe
    fun onVerifyInitialGameProfileEvent(event: VerifyInitialGameProfileEvent) {
        if (!isEnabled()) return

        // 客户端上报的原始档案直接放行（不再使用随机临时档案，无需 remap 校验）
        event.pass = true
    }

    private fun isEnabled(): Boolean {
        return HyperZoneLoginMain.getInstance().serverAdapter?.needsBackendInitialProfileCompat() == true
    }
}
