表定义
-- authme definition

CREATE TABLE authme (id INTEGER AUTO_INCREMENT, username VARCHAR(255) NOT NULL UNIQUE, realname VARCHAR(255) NOT NULL DEFAULT 'Player', password VARCHAR(255) NOT NULL DEFAULT '', ip VARCHAR(40), lastlogin TIMESTAMP, regip VARCHAR(40), regdate TIMESTAMP NOT NULL DEFAULT '0', x DOUBLE NOT NULL DEFAULT '0.0', y DOUBLE NOT NULL DEFAULT '0.0', z DOUBLE NOT NULL DEFAULT '0.0', world VARCHAR(255) NOT NULL DEFAULT 'world', yaw FLOAT, pitch FLOAT, email VARCHAR(255), isLogged INT NOT NULL DEFAULT '0', hasSession INT NOT NULL DEFAULT '0', totp VARCHAR(32), CONSTRAINT table_const_prim PRIMARY KEY (id));

示例数据
INSERT INTO `authme` (`id`, `username`, `realname`, `password`, `ip`, `lastlogin`, `x`, `y`, `z`, `world`, `regdate`, `regip`, `yaw`, `pitch`, `email`, `isLogged`, `hasSession`, `totp`) VALUES (1, 'canchan0509', 'Canchan0509', '$SHA$889320a285c86a98$f9bca6fe17d28568742d7802dd46af09768deb6f01ec5bbefcad08e901cf28ab', '125.87.25.78', 1771674605039, 317.0186343484482, -3.5, 9.15890265178587, 'world', 1766910852748, '183.228.48.91', 75.1525, 6.59984, NULL, 0, 1, NULL);

迁移方案
读取realname、password，给他们对应到OfflineAuthTable上，其中示例的密码格式需要拆分，格式为SHA256，解析后插入到我们的数据库

我们的ProfileTable则都进行生成
UUID:用username生成prefix为OfflinePlayer
ProfileID用我们自己的算法进行生成

需要有迁移配置，配置项包括连接方式（SQLite和MYSQL），迁移后要生成单独的merge-am.log ，要有每一条数据的迁移结果和对应情况，以及最后的数据汇总
需要有用于触发的命令 hzl-merge ，然后子命令 am 进行迁移