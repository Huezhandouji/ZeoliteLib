# inventoryMenu 箱子菜单模块设计文档

> 代码内注释为精简版，本文是该模块完整语义的权威来源。
> 设计参照 TabooLib BukkitUI（Menu/Chest/Hopper 层次模型），调研底稿见
> `research/2026-11-02-taboolib-chest-menu.md`；模块骨架约定见 06（LazyModuleHost / 事件总线 / 包纪律）。

## 1. 类层次

| 类 | 职责 |
|---|---|
| `InventoryMenu<M>`（抽象基类） | 布局、物品、回调、handLocked 锁定、事件路由、创建管线、配置冻结 |
| `ChestMenu` | 标准箱子：1-6 行 × 9 格，按尺寸创建容器；小/大箱子只是 `rows(3)` / `rows(6)` |
| `HopperMenu` | 漏斗：固定 5 格、单行 5 字符布局，按 `InventoryType.HOPPER` 创建 |
| `internal/MenuHolder` | 菜单身份载体（InventoryHolder），事件路由的识别键 |
| `internal/MenuListener` | 关闭回调与手部锁定的 Bukkit 原生事件监听 |
| `InventoryMenuManager` | 唯一路由入口（事件总线订阅 + Bukkit 监听注册） |

## 2. 核心模型

- **菜单实例 = 配置载体 + 状态容器**，不设独立 Builder。`createInventory()` 每次调用创建
  一个全新的 Bukkit `Inventory` 并重新生成全部物品。
- **身份识别绑定在 holder 上**：`MenuHolder` 持有菜单实例，事件层以
  `inventory.getHolder() instanceof MenuHolder` 取回菜单。普通方块容器（箱子/漏斗等）的
  holder 是 BlockState，不会被误判；标题碰撞、内容相似均不影响识别。
- 同一实例可多次 `open()`（每次独立 Inventory，回调共享）；也可只 `createInventory()`
  自行管理打开方式——此时须自行确保 `InventoryMenuManager` 已初始化（`open()` 内部自会初始化）。

## 3. 创建管线

```
createInventory()                      // 模板方法，每次全新装配
  ├─ createMenuHolder()                // 身份载体（钩子，可覆写）
  ├─ createContainer(holder)           // 空容器（抽象：箱子按尺寸 / 漏斗按 InventoryType）
  ├─ holder.bind(inventory)            // 回绑，保证 holder.getInventory() 返回真实界面
  ├─ fillItems(inventory)              // 物品填充
  └─ onBuild 回调（按注册序）          // 之后配置冻结
```

物品填充顺序（同槽位后者覆盖前者）：

1. 字符静态（`set(char, ItemStack)`，同字符全部槽位各放一份**克隆**）
2. 字符动态（`setSupplier(char, Supplier)`，返回 null 则该槽留空）
3. 槽位动态（`setSupplier(int, Supplier)`）
4. 槽位静态（`set(int, ItemStack)`，覆盖同槽位的字符物品）

物品放入一律 `clone()`，杜绝同一 ItemStack 实例跨槽位共享引发的复制/被改隐患。

## 4. 子类扩展契约

1. **self-type 泛型**：`class FooMenu extends InventoryMenu<FooMenu>`——基类流式方法经
   `self()` 返回 `M`，保证链式调用的静态类型不收窄（否则 `FooMenu m = new FooMenu(..).onBuild(..)`
   编译不过）。
2. **三个抽象成员**：
   - `createContainer(MenuHolder)`：创建空容器（不含物品与回调）；标题为 Component，
     用 `getTitle()` 取得后直接传入（Paper 已过时 String 标题重载）。
   - `getRowWidth()`：布局每行字符数（箱子 9、漏斗 5、铁砧 3）。
   - `getSize()`：容器总槽位数；未完成必要配置时返回 0，`createInventory()` 拒绝。
3. **两个钩子**（基类默认实现，按需覆写）：
   - `createMenuHolder()`：默认 `new MenuHolder(this)`；定制身份载体时覆写。
   - `onMapApplied(int rowCount)`：`map()` 应用后回调；需要"布局行数反哺配置"的子类用它
     （ChestMenu 据此抬升 rows）。固定尺寸容器可不覆写，map 自会校验超容。
4. 子类自有配置方法应先调 `checkNotBuilt()` 保证冻结纪律。

## 5. 事件路由架构

```
Bukkit InventoryClickEvent / InventoryDragEvent
  → item 模块 InventoryInteractionListener（已存在的 Bukkit 监听）
  → ZeoliteEventBus 发布 ZeoliteInventoryClickEvent / ZeoliteInventoryDragEvent
  → InventoryMenuManager（MenuHolder.menuOf 识别菜单）
  → menu.handleClick / handleDrag（handLocked 处理 + 回调分发）
  → zEvent.setCanceled 回传 → item 模块统一取消原生 Bukkit 事件

Bukkit InventoryCloseEvent / PlayerDropItemEvent / PlayerItemHeldEvent / PlayerSwapHandItemsEvent
  → 本模块 MenuListener（直接 Bukkit 监听）
  → 关闭经 Manager.handleClose 路由；手部事件按 handLocked 就地取消
```

- 菜单点击/拖拽**不新造 Bukkit 监听**，搭 item 公共面事件车；取消是建议性标志（总线语义），
  由 item 模块在 publish 返回后统一落到原生事件。
- `InventoryMenuManager.create()` 先触碰 `ItemInteractionManager.get()`——否则 item 模块
  监听器未注册，总线上永远没有事件可订阅。
- MenuListener 处理无关事件时先经 holder 判断，**不得触发模块懒初始化**。
- 菜单实例不注册、不追踪：身份绑定在 Inventory 的 holder 上，随 Inventory 一起被 GC。

## 6. handLocked 锁定语义（默认 true，比 TabooLib 保守）

菜单打开期间，视图内一切会移动物品的交互**默认取消**，包括：

- 菜单本体槽位的任何点击
- 拖入菜单本体（纯玩家背包内的拖拽放行）
- 数字键交换、F 键换手（SWAP_OFFHAND）、双击收集（DOUBLE_CLICK）
- Q / Ctrl+Q 丢弃、窗口外丢弃（WINDOW_BORDER_*）
- 滚轮切换手持（PlayerItemHeldEvent）、游戏内丢出物品（PlayerDropItemEvent）

**放行方式**（两种）：

1. 整体 `handLocked(false)`：转由绑定回调按槽位自动锁定（可储存类菜单的形态）。
2. 回调内 `zEvent.setCanceled(false)`：默认取消先于回调执行、后写覆盖先写，
   回调的放行/取消即最终决定。

> TabooLib 的 handLocked 只锁手部动作，展示物品可被直接拿走（其 FAQ 常见坑）；本库默认全锁。

## 7. 回调面

| 方法 | 匹配范围 | 自动取消 | 备注 |
|---|---|---|---|
| `onClick(char, l)` | 布局字符命中的菜单本体槽位 | 是 | 不处理拖拽；玩家背包槽位对应 `' '`，永不命中 |
| `onClick(int, l)` | rawSlot 精确匹配，覆盖整个视图（含玩家背包） | 是 | 不处理拖拽 |
| `onClick(l)` / `onClick(true, l)` | 视图内所有点击 | 是 | 全局回调 |
| `onClick(false, l)` | 视图内所有点击 | 否 | 由回调决定取消 |
| `onDrag(l)` | 视图内所有拖拽 | 否 | 是否拦截由回调决定 |
| `onBuild(l)` | 每次 createInventory()，物品填充之后 | — | 同步执行 |
| `onClose(l)` | 菜单关闭：玩家关闭、菜单切换、被插件关闭 | — | 收到原生 `InventoryCloseEvent` |

**点击分发顺序**（`handleClick`）：handLocked 默认取消 → 字符绑定（按注册序）→ 槽位绑定 →
全局回调；后写覆盖先写，因此全局回调的取消/放行是最终决定。

事件载体是 item 公共面的 `ZeoliteInventoryClickEvent` / `ZeoliteInventoryDragEvent`
（已含 player/rawSlot/currentItem/cursorItem/view 等载荷），不另造包装层。

## 8. 配置冻结与生命周期

- 首次 `createInventory()` 后，布局与物品配置（`map/set/setSupplier` 及子类配置方法）冻结，
  再调抛 `IllegalStateException`——菜单是一次性配置，变更请新建实例（对齐 TabooLib
  每次 openMenu 新建实例的用法）。
- 回调注册**不冻结**，事件到达时实时读取。
- `open(player)` = 模块初始化 + createInventory + openInventory；`getLastInventory()`
  返回最近一次产物（未创建过为 null）。

## 9. 校验规则

| 调用 | 抛出条件 |
|---|---|
| `map()` | 行数 0 / 行字符数 ≠ getRowWidth() / 行格数超容器大小（固定尺寸容器即时拦截） |
| `ChestMenu.rows()` | 不在 1-6 |
| `ChestMenu.onMapApplied` | 布局行数 > 6 |
| `set/onClick(char)` | 绑定字符为空格（空格保留为空槽位） |
| `set/onClick(int)` | 槽位为负 |
| `createInventory()` | getSize() ≤ 0（如箱子未设行数）/ 布局超容（rows 被显式调低）/ 配置已冻结 |
| 填充阶段 | 槽位物品超出菜单大小（IndexOutOfBoundsException） |

## 10. 线程纪律

仅服务器主线程使用（与库内其余模块一致）。`open()` 经 `InventoryMenuManager.get()`
（LazyModuleHost）自带主线程断言；`createInventory()` 单独使用时线程责任归调用方
（Bukkit.createInventory 本身主线程限定）。

## 11. 与 TabooLib 的刻意差异

| 项 | TabooLib | 本库 |
|---|---|---|
| 行宽校验 | 不校验（静默截断/留空） | 严格等于 getRowWidth()，违规即抛 |
| handLocked | 只锁手部动作，物品可被拿走 | 默认全锁 + 回调放行通道 |
| 构建异常 | 吞掉只打印堆栈 | 原样上抛 |
| onBuild | 支持异步（异步线程写 Inventory，有线程风险） | 仅同步 |
| MenuHolder | 构造器自建 Inventory，与实际界面是两个对象 | 不自建，由创建流程回绑真实界面 |
| 物品放置 | 同一实例跨槽位共享 | 每槽位克隆副本 |
| 层次 | Menu 接口 + Chest/PageableChest/StorableChest/Anvil/Hopper | 抽象类 + self-type 泛型；Pageable/Storable/Anvil 为后续任务 |

## 12. 用法示例

```java
// 标准箱子：字符布局 + 静态/动态物品 + 点击回调
new ChestMenu(Component.text("商店", NamedTextColor.DARK_GRAY))
    .map("#########",
         "#  A  B #",
         "#########")
    .set('#', new ItemStack(Material.GRAY_STAINED_GLASS_PANE))
    .set('A', new ItemStack(Material.DIAMOND), e -> e.getPlayer().sendMessage("买了钻石"))
    .setSupplier('B', () -> clockItem())                 // 每次构建重新生成
    .onClick('X', e -> e.getPlayer().closeInventory())
    .open(player);

// 漏斗：固定 5 格
new HopperMenu(Component.text("快速选择", NamedTextColor.DARK_GRAY))
    .map("ABCD ")
    .set('A', new ItemStack(Material.DIAMOND_SWORD), e -> setMode(e.getPlayer(), "战斗"))
    .open(player);

// 放行模式：handLocked(false) + 按槽位绑定（可储存菜单形态）
new ChestMenu(Component.text("存入", NamedTextColor.DARK_GRAY))
    .handLocked(false)
    .map("#########",
         "#  ...  #",
         "#########")
    .set('#', new ItemStack(Material.GRAY_STAINED_GLASS_PANE))
    .onClick('#', e -> e.setCanceled(true))              // 仅边框锁定
    .onClose(e -> returnItems(e.getInventory(), e.getPlayer()))
    .open(player);
```

## 13. 后续任务

- **PageableMenu**：翻页列表（elements/onGenerate/翻页按钮），经扩展契约落子类。
- **StorableMenu**：可储存（存取规则系统），`handLocked(false)` 形态。
- **AnvilMenu**：重命名文本捕获——TabooLib 走 NMS 数据包；Paper 侧 `PrepareAnvilEvent`
  路线是否覆盖自建 ANVIL 背包未核实，核实前不实现。
- **实测缺口**：真实玩家点击的路由链路尚无自动化覆盖（无头环境无法模拟点击）。
