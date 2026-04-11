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

package icu.h2l.login.inject.network.netty

import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket
import icu.h2l.api.log.debug
import icu.h2l.api.log.error
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.login.manager.HyperZonePlayerManager
import icu.h2l.login.player.SelfPlayerInfoSkinSender
import icu.h2l.login.player.VelocityHyperZonePlayer
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise

/**
 * 拦截客户端方向的原始 self `ADD_PLAYER`，并用我们预先准备好的资料替换后立即退役。
 *
 * 设计目的：
 * 1. 不提前抢时机发包；
 * 2. 直接复用原始 self `ADD_PLAYER` 到达客户端的真实时机；
 * 3. 替换后马上 retire，避免长期挂在链路上影响其他 PlayerInfo 包。
 */
class SelfAddPlayerPacketReplacer(
    private val channel: Channel
) : ChannelOutboundHandlerAdapter() {

    override fun write(ctx: ChannelHandlerContext, msg: Any?, promise: ChannelPromise?) {
        try {
            if (!HyperZoneLoginMain.getMiscConfig().enableReplaceGameProfile) {
                super.write(ctx, msg, promise)
                return
            }

            val velocityPlayer = runCatching {
                HyperZonePlayerManager.getByChannel(channel)
            }.getOrNull() as? VelocityHyperZonePlayer

            if (velocityPlayer == null) {
                super.write(ctx, msg, promise)
                return
            }

            when (msg) {
                is UpsertPlayerInfoPacket -> handleModern(ctx, msg, promise, velocityPlayer)
                is LegacyPlayerListItemPacket -> handleLegacy(ctx, msg, promise, velocityPlayer)
                else -> super.write(ctx, msg, promise)
            }
        } catch (throwable: Throwable) {
            error(throwable) { "SelfAddPlayerPacketReplacer write failed: ${throwable.message}" }
            try {
                ctx.fireExceptionCaught(throwable)
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun handleModern(
        ctx: ChannelHandlerContext,
        packet: UpsertPlayerInfoPacket,
        promise: ChannelPromise?,
        velocityPlayer: VelocityHyperZonePlayer
    ) {
        if (!packet.containsAction(UpsertPlayerInfoPacket.Action.ADD_PLAYER)) {
            super.write(ctx, packet, promise)
            return
        }

        val selfEntry = packet.entries.firstOrNull { velocityPlayer.shouldReplaceSelfAddPlayer(it.profileId) }
        if (selfEntry == null) {
            super.write(ctx, packet, promise)
            return
        }

        val originalProfile = selfEntry.profile
        val replacementProfile = velocityPlayer.createPendingSelfAddPlayerProfile()
        if (replacementProfile == null) {
            velocityPlayer.markSelfAddPlayerIntercepted(originalProfile?.name ?: velocityPlayer.getTemporaryGameProfile().name)
            debug {
                "[ProfileSkinFlow] self ADD_PLAYER delayed: replacement profile not ready yet, player=${velocityPlayer.userName}, originalProfileId=${selfEntry.profileId}"
            }

            val passthroughPacket = SelfPlayerInfoSkinSender.createModernPassthroughWithoutSelf(packet, selfEntry.profileId)
            if (passthroughPacket != null) {
                ctx.write(passthroughPacket, promise)
            } else {
                promise?.setSuccess()
            }
            return
        }

        val passthroughPacket = SelfPlayerInfoSkinSender.createModernPassthroughWithoutSelf(packet, selfEntry.profileId)
        val replacementPacket = SelfPlayerInfoSkinSender.createModernReplacement(packet, selfEntry, replacementProfile)

        if (passthroughPacket != null) {
            ctx.write(passthroughPacket, ctx.voidPromise())
        }
        velocityPlayer.completeSelfSkinAddPlayerReplacement()
        retire(ctx)
        ctx.write(replacementPacket, promise)
    }

    private fun handleLegacy(
        ctx: ChannelHandlerContext,
        packet: LegacyPlayerListItemPacket,
        promise: ChannelPromise?,
        velocityPlayer: VelocityHyperZonePlayer
    ) {
        if (packet.action != LegacyPlayerListItemPacket.ADD_PLAYER) {
            super.write(ctx, packet, promise)
            return
        }

        val selfItem = packet.items.firstOrNull { item ->
            val itemUuid = item.uuid ?: return@firstOrNull false
            velocityPlayer.shouldReplaceSelfAddPlayer(itemUuid)
        }
        if (selfItem == null) {
            super.write(ctx, packet, promise)
            return
        }

        val replacementProfile = velocityPlayer.createPendingSelfAddPlayerProfile()
        if (replacementProfile == null) {
            velocityPlayer.markSelfAddPlayerIntercepted(selfItem.name)
            debug {
                "[ProfileSkinFlow] self ADD_PLAYER delayed: replacement profile not ready yet, player=${velocityPlayer.userName}, originalProfileId=${selfItem.uuid}"
            }

            val passthroughPacket = selfItem.uuid?.let {
                SelfPlayerInfoSkinSender.createLegacyPassthroughWithoutSelf(packet, it)
            }
            if (passthroughPacket != null) {
                ctx.write(passthroughPacket, promise)
            } else {
                promise?.setSuccess()
            }
            return
        }

        val passthroughPacket = selfItem.uuid?.let {
            SelfPlayerInfoSkinSender.createLegacyPassthroughWithoutSelf(packet, it)
        }
        val replacementPacket = SelfPlayerInfoSkinSender.createLegacyReplacement(selfItem, replacementProfile)

        if (passthroughPacket != null) {
            ctx.write(passthroughPacket, ctx.voidPromise())
        }
        velocityPlayer.completeSelfSkinAddPlayerReplacement()
        retire(ctx)
        ctx.write(replacementPacket, promise)
    }

    private fun retire(ctx: ChannelHandlerContext) {
        ctx.pipeline().remove(this)
    }
}

