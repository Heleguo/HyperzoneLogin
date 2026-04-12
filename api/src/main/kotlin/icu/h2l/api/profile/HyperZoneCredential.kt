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

import java.util.UUID

/**
 * 认证子模块向登录核心提交的可信凭证。
 *
 * - [channelId]：认证渠道唯一标识，由子模块负责稳定定义；
 * - [credentialId]：该渠道内部可识别的凭证标识；
 * - [profileId]：该凭证最终绑定到的正式 Profile。
 */
data class HyperZoneCredential(
    val channelId: String,
    val credentialId: String,
    val profileId: UUID
)

