# Minecraft 物品左右键交互：库选型与 Paper 1.21.11 原生实现

- 调研日期：2026-11-02
- 目标工程：ZeoliteLib（Paper 1.21.11 / Java 21 / Gradle）
- 适用范围：服务端插件与小游戏物品交互，不涉及 Fabric/Forge 客户端模组

## 1. 结论

**没有找到成熟、维护中、可直接依赖的“物品左右键交互库”。** 这个需求在 Paper 服务端上属于平台原生能力，最合适的做法是在 ZeoliteLib 的 `item` 模块内实现自己的 `InteractiveItem` 抽象，而不是引入第三方框架。

具体判断：

1. **原生方案足够**：`PlayerInteractEvent` + `org.bukkit.event.block.Action` + `EquipmentSlot` 已经覆盖左键/右键、空气/方块；实体点击另有专用事件。物品身份用 `NamespacedKey` + `PersistentDataContainer` 标记即可，不需要 NBT 库。
2. **最接近的现成库是 ItemActionsLib**：它的 `ActionType` 设计和“每个动作一个监听器”的思路值得借鉴，但最后更新停留在 2020 年，没有 PDC、冷却和 1.21 维护，不能作为稳定依赖。
3. **PacketEvents / ProtocolLib 不是这个问题的答案**：它们是数据包层工具，只在需要拦截客户端原始交互包、做跨版本协议适配时才需要；普通小游戏物品用事件 API 更简单、更稳。
4. **Oraxen / ItemsAdder / ExecutableItems / MythicMobs / Denizen / Skript 是成品插件或脚本平台**，不是给插件库嵌入的依赖。它们适合服务器管理员配置，不适合 ZeoliteLib 的“ Java 回调执行代码”模型。
5. **推荐架构**：注册表按命名空间 ID 索引；监听 `PlayerInteractEvent` 处理左右键空气/方块，`PlayerInteractEntityEvent` 处理右键实体，`PrePlayerAttackEntityEvent` 处理左键实体；主手过滤、原版行为压制、冷却和去重由库统一处理。

## 2. 基线核实

工程使用的编译依赖是：

```kotlin
compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
```

本地 `paper-api-1.21.11-R0.1-SNAPSHOT.jar` 的关键签名已用 `javap` 核对：

- `PlayerInteractEvent` 存在 `getAction()` / `getHand()` / `getItem()` / `getClickedBlock()` / `getBlockFace()` / `getInteractionPoint()` / `useItemInHand()` / `useInteractedBlock()` / `setUseItemInHand(Result)` / `setUseInteractedBlock(Result)`。
- `Action` 是 `org.bukkit.event.block.Action`，**不是** `PlayerInteractEvent.Action`。
- `ItemStack` 实现的是 `io.papermc.paper.persistence.PersistentDataViewHolder`（只读视图），不是 `PersistentDataHolder`；写入用 `ItemStack#editPersistentDataContainer(...)` 或 `ItemMeta#getPersistentDataContainer()`。
- `HumanEntity` 提供 `setCooldown(ItemStack, int)` / `hasCooldown(ItemStack)` / `getCooldown(ItemStack)`。
- `io.papermc.paper.event.player.PlayerArmSwingEvent` 和 `PrePlayerAttackEntityEvent` 均存在。

## 3. 原生 API 的准确用法

### 3.1 动作枚举

```java
import org.bukkit.event.block.Action;

Action.LEFT_CLICK_BLOCK;   // 左键方块
Action.RIGHT_CLICK_BLOCK;  // 右键方块
Action.LEFT_CLICK_AIR;     // 左键空气
Action.RIGHT_CLICK_AIR;    // 右键空气
Action.PHYSICAL;           // 踩压力板/耕地等物理交互，不是点击
```

`Action` 自带 `isLeftClick()` / `isRightClick()`，但做物品技能时建议直接使用 `switch`，因为还需要区分空气/方块。

### 3.2 每个动作什么时候触发

| 动作 | 触发路径（1.21.11） | 想压制原版行为时 |
|---|---|---|
| `LEFT_CLICK_BLOCK` | 客户端 `START_DESTROY_BLOCK`，事件带主手与主手物品 | `setUseInteractedBlock(DENY)` 或 `setCancelled(true)` 阻止破坏；只设 `setUseItemInHand(DENY)` 挡不住破坏 |
| `LEFT_CLICK_AIR` | 客户端挥手包，射线没有命中方块/实体，或命中的实体超出交互距离且不是创造模式 | **取消事件不会阻止挥手动画**；要拦截挥手用 `PlayerArmSwingEvent`。对自定义物品逻辑本身取消与否不影响执行 |
| `RIGHT_CLICK_AIR` | 客户端 `UseItemPacket`，没有命中方块 | 服务端检查 `useItemInHand()`；设 `DENY` 或 `setCancelled(true)` |
| `RIGHT_CLICK_BLOCK` | 客户端 `UseItemOnPacket` | 方块交互看 `useInteractedBlock()`，物品使用看 `useItemInHand()`；两个都要按需 `DENY` |
| `PHYSICAL` | 踩方块、压力板、绊线等 | 直接忽略，不要当成左右键 |

`PlayerInteractEvent` 的 Javadoc 明确说明它“**potentially fired once for each hand**”。一次物理右键可能分别产生主手和副手事件，必须用 `event.getHand() == EquipmentSlot.HAND` 过滤，否则会重复执行。

### 3.3 极容易踩的四个坑

1. **空气事件默认就是 cancelled。** 1.21.11 源码里 `useInteractedBlock` 在 `clickedBlock == null` 时默认 `DENY`，而 `isCancelled()` 只反映 `useInteractedBlock()`。所以 `LEFT_CLICK_AIR` / `RIGHT_CLICK_AIR` 事件默认 `isCancelled() == true`。
   - 不要给 `PlayerInteractEvent` 监听器加 `ignoreCancelled = true`，否则空气点击会被直接跳过。
   - 不要用 `isCancelled()` 判断“玩家是否还能使用物品”；分别检查 `useItemInHand()` 与 `useInteractedBlock()`。
2. **`setCancelled(false)` 不等于强制允许。** 它只是把 `DENY` 降回 `DEFAULT`，不会把 `ALLOW` 改掉。要显式允许就设 `ALLOW`。
3. **`getBlockFace()` 在空气事件里也可能非 null**（1.21.11 传的是占位方向）。判断有没有方块必须用 `hasBlock()` / `getClickedBlock() != null`。
4. **左键空气不是“每次左键”都触发。** 玩家瞄准方块时会走 `LEFT_CLICK_BLOCK`；瞄准可攻击实体时可能根本不发 `LEFT_CLICK_AIR`。如果技能要求“只要左键就触发，不管指着什么”，应使用 `PlayerArmSwingEvent`，并避免同时再处理 `LEFT_CLICK_AIR/BLOCK`，否则会双触发。

### 3.4 实体点击

- **右键实体**：`PlayerInteractEntityEvent`。它带 `getHand()` 和 `getRightClicked()`；同样要过滤主手。
- **盔甲架**：只触发 `PlayerInteractAtEntityEvent`，不触发父类 `PlayerInteractEntityEvent`。需要支持盔甲架时单独监听，注意与父类事件去重。
- **左键实体**：Paper 提供 `PrePlayerAttackEntityEvent`，在伤害逻辑之前触发；即使实体不可攻击也会触发（`willAttack()` 可能为 `false`）。这是“左键尝试攻击”的最可靠信号。
- **实际造成伤害**：`EntityDamageByEntityEvent`，用 `getCause() == ENTITY_ATTACK` / `ENTITY_SWEEP_ATTACK` 判断近战。它不会在每次点击都触发（无敌帧、取消、未造成伤害时可能没有）。
- **纯挥手信号**：`PlayerArmSwingEvent`（Paper）继承 `PlayerAnimationEvent`，带 `getHand()`。每次挥手包都会触发，适合“左键使用物品”的统一入口。

### 3.5 物品身份与冷却

- 身份：给物品 PDC 写入一个字符串 ID，读取时按 ID 查注册表。**不要用显示名称或 lore 匹配**，它们可被玩家修改/重复。
- 写入（1.21.4+）：

  ```java
  stack.editPersistentDataContainer(pdc ->
      pdc.set(ID_KEY, PersistentDataType.STRING, "myplugin:fireball"));
  ```

- 读取：

  ```java
  String id = stack.getPersistentDataContainer()
      .get(ID_KEY, PersistentDataType.STRING);
  ```

- 冷却：`player.setCooldown(stack, ticks)` 可以让客户端显示原版冷却遮罩；但多个自定义物品可能共用同一个 `Material`，逻辑冷却建议再用 `Map<UUID, Map<String, Long>>` 按物品 ID 记录，避免互相影响。

## 4. 第三方库对比

### 4.1 可以作为依赖的

| 方案 | 定位 | 依赖方式 | 1.21.11 状态（调研时） | 许可 | 结论 |
|---|---|---|---|---|---|
| **Paper API** | 平台 API | `compileOnly` | 原生支持 | GPL-3.0（平台） | **推荐**，自己实现 `InteractiveItem` |
| **Item-NBT-API** | 原始 NBT 工具 | 可 shade + relocate | 2.16.1，支持 1.21.11 | MIT | 可选；1.21.11 用 PDC 已足够 |
| **PacketEvents** | 数据包/协议库 | 可 shade 或作为插件依赖 | 2.14.0，支持 1.21.11 | GPL-3.0 | 只在需要包级拦截时使用；普通点击不需要 |
| **ProtocolLib** | 数据包库 | `compileOnly` / 插件依赖 | 5.4.0 发布早于 1.21.11，1.21.11 仅 snapshot | GPL-2.0 | 新项目避免；版本支持滞后 |
| **CommandAPI** | 命令注册 | 可 shade | 12.1.0，支持 1.21.11 | MIT | 与点击无关；只在需要命令系统时用 |
| **Adventure / MiniMessage** | 文本组件 | `paper-api` 已传递依赖 | Paper 1.21.11 自带 4.26.1 | MIT | 物品名称/lore 用；不要额外引入 5.x |

### 4.2 成品插件 / 脚本平台（不是库依赖）

| 方案 | 实际形态 | 为什么不适合嵌入 |
|---|---|---|
| **Oraxen** | 付费自定义物品/内容插件 | 有自己的物品注册表和资源包体系；源码可读但许可不允许当库分发 |
| **ItemsAdder** | 付费自定义内容插件 | 闭源/专有；只有插件级 API，无公开 Maven 依赖 |
| **ExecutableItems** | 配置驱动自定义物品插件 | 有 addon API，但核心是成品插件；左/右键“激活器”是配置项，不是 Java 回调库 |
| **MythicMobs** | 配置驱动怪物/技能/物品插件 | 体量大、专有；物品技能绑定其技能系统 |
| **Denizen** | 脚本插件 | 面向服务器管理员的脚本语言，不是给插件嵌入的 API |
| **Skript** | 脚本插件 | 同上；GPL-3.0，嵌入有许可和维护负担 |

### 4.3 值得读源码的设计参考

| 项目 | 可借鉴点 | 注意 |
|---|---|---|
| **ItemActionsLib** | `ActionType` 枚举（左/右键 × 空气/方块 + 潜行变体）、按动作注册监听器 | 2020 年停更，Maven 仓库调研时不可达；只参考 API 形状 |
| **Nova** | 用 PDC 存物品 ID；`WrappedPlayerInteractEvent` 用标志位抑制主/副手重复；用 `USE_COOLDOWN` 数据组件做冷却 | LGPL-3.0；大型内容框架，不要嵌入 |
| **ItemJoin** | 配置物品注册表、按玩家记录冷却、退出清理 | LGPL-3.0；配置驱动，无 PDC 身份 |
| **CustomItemAPI (firewolf8385)** | 最小的 PDC + `onInteract(PlayerInteractEvent)` 模型 | MIT；早期项目，无点击类型/冷却抽象 |
| **Slimefun4** | PDC 存 `slimefun_item`、`PlayerRightClickEvent` 包装、`ItemUseHandler` 分发 | GPL-3.0；README 仅 1.16–1.20，不能当 1.21 实现 |

### 4.4 明确不建议

- `MinigamesLib`（mepeisen/mclib）：2017 年后停更。
- `GameAPI`（phase/GameAPI）：1.8 时代代码，停更。
- `ClickableItems`（stonar96）：2018 年，非 1.21 兼容。
- `ItemLib`（Miqhtiedev）：2020 年，只有几个类，不是交互框架。
- 各种 `ItemBuilder` / `ItemStackBuilder`：只负责构造物品，不处理点击路由。

## 5. 推荐落地到 ZeoliteLib

### 5.1 模块结构

```text
com.zeoliteLib.item
  ├─ InteractiveItem          公共接口/抽象：ID + 点击回调 + 冷却
  ├─ ClickType                左/右键 × 空气/方块/实体
  ├─ ItemClickContext         玩家、物品、点击类型、原始事件
  └─ InteractiveItemManager   静态 get()，经 LazyModuleHost 懒初始化

com.zeoliteLib.item.internal
  ├─ InteractiveItemRegistry  ID → InteractiveItem
  ├─ InteractiveItemListener  事件监听与分发
  └─ CooldownTracker          按 UUID + 物品 ID 记录冷却
```

监听器在 `InteractiveItemManager.create()` 里注册一次；不要放进 `onEnable`，保持现有骨架“模块首调才初始化”的纪律。实现公共类后，记得在 `module-info.java` 打开 `exports com.zeoliteLib.item;`。

### 5.2 点击类型

```java
public enum ClickType {
    LEFT_AIR,
    LEFT_BLOCK,
    LEFT_ENTITY,
    RIGHT_AIR,
    RIGHT_BLOCK,
    RIGHT_ENTITY,
    LEFT_SWING // 仅在 PlayerArmSwingEvent 模式下使用
}
```

建议不要只做 `LEFT` / `RIGHT` 两个值。小游戏里“左键空气放技能”和“左键方块挖掘”经常需要不同行为；把空气/方块/实体分开，后续加潜行变体也容易。

### 5.3 监听与分发草图

```java
public final class InteractiveItemListener implements Listener {

    private static final NamespacedKey ID_KEY = new NamespacedKey(plugin, "item_id");

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // 主手，避免副手重复
        ClickType click = switch (event.getAction()) {
            case LEFT_CLICK_AIR -> ClickType.LEFT_AIR;
            case LEFT_CLICK_BLOCK -> ClickType.LEFT_BLOCK;
            case RIGHT_CLICK_AIR -> ClickType.RIGHT_AIR;
            case RIGHT_CLICK_BLOCK -> ClickType.RIGHT_BLOCK;
            case PHYSICAL -> null;
        };
        if (click == null) return;

        dispatch(event.getPlayer(), event.getItem(), click, event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        dispatch(event.getPlayer(),
            event.getPlayer().getInventory().getItemInMainHand(),
            ClickType.RIGHT_ENTITY, event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onAttackEntity(PrePlayerAttackEntityEvent event) {
        dispatch(event.getPlayer(),
            event.getPlayer().getInventory().getItemInMainHand(),
            ClickType.LEFT_ENTITY, event);
    }

    private void dispatch(Player player, ItemStack stack,
                          ClickType click, Cancellable event) {
        if (stack == null || stack.getType().isAir()) return;

        String id = stack.getPersistentDataContainer()
            .get(ID_KEY, PersistentDataType.STRING);
        InteractiveItem item = id == null ? null : registry.get(id);
        if (item == null) return;

        // 自定义物品：先压掉原版行为，再执行回调
        event.setCancelled(true); // 同时 DENY 方块交互与物品使用

        if (!item.tryUse(player)) return; // 冷却中
        item.onClick(new ItemClickContext(player, stack, click));
    }
}
```

要点：

- `event.setCancelled(true)` 对 `PlayerInteractEvent` 会同时把 `useInteractedBlock` 与 `useItemInHand` 设为 `DENY`，适合“这个物品完全由我接管”的场景。
- 如果只想压制其中一半（例如允许右键方块交互、只禁用物品使用），改用 `setUseInteractedBlock(...)` / `setUseItemInHand(...)` 分别设置。
- 冷却检查放在取消原版行为之后：冷却中也不应让物品去挖方块、吃东西或放方块。
- 分发前必须确认 PDC ID 存在；没有 ID 的普通物品直接放行。

### 5.4 需要“每次左键都触发”时

`PlayerInteractEvent` 的 `LEFT_CLICK_AIR` 在玩家瞄准方块/近处实体时不会触发。如果物品技能要求“只要左键就执行”，用：

```java
@EventHandler(priority = EventPriority.NORMAL)
public void onSwing(PlayerArmSwingEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) return;

    dispatch(event.getPlayer(),
        event.getPlayer().getInventory().getItemInMainHand(),
        ClickType.LEFT_SWING, null); // 不要取消挥手动画
}
```

**不要同时**监听 `LEFT_CLICK_AIR` / `LEFT_CLICK_BLOCK` 和 `PlayerArmSwingEvent` 来做同一个左键技能，否则一次点击会执行两遍。二选一：

- 需要知道点的是空气、方块还是实体：用 `PlayerInteractEvent` + `PrePlayerAttackEntityEvent`。
- 只需要“左键这个动作”：用 `PlayerArmSwingEvent`，不区分目标。

### 5.5 物品 ID 的命名空间

ZeoliteLib 可能被多个插件同时使用，注册表是库级的，物品 ID 必须带命名空间：

```text
myplugin:fireball
myplugin:shop_wand
anotherplugin:grappling_hook
```

PDC 里存 `namespace:key` 字符串，注册表用 `NamespacedKey` 或字符串索引。不要用裸 `"fireball"`，否则两个插件会撞 ID。

### 5.6 冷却与清理

```java
boolean tryUse(Player player) {
    long now = System.currentTimeMillis();
    long readyAt = nextUse.getOrDefault(player.getUniqueId(), 0L);
    if (now < readyAt) return false;
    nextUse.put(player.getUniqueId(), now + cooldownMillis);
    return true;
}
```

- 客户端遮罩：`player.setCooldown(stack, ticks)`。
- 逻辑冷却：按 `UUID + 物品 ID` 记录，避免同 Material 的不同自定义物品共享冷却。
- 监听 `PlayerQuitEvent` 清理该玩家的冷却记录，防止内存泄漏。

### 5.7 Folia 注意

Paper 官方文档没有承诺这些交互事件在哪个线程触发；Folia 下不应假设主线程。普通 Paper 可以按主线程处理；如果要声明 Folia 兼容，回调里涉及实体/世界操作时用 `entity.getScheduler()` 或对应的区域调度器，不要直接跨线程操作游戏状态。

## 6. 验证与未决项

- 本文的原生 API 签名已用本地 `paper-api-1.21.11-R0.1-SNAPSHOT.jar` 执行 `javap` 核对。
- 事件触发路径的细节（`LEFT_CLICK_AIR` 的射线条件、右键方块的双重判定、`isCancelled()` 的默认值）来自 PaperMC `ver/1.21.11` 服务端源码与补丁，不是 Javadoc 的完整描述；升级版本前应重新核对。
- `PlayerInteractEvent` 的主手/副手事件顺序未在 Javadoc 中定义，代码不应依赖顺序。
- Folia 下这些事件的线程归属未在官方文档中明确，本文只给出保守建议。
- 第三方库的版本、维护状态和许可会变化；本文的版本号是 2026-11-02 调研时的快照，接入前需重新确认。
- ItemActionsLib 的 Maven 仓库在调研时不可达，因此只把它作为设计参考，不作为依赖建议。

## 7. 来源

### Paper / Bukkit 官方

- `PlayerInteractEvent` Javadoc：https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerInteractEvent.html
- `Action` Javadoc：https://jd.papermc.io/paper/1.21.11/org/bukkit/event/block/Action.html
- `Event.Result` Javadoc：https://jd.papermc.io/paper/1.21.11/org/bukkit/event/Event.Result.html
- `PlayerInteractEntityEvent` Javadoc：https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerInteractEntityEvent.html
- `PlayerInteractAtEntityEvent` Javadoc：https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerInteractAtEntityEvent.html
- `PlayerArmSwingEvent` Javadoc：https://jd.papermc.io/paper/1.21.11/io/papermc/paper/event/player/PlayerArmSwingEvent.html
- `PrePlayerAttackEntityEvent` Javadoc：https://jd.papermc.io/paper/1.21.11/io/papermc/paper/event/player/PrePlayerAttackEntityEvent.html
- `ItemStack` Javadoc：https://jd.papermc.io/paper/1.21.11/org/bukkit/inventory/ItemStack.html
- `ItemMeta` Javadoc：https://jd.papermc.io/paper/1.21.11/org/bukkit/inventory/meta/ItemMeta.html
- `HumanEntity` Javadoc：https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/HumanEntity.html
- Persistent Data Container 文档：https://docs.papermc.io/paper/dev/pdc/
- 事件监听器文档：https://docs.papermc.io/paper/dev/event-listeners/
- Folia 支持文档：https://docs.papermc.io/paper/dev/folia-support
- Paper 1.21.11 服务端补丁：https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/patches/sources/net/minecraft/server/network/ServerGamePacketListenerImpl.java.patch
- Paper 1.21.11 服务端补丁：https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/patches/sources/net/minecraft/server/level/ServerPlayerGameMode.java.patch
- Paper 1.21.11 `CraftEventFactory`：https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/src/main/java/org/bukkit/craftbukkit/event/CraftEventFactory.java

### 库与参考实现

- ItemActionsLib：https://github.com/katsumag/ItemActionsLib
- PacketEvents：https://github.com/retrooper/packetevents
- ProtocolLib：https://github.com/dmulloy2/ProtocolLib
- Item-NBT-API：https://github.com/tr7zw/Item-NBT-API
- CommandAPI：https://github.com/CommandAPI/CommandAPI
- Adventure：https://github.com/PaperMC/adventure
- Nova：https://github.com/xenondevs/Nova
- ItemJoin：https://github.com/RockinChaos/ItemJoin
- CustomItemAPI：https://github.com/firewolf8385/CustomItemAPI
- Slimefun4：https://github.com/Slimefun/Slimefun4
- Oraxen：https://github.com/oraxen/oraxen
- ItemsAdder：https://www.itemsadder.com/
- ExecutableItems：https://modrinth.com/plugin/executableitems
- MythicMobs：https://mythiccraft.io/
- Denizen：https://github.com/DenizenScript/Denizen
- Skript：https://github.com/SkriptLang/Skript
