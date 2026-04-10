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

package icu.h2l.login.auth.offline.command

import icu.h2l.api.command.HyperChatCommandManager
import icu.h2l.api.command.HyperChatCommandRegistration
import icu.h2l.login.auth.offline.service.OfflineAuthService

object OfflineAuthCommandRegistrar {
    fun registerAll(
        commandManager: HyperChatCommandManager,
        authService: OfflineAuthService
    ) {
        commandManager.register(
            HyperChatCommandRegistration(
                name = "login",
                aliases = setOf("l"),
                command = LoginCommand(authService)
            )
        )
        commandManager.register(
            HyperChatCommandRegistration(
                name = "register",
                aliases = setOf("reg"),
                command = RegisterCommand(authService)
            )
        )
        commandManager.register(
            HyperChatCommandRegistration(
                name = "bind",
                aliases = setOf("b"),
                command = BindCommand(authService)
            )
        )
        commandManager.register(
            HyperChatCommandRegistration(
                name = "changepassword",
                aliases = setOf("cpass", "changepass"),
                command = ChangePasswordCommand(authService)
            )
        )
        commandManager.register(
            HyperChatCommandRegistration(
                name = "logout",
                command = LogoutCommand(authService)
            )
        )
        commandManager.register(
            HyperChatCommandRegistration(
                name = "email",
                aliases = setOf("mail"),
                command = EmailCommand(authService)
            )
        )
        commandManager.register(
            HyperChatCommandRegistration(
                name = "totp",
                aliases = setOf("2fa"),
                command = TotpCommand(authService)
            )
        )
        commandManager.register(
            HyperChatCommandRegistration(
                name = "unregister",
                aliases = setOf("delaccount"),
                command = UnregisterCommand(authService)
            )
        )
    }
}
