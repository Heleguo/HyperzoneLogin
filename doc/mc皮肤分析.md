# mc皮肤分析

## 背景

当前项目里已经在 `velocity` 侧尝试通过改写登录成功包来影响客户端对“自己是谁”的认知，并进一步影响自己的皮肤渲染。

这里需要回答两个问题：

1. 除了当前使用的 `ServerLoginSuccessPacket` 这一条链路，客户端源码里还有没有别的办法，能让客户端改变对**自己 UUID / 自己档案 / 自己皮肤**的认知？
2. 如果有，这些办法到底是：
   - 真正改了“我是谁”；
   - 还是只改了“同一个 UUID 对应的皮肤纹理”；
   - 还是只是理论上能动、但实际上不稳定？

本文基于 `ref/mc` 下的客户端源码整理，目标是给后续实现和排障一个稳定结论。

---

## 先说结论

### 结论 1：**真正决定客户端“我是谁”的核心入口，仍然是登录阶段的 `GameProfile`**

在当前客户端源码里，本地玩家身份会在登录阶段由 `ClientboundLoginFinishedPacket` 下发的 `GameProfile` 固定下来。

它会一路进入：

- `ClientHandshakePacketListenerImpl.handleLoginFinished(...)`
- `CommonListenerCookie.localGameProfile`
- `ClientConfigurationPacketListenerImpl`
- `ClientPacketListener.localGameProfile`
- `LocalPlayer(..., connection.getLocalGameProfile())`

也就是说：

> 想让客户端改变“自己 UUID / 自己名字 / 自己默认皮肤归属”的认知，最核心、最稳定的入口仍然是登录阶段这份本地 `GameProfile`。

---

### 结论 2：**还有另一条能影响“自己皮肤渲染”的路径，但它本质上不是改身份，而是改 `PlayerInfo` 里的皮肤属性**

游戏阶段客户端会通过 `ClientboundPlayerInfoUpdatePacket` 的 `ADD_PLAYER` 动作，为某个 UUID 创建 `PlayerInfo`。这里的 `GameProfile` 包含：

- `UUID`
- `name`
- `properties`（包括 `textures`）

而 `AbstractClientPlayer.getSkin()` 实际上优先走的是：

- `getPlayerInfo()`
- `PlayerInfo.getSkin()`
- `SkinManager.createLookup(profile, requireSecure)`

所以：

> 除了登录阶段本地 `GameProfile` 之外，`ClientboundPlayerInfoUpdatePacket` 的 `ADD_PLAYER` 确实也能影响“自己最终显示什么皮肤”。

但这条路的本质是：

- 改的是同一个 UUID 对应的 `textures`；
- **不是**在运行中重新定义“我是谁”。

换句话说：

> `ClientboundLoginFinishedPacket` 更像“本地身份建立”；
> `ClientboundPlayerInfoUpdatePacket.ADD_PLAYER` 更像“同 UUID 下的皮肤来源覆盖”。

---

### 结论 3：**没有发现稳定的运行期 vanilla 包，能在不重登的情况下重新改写客户端对“自己 UUID / 自己名字”的认知**

排查结果里：

- `respawn` 不会改本地 `GameProfile`；
- `start configuration` / reconfigure 也只是沿用已有 `localGameProfile`；
- `PlayerInfoUpdate` 的普通 `UPDATE_*` 动作不带 `GameProfile.properties`；
- `PlayerInfoRemove + ADD_PLAYER` 对“自己皮肤刷新”理论上可尝试，但存在缓存问题，不是稳定方案。

因此：

> 如果目标是稳定改变客户端对“自己 UUID / 自己名字”的认知，当前源码里没有看到比登录阶段更稳的 vanilla 路径。

---

## 术语对齐

### 1. 项目里说的 `ServerLoginSuccessPacket`

在 `velocity` 模块里你当前操作的是：

- `velocity/src/main/kotlin/icu/h2l/login/inject/network/netty/ServerLoginSuccessPacketReplacer.kt`

从协议方向看，它是**服务端发给客户端**的登录成功包。

### 2. Mojang 客户端源码里的对应关键点

在 `ref/mc` 里，客户端登录阶段真正接收并写入本地 `GameProfile` 的类名是：

- `net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket`

不要和游戏阶段的：

- `ClientboundLoginPacket`

混淆。后者是进入游戏世界时的初始化包，不负责下发本地 `GameProfile`。

所以本文里说“登录成功包改自己身份”，在客户端源码落点上，主要对应的是：

- `ClientboundLoginFinishedPacket`
- 以及其后续把 `GameProfile` 传入 `LocalPlayer` 的链路

同时要补一句：

- `PlayerInfo` 不是由 `ClientboundLoginFinishedPacket` 直接创建的；
- `PlayerInfo` 是游戏阶段由 `ClientboundPlayerInfoUpdatePacket.ADD_PLAYER` 单独建立的；
- 但本地玩家后续显示皮肤时，会按自己的 UUID 去找这份 `PlayerInfo`。

因此两者关系应理解为：

> 没有“直接构造关系”，但有“通过同一 UUID 发生关联的渲染关系”。

---

## 一、本地玩家身份是如何固定下来的

### 1. 登录完成包直接携带 `GameProfile`

文件：`ref/mc/net/minecraft/network/protocol/login/ClientboundLoginFinishedPacket.java`

```java
public record ClientboundLoginFinishedPacket(GameProfile gameProfile)
```

并且它使用：

- `ByteBufCodecs.GAME_PROFILE`

文件：`ref/mc/net/minecraft/network/codec/ByteBufCodecs.java`

```java
StreamCodec<ByteBuf, GameProfile> GAME_PROFILE = StreamCodec.composite(
   UUIDUtil.STREAM_CODEC, GameProfile::id,
   PLAYER_NAME, GameProfile::name,
   GAME_PROFILE_PROPERTIES, GameProfile::properties,
   GameProfile::new
);
```

这说明登录完成时客户端收到的是完整 `GameProfile`，其中包括：

- `UUID`
- `name`
- `properties`

也就是：

> 客户端在登录结束时，不只是知道“我叫啥、我 UUID 是啥”，还会连同 profile properties 一起收到。

---

### 2. 这份 `GameProfile` 会被保存为本地身份

文件：`ref/mc/net/minecraft/client/multiplayer/ClientHandshakePacketListenerImpl.java`

```java
GameProfile localGameProfile = packet.gameProfile();
...
new CommonListenerCookie(..., localGameProfile, ...)
```

文件：`ref/mc/net/minecraft/client/multiplayer/CommonListenerCookie.java`

```java
GameProfile localGameProfile
```

文件：`ref/mc/net/minecraft/client/multiplayer/ClientConfigurationPacketListenerImpl.java`

```java
private final GameProfile localGameProfile;
...
this.localGameProfile = cookie.localGameProfile();
```

文件：`ref/mc/net/minecraft/client/multiplayer/ClientPacketListener.java`

```java
private final GameProfile localGameProfile;
...
this.localGameProfile = cookie.localGameProfile();
```

文件：`ref/mc/net/minecraft/client/player/LocalPlayer.java`

```java
super(level, connection.getLocalGameProfile());
```

所以链路非常明确：

> `ClientboundLoginFinishedPacket.gameProfile`
> → `CommonListenerCookie.localGameProfile`
> → `ClientPacketListener.localGameProfile`
> → `LocalPlayer` 构造时的 `GameProfile`

这就是客户端“本地自己是谁”的主来源。

---

### 3. `respawn` 不会改这份本地身份

文件：`ref/mc/net/minecraft/client/multiplayer/ClientPacketListener.java`

在 `handleRespawn(...)` 中：

```java
newPlayer = this.minecraft.gameMode.createPlayer(...)
```

而 `MultiPlayerGameMode.createPlayer(...)`：

文件：`ref/mc/net/minecraft/client/multiplayer/MultiPlayerGameMode.java`

```java
return new LocalPlayer(this.minecraft, level, this.connection, ...)
```

最终 `LocalPlayer` 仍然是：

```java
super(level, connection.getLocalGameProfile());
```

这说明：

> `respawn` 只是重建 `LocalPlayer` 实体，但用的还是 `ClientPacketListener.localGameProfile`，不会在运行中重新改 UUID / name。

---

### 4. reconfigure 也不会改这份本地身份

文件：`ref/mc/net/minecraft/client/multiplayer/ClientPacketListener.java`

`handleConfigurationStart(...)` 中：

```java
new CommonListenerCookie(..., this.localGameProfile, ...)
```

也就是说：

> 进入重新配置阶段时，客户端仍然把旧的 `localGameProfile` 原样传下去，并没有借这个阶段重设本地身份。

因此在 vanilla 视角里：

- `login finished` 会设定本地身份；
- `respawn` / `reconfigure` 都只会复用它。

---

## 二、客户端实际是如何决定“自己显示什么皮肤”的

这里必须区分两个概念：

1. **本地玩家身份**：我是谁，我的 UUID / name 是什么；
2. **本地玩家皮肤渲染**：最终渲染时去哪里取 `textures`。

它们相关，但不是同一层。

---

### 1. 本地玩家渲染皮肤时，优先查 `PlayerInfo`

文件：`ref/mc/net/minecraft/client/player/AbstractClientPlayer.java`

```java
protected @Nullable PlayerInfo getPlayerInfo() {
   if (this.playerInfo == null) {
      this.playerInfo = Minecraft.getInstance().getConnection().getPlayerInfo(this.getUUID());
   }
   return this.playerInfo;
}
```

```java
public PlayerSkin getSkin() {
   PlayerInfo info = this.getPlayerInfo();
   return info == null ? DefaultPlayerSkin.get(this.getUUID()) : info.getSkin();
}
```

结论：

> 客户端渲染“自己皮肤”时，不是永远直接看 `LocalPlayer` 构造时那份 `GameProfile`。
> 它会优先去 `ClientPacketListener.playerInfoMap` 里按自己的 UUID 找 `PlayerInfo`。

如果找不到，才退回默认皮肤：

```java
DefaultPlayerSkin.get(this.getUUID())
```

因此这里要特别强调：

> `ClientboundLoginFinishedPacket` 不会直接把自己的 `properties` 自动同步成 `PlayerInfo`；
> 但它决定了“本地玩家是谁、之后按哪个 UUID 去找 `PlayerInfo`”。

---

### 2. `PlayerInfo` 的皮肤来自它自己的 `GameProfile`

文件：`ref/mc/net/minecraft/client/multiplayer/PlayerInfo.java`

```java
private final GameProfile profile;
```

```java
public PlayerSkin getSkin() {
   if (this.skinLookup == null) {
      this.skinLookup = createSkinLookup(this.profile);
   }
   return this.skinLookup.get();
}
```

也就是说：

> 一旦 `PlayerInfo` 建好了，客户端之后查皮肤用的是 `PlayerInfo.profile`，不是直接重新回头读 `LocalPlayer` 当初那份 profile。

---

### 3. `SkinManager` 从 `GameProfile.properties` 里取纹理

文件：`ref/mc/net/minecraft/client/resources/SkinManager.java`

```java
Property packedTextures = this.services.sessionService().getPackedTextures(profile);
return this.skinCache.getUnchecked(new CacheKey(profile.id(), packedTextures));
```

并且：

```java
return minecraft.getSkinManager().createLookup(profile, requireSecure);
```

因此：

> 对客户端来说，最终皮肤来源仍然是 `GameProfile` 上的 `textures` property。

如果没有 `textures`，或者不满足安全要求，则可能回落到默认皮肤。

---

### 3.1 `PlayerInfo` 到底会用到哪些字段

结合 `ClientboundPlayerInfoUpdatePacket`、`ClientPacketListener`、`PlayerInfo`、`AbstractClientPlayer` 当前版本源码，可以把客户端对 `PlayerInfo` 的读取拆成下面几类：

#### A. `profile.id`（UUID）——**关键，决定关联与皮肤查找主键**

- `ClientPacketListener.playerInfoMap` 以 `UUID` 为 key 存储 `PlayerInfo`
- `AbstractClientPlayer.getPlayerInfo()` 也是按 `this.getUUID()` 去 `getPlayerInfo(UUID)`
- `DefaultPlayerSkin` 回退也是按 `UUID`
- `SkinManager` 缓存 key 里也包含 `profile.id`
- `PlayerInfo.createSkinLookup(...)` 里 `requireSecure` 的判断，也会看这个 `UUID` 是否等于 `Minecraft.user.uuid`

也就是说：

> 对“自己皮肤能不能命中到这份 PlayerInfo”来说，`UUID` 是绝对核心字段。

#### B. `profile.properties`（尤其 `textures`）——**关键，决定皮肤内容**

- `SkinManager.get(profile)` 通过 `sessionService().getPackedTextures(profile)` 读取纹理属性
- `PlayerInfo.getSkin()` 懒加载后会缓存这份皮肤解析结果

也就是说：

> `textures` 是否存在、是否能通过当前 secure 判定，直接决定最终是自定义皮肤还是默认皮肤。

#### C. `profile.name`（玩家名）——**会被使用，但不是当前版本皮肤查找主键**

当前版本明确能看到的使用点主要是：

- `ClientPacketListener.getPlayerInfo(String)` / `getPlayerInfoIgnoreCase(String)`：按名字查 `PlayerInfo`
- `PlayerInfo.getTeam()`：按 `this.getProfile().name()` 去记分板查队伍
- `AbstractClientPlayer` 构造时会用实体自己的 `GameProfile.name` 判断 `deadmau5` 彩蛋耳朵
- 某些日志 / 社交 / UI 语义里也会读名字

但对“自己皮肤渲染主链”来说，当前版本源码没有看到：

- 用 `profile.name` 去命中 `playerInfoMap`
- 用 `profile.name` 去选择皮肤纹理
- 用 `profile.name` 去决定 `AbstractClientPlayer.getSkin()` 返回哪一张皮肤

因此当前版本可以下一个比较稳的结论：

> **名字不是 self 皮肤命中的主键；UUID 和 textures 才是。**

---

### 3.2 但工程上仍建议 self `ADD_PLAYER` 的名字与客户端 self 名字保持一致

虽然当前版本源码里，名字不是皮肤主链的关键字段，但工程上仍然建议：

- self `ADD_PLAYER.profile.name`
- 登录阶段客户端最终认定的 self name

尽量保持一致。

原因不是“否则一定没皮肤”，而是：

1. 可以避免记分板队伍、按名字查玩家、tab 相关语义出现分裂；
2. 可以避免排障时把“名字不一致带来的副作用”误判成“皮肤链本身有问题”；
3. 如果未来还要兼容旧版本客户端，名字参与行为的概率会比当前版本更高。

所以更准确的说法是：

> **名字在当前版本不是皮肤主因，但应保持一致，以免引入额外变量。**

---

### 4. 默认皮肤只和 UUID 有关

文件：`ref/mc/net/minecraft/client/resources/DefaultPlayerSkin.java`

```java
public static PlayerSkin get(final UUID profileId) {
   return DEFAULT_SKINS[Math.floorMod(profileId.hashCode(), DEFAULT_SKINS.length)];
}
```

所以：

> 即使没有任何 `textures`，只改 UUID 也会影响默认皮肤的落点。

但这只是默认皮肤分配，不是自定义皮肤上传或 Mojang 纹理本身。

---

### 5. `ClientboundLoginFinishedPacket` 不带皮肤时，后续 `ADD_PLAYER` 还能不能补救

这部分是当前实现判断里非常关键的细节。

需要分两种情况看：

#### 情况 A：本地玩家第一次查皮肤时，还没有拿到非空 `PlayerInfo`

`AbstractClientPlayer.getPlayerInfo()` 的逻辑是：

```java
if (this.playerInfo == null) {
   this.playerInfo = Minecraft.getInstance().getConnection().getPlayerInfo(this.getUUID());
}
```

如果当时 `connection.getPlayerInfo(uuid)` 返回 `null`，那么字段仍然是 `null`。

这意味着：

- 此时虽然 `ClientboundLoginFinishedPacket` 可能没带 `textures`；
- 客户端第一次渲染可能会先退回默认皮肤；
- 但后面如果来了**同 UUID** 的 `ClientboundPlayerInfoUpdatePacket.ADD_PLAYER`，仍然有机会在后续渲染时补上皮肤。

也就是说：

> `LoginFinished` 没皮肤，并不自动等于“之后永远没皮肤”。

#### 情况 B：本地玩家第一次已经拿到了一个非空但无皮肤的 `PlayerInfo`

那 `AbstractClientPlayer.playerInfo` 就会缓存这个旧对象。

而 `PlayerInfo.getSkin()` 自己也会懒缓存：

```java
if (this.skinLookup == null) {
   this.skinLookup = createSkinLookup(this.profile);
}
```

因此如果第一次稳定拿到的是“无皮肤 `PlayerInfo`”，后面再发普通同 UUID `ADD_PLAYER`，就不应当乐观地认为客户端一定会自动切到新皮肤。

所以这部分更准确的结论是：

> `ClientboundLoginFinishedPacket` 无皮肤时，后续 `ADD_PLAYER` **有机会补救**；
> 但补救是否稳定，取决于 self `PlayerInfo` 的建立时机、是否同 UUID，以及本地玩家是否已经缓存住旧 `PlayerInfo`。

再补一个非常关键、很容易误判的实现细节：

`ClientPacketListener.handlePlayerInfoUpdate(...)` 在处理 `ADD_PLAYER` 时，是先：

```java
this.playerInfoMap.putIfAbsent(entry.profileId(), playerInfo)
```

然后再对该 entry 的 `actions` 逐项调用：

```java
this.applyPlayerInfoUpdate(action, entryx, info)
```

而 `applyPlayerInfoUpdate(...)` **并没有 `ADD_PLAYER` 分支**，也不会把 `profile`、`name`、`properties` 重写进已存在的 `PlayerInfo`。

这意味着：

> 对同一个 UUID 来说，**第一次成功放进 `playerInfoMap` 的那份 `GameProfile` 才真正决定 profile/name/textures**；
> 后面再来一个同 UUID `ADD_PLAYER`，并不会把旧 `PlayerInfo.profile` 替换掉。

因此如果你观察到：

- “我后来补发了 self `ADD_PLAYER`”；
- “包里 UUID、textures 看起来都对”；
- “但客户端仍然不显示预期皮肤”；

那么比起“名字不对导致皮肤不显示”，当前版本更值得优先怀疑的是：

1. **客户端先收到了另一份同 UUID 的旧 `ADD_PLAYER`，你的补包没有真正替换进去；**
2. **本地 `AbstractClientPlayer` 已经缓存住旧 `PlayerInfo`；**
3. **旧 `PlayerInfo` 的 `skinLookup` 已经按无皮肤或不通过 secure 的状态建立完成；**
4. **当前测试客户端版本并不是本文这份源码对应的版本。**

---

## 三、除了登录成功包外，还有没有别的办法？

答案是：**有一条“改皮肤”的辅助路径，但没有发现更稳定的“改本地身份”的替代路径。**

---

### 方案 A：登录阶段改本地 `GameProfile` —— 稳定、核心、真正改“我是谁”

来源：`ClientboundLoginFinishedPacket`

能影响：

- 本地玩家 UUID
- 本地玩家名字
- `LocalPlayer` 的基础 `GameProfile`
- 默认皮肤回退 UUID

这是当前最核心的入口。

如果你的目标是：

- 客户端认为“我现在就是另一个 UUID / 另一个名字”；
- 之后所有与本地身份相关的逻辑都跟着改；

那么目前源码里最稳的仍然是这一条。

---

### 方案 B：`ClientboundPlayerInfoUpdatePacket.ADD_PLAYER` —— 能影响皮肤，但不是真正改本地身份

文件：`ref/mc/net/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket.java`

`ADD_PLAYER` 动作里：

```java
String name = ByteBufCodecs.PLAYER_NAME.decode(input);
PropertyMap properties = ByteBufCodecs.GAME_PROFILE_PROPERTIES.decode(input);
entry.profile = new GameProfile(entry.profileId, name, properties);
```

客户端接收时：

文件：`ref/mc/net/minecraft/client/multiplayer/ClientPacketListener.java`

```java
PlayerInfo playerInfo = new PlayerInfo(Objects.requireNonNull(entry.profile()), ...)
this.playerInfoMap.putIfAbsent(entry.profileId(), playerInfo)
```

这说明：

> 如果对“自己的 UUID”发来一个 `ADD_PLAYER`，其 `GameProfile.properties` 中的 `textures` 确实可以进入 `PlayerInfo`，从而影响 `AbstractClientPlayer.getSkin()` 的结果。

但它有几个限制：

1. 它改的是 `PlayerInfo.profile`，不是 `ClientPacketListener.localGameProfile`；
2. 它不会让客户端重新认为“我换了一个 UUID / 名字”；
3. 它更像是“给当前 UUID 塞一份皮肤来源”。

所以这条路可以归纳成：

> **能改自己最终渲染皮肤，但不是改运行中的本地身份。**

此外，还应补一个工程上更实用的理解：

> 如果 `ClientboundLoginFinishedPacket` 先把本地 UUID 稳定下来，而后续 self `ADD_PLAYER` 再以**同 UUID** 送入想要的 `textures`，那么客户端就可能同时满足：
> 1. 自己 UUID 稳定；
> 2. 自己可见皮肤来自后续 `PlayerInfo`。

这个判断是当前源码阅读下的实现方向，不应写成无条件保证；它依赖同 UUID 匹配、包时序，以及客户端还未被旧 `PlayerInfo` 锁死等条件。

---

### 方案 C：`UPDATE_*` 类 player info 包 —— 不能更新 `textures`

`ClientboundPlayerInfoUpdatePacket.Action` 可见的增量动作有：

- `UPDATE_GAME_MODE`
- `UPDATE_LISTED`
- `UPDATE_LATENCY`
- `UPDATE_DISPLAY_NAME`
- `UPDATE_LIST_ORDER`
- `UPDATE_HAT`
- `INITIALIZE_CHAT`

这些动作不会重新带一份 `GameProfile.properties`。

客户端实际应用更新时：

文件：`ref/mc/net/minecraft/client/multiplayer/ClientPacketListener.java`

```java
private void applyPlayerInfoUpdate(Action action, Entry entry, PlayerInfo info)
```

这里只更新：

- game mode
- listed
- latency
- display name
- hat
- list order
- chat session

没有“更新 profile properties / textures”的分支。

所以：

> 常规 `UPDATE_*` player info 包不能用来热更新自己的皮肤纹理。

---

### 方案 D：`PlayerInfoRemove + ADD_PLAYER` —— 理论上可尝试，实际上不稳定

从包格式上说，确实可以：

1. 先 `ClientboundPlayerInfoRemovePacket`
2. 再发一个新的 `ADD_PLAYER`

这会让 `playerInfoMap` 里旧条目被移除，再放入新条目。

但客户端还有一个非常关键的实现细节：

文件：`ref/mc/net/minecraft/client/player/AbstractClientPlayer.java`

```java
private @Nullable PlayerInfo playerInfo;
```

```java
if (this.playerInfo == null) {
   this.playerInfo = Minecraft.getInstance().getConnection().getPlayerInfo(this.getUUID());
}
```

也就是说：

> `AbstractClientPlayer` 会把第一次查到的 `PlayerInfo` 缓存在字段里，之后不会主动失效重取。

这意味着：

- 即使 `playerInfoMap` 中 self 的 entry 被 remove 再 add；
- 已经存在的本地玩家实体也可能还抓着旧的 `PlayerInfo` 引用；
- 没有看到明确的客户端逻辑会主动把 `AbstractClientPlayer.playerInfo` 置空。

因此：

> `remove + add` 更像一种理论上的 hack，不是稳定依赖的方案，尤其不适合拿来保证“自己皮肤一定刷新”。

---

### 方案 E：`respawn` / `reconfigure` / 其他常规游戏包 —— 没看到能重写本地 UUID/name 的能力

本次排查里没有发现这些包会在运行中重设：

- `ClientPacketListener.localGameProfile`
- `LocalPlayer` 的来源 `GameProfile`
- `Minecraft.user`

所以如果目标是：

- 不断线；
- 不重新进入登录阶段；
- 直接让客户端重新认自己 UUID/name；

当前源码里没看到稳定 vanilla 办法。

---

## 四、还有一个很关键的坑：客户端是否把这个 UUID 认成“自己本人”

文件：`ref/mc/net/minecraft/client/multiplayer/PlayerInfo.java`

```java
boolean requireSecure = !minecraft.isLocalPlayer(profile.id());
```

文件：`ref/mc/net/minecraft/client/Minecraft.java`

```java
public boolean isLocalPlayer(final UUID profileId) {
   return profileId.equals(this.getUser().getProfileId());
}
```

文件：`ref/mc/net/minecraft/client/User.java`

```java
public UUID getProfileId() {
   return this.uuid;
}
```

这里的“是不是本地玩家”，比较的不是：

- `ClientPacketListener.localGameProfile.id()`

而是：

- 启动器 / 会话里的 `Minecraft.user.uuid`

这会带来一个很实际的问题：

> 如果代理在登录阶段把客户端眼中的 self UUID 改成了**不同于启动器账户 UUID** 的值，客户端在 `PlayerInfo` 取皮肤时就可能不会把它当作“真正的本地玩家”，从而启用更严格的 `requireSecure` 判断。

而 `SkinManager.createLookup(...)` 在 `requireSecure == true` 时，会过滤掉非安全皮肤，最后可能退回默认皮肤。

因此：

- **改 self UUID** 不只是“身份变了”这么简单；
- 它还会影响客户端对 self 皮肤纹理的安全要求；
- 这也是为什么“UUID 变了、皮肤却没如预期显示”在客户端侧是完全可能发生的。

---

## 五、回到当前项目：对 HyperZoneLogin 的实际启示

### 1. 如果目标是“稳定改变客户端认知中的自己 UUID/name”

优先级最高的仍然是：

- 登录阶段下发给客户端的那份成功 `GameProfile`

也就是你现在在 `ServerLoginSuccessPacketReplacer` 上做的事情所对应的那层。

这一层最接近客户端源码里的：

- `ClientboundLoginFinishedPacket.gameProfile`

它是“真正改自己是谁”的主入口。

---

### 2. 如果目标只是“让自己渲染成某张皮肤”

理论上还可以借助：

- self 对应 UUID 的 `PlayerInfo`
- 特别是 `ADD_PLAYER` 时带进去的 `textures`

但这条路要注意：

- 它不是在改本地身份；
- 普通 `UPDATE_*` 不会更新 `textures`；
- `remove + add` 受 `AbstractClientPlayer.playerInfo` 缓存影响，不稳定；
- UUID 如果和客户端会话 UUID 不一致，还会碰到 `requireSecure` 的影响。

所以这条路更适合作为：

- **辅助理解客户端行为**
- 或 **验证客户端为何会显示 / 不显示某张皮肤**

而不适合当作主实现依赖。

---

### 2.1 当前更推荐的实现方向

结合当前源码分析，比较值得推进的方向是：

1. **保证客户端稳定接收到自己想要的 UUID**；
2. **阻断服务器原本发给客户端的 self `ADD_PLAYER`**；
3. **由代理自己补发一个 self `ADD_PLAYER`，并携带期望的 `textures`**；
4. 让客户端对“自己是谁”和“自己显示什么皮肤”分别落在：
   - 本地身份链：`ClientboundLoginFinishedPacket` / 对应成功登录身份；
   - 皮肤显示链：自定义的同 UUID `PlayerInfo ADD_PLAYER`。

当前实现层面还需要补一条工程约束：

- 如果上游 `textures` 自带 `signature`，替换 self `ADD_PLAYER` 时应原样保留；
- 不要把 `signature` 强行改成 `null`，否则在当前代理实现里可能连包体都无法按预期发送；
- 因而更稳妥的实现是直接缓存并复用整份 `ProfileSkinTextures`，而不是只取 `value` 后手动重建。

这个方向的优点在于：

- 不再依赖后端原始 self `ADD_PLAYER` 的 profile 内容；
- 可以主动控制 self `PlayerInfo` 何时建立、携带什么 `textures`；
- 如果同 UUID 稳定，客户端显示自己皮肤的概率会比“先收服务端原包、再事后修补”更高。

当前可以把这个方向记为：

> **在保持客户端 self UUID 稳定的前提下，拦截并替换 self `ADD_PLAYER`，是目前最值得尝试的实现路线。**

但文档里仍应保留前提条件，不把它写成绝对保证：

- 这里说的“稳定”是基于当前 `ref/mc` 版本源码推断；
- 它依赖 self `ADD_PLAYER` 与本地 UUID 严格一致；
- 它依赖客户端最终确实消费的是这份替换后的 `PlayerInfo`；
- 它依赖没有被更早建立的旧 `PlayerInfo` / `skinLookup` 抢先锁定；
- 它也默认你对签名策略、`requireSecure`、以及包时序有完整控制。

---

### 3. 如果想做到“运行中无感切换自己 UUID 与皮肤”

从当前 vanilla 客户端源码看：

> 没有发现稳定、规范、可长期依赖的包级方案。

想要做到真正稳定的“热切换”：

- 要么重新走一轮登录身份建立过程；
- 要么接受客户端侧存在缓存与安全判定限制；
- 要么就不是 vanilla 客户端能力范围，而是需要 mod / 客户端注入。

---

## 六、最终结论

可以把结论压缩成三句话：

1. **要改客户端对“自己是谁”的认知，核心仍然是登录阶段那份本地 `GameProfile`。**
2. **`PlayerInfo ADD_PLAYER` 还能影响“自己显示什么皮肤”，但它改的是皮肤来源，不是本地身份。**
3. **没有发现稳定的运行期 vanilla 包，能在不中断登录链的前提下重新改写客户端对自己 UUID/name 的认知。**

因此对当前项目来说：

- `ServerLoginSuccessPacket` 这一层仍然是主战场；
- `PlayerInfo` 链路更适合拿来解释皮肤表现，而不是替代登录身份链；
- `remove + add` 之类方案不要作为稳定方案押宝。

如果进一步结合当前实现方向，可以把结论再具体化成：

1. `ServerLoginSuccessPacket` / 对应的登录成功身份链，负责把客户端的 self UUID 稳定下来；
2. self `ADD_PLAYER` 更适合作为“自己可见皮肤”的主控制点；
3. 与其等待后端发出不理想的 self `ADD_PLAYER` 再被动修补，不如主动阻断并替换；
4. 该方向在当前源码分析下是合理路线，但仍应视为“当前最优工程判断”，而不是脱离版本与时序条件的绝对结论。

---

## 附：本次确认过的关键源码

### 身份建立链

- `ref/mc/net/minecraft/network/protocol/login/ClientboundLoginFinishedPacket.java`
- `ref/mc/net/minecraft/network/codec/ByteBufCodecs.java`
- `ref/mc/net/minecraft/client/multiplayer/ClientHandshakePacketListenerImpl.java`
- `ref/mc/net/minecraft/client/multiplayer/CommonListenerCookie.java`
- `ref/mc/net/minecraft/client/multiplayer/ClientConfigurationPacketListenerImpl.java`
- `ref/mc/net/minecraft/client/multiplayer/ClientPacketListener.java`
- `ref/mc/net/minecraft/client/multiplayer/MultiPlayerGameMode.java`
- `ref/mc/net/minecraft/client/player/LocalPlayer.java`

### 皮肤渲染链

- `ref/mc/net/minecraft/client/player/AbstractClientPlayer.java`
- `ref/mc/net/minecraft/client/multiplayer/PlayerInfo.java`
- `ref/mc/net/minecraft/client/resources/SkinManager.java`
- `ref/mc/net/minecraft/client/resources/DefaultPlayerSkin.java`
- `ref/mc/net/minecraft/client/Minecraft.java`
- `ref/mc/net/minecraft/client/User.java`

### 运行期 player info 链

- `ref/mc/net/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket.java`
- `ref/mc/net/minecraft/network/protocol/game/ClientboundPlayerInfoRemovePacket.java`
- `ref/mc/net/minecraft/client/multiplayer/ClientPacketListener.java`


