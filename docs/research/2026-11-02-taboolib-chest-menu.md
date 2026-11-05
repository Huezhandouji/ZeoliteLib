# TabooLib 箱子菜单（Chest Menu / UI）实现调研

- 调研日期：2026-11-02
- 源码基线：`TabooLib/TabooLib`，默认分支 `dev/6.3.0`，commit `967417225a7ab8be38cf634beca2b5794383497c`（2026-10-26）
- 文档基线：官方文档站 TabooWiki；文档源仓库 `FxRayHughes/taboowiki`，commit `4ab5015f6db5469a74704a0d299d3f22ad67f655`（2026-04-22）
- 说明：本报告只使用官方文档站与 GitHub 源码。未运行 Gradle，未做服务器运行时测试；标注“源码可核实”的内容来自静态阅读，标注“需实测”的内容没有实际运行验证。

## 1. 资料定位与结论

### 1.1 官方文档站

`https://taboolib.maplex.top/` 是一个 Docusaurus 站点（TabooWiki）。定位过程与结果：

- `https://taboolib.maplex.top/llms.txt`：HTTP 404，不存在。
- `https://taboolib.maplex.top/sitemap.xml`：可用；其中 `<loc>` 使用 canonical 域名 `https://taboolib.org/...`。
- 直接给页面 URL 加 `/index.md` 或 `/index.mdx`：HTTP 404；正文通过文档源仓库读取。
- 官网首页 footer 的“文档仓库”指向 `https://github.com/FxRayHughes/taboowiki`，本报告使用其 commit `4ab5015...` 作为文档源。

目标页面：

| 页面 | URL | 页面标题 |
|---|---|---|
| 箱子菜单 | `https://taboolib.maplex.top/docs/basic-tech/chest-menu/`（canonical：`https://taboolib.org/docs/basic-tech/chest-menu/`） | `箱子菜单 \| TabooWiki` |
| 物品构建与操作 | `https://taboolib.maplex.top/docs/basic-tech/item-builder/`（canonical：`https://taboolib.org/docs/basic-tech/item-builder/`） | `物品构建与操作 \| TabooWiki` |
| Arim 菜单快速构建工具 | `https://taboolib.maplex.top/docs/expanding-technology/arim/menu-helper/`（canonical：`https://taboolib.org/docs/expanding-technology/arim/menu-helper/`） | `菜单快速构建工具 \| TabooWiki` |

文档源文件：

- [chest-menu/index.mdx](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx)
- [item-builder/index.mdx](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/item-builder/index.mdx)
- [arim/menu-helper/index.md](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/expanding-technology/arim/menu-helper/index.md)

### 1.2 源码基线

浅克隆到 `C:\Users\Trigg\AppData\Local\Temp\taboolib-research\TabooLib`：

```text
branch: dev/6.3.0
commit: 967417225a7ab8be38cf634beca2b5794383497c
date:   2026-10-26T19:58:24+08:00
```

`git ls-remote --symref https://github.com/TabooLib/TabooLib.git HEAD` 也确认默认分支为 `refs/heads/dev/6.3.0`。

菜单模块位于：

```text
module/bukkit/bukkit-ui/
```

主包为 `taboolib.module.ui`，另有版本子模块：

- `module/bukkit/bukkit-ui/bukkit-ui-12100`（1.21 相关实现）
- `module/bukkit/bukkit-ui/bukkit-ui-legacy`（旧版本实现）

官方文档给出的模块安装名是 `BukkitUI`：

```kotlin
taboolib {
    subproject = true
    install(BukkitUI)
}
```

来源：[文档 L9-L18](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L9-L18)。

### 1.3 类名核实

在 commit `9674172` 的全仓搜索中，**没有发现**以下命名：

- `MenuParser`
- `MenuSession`
- `MenuListener`
- `MenuType`
- `MenuInventoryHolder`
- `ChestMenu`

实际对应关系是：

| 任务中提到的名字 | 实际实现 |
|---|---|
| `Menu` / `MenuBuilder` | `Menu` 接口与 `MenuBuilder.kt` 中的 `buildMenu` / `openMenu` 顶层函数 |
| `MenuParser` | 没有独立解析器；布局解析在 `ChestImpl.map()` 与 `getSlot` / `getSlots` 中 |
| `MenuListener` | `ClickListener`（`internal object`） |
| `MenuSession` | 没有 session 类；普通菜单状态在菜单实例本身，虚拟菜单在 `InventoryHandler.playerRemoteInventoryMap` |
| `MenuType` | 没有 `MenuType` 枚举；类型由 `Chest` / `PageableChest` / `StorableChest` / `Anvil` / `Hopper` 接口及 `InventoryType` 表达 |
| `MenuInventoryHolder` | `MenuHolder` |
| `ChestMenu` | `Chest` / `ChestImpl` |

### 1.4 核心结论

TabooLib 的箱子菜单不是“每个玩家一个 Session 对象”的模型，而是：

1. **菜单对象本身就是 builder 与状态容器**。`openMenu<Chest> { ... }` 每次通过反射创建新的菜单实例，DSL 直接修改该实例；`build()` 生成 Bukkit `Inventory`。
2. **用自定义 `InventoryHolder` 标识菜单**。`MenuHolder` 持有菜单对象；事件层通过 `inventory.holder as? MenuHolder` 取回菜单，而不是靠标题、材质或槽位内容判断。
3. **事件路由集中在一个监听器**。`ClickListener` 监听 `InventoryOpenEvent`、`InventoryClickEvent`、`InventoryDragEvent`、`InventoryCloseEvent` 以及手部锁定相关事件；点击被转换为 `ClickEvent`，再按槽位字符/原始槽位分发给回调。
4. **虚拟化菜单是另一条链路**。`VirtualInventory` 包装真实 Bukkit Inventory，但通过 NMS 数据包模拟容器；点击来自 `PacketReceiveEvent`，再合成 `VirtualInventoryInteractEvent` 和 `ClickEvent(VIRTUAL)`，不经过 Bukkit 的 `InventoryClickEvent`。
5. **五种内置类型**：`Chest`、`PageableChest<T>`、`StorableChest`、`Anvil`、`Hopper`。其中 `PageableChest`、`StorableChest`、`Anvil`、`Hopper` 在源码中都继承 `Chest`。

## 2. 模块与关键文件

| 职责 | 文件 |
|---|---|
| 菜单顶层接口、实现注册表 | `module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/Menu.kt` |
| `buildMenu` / `openMenu` / 原始标题 | `.../ui/MenuBuilder.kt`、`.../ui/MenuBuilderRaw.kt` |
| 自定义 Holder | `.../ui/MenuHolder.kt` |
| 事件监听与路由 | `.../ui/ClickListener.kt` |
| 点击事件封装 | `.../ui/ClickEvent.kt`、`.../ui/ClickType.kt` |
| 标准箱子接口/实现 | `.../ui/type/Chest.kt`、`.../ui/type/impl/ChestImpl.kt` |
| 翻页箱子 | `.../ui/type/PageableChest.kt`、`.../ui/type/impl/PageableChestImpl.kt` |
| 可储存箱子 | `.../ui/type/StorableChest.kt`、`.../ui/type/impl/StorableChestImpl.kt` |
| 铁砧/漏斗 | `.../ui/type/impl/AnvilImpl.kt`、`.../ui/type/impl/HopperImpl.kt` |
| 点击扩展（返还/条件/限量/锁定） | `.../ui/MenuExtensions.kt` |
| 虚拟 Inventory | `.../ui/virtual/VirtualInventory.kt`、`VirtualInventoryFactory.kt` |
| 虚拟容器数据包处理 | `.../ui/virtual/InventoryHandler.kt`、`InventoryHandlerImpl.kt` |
| Paper 1.21 InventoryView 兼容 | `.../ui/InventoryViewProxy.kt`、`bukkit-ui-12100/.../VirtualInventoryViewModern.kt` |
| 物品构建器 | `module/bukkit/bukkit-util/src/main/kotlin/taboolib/platform/util/ItemBuilder.kt` |
| 自定义堆叠上限 | `module/bukkit/bukkit-util/src/main/java/taboolib/module/ui/ItemStacker.java` |

源码 permalink 前缀：

```text
https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/
```

## 3. 对外 API 与 Kotlin DSL

### 3.1 `Menu` 接口

`Menu` 只规定标题和构建方法：

```kotlin
interface Menu {
    var title: String
    fun build(): Inventory
}
```

同时用 `ConcurrentHashMap<Class<*>, Class<*>>` 维护接口到实现的映射：

```kotlin
impl[Anvil::class.java] = AnvilImpl::class.java
impl[Chest::class.java] = ChestImpl::class.java
impl[Hopper::class.java] = HopperImpl::class.java
impl[PageableChest::class.java] = PageableChestImpl::class.java
impl[StorableChest::class.java] = StorableChestImpl::class.java
```

并提供 `Menu.registerImplementation(clazz, implementation)` 扩展点。来源：[Menu.kt L9-L43](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/Menu.kt#L9-L43)。

### 3.2 `buildMenu` 与 `openMenu`

核心入口是 `MenuBuilder.kt`：

```kotlin
inline fun <reified T : Menu> buildMenu(title: String = "chest", builder: T.() -> Unit): Inventory {
    val type = if (T::class.java.isInterface) Menu.getImplementation(T::class.java) else T::class.java
    val instance = type.getDeclaredConstructor(String::class.java).newInstance(title) as T
    return instance.also(builder).build()
}

inline fun <reified T : Menu> HumanEntity.openMenu(title: String = "chest", builder: T.() -> Unit) {
    try {
        openMenu(buildMenu(title, builder))
    } catch (ex: Throwable) {
        ex.printStackTrace()
    }
}
```

`openMenu(Inventory, changeId)` 判断传入对象：

- 如果是 `VirtualInventory`，走 `openVirtualInventory`，再通过 `RemoteInventory.inject` 注入菜单回调。
- 否则直接调用 Bukkit 的 `openInventory`。

来源：[MenuBuilder.kt L75-L112](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/MenuBuilder.kt#L75-L112)。

这意味着：

- `openMenu<T> {}` 每次调用都会创建**新的菜单实例**，不是复用全局菜单。
- `buildMenu` 要求实现类有 `(String)` 构造器；这是 `registerImplementation` 的隐式约束。
- `openMenu` 捕获所有 `Throwable` 并打印，不会把异常抛回调用方；调试时需要看控制台。
- `openMenu(Inventory)` 也接受已经构建好的 Inventory，可用于自行管理生命周期。

### 3.3 最小用法

官方文档的快速开始：

```kotlin
player.openMenu<Chest>("我的第一个菜单") {
    rows(3)
    set(13, buildItem(XMaterial.DIAMOND) {
        name = "§b点击我！"
        lore += "§7这是一个示例物品"
    }) {
        clicker.sendMessage("§a你点击了钻石！")
    }
}
```

来源：[文档 L118-L149](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L118-L149)。

槽位索引从 0 开始，第一行 0-8，第二行 9-17，以此类推。

### 3.4 原始标题（Raw Title）

`MenuBuilderRaw.kt` 提供接受 `Source` 的重载：

```kotlin
inline fun <reified T : Menu> HumanEntity.openMenu(title: Source, builder: T.() -> Unit) {
    openMenu(title.toRawMessage(), builder)
}
```

来源：[MenuBuilderRaw.kt L7-L12](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/MenuBuilderRaw.kt#L7-L12)。

普通 Bukkit Inventory 要使用 JSON/raw title，需要主动调用：

```kotlin
enableRawTitleInVanillaInventory()
```

它会注册 `PacketSendEvent` 监听，处理 `PacketPlayOutOpenWindow` / `ClientboundOpenScreenPacket`，把 `{...}` 形式的标题转成聊天组件。源码注释说明这是**可选**的，因为一旦启用就会注入玩家 Channel，带来额外开销；虚拟菜单不需要该选项。来源：[MenuBuilder.kt L28-L59](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/MenuBuilder.kt#L28-L59)。

## 4. 菜单定义：标题、行数、布局、物品、点击

### 4.1 `Chest` 接口的状态

`Chest` 在 `Menu` 上增加了：

- `rows: Int`：行数，文档约定 1-6。
- `virtualized: Boolean`、`virtualizedStorageContents: List<ItemStack>?`：虚拟化状态与虚拟背包内容。
- `items: ConcurrentHashMap<Char, ItemStack>`：字符到静态物品的映射。
- `slots: CopyOnWriteArrayList<List<Char>>`：字符布局，每个 `List<Char>` 是一行。
- `handLocked: Boolean`：是否锁定主手操作，默认 `true`。
- `isOpened: Boolean`：是否已经打开过。

来源：[Chest.kt L18-L49](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/Chest.kt#L18-L49)、[ChestImpl.kt L22-L82](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/ChestImpl.kt#L22-L82)。

### 4.2 字符布局 `map()`

```kotlin
map(
    "#########",
    "#  A    #",
    "#########"
)
```

实现非常直接：

```kotlin
override fun map(vararg slots: String) {
    this.slots.clear()
    this.slots.addAll(slots.map { it.toCharArray().toList() })
    if (rows < slots.size) {
        rows = slots.size
    }
}
```

来源：[ChestImpl.kt L250-L257](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/ChestImpl.kt#L250-L257)。

关键点：

- `map()` 只负责布局；每个字符对应什么物品由 `set(Char, ...)` 决定。
- 同一字符可以出现多次，`getSlots(char)` 返回全部槽位。
- `map()` 会把 `rows` 提升到至少行数，但不会把过大的 `rows` 降下来。
- `ChestImpl.build()` 和 `getSlot/getSlots` 都只读取每行前 9 个字符；`HopperImpl` 只读前 5 个，`AnvilImpl` 只读前 3 个。
- 源码没有校验每行长度；短行会留下未覆盖的槽位，长行会被截断。

字符到槽位的查询：

- `getSlot(slot: Int): Char`：槽位对应的字符，不存在返回空格 `' '`。
- `getSlots(slot: Char): List<Int>`：字符对应的全部槽位。
- `getFirstSlot(slot: Char): Int`：第一个槽位，不存在返回 `-1`。

来源：[ChestImpl.kt L326-L368](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/ChestImpl.kt#L326-L368)。

### 4.3 `set()` 的多种重载

`Chest` 接口提供：

- `set(Char, ItemStack)`：静态物品，写入 `items` 映射，构建时填充。
- `set(Int, ItemStack)`：通过 `onBuild` 在打开时填充指定槽位。
- `set(Char, callback: () -> ItemStack)`：动态物品，每次构建时按字符对应的所有槽位生成。
- `set(Int, callback: () -> ItemStack)`：动态物品，每次构建时生成指定槽位。
- `set(Char/Int, XMaterial, ItemBuilder.() -> Unit)`：用 `buildItem` 构建。
- `set(Char/Int, ItemStack, onClick)`：设置物品并同时注册点击回调。

来源：[Chest.kt L120-L158](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/Chest.kt#L120-L158)、[ChestImpl.kt L262-L321](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/ChestImpl.kt#L262-L321)。

实现差异很重要：

- `set(Char, ItemStack)` 直接写入 `items`，如果菜单已经打开会抛错。
- `set(Int, ItemStack)` 不是写入 `items`，而是注册 `onBuild { inventory.setItem(...) }`。
- 动态 `set(Char) { ... }` 也是 `onBuild`，所以每次打开/重建都会重新执行。
- `build()` 先按 `slots` 填充静态物品；动态 `onBuild` 在 `InventoryOpenEvent` 之后执行，因此会覆盖静态物品。

### 4.4 点击回调的三种绑定方式

```kotlin
onClick('A') { /* 自动取消，只处理非拖拽 */ }
onClick(13) { /* 自动取消，只处理非拖拽 */ }
onClick(lock = false) { /* 不自动取消 */ }
```

实现：

- `onClick(bind: Int)`：比较 `it.rawSlot == bind`，设置 `isCancelled = true`，且只在 `clickType != DRAG` 时执行用户回调。
- `onClick(bind: Char)`：比较 `it.slot == bind`，同样自动取消并跳过拖拽。
- `onClick(lock: Boolean)`：`lock = true` 时自动取消；`lock = false` 时不取消，交给用户决定。
- `set(item) { onClick }` 最终调用 `onClick(Char/Int)`，所以也自动取消。

来源：[ChestImpl.kt L170-L214](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/ChestImpl.kt#L170-L214)。

`ClickEvent` 的关键字段：

- `clicker: Player`
- `inventory: Inventory`
- `view: InventoryView`
- `clickType: ClickType`（`CLICK` / `DRAG` / `VIRTUAL`）
- `slot: Char`（拖拽固定为 `' '`）
- `rawSlot: Int`（单槽拖拽返回目标槽位，多槽拖拽返回 `-1`）
- `currentItem: ItemStack?`
- `cursorItem: ItemStack?`
- `isCancelled: Boolean`
- `clickEvent()` / `dragEvent()` / `virtualEvent()` 及各自的 `OrNull` 安全版本
- `onClick { }` / `onDrag { }` / `onVirtualClick { }` 安全访问器

来源：[ClickEvent.kt L20-L201](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickEvent.kt#L20-L201)。

### 4.5 `handLocked`

`ChestImpl.handLocked` 默认 `true`。`ClickListener` 在点击时处理：

- `COLLECT_TO_CURSOR` 与主手物品相似时取消。
- 数字键交换主手槽位时取消。
- `PlayerDropItemEvent`、`PlayerItemHeldEvent`、`PlayerSwapHandItemsEvent` 在菜单打开且 `handLocked` 时取消。

来源：[ClickListener.kt L56-L69](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickListener.kt#L56-L69)、[ClickListener.kt L132-L155](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickListener.kt#L132-L155)。

`handLocked` 不是“取消所有点击”；它只锁定与主手/丢弃/切换相关的手部动作。普通槽位点击仍由 `onClick` 回调决定是否取消。

## 5. Inventory 创建与 Holder

### 5.1 `MenuHolder`

```kotlin
open class MenuHolder(val menu: Chest) : InventoryHolder {
    private val inventory = Bukkit.createInventory(
        this,
        if (menu.rows > 0) menu.rows * 9 else menu.slots.size * 9,
        menu.title
    )

    override fun getInventory(): Inventory = inventory

    companion object {
        fun fromInventory(inventory: Inventory): Chest? {
            return (inventory.holder as? MenuHolder)?.menu
        }
    }
}
```

来源：[MenuHolder.kt L13-L26](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/MenuHolder.kt#L13-L26)。

`ChestImpl.build()` 用这个 holder 创建实际 Inventory：

```kotlin
lastInventory = Bukkit.createInventory(
    holderCallback(this),
    if (rows > 0) rows * 9 else slots.size * 9,
    createTitle()
)
```

来源：[ChestImpl.kt L402-L404](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/ChestImpl.kt#L402-L404)。

### 5.2 如何避免普通箱子被误判

事件层不检查标题、材质或内容，只做：

```kotlin
val builder = MenuHolder.fromInventory(e.inventory) as? ChestImpl ?: return
```

`fromInventory` 判断 `inventory.holder is MenuHolder`。普通方块箱子、桶、漏斗的 holder 是对应的 BlockState，不是 `MenuHolder`，因此不会被当成菜单。这个设计把“菜单身份”绑定在 holder 类型上，避免了标题碰撞和物品内容误判。

### 5.3 Holder 可定制，但有一个源码细节

`Chest` 提供：

```kotlin
fun holder(func: (menu: Chest) -> MenuHolder)
```

允许返回自定义 `MenuHolder` 子类。

但需要注意：`MenuHolder` 构造器自己会 `Bukkit.createInventory(this, ...)` 创建一个 Inventory；`ChestImpl.build()` 又用同一个 holder 调 `Bukkit.createInventory(holder, ...)` 创建实际打开的 `lastInventory`。源码里存在**两个 Inventory 对象**：

- `MenuHolder.inventory`：构造器创建，`getInventory()` 返回它。
- `ChestImpl.lastInventory`：实际打开、玩家看到的界面。

框架内部始终通过 `MenuHolder.menu` 路由，不依赖 `getInventory()`，所以点击功能正常；但如果外部代码调用 `holder.getInventory()`，拿到的可能不是玩家正在看的界面。这是静态阅读可核实的实现细节，使用自定义 holder 时值得注意。

### 5.4 虚拟化时的 Holder

`VirtualInventory` 实现 `Inventory`，其 `getHolder()` 委托给内部的 `bukkitInventory.holder`：

```kotlin
override fun getHolder(): InventoryHolder? {
    return bukkitInventory.holder
}
```

来源：[VirtualInventory.kt L218-L220](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/VirtualInventory.kt#L218-L220)。

因此虚拟菜单仍然能通过 `MenuHolder.fromInventory(virtualInventory)` 取回菜单。虚拟路径不会产生真实的 Bukkit `InventoryClickEvent`，点击由数据包监听器合成。

## 6. 事件层：监听与路由

`ClickListener` 是 `internal object`，标注 `@Inject`、`@PlatformSide(Platform.BUKKIT)`，使用 TabooLib 的 `@SubscribeEvent` 注册。来源：[ClickListener.kt L27-L29](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickListener.kt#L27-L29)。

### 6.1 监听的事件

| 事件 | 处理 |
|---|---|
| `InventoryOpenEvent` | 通过 holder 找到 `ChestImpl`；`submit` 执行 `buildCallback` / `finalBuildCallback`，`submitAsync` 执行异步版本 |
| `InventoryClickEvent` | 找到菜单；处理 `handLocked`；构造 `ClickEvent(CLICK)`；执行 `clickCallback` 和 `selfClickCallback`；未取消时处理丢弃逻辑 |
| `InventoryDragEvent` | 找到菜单；构造 `ClickEvent(DRAG, slot = ' ')`；执行点击回调 |
| `InventoryCloseEvent` | 找到菜单；跳过 `updateTitle` 导致的关闭；执行 `closeCallback`；按 `onceCloseCallback` 清除 |
| `PlayerDropItemEvent` | 菜单打开且 `handLocked` 时取消；带 `internal-drop` 元数据的内部丢弃放行 |
| `PlayerItemHeldEvent` | 菜单打开且 `handLocked` 时取消 |
| `PlayerSwapHandItemsEvent` | `@Ghost` 事件；菜单打开且 `handLocked` 时取消 |
| `LifeCycle.DISABLE` | 遍历在线玩家，关闭顶层为菜单的 Inventory |
| `PlayerQuitEvent`（虚拟模块） | 清理 `playerContainerCounterMap` 和 `playerRemoteInventoryMap` |
| `PacketReceiveEvent`（虚拟模块） | 处理虚拟容器的关闭、点击、重命名 |

来源：[ClickListener.kt L31-L155](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickListener.kt#L31-L155)、[InventoryHandler.kt L44-L130](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandler.kt#L44-L130)。

### 6.2 点击路由

普通点击的核心流程：

```kotlin
val builder = MenuHolder.fromInventory(e.inventory) as? ChestImpl ?: return
val event = ClickEvent(e, ClickType.CLICK, builder.getSlot(e.rawSlot), builder)
builder.clickCallback.forEach { it(event) }
builder.selfClickCallback(event)
```

来源：[ClickListener.kt L70-L77](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickListener.kt#L70-L77)。

路由分两层：

1. **用户注册的回调列表**：`clickCallback` 是 `CopyOnWriteArrayList`，按注册顺序执行。`onClick(Char)`、`onClick(Int)`、`onClick(lock)` 都向其中追加回调。
2. **内部点击回调**：`selfClickCallback` 是单个函数，供 `PageableChestImpl` 和 `StorableChestImpl` 做元素路由/存储规则处理。`PageableChestImpl` 在每次 `build()` 时用 `selfClick` 覆盖它，通过 `elementMap[rawSlot]` 找到被点击元素。

`ClickEvent.slot` 是 `builder.getSlot(rawSlot)` 的结果：

- 上方菜单槽位：返回 `map()` 中对应字符。
- 玩家背包槽位：返回 `' '`（因为 `getSlot` 只覆盖布局范围）。
- 拖拽：固定 `' '`。

因此 `onClick(Char)` 不会响应玩家背包槽位，`onClick(Int)` 则可以通过 `rawSlot` 匹配包括背包在内的槽位。

### 6.3 拖拽

普通拖拽：

```kotlin
val clickEvent = ClickEvent(e, ClickType.DRAG, ' ', menu)
menu.clickCallback.forEach { it.invoke(clickEvent) }
menu.selfClickCallback(clickEvent)
```

来源：[ClickListener.kt L110-L116](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickListener.kt#L110-L116)。

- `ClickEvent.rawSlot` 在拖拽时：单槽拖拽返回该槽位，多槽拖拽返回 `-1`。
- `cursorItem` 对 `DRAG` 可读可写。
- `currentItem` 对 `DRAG` 返回 `null`。
- `onClick(Char/Int)` 会跳过 `DRAG`，所以字符/槽位绑定不会处理拖拽；需要全局 `onClick(lock = false)` 或 `onDrag {}`。
- 默认不取消拖拽；要拦截必须自己设置 `isCancelled = true`。

### 6.4 关闭与 `once`

```kotlin
fun onClose(e: InventoryCloseEvent) {
    val menu = MenuHolder.fromInventory(e.inventory) as? ChestImpl ?: return
    if (menu.isUpdateTitle && menu.isSkipCloseCallbackOnUpdateTitle) {
        return
    }
    menu.closeCallback.invoke(e)
    if (menu.onceCloseCallback) {
        menu.closeCallback = {}
    }
}
```

来源：[ClickListener.kt L118-L130](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickListener.kt#L118-L130)。

- `onClose(once = true, skipUpdateTitle = true)` 是接口默认值。
- `ChestImpl` 内部 `closeCallback` 的初始值是 `{ isOpened = false }`。
- 一旦用户调用 `onClose`，`closeCallback` 会被**替换**，默认的 `isOpened = false` 不再执行；这是源码可核实的细节。
- `skipUpdateTitle` 用于让 `updateTitle()` 触发的重新打开不触发关闭回调。

### 6.5 丢弃逻辑

如果点击没有被取消，且是 `DROP` 或光标在界面外丢弃，`ClickListener` 会：

1. 通过 `VectorUtil.itemDrop` 生成掉落物。
2. 设置 `pickupDelay = 20`。
3. 给掉落物写入 `internal-drop` 元数据。
4. 手动触发 `PlayerDropItemEvent`。
5. 如果事件被取消，移除掉落物；否则清空原槽位/光标。

来源：[ClickListener.kt L82-L107](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickListener.kt#L82-L107)。

`PlayerDropItemEvent` 监听器会忽略带 `internal-drop` 的掉落物，避免自己再次取消。

## 7. 会话与生命周期

### 7.1 没有 `MenuSession`

在当前 commit 中不存在 `MenuSession`。菜单状态分布如下：

- **普通菜单**：状态在 `ChestImpl` 实例本身（`items`、`slots`、`lastInventory`、`clickCallback`、`closeCallback`、`isOpened` 等）。`openMenu` 每次创建新实例，因此不同玩家通常对应不同菜单实例和不同 Inventory。
- **虚拟菜单**：额外的玩家级状态在 `InventoryHandler` 的静态 Map：
  - `playerContainerCounterMap: ConcurrentHashMap<String, Int>`
  - `playerRemoteInventoryMap: ConcurrentHashMap<String, RemoteInventory>`
- **翻页菜单**：`PageableChestImpl.viewer` 是单个 `Player`，记录当前页面属于谁；它不适合同一个菜单实例同时服务多个玩家。

来源：[ChestImpl.kt L22-L82](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/ChestImpl.kt#L22-L82)、[PageableChestImpl.kt L16-L49](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/PageableChestImpl.kt#L16-L49)、[InventoryHandler.kt L44-L61](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandler.kt#L44-L61)。

### 7.2 构建与打开顺序

1. `buildMenu` 反射创建实例。
2. 执行 DSL：`rows`、`map`、`set(Char, ItemStack)`、`onClick`、`onClose`、`virtualize` 等。
3. `build()`：创建 `lastInventory`，填充静态字符物品，虚拟化时包装为 `VirtualInventory`。
4. `openMenu`：打开真实 Inventory 或虚拟 Inventory。
5. `InventoryOpenEvent`：
   - 主线程 `submit`：`buildCallback`（用户 `onBuild` + 默认 `isOpened = true`），然后 `finalBuildCallback`。
   - 异步 `submitAsync`：`asyncBuildCallback`，然后 `asyncFinalBuildCallback`。
6. 玩家点击：`ClickListener` 路由到 `clickCallback` / `selfClickCallback`。
7. 关闭：`closeCallback`；虚拟菜单由数据包关闭或服务端关闭触发。

来源：[ClickListener.kt L40-L54](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickListener.kt#L40-L54)。

注意：静态物品在 `build()` 阶段填充；动态 `set(Int/Char) { ... }` 和 `onBuild` 在 `InventoryOpenEvent` 之后执行，因此打开瞬间可能出现静态物品先显示、动态物品后覆盖的过程。

### 7.3 重复打开、关闭后重开

- `openMenu` 每次创建新实例；重复打开会打开新的 Inventory，旧 Inventory 按 Bukkit 语义关闭并触发旧菜单的关闭回调。
- `updateTitle(title)` 会：
  1. 修改 `title`；
  2. 设置 `isUpdateTitle = true`；
  3. 取 `lastInventory.viewers`；
  4. 重新 `build()`；
  5. 对每个 viewer 调用 `openMenu(lastInventory, changeId = false)`；
  6. 复位 `isUpdateTitle`。
- `changeId = false` 只对虚拟菜单有意义：复用容器 ID，避免客户端感知为新窗口。
- `updateTitle` 会重新执行 `onBuild`；文档也明确说明这一点。

来源：[ChestImpl.kt L375-L390](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/ChestImpl.kt#L375-L390)、[文档 L646-L682](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L646-L682)。

### 7.4 翻页刷新

`PageableChestImpl` 的上一页/下一页不是修改现有 Inventory 的内容，而是：

```kotlin
fun refresh() {
    if (virtualized) {
        viewer.openVirtualInventory(build() as VirtualInventory).inject(this)
    } else {
        viewer.openInventory(build())
    }
    pageChangeCallback(viewer)
}
```

来源：[PageableChestImpl.kt L120-L147](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/PageableChestImpl.kt#L120-L147)。

因此翻页是“重新构建并重新打开”，不是原地替换。普通菜单下这会走 Bukkit 的 `openInventory`，旧 Inventory 可能触发一次 `InventoryCloseEvent`；源码没有为翻页设置 `isUpdateTitle` 来抑制关闭回调。这个行为需要在实际服务器上验证。

### 7.5 清理

- 普通菜单：关闭回调负责清理；`LifeCycle.DISABLE` 时 `ClickListener` 会关闭在线玩家当前打开的菜单。
- 虚拟菜单：`PlayerQuitEvent` 清理两个 Map；`LifeCycle.DISABLE` 关闭所有 `RemoteInventory`。
- `RemoteInventory.close()` 会发送关闭包、执行 `onCloseCallback`，并手动触发 `InventoryCloseEvent`；如果不在主线程，通过 `submit` 切回主线程。

来源：[InventoryHandler.kt L57-L70](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandler.kt#L57-L70)、[InventoryHandlerImpl.kt L281-L314](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandlerImpl.kt#L281-L314)。

### 7.6 定时刷新与动画

菜单模块没有内置定时器、动画或自动刷新 API。官方 FAQ 的建议是用 `onBuild` + `submit(period = 20L)`，并在任务中检查 `inventory.viewers.isEmpty()` 后取消：

```kotlin
onBuild { player, inventory ->
    submit(period = 20L) {
        if (inventory.viewers.isEmpty()) {
            cancel()
            return@submit
        }
        inventory.setItem(slot, buildItem(XMaterial.CLOCK) { name = "..." })
    }
}
```

来源：[文档 L2399-L2437](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L2399-L2437)。

没有找到“滚动列表”或“动画”相关 API；翻页是唯一的列表分页机制。

## 8. 物品渲染

### 8.1 菜单层不构建物品

菜单层只接收 `ItemStack`，或者接收 `XMaterial + ItemBuilder.() -> Unit` 并调用 `buildItem`。物品构建由 `module/bukkit/bukkit-util` 的 `ItemBuilder` 完成。

`ItemBuilder` 支持的主要字段：

- `material`、`amount`、`damage`
- `name`、`itemName`（1.20.5+）
- `lore`、`flags`、`enchants`
- `color`、`potions`、`potionData`、`patterns`
- `skullOwner`、`skullTexture`
- `isUnbreakable`、`customModelData`
- `tooltipStyle`（1.21.2+）、`itemModel`（1.21.4+）、`isHideTooltip`（1.20.5+）
- `attributeModifiers`
- `unique`
- `originMeta`、`finishing`

来源：[ItemBuilder.kt L64-L200](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-util/src/main/kotlin/taboolib/platform/util/ItemBuilder.kt#L64-L200)、[ItemBuilder.kt L265-L389](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-util/src/main/kotlin/taboolib/platform/util/ItemBuilder.kt#L265-L389)。

### 8.2 头颅

- `skullOwner`：设置 `SkullMeta.owner`。
- `skullTexture`：依赖 `bukkit-xseries-skull` 模块，通过 `BukkitSkull.applySkull` 写入材质。

源码：

```kotlin
is SkullMeta -> {
    if (skullOwner != null) itemMeta.owner = skullOwner
}
...
if (itemMeta is SkullMeta && skullTexture != null) {
    itemStack = BukkitSkull.applySkull(itemStack, skullTexture!!.texture)
}
```

来源：[ItemBuilder.kt L307-L311](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-util/src/main/kotlin/taboolib/platform/util/ItemBuilder.kt#L307-L311)、[ItemBuilder.kt L382-L386](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-util/src/main/kotlin/taboolib/platform/util/ItemBuilder.kt#L382-L386)。

官方物品文档同样给出 `skullOwner` 与 `SkullTexture` 示例：[文档 L246-L270](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/item-builder/index.mdx#L246-L270)。

### 8.3 NBT / PDC

- 菜单模块（`bukkit-ui`）与 `ItemBuilder` 中没有 `PersistentDataContainer` 专用封装。
- TabooLib 的 NBT 能力在独立模块 `module/bukkit-nms/bukkit-nms-tag`，提供 `ItemTag` / `ItemTagReader` 等类；官方文档见 [物品构建与操作 L707-L740](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/item-builder/index.mdx#L707-L740)。
- 如果需要在菜单物品上写 PDC，可以通过 `ItemBuilder.finishing { item -> ... }` 或 `originMeta` 直接操作 `ItemMeta`/`ItemStack`；菜单模块不会拦截或重写这些数据。

### 8.4 占位符

核心菜单没有通用的 PlaceholderAPI 集成，也没有占位符解析器。可核实的占位符只有：

- `PageableChestImpl.createTitle()` 中的 `%p`：替换为当前页码（从 1 开始）。

```kotlin
override fun createTitle(): String {
    return title.replace("%p", (page + 1).toString())
}
```

来源：[PageableChestImpl.kt L206-L208](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/PageableChestImpl.kt#L206-L208)。

动态内容通过 Kotlin 回调完成：

- `set(Char/Int) { ItemStack }`：每次构建时生成。
- `PageableChest.onGenerate { player, element, index, slot -> ItemStack }`：每个元素生成物品。
- `onGenerate(async = true)`：在异步回调中生成。

### 8.5 声明式配置扩展（Arim）

官方文档另有一个“菜单快速构建工具”页面，属于 Arim 扩展（`top.maplex.arim.tools.menuhelper.UIConfigHelper`），不是 `TabooLib/TabooLib` 核心仓库的 `bukkit-ui` 类。它提供：

- YAML 的 `layout`、`button`、图标配置；
- `<key>` 形式的变量替换；
- `XItemStack` 图标配置；
- Kether 脚本作为 `action`；
- `initMenu()`、`buildIcon()`、`buildIconAction()`、`putPagination()`、`updateIcon()` 等。

用法示意：

```kotlin
player.openMenu<Chest>("标题") {
    initMenu()
}
```

来源：[Arim 文档 L5-L36](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/expanding-technology/arim/menu-helper/index.md#L5-L36)、[Arim 文档 L210-L262](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/expanding-technology/arim/menu-helper/index.md#L210-L262)。

Arim 的实现源码不在 `TabooLib/TabooLib` 仓库内，本报告没有核实其内部实现；它是在核心 `Chest`/`PageableChest` API 之上的声明式封装。

## 9. 分页、滚动与不同 InventoryType

### 9.1 `PageableChest<T>`

接口：

```kotlin
interface PageableChest<T> : Chest {
    val page: Int
    fun menuLocked(lockAll: Boolean)
    fun page(page: Int)
    fun slots(slots: List<Int>)
    fun slotsBy(char: Char)
    fun elements(elements: () -> List<T>)
    fun onGenerate(async: Boolean = false, callback: (player, element, index, slot) -> ItemStack)
    fun onClick(callback: (event: ClickEvent, element: T) -> Unit)
    fun setNextPage(slot: Int, roll: Boolean = false, callback: ...)
    fun setPreviousPage(slot: Int, roll: Boolean = false, callback: ...)
    fun onPageChange(callback: (player: Player) -> Unit)
    fun hasPreviousPage(): Boolean
    fun hasNextPage(): Boolean
    fun resetElementsCache()
}
```

来源：[PageableChest.kt L11-L93](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/PageableChest.kt#L11-L93)。

构建逻辑：

```kotlin
elementsCache = elementsCallback()
val elementMap = hashMapOf<Int, T>()
val elementItems = subList(
    elementsCache,
    page * menuSlots.size,
    (page + 1) * menuSlots.size
)
maxPage = elementsCache.size / menuSlots.size
```

来源：[PageableChestImpl.kt L217-L226](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/PageableChestImpl.kt#L217-L226)。

- `slotsBy('@')` 把 `map()` 中 `@` 的槽位设为可用槽位。
- 当前页元素按 `menuSlots` 顺序填充。
- `selfClick` 通过 `elementMap[rawSlot]` 找到元素并调用 `onClick { event, element -> }`。
- `menuLocked = true`（默认）会取消所有点击；`false` 时不取消，玩家可能把物品拿走。
- `setNextPage` / `setPreviousPage` 通过 `set(slot) { ... }` 生成按钮物品，通过 `onClick(slot)` 切换页码并重新打开。
- `%p` 在标题中替换页码。

### 9.2 分页的边界

源码可核实的几个边界：

1. `maxPage = elementsCache.size / menuSlots.size` 是整数除法。当元素数不是槽位数的整数倍时，`maxPage` 偏小；`setPreviousPage` 的 `roll` 分支使用 `maxPage - 1`，从第 0 页“循环到最后一页”可能落到错误的页（例如 10 个元素、9 个槽位，`maxPage = 1`，roll 后仍是第 0 页）。
2. `menuSlots` 为空时，`elementsCache.size / menuSlots.size` 会除零；使用 `PageableChest` 必须设置 `slots` 或 `slotsBy`。
3. `hasNextPage()` 使用浮点除法：`size / entry.toDouble() > page + 1`；与 `maxPage` 的整数除法不一致。
4. `elementsCache` 在每次 `build()` 时都会重新执行 `elementsCallback()`。官方 FAQ 说“翻页时不会重新获取元素列表”，但当前源码并非如此；文档自身在 L912-L915 与 L2374-L2380 也存在矛盾。按源码，翻页 `refresh()` 会再次调用 `elements` 回调。

来源：[PageableChestImpl.kt L195-L265](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/PageableChestImpl.kt#L195-L265)、[文档 L912-L915](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L912-L915)、[文档 L2374-L2380](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L2374-L2380)。

### 9.3 不同容器类型

| 类型 | 接口/实现 | InventoryType | 槽位 | 说明 |
|---|---|---|---|---|
| `Chest` | `Chest` / `ChestImpl` | 默认 CHEST | `rows * 9`，1-6 行 | 标准箱子，字符布局 |
| `PageableChest<T>` | `PageableChest` / `PageableChestImpl` | 默认 CHEST | 由 `menuSlots` 决定 | 分页列表 |
| `StorableChest` | `StorableChest` / `StorableChestImpl` | 默认 CHEST | 由布局决定 | 可放入/取出，带规则系统 |
| `Anvil` | `Anvil` / `AnvilImpl` | `InventoryType.ANVIL` | 固定 3 | 通过数据包监听重命名 |
| `Hopper` | `Hopper` / `HopperImpl` | `InventoryType.HOPPER` | 固定 5 | 小型快捷菜单 |

源码中 `Anvil` 和 `Hopper` 都继承 `Chest`，不是直接继承 `Menu`：

```kotlin
interface Anvil : Chest { ... }
interface Hopper : Chest
```

来源：[Anvil.kt L9-L12](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/Anvil.kt#L9-L12)、[Hopper.kt L6](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/Hopper.kt#L6)。

官方文档的类型层次图把 `Anvil`/`Hopper` 画成直接从 `Menu` 继承（[文档 L24-L49](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L24-L49)），与源码不一致；应以源码为准。

`AnvilImpl` 和 `HopperImpl` 直接重写 `build()`：

- `AnvilImpl`：`Bukkit.createInventory(holderCallback(this), InventoryType.ANVIL, title)`，只填充 `slots[0]` 的前 3 个字符。
- `HopperImpl`：`Bukkit.createInventory(holderCallback(this), InventoryType.HOPPER, title)`，只填充 `slots[0]` 的前 5 个字符。
- `HopperImpl` 直接访问 `slots[0]`，没有空判断；如果使用 `Hopper` 但没有调用 `map()`，会抛 `IndexOutOfBoundsException`。
- `AnvilImpl` 有 `if (slots.isNotEmpty())` 保护。

来源：[AnvilImpl.kt L20-L47](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/AnvilImpl.kt#L20-L47)、[HopperImpl.kt L10-L24](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/HopperImpl.kt#L10-L24)。

**没有内置工作台、熔炉、附魔台、发射器等菜单类。** 当前模块只提供上述五种。若需要其他 `InventoryType`，可以：

1. 实现自己的 `Menu` / `Chest` 子接口与实现类；
2. 重写 `build()`，用 `Bukkit.createInventory(holder, InventoryType.XXX, title)`；
3. 如果声明的是接口，通过 `Menu.registerImplementation` 注册；
4. 虚拟化时，`InventoryHandlerImpl` 使用 `CraftContainer.getNotchInventoryType(inventory.bukkitInventory)` 推导容器类型，理论上能处理更多类型，但核心库没有为它们提供现成封装。

### 9.4 铁砧重命名

`AnvilImpl` 实现 `AnvilCallback`：

```kotlin
interface AnvilCallback {
    fun invoke(player: Player, text: String, inventory: Inventory)
}
```

`InventoryHandler` 监听数据包：

- `PacketPlayInItemName` / `ServerboundRenameItemPacket`
- 虚拟容器：从 `playerRemoteInventoryMap` 取 `VirtualInventory`，通过 `MenuHolder` 找 `AnvilCallback`。
- 普通容器：从 `InventoryViewProxy.getTopInventory(player.openInventory)` 找 `AnvilCallback`。

来源：[InventoryHandler.kt L108-L127](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandler.kt#L108-L127)、[AnvilImpl.kt L20-L31](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/AnvilImpl.kt#L20-L31)。

因此铁砧文本输入不依赖 `InventoryClickEvent`，而是数据包层回调。

### 9.5 可储存箱子

`StorableChestImpl` 继承 `ChestImpl`，并引入：

- `rule { checkSlot / firstSlot / writeItem / readItem / shiftSwap / mergeSlots / itemStacker / consumeSlots }`
- `autoStack(enabled = true)`：自动合并/交换模式
- `allowNumberKey`：是否允许数字键
- 五个处理器：`PlaceActionHandler`、`PickupActionHandler`、`SwapActionHandler`、`ShiftClickHandler`、`DragActionHandler`

点击处理：

- `COLLECT_TO_CURSOR` 直接取消。
- 从玩家背包 Shift 到 UI：交给 `shiftClickHandler.shiftClickToUI`。
- UI 内槽位：按 `InventoryAction` 分发到放置/取出/交换/Shift/丢弃处理器。
- `StorableActionResult.HANDLED` / `DENIED` 取消事件，`PASS` 交给 Bukkit。

来源：[StorableChestImpl.kt L21-L86](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/StorableChestImpl.kt#L21-L86)、[StorableChestImpl.kt L125-L210](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/StorableChestImpl.kt#L125-L210)。

注意：

- `StorableChestImpl` 重写了 `onClick(Int/Char)`，只做匹配，**不自动取消**；这与 `ChestImpl` 的行为不同，因为存储菜单需要让事件继续被 Bukkit 处理。
- `rule()` 在 `virtualized` 时直接报错；`buildRule()` 只处理 `CLICK` 和 `DRAG`，`VIRTUAL` 不处理。因此 `StorableChest` 的存储规则不支持虚拟化菜单。

来源：[StorableChestImpl.kt L39-L47](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/StorableChestImpl.kt#L39-L47)、[StorableChestImpl.kt L77-L85](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/StorableChestImpl.kt#L77-L85)。

## 10. 虚拟化：数据包模拟容器

### 10.1 包装方式

`Chest.virtualize(storageContents)` 只是设置标记：

```kotlin
override fun virtualize(storageContents: List<ItemStack>?) {
    this.virtualized = true
    this.virtualizedStorageContents = storageContents
}

override fun hidePlayerInventory() {
    virtualize((0 until 36).map { ItemStack(Material.AIR) })
}
```

来源：[ChestImpl.kt L227-L237](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/ChestImpl.kt#L227-L237)。

`build()` 在创建真实 Inventory 后包装：

```kotlin
lastInventory = Bukkit.createInventory(holderCallback(this), ..., createTitle())
if (virtualized) {
    lastInventory = lastInventory.virtualize(virtualizedStorageContents)
}
```

来源：[ChestImpl.kt L402-L408](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/ChestImpl.kt#L402-L408)。

`VirtualInventory`：

- 实现 `Inventory`，大多数方法委托给 `bukkitInventory`。
- `setItem` 同时向 `RemoteInventory` 发送槽位变更。
- `getViewers()` 返回虚拟容器的 viewer。
- `getHolder()` 委托给真实 Inventory 的 holder。
- `storageContents` 不足 36 时补 AIR。

来源：[VirtualInventory.kt L19-L45](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/VirtualInventory.kt#L19-L45)、[VirtualInventory.kt L116-L123](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/VirtualInventory.kt#L116-L123)、[VirtualInventory.kt L210-L232](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/VirtualInventory.kt#L210-L232)。

### 10.2 打开与注入

```kotlin
fun HumanEntity.openVirtualInventory(inventory: VirtualInventory, updateId: Boolean = true): RemoteInventory {
    val remoteInventory = InventoryHandler.instance.openInventory(this as Player, inventory, ItemStack(Material.AIR), updateId)
    inventory.remoteInventory = remoteInventory
    InventoryHandler.playerRemoteInventoryMap[name] = remoteInventory
    if (isPrimaryThread) {
        Bukkit.getPluginManager().callEvent(InventoryOpenEvent(remoteInventory.createInventoryView()))
    } else {
        submit { Bukkit.getPluginManager().callEvent(InventoryOpenEvent(...)) }
    }
    return remoteInventory
}
```

来源：[VirtualInventoryFactory.kt L31-L42](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/VirtualInventoryFactory.kt#L31-L42)。

`RemoteInventory.inject(menu)` 做两件事：

- `onClick`：构造 `VirtualInventoryInteractEvent` 和 `ClickEvent(VIRTUAL)`，执行菜单的 `clickCallback` / `selfClickCallback`。
- `onClose`：执行菜单 `closeCallback`，并处理 `onceCloseCallback`。

来源：[VirtualInventoryFactory.kt L51-L78](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/VirtualInventoryFactory.kt#L51-L78)。

### 10.3 点击与关闭

`InventoryHandler` 监听 `PacketReceiveEvent`：

- `PacketPlayInCloseWindow` / `ServerboundContainerClosePacket`：关闭虚拟容器，`close(sendPacket = false)`。
- `PacketPlayInWindowClick` / `ServerboundContainerClickPacket`：调用 `remoteInventory.handleClick(packet)`。
- `PacketPlayInItemName` / `ServerboundRenameItemPacket`：调用 `AnvilCallback`。

来源：[InventoryHandler.kt L75-L130](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandler.kt#L75-L130)。

`InventoryHandlerImpl.VInventory` 负责：

- 按版本构造 `OpenWindow` / `WindowItems` / `SetSlot` / `SetCursor` / `CloseWindow` 数据包。
- 把客户端点击包转换为 `VirtualClickType`（`LEFT`、`RIGHT`、`SHIFT_LEFT`、`NUMBER_KEY`、`DROP`、`DOUBLE_CLICK` 等）。
- 通过 `submit { onClickCallback?.invoke(...) }` 切回主线程执行菜单回调。
- 在回调之后重新发送光标/槽位/背包数据，使客户端显示与服务端状态一致。

来源：[InventoryHandlerImpl.kt L149-L232](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandlerImpl.kt#L149-L232)、[InventoryHandlerImpl.kt L324-L470](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandlerImpl.kt#L324-L470)。

虚拟菜单的“自动锁定”本质上是：客户端看到的是服务端伪造的容器，服务端没有真实打开对应容器；点击只被转换为回调，物品不会真正移动到玩家背包。`ClickEvent.isCancelled` 操作的是合成的 `VirtualInventoryInteractEvent`，不会像真实 Bukkit 事件那样阻止原版逻辑。

### 10.4 已知虚拟化限制

- `Chest` 接口注释明确写着：虚拟化菜单下 `player.closeInventory()` 不会触发 `onClose` 回调（TODO 2023/10/09）。官方 FAQ 也把它列为已知问题。
- 文档说虚拟化“某些特殊版本上可能有兼容性问题”，建议在目标版本测试。
- 虚拟点击类型映射中，拖拽没有完整语义：`handleClick()` 在 1.8 会把 shift=5 翻译成字符串 `"DRAG"`，但 `handle()` 没有 `"DRAG"` 分支，最终仍落到 `else -> VirtualClickType.UNKNOWN`；1.9+ 也没有完整的拖拽处理。
- `StorableChest` 的规则不支持虚拟化（`rule()` 在 virtualized 时直接报错）。

来源：[Chest.kt L89-L95](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/Chest.kt#L89-L95)、[文档 L1173-L1198](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L1173-L1198)、[InventoryHandlerImpl.kt L367-L397](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandlerImpl.kt#L367-L397)。

## 11. 线程模型与版本兼容

### 11.1 线程

- `submit(...)`：默认 `async = false`，由平台调度器执行；Bukkit 下是主线程任务。
- `submitAsync(...)`：`async = true`，异步执行。
- `InventoryOpenEvent` 的构建回调：
  - 同步：`submit { buildCallback; finalBuildCallback }`
  - 异步：`submitAsync { asyncBuildCallback; asyncFinalBuildCallback }`
- `onBuild(async = true)` 和 `onGenerate(async = true)` 的回调在异步任务中执行。
- 官方文档要求异步回调中不要直接访问 Bukkit API，除非用 `submit {}` 切回主线程。
- 但源码中 `PageableChestImpl.processBuild(async = true)` 在异步回调返回后**直接** `inventory.setItem(slot, itemStack)`；框架并没有自动切回主线程。也就是说，`onGenerate(async = true)` 的回调本身以及它的返回值写入 Inventory 都在异步线程，是否安全取决于服务端实现和具体操作。这一点文档没有展开，属于源码可核实的风险点。

来源：[ClickListener.kt L40-L54](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickListener.kt#L40-L54)、[PageableChestImpl.kt L231-L248](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/PageableChestImpl.kt#L231-L248)、[文档 L970-L978](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L970-L978)。

另外：

- `openMenu` 没有显式线程检查；如果从异步线程调用 `openInventory`，需要调用方自己保证主线程。
- `openVirtualInventory` 会在非主线程时用 `submit` 补发 `InventoryOpenEvent`。
- `RemoteInventory.close()` 在非主线程时用 `submit` 执行回调和 `InventoryCloseEvent`。
- 框架的 `BukkitExecutor` 在 Folia 环境下使用 `FoliaRunningTask`，但 `bukkit-ui` 没有 Folia 专用区域调度逻辑；Folia 兼容性未验证。

来源：[VirtualInventoryFactory.kt L36-L41](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/VirtualInventoryFactory.kt#L36-L41)、[InventoryHandlerImpl.kt L302-L313](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandlerImpl.kt#L302-L313)、[BukkitExecutor.kt L77-L79](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/platform/platform-bukkit-impl/src/main/kotlin/taboolib/platform/BukkitExecutor.kt#L77-L79)。

### 11.2 版本分支

`InventoryHandlerImpl` 按 `MinecraftVersion.major` 分支：

- 1.8-1.12：`Craft9Container`、`PacketPlayOutOpenWindow`、`PacketPlayOutWindowItems`。
- 1.13-1.16：`Craft16Container`。
- 1.17-1.21：`Craft19Container`。
- 26.1+：`CraftContainer` / `ClientboundOpenScreenPacket`（Mojang 映射）。

来源：[InventoryHandlerImpl.kt L81-L146](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandlerImpl.kt#L81-L146)。

1.21 相关：

- `MinecraftVersion.V1_21`、`versionId = 12100`。
- `VirtualInventoryViewModern` 用于 `>= 1.21` 的 `InventoryView` 实现。
- `InventoryViewProxy` 处理 Paper 1.21 中 `InventoryView` 变成接口的情况。
- `InventoryHandlerImpl.sendInitialData` 对 `versionId >= 12108` 使用 `ClientboundContainerSetContentPacket` 的 `List` 构造器，否则使用 `NonNullList`。
- `bukkit-ui-12100` 子模块编译 `v12101`；主模块还编译 `v12104` 与 `v260100`。

来源：[MinecraftVersion.kt L27-L43](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit-nms/src/main/kotlin/taboolib/module/nms/MinecraftVersion.kt#L27-L43)、[MinecraftVersion.kt L121-L142](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit-nms/src/main/kotlin/taboolib/module/nms/MinecraftVersion.kt#L121-L142)、[VirtualInventoryFactory.kt L83-L125](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/VirtualInventoryFactory.kt#L83-L125)、[InventoryViewProxy.kt L17-L79](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/InventoryViewProxy.kt#L17-L79)、[bukkit-ui/build.gradle.kts L1-L21](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/build.gradle.kts#L1-L21)。

### 11.3 Paper / Spigot / 混合端

- 数据包名同时兼容 Spigot 与 Paper 命名，例如 `PacketPlayInWindowClick` 与 `ServerboundContainerClickPacket`。
- `MinecraftVersion.isUniversal`（>=1.17）和 `isUnobfuscated`（>=26.1）用于选择 NMS 字段/构造器。
- `PacketSender` 对混合端（注释明确提到 CatServer）使用原生反射回退；`AsmClassTranslation` 注释提到从 Spigot 切换到 Arclight 时需要重新转译。
- 但 `bukkit-ui` 没有 Arclight 专用分支；虚拟菜单依赖 `CraftContainer`/NMS 数据包，在 Arclight 等混合端属于**未验证组合**。官方文档也只说“某些特殊版本可能有问题”，没有给出 Arclight 保证。

来源：[InventoryHandler.kt L75-L107](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandler.kt#L75-L107)、[PacketSender.kt L65-L152](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit-nms/src/main/kotlin/taboolib/module/nms/PacketSender.kt#L65-L152)、[AsmClassTranslation.kt L111](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit-nms/src/main/kotlin/taboolib/module/nms/AsmClassTranslation.kt#L111)。

## 12. 扩展点

1. **`Menu.registerImplementation`**：为自定义 `Menu` 接口注册实现类；实现类必须有 `(String)` 构造器。
2. **`holder { menu -> ... }`**：替换 `MenuHolder` 创建逻辑，可返回子类。
3. **`onBuild` / `onFinalBuild` / `onInventoryCreate`**：构建阶段扩展。
4. **`selfClick`**（`ChestImpl` 的 `open fun`，不在 `Chest` 接口）：内部单回调，供 `PageableChestImpl` / `StorableChestImpl` 覆盖；自定义子类可利用。
5. **`MenuExtensions.kt`**：
   - `InventoryCloseEvent.returnItems(slots)`：关闭时返还物品，背包满时掉落。
   - `ClickEvent.conditionSlot(rawSlot, condition, failedCallback)`：按放入/取出条件过滤。
   - `ClickEvent.amountCondition(rawSlot, amount, failedCallback)`：限制槽位最大数量。
   - `ClickEvent.lockSlots(rawSlots, reverse)`：锁定/仅允许指定槽位。
   - `ClickEvent.applyAmountConditionShiftIfNeeded()`：处理 Shift 点击时需要手动调用的收尾方法。
6. **`StorableChest.rule`**：`checkSlot`、`firstSlot`、`writeItem`、`readItem`、`shiftSwap`、`mergeSlots`、`itemStacker`、`consumeSlots`，可把物品存到数据库/影子状态。
7. **`ItemStacker`**：自定义最大堆叠数量与物品合并逻辑。
8. **`MenuBuilderRaw`**：`Source` 原始标题。
9. **Arim `UIConfigHelper`**：YAML + 变量替换 + Kether 的声明式封装，属于核心 API 之上的扩展。

来源：[MenuExtensions.kt L29-L37](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/MenuExtensions.kt#L29-L37)、[MenuExtensions.kt L51-L206](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/MenuExtensions.kt#L51-L206)、[MenuExtensions.kt L214-L306](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/MenuExtensions.kt#L214-L306)、[MenuExtensions.kt L573-L657](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/MenuExtensions.kt#L573-L657)、[StorableChest.kt L26-L102](https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/StorableChest.kt#L26-L102)。

## 13. 已知限制与文档/源码差异

### 13.1 文档明确说明的限制

- 虚拟化菜单下 `player.closeInventory()` 不触发 `onClose`；官方 FAQ 将其列为已知问题。
- 虚拟化菜单有网络开销，不适合高频更新。
- 虚拟化在部分特殊版本可能有兼容性问题，需要在目标版本测试。
- 异步生成物品时不要直接访问 Bukkit API，除非用 `submit {}`。
- 没有内置自动刷新；需要自己用调度器并在关闭时取消。
- `onClick(lock = true)` / `onClick(Char/Int)` 会自动取消，扩展方法应在 `onClick(lock = false)` 中使用。

来源：[文档 L1173-L1198](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L1173-L1198)、[文档 L970-L978](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L970-L978)、[文档 L2399-L2437](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L2399-L2437)、[文档 L2560-L2588](https://github.com/FxRayHughes/taboowiki/blob/4ab5015f6db5469a74704a0d299d3f22ad67f655/docs/basic-tech/chest-menu/index.mdx#L2560-L2588)。

### 13.2 源码可核实的限制/注意点

1. **没有 `MenuSession`**：普通菜单没有每玩家 session；`PageableChestImpl.viewer` 是单值，不能安全地让同一实例同时服务多个玩家。
2. **`onClose` 会替换默认关闭回调**：默认 `closeCallback` 负责 `isOpened = false`；用户调用 `onClose` 后，该默认行为丢失，`isOpened` 可能一直为 `true`。
3. **`PageableChestImpl.maxPage` 整数除法**：部分页场景下 `roll` 到最后一页可能不正确；`menuSlots` 为空会除零。
4. **`elements` 回调并非“翻页不重新获取”**：`build()` 每次都调用 `elementsCallback()`，与 FAQ 描述不一致。
5. **`HopperImpl` 无 `slots[0]` 空保护**：不调用 `map()` 会抛异常。
6. **`onGenerate(async = true)` 直接写 Inventory**：异步线程写 Bukkit Inventory，存在线程安全风险。
7. **`map()` 不校验行宽**：超过 9 字符截断，短行留下空槽；`Hopper`/`Anvil` 分别只取前 5/3 字符。
8. **`openMenu` 吞异常**：构建/打开失败只打印堆栈，不向调用方抛出。
9. **虚拟拖拽不完整**：1.9+ 的拖拽在 `handle()` 中映射为 `VirtualClickType.UNKNOWN`，没有完整拖拽语义。
10. **`StorableChest` 不支持虚拟化规则**：`rule()` 在 virtualized 时直接报错，`VIRTUAL` 点击不进入存储处理器。
11. **`Menu` 自定义实现不等于可获得事件路由**：`MenuHolder` 要求 `Chest`，`ClickListener` 要求 `ChestImpl`；只实现 `Menu` 的非 Chest 菜单可以打开，但不会被这套点击系统接管。
12. **`MenuHolder` 双 Inventory 细节**：`MenuHolder` 构造器创建的 Inventory 与 `ChestImpl.lastInventory` 不是同一对象；`getInventory()` 不保证返回实际打开的界面。

### 13.3 文档与源码不一致的地方

- 类型层次图：文档把 `Anvil`/`Hopper` 画成直接继承 `Menu`，源码是继承 `Chest`。
- `PageableChest` 元素缓存：文档 FAQ 说翻页不重新获取，源码每次 `build()` 都重新执行 `elements`；文档自身 L912-L915 与 L2374-L2380 也互相矛盾。
- `onClick(Char/Int)` 自动取消：对 `Chest` 成立，但 `StorableChestImpl` 重写后不自动取消；文档 FAQ 的概括不适用于 `StorableChest`。
- 异步生成：文档说“加载完成后自动设置到对应槽位”，源码确实设置，但设置动作本身在异步线程，文档没有说明线程风险。

### 13.4 未验证项

- Arclight、Mohist、CatServer 等混合端上的虚拟菜单行为（源码只有通用混合端回退，没有菜单专用分支）。
- Folia 上的菜单行为（调度器层有 Folia 支持，但菜单模块没有区域调度逻辑）。
- 所有 1.21.x 补丁版本的实测覆盖；源码只体现了 1.21/1.21.8 的分支处理。
- 普通菜单翻页重新打开时是否一定触发旧 Inventory 的 `InventoryCloseEvent`（按 Bukkit 语义推断，未实测）。
- Arim `UIConfigHelper` 的内部实现（不在 `TabooLib/TabooLib` 仓库）。
- 未运行 Gradle 构建，未验证编译产物。

## 14. 关键链接索引

### 官方文档

- 箱子菜单：`https://taboolib.maplex.top/docs/basic-tech/chest-menu/`（canonical `https://taboolib.org/docs/basic-tech/chest-menu/`）
- 物品构建与操作：`https://taboolib.maplex.top/docs/basic-tech/item-builder/`（canonical `https://taboolib.org/docs/basic-tech/item-builder/`）
- Arim 菜单快速构建工具：`https://taboolib.maplex.top/docs/expanding-technology/arim/menu-helper/`（canonical `https://taboolib.org/docs/expanding-technology/arim/menu-helper/`）
- 文档源仓库：`https://github.com/FxRayHughes/taboowiki`（commit `4ab5015f6db5469a74704a0d299d3f22ad67f655`）

### 核心源码 permalink

- `Menu.kt`：https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/Menu.kt
- `MenuBuilder.kt`：https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/MenuBuilder.kt
- `MenuHolder.kt`：https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/MenuHolder.kt
- `ClickListener.kt`：https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickListener.kt
- `ClickEvent.kt`：https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/ClickEvent.kt
- `ChestImpl.kt`：https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/ChestImpl.kt
- `PageableChestImpl.kt`：https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/PageableChestImpl.kt
- `StorableChestImpl.kt`：https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/type/impl/StorableChestImpl.kt
- `InventoryHandlerImpl.kt`：https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/InventoryHandlerImpl.kt
- `VirtualInventoryFactory.kt`：https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/virtual/VirtualInventoryFactory.kt
- `ItemBuilder.kt`：https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-util/src/main/kotlin/taboolib/platform/util/ItemBuilder.kt
- `MenuExtensions.kt`：https://github.com/TabooLib/TabooLib/blob/967417225a7ab8be38cf634beca2b5794383497c/module/bukkit/bukkit-ui/src/main/kotlin/taboolib/module/ui/MenuExtensions.kt
