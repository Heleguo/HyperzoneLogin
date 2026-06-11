# HyperZoneLogin Agent Rules

本文件记录 HyperZoneLogin 登录链路的强制工程规范。

## 1. 登录流程必须使用明确语义

HyperZoneLogin 的登录链路属于**严格状态机**，禁止使用会混淆状态边界的模糊命名或混合流程。

### 强制规则

- `resolve` 只允许表示：**按既有 `profileId` 解析已有 Profile**。
- `create` 只允许表示：**以指定注册名尝试新建 Profile**。
- `canResolve` 只允许表示：**既有 `profileId` 是否可解析**。
- `canCreate` 只允许表示：**给定 `(userName, uuid?)` 是否允许建档**。

### 明确禁止

以下语义在本项目中视为不合规，禁止重新引入：

- `resolveOrCreate`
- `canResolveOrCreate`
- 任何把 `resolve / create / rename / 回退匹配 / 自动改名` 混在一个入口中的 API

原因：登录流程必须可审计、可推导、可断言，不能依赖“也许解析、也许创建、也许顺便改名”的模糊副作用。

## 2. `clientOriginalName` 与 `registrationName` 必须严格区分

- `clientOriginalName`：客户端初始上报的**不可信原始名**，创建后不得改写。
- `registrationName`：当前等待区会话在“若要新建 Profile”时应使用的**待建档注册名**，可由 `/rename` 在等待区中显式修改。

禁止把 `clientOriginalName` 当成：

- 可信认证结果
- 正式游戏身份
- 当前可变注册名

## 4. `attachProfile` 后的游戏区流程边界

`attachProfile` 之后，玩家身份已进入正式 Profile 语义。

此阶段若存在 GameProfile 显示优化、正式 name/UUID 补偿同步、切服后身份一致性修复等需求，必须遵守：

- 只允许由核心层**内部受控机制**处理；
- 必须与等待区建档流程彻底隔离；
- 设计与实现时必须明确标注"这是正式游戏区流程，不是等待区流程"。

> **注意：** `/rename`、`/reUUID` 等命令已被移除。等待区不再支持用户名/UUID修改功能。

## 5.2 `VelocityHyperZoneProfileService` / `DatabaseHelper` 的禁止越界项

`VelocityHyperZoneProfileService` 与相关 `DatabaseHelper` 默认只承担：

- 既有 Profile 读取；
- 新 Profile 创建；
- attach / bind 所需业务逻辑；
- 围绕创建/读取的必要校验与查询辅助。

除非需求被**明确、单独、强语义**地说明，否则禁止：

- 在这些类中新增“修改现有 Profile 数据”的接口；
- 在这些类中直接改写既有 Profile 的正式 `name` / `UUID`；
- 把运行时显示修正、Velocity 内存补偿同步、等待区临时态修正，错误地下沉为正式 Profile 数据更新。

## 5. 等待区放行条件

玩家离开等待区必须同时满足：

- 认证已通过；
- 正式 Profile 已 attach。

任何仅完成其中一半的状态，都必须继续停留在等待区。

## 6. 模块实现建议

- Offline / Yggdrasil / Floodgate 等认证模块，如遇“可信认证完成但当前名称无法建档”，应保留玩家在等待区并提供明确提示；
- 后续恢复流程应通过显式 `create(...)` + 绑定逻辑推进，不得重新拼回模糊入口；
- 新模块接入时，优先复用核心事件与明确接口，不得私自引入“自动 resolveOrCreate”捷径。

## 7. 模块对齐原则

所有子模块都必须与现有模块保持一致的基础能力，不允许出现“功能已做但外围体验缺失”的半成品接入。

### 7.1 i18n 为强制项

凡是面向玩家展示的模块消息，必须接入统一 i18n：

- 提供模块级消息 helper（如 `YggdrasilMessages`、`FloodgateMessages`）；
- 提供 `messages/<namespace>/en_us.conf`、`zh_cn.conf`、`ru_ru.conf`；
- 在模块注册阶段复制 bundled locale 到数据目录；
- 禁止在监听器 / service 中长期保留玩家可见硬编码字符串。

### 7.2 模块行为与核心提示必须对齐

- 若模块会把玩家留在等待区，就必须给出下一步操作提示；
- 若模块支持 `/bindcode use` 这类恢复路径，就必须在对应失败提示中明确告知；
- 不允许某个模块具备功能，但提示链路缺失，导致玩家只能“猜命令”。

### 7.3 新模块接入检查项

新增或重构模块时，至少检查以下项目是否齐全：

1. 明确接口语义是否符合核心规则；
2. 玩家可见消息是否全部接入 i18n；
3. 是否提供中/英/俄三语资源；
4. 是否在失败/等待区状态下提供明确下一步引导；
5. 是否与现有模块的参数格式、提示风格、资源落盘方式保持一致。

## 8. 源码获取限制

- 寻找源码时不能去 cache 中找，如果需要源码且 `ref/` 目录下没有相关源码，请停止编码，并向用户请求对应的源码。
