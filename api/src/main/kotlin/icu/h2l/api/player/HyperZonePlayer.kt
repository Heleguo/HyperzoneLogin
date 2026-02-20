package icu.h2l.api.player

import net.kyori.adventure.text.Component

/**
 * HyperZone 登录流程中的统一玩家抽象。
 *
 * 该对象用于封装登入流程中常用的能力，
 * 让各模块不再直接依赖底层 Limbo 会话处理实现。
 */
interface HyperZonePlayer {
    /**
     * 判断是否允许执行注册流程。
     *
     * 主要依据：数据库中是否已存在该玩家 Profile。
     */
    fun canRegister(): Boolean

    /**
     * 当前玩家是否已完成验证。
     */
    fun isVerified(): Boolean

    /**
     * 结束玩家验证流程。
     */
    fun overVerify()

    /**
     * 发送消息给玩家。
     */
    fun sendMessage(message: Component)
}