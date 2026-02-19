package icu.h2l.login.auth.online.events

import icu.h2l.login.auth.online.config.entry.EntryConfig

/**
 * Entry 注册事件
 * 当 Entry 配置被加载时触发，用于通知其他组件进行相应的注册操作
 */
data class EntryRegisterEvent(
    /**
     * Entry 配置名称
     */
    val configName: String,

    /**
     * Entry 配置对象
     */
    val entryConfig: EntryConfig
)