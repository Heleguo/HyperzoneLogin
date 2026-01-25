# HyperZoneLogin
实现正版和盗版混合登入的插件
## 兼容情况
### 版本兼容情况
低于1.19.1:  
域名前加o-或者offline识别为离线玩家  
高于1.19.1:  
直接进入即可
### 启动器兼容情况
- [x] HMCL  
- [x] PCL  
- [x] 原版启动器  
- [x] Zalith

## 开发计划
- [x] 基础原理实现
- [ ] 添加基础toml配置
- [ ] 取代AuthMe实现离线登入
- [ ] 取代MultiLogin实现档案管理
- [ ] 为档案管理添加一套可用的Web页面

## 开发时间
啊。。遥遥无期呢，作者在考研
 
## 服务器启动参数
`java -Dignite.mods=./plugins -Dignite.jar=./velocity-3.4.0-SNAPSHOT-563.jar -Dignite.libraries=./libraries -jar ignite-launcher-1.1.1-SNAPSHOT-all.jar`