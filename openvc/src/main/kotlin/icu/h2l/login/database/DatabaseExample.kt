package icu.h2l.login.database

import java.util.UUID
import java.util.logging.Logger

/**
 * 数据库使用示例
 * 
 * 此文件展示了如何使用 DatabaseManager 和相关的数据库类
 */
object DatabaseExample {
    
    fun example(logger: Logger) {
        // 1. 创建数据库配置
        val config = DatabaseConfig.mysql(
            host = "localhost",
            port = 3306,
            database = "hyperzone_login",
            username = "root",
            password = "password",
            tablePrefix = "hz_"
        )
        
        // 或者使用 SQLite 数据库（推荐用于单机部署）
        // val config = DatabaseConfig.sqlite(path = "./data/hyperzone_login.db")
        
        // 或者使用 H2 数据库（用于测试）
        // val config = DatabaseConfig.h2(path = "./data/test_db")
        
        // 2. 创建数据库管理器
        val manager = DatabaseManager(logger, config)
        
        // 3. 连接数据库
        manager.connect()
        
        // 4. 注册需要的入口表
        manager.registerEntry("mojang")  // 正版入口
        manager.registerEntry("offline")  // 离线入口
        
        // 5. 创建所有表
        manager.createTables()
        
        // 6. 使用数据库助手类进行操作
        val helper = DatabaseHelper(manager, logger)
        
        // 示例：处理玩家登录
        exampleLoginFlow(helper, "mojang", "PlayerName", UUID.randomUUID())
        
        // 7. 程序退出时断开连接
        // manager.disconnect()
    }
    
    /**
     * 示例：登录流程
     */
    private fun exampleLoginFlow(
        helper: DatabaseHelper,
        entryId: String,
        playerName: String,
        playerUuid: UUID
    ) {
        // 步骤1：检查入口表中是否存在记录
        val existingPid = helper.findEntryByNameAndUuid(entryId, playerName, playerUuid)
        
        if (existingPid != null) {
            // 情况2：档案存在且与当前渠道对应 - 登录成功
            println("玩家 $playerName 登录成功！档案ID: $existingPid")
            return
        }
        
        // 步骤2：根据 name 或 uuid 查找档案
        val profileId = helper.findProfileByNameOrUuid(playerName, playerUuid)
        
        if (profileId == null) {
            // 情况1：档案不存在 - 创建新档案和入口记录
            val newProfileId = UUID.randomUUID()
            
            // 创建档案
            if (helper.createProfile(newProfileId, playerName, playerUuid)) {
                // 创建入口记录
                if (helper.createEntry(entryId, playerName, playerUuid, newProfileId)) {
                    println("新玩家 $playerName 创建成功！档案ID: $newProfileId")
                }
            }
        } else {
            // 情况3：档案存在但与当前渠道不同
            println("档案已存在但未绑定到当前渠道: $entryId")
            println("需要进一步处理（如验证密码等）")
            
            // 这里可以添加更复杂的逻辑，如：
            // - 检查是否存在离线账户
            // - 要求密码验证
            // - 绑定新渠道等
        }
    }
    
    /**
     * 示例：名称更新（皮肤站）
     */
    private fun exampleNameUpdate(
        helper: DatabaseHelper,
        entryId: String,
        oldName: String,
        newName: String,
        uuid: UUID
    ) {
        // 更新入口表中的名称
        if (helper.updateEntryName(entryId, uuid, newName)) {
            println("入口表中的名称已更新：$oldName -> $newName")
            
            // 注意：根据设计文档，不直接更新档案表中的名称
            // 需要玩家通过命令手动更新
        }
    }
}
