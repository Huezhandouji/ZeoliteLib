package com.zeoliteLib.inventoryMenu;

import com.zeoliteLib.inventoryMenu.internal.MenuHolder;
import com.zeoliteLib.item.event.ZeoliteInventoryClickEvent;
import com.zeoliteLib.item.event.ZeoliteInventoryDragEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 菜单抽象基类（TabooLib BukkitUI 模型的 Java 化）：布局/物品/回调/锁定/路由全在基类。
 * 锁定语义、回调顺序、扩展契约、与 TabooLib 差异见 docs/inventory-menu.md。
 */
public abstract class InventoryMenu<M extends InventoryMenu<M>> {

    /** 空格 = 空槽位，也作"无字符"的返回值。 */
    private static final char EMPTY = ' ';

    private String title;
    private boolean handLocked = true;

    /** 字符布局，每行恰好 getRowWidth() 个字符。 */
    private final List<char[]> layout = new ArrayList<>();

    //物品配置：静态存实例（放入时克隆），动态存每次构建重新生成的 Supplier
    private final Map<Character, ItemStack> charItems = new LinkedHashMap<>();
    private final Map<Character, Supplier<ItemStack>> charSuppliers = new LinkedHashMap<>();
    private final Map<Integer, ItemStack> slotItems = new LinkedHashMap<>();
    private final Map<Integer, Supplier<ItemStack>> slotSuppliers = new LinkedHashMap<>();

    //回调绑定：绑定式（char/slot，命中自动取消）与全局式
    private final List<CharClickBinding> charClicks = new ArrayList<>();
    private final List<SlotClickBinding> slotClicks = new ArrayList<>();
    private final List<GlobalClickBinding> globalClicks = new ArrayList<>();
    private final List<Consumer<ZeoliteInventoryDragEvent>> dragListeners = new ArrayList<>();
    private final List<Consumer<Inventory>> buildCallbacks = new ArrayList<>();
    private final List<Consumer<InventoryCloseEvent>> closeCallbacks = new ArrayList<>();

    /** 首次 createInventory() 后置位：布局与物品配置随之冻结（回调不冻结）。 */
    private boolean built;
    /** 最近一次 createInventory() 的产物。 */
    private Inventory lastInventory;

    /** @param title 标题，legacy § 颜色码按颜色渲染 */
    protected InventoryMenu(String title) {
        this.title = title == null ? "" : title;
    }

    //-------- 子类扩展契约 --------

    /** 创建空容器（不含物品与回调）：箱子按尺寸、漏斗/铁砧按 InventoryType；标题经 {@link #titleComponent()} 转换。 */
    protected abstract Inventory createContainer(MenuHolder holder);

    /** 布局每行字符数：箱子 9、漏斗 5、铁砧 3。 */
    protected abstract int getRowWidth();

    /** 容器总槽位数；未完成必要配置时为 0，createInventory() 拒绝。 */
    public abstract int getSize();

    /** 创建菜单身份载体；默认 {@code new MenuHolder(this)}，子类按需覆写。 */
    protected MenuHolder createMenuHolder() {
        return new MenuHolder(this);
    }

    /** {@link #map(String...)} 应用后的钩子（如 ChestMenu 据布局行数抬升 rows），基类空实现。 */
    protected void onMapApplied(int rowCount) {
    }

    /** 标题转 Component（legacy § 渲染）——Paper 已过时 String 标题重载。 */
    protected Component titleComponent() {
        return LegacyComponentSerializer.legacySection().deserialize(title);
    }

    /** 配置冻结守卫：子类自有配置方法应先调本方法。 */
    protected void checkNotBuilt() {
        if (built) {
            throw new IllegalStateException("menu already built, config frozen; create a new instance to reconfigure (title: " + title + ")");
        }
    }

    /** 流式链式的自类型返回（子类以自身类型参数化基类，静态类型不收窄）。 */
    @SuppressWarnings("unchecked")
    protected final M self() {
        return (M) this;
    }

    //-------- 基础配置 --------

    /** 设置标题（legacy § 颜色码）。 */
    public M title(String title) {
        checkNotBuilt();
        this.title = title == null ? "" : title;
        return self();
    }

    public String getTitle() {
        return title;
    }

    /** 是否锁定交互，默认 true（全锁，语义见 docs/inventory-menu.md）；可储存菜单置 false。 */
    public M handLocked(boolean locked) {
        this.handLocked = locked;
        return self();
    }

    public boolean isHandLocked() {
        return handLocked;
    }

    //-------- 布局 --------

    /** 字符画布局：每行恰好 getRowWidth() 个字符，空格为空槽位；超容抛出，行数反哺见 {@link #onMapApplied(int)}。 */
    public M map(String... rows) {
        checkNotBuilt();
        if (rows == null || rows.length == 0) {
            throw new IllegalArgumentException("map requires at least one row (InventoryMenu.map)");
        }
        int width = getRowWidth();
        this.layout.clear();
        for (String row : rows) {
            if (row == null || row.length() != width) {
                throw new IllegalArgumentException("each layout row must be exactly " + width + " chars, got: \"" + row + "\" (InventoryMenu.map)");
            }
            this.layout.add(row.toCharArray());
        }
        onMapApplied(rows.length);
        if (rows.length * width > getSize()) {
            throw new IllegalArgumentException("layout of " + rows.length + " rows = " + (rows.length * width)
                + " slots exceeds container size " + getSize() + " (InventoryMenu.map)");
        }
        return self();
    }

    /** 槽位对应字符；布局范围外（含玩家背包槽位）返回 {@code ' '}。 */
    public char getSlot(int slot) {
        if (slot < 0) {
            return EMPTY;
        }
        int width = getRowWidth();
        int row = slot / width;
        if (row >= layout.size()) {
            return EMPTY;
        }
        return layout.get(row)[slot % width];
    }

    /** 字符对应全部槽位；无命中返回空列表。 */
    public List<Integer> getSlots(char bind) {
        List<Integer> slots = new ArrayList<>();
        int width = getRowWidth();
        for (int row = 0; row < layout.size(); row++) {
            char[] line = layout.get(row);
            for (int col = 0; col < width; col++) {
                if (line[col] == bind) {
                    slots.add(row * width + col);
                }
            }
        }
        return slots;
    }

    /** 字符对应第一个槽位；无命中返回 -1。 */
    public int getFirstSlot(char bind) {
        List<Integer> slots = getSlots(bind);
        return slots.isEmpty() ? -1 : slots.get(0);
    }

    //-------- 物品 --------

    /** 按字符设置静态物品，同字符全部槽位各放一份副本；null 解除绑定。 */
    public M set(char bind, ItemStack item) {
        checkNotBuilt();
        checkBindChar(bind);
        if (item == null) {
            charItems.remove(bind);
        } else {
            charItems.put(bind, item);
        }
        charSuppliers.remove(bind);
        return self();
    }

    /** 按字符设置动态物品：每次构建重新生成，返回 null 则留空；与静态互斥、后设生效。 */
    public M setSupplier(char bind, Supplier<ItemStack> itemSupplier) {
        checkNotBuilt();
        checkBindChar(bind);
        if (itemSupplier == null) {
            throw new IllegalArgumentException("itemSupplier must not be null (InventoryMenu.setSupplier)");
        }
        charSuppliers.put(bind, itemSupplier);
        charItems.remove(bind);
        return self();
    }

    /** 按字符设置静态物品并绑定点击回调（= set + onClick）。 */
    public M set(char bind, ItemStack item, Consumer<ZeoliteInventoryClickEvent> clickListener) {
        set(bind, item);
        onClick(bind, clickListener);
        return self();
    }

    /** 按槽位设置静态物品（覆盖同槽位字符物品）；null 解除绑定。 */
    public M set(int slot, ItemStack item) {
        checkNotBuilt();
        checkSlot(slot);
        if (item == null) {
            slotItems.remove(slot);
        } else {
            slotItems.put(slot, item);
        }
        slotSuppliers.remove(slot);
        return self();
    }

    /** 按槽位设置动态物品：每次构建重新生成，返回 null 则留空；与静态互斥、后设生效。 */
    public M setSupplier(int slot, Supplier<ItemStack> itemSupplier) {
        checkNotBuilt();
        checkSlot(slot);
        if (itemSupplier == null) {
            throw new IllegalArgumentException("itemSupplier must not be null (InventoryMenu.setSupplier)");
        }
        slotSuppliers.put(slot, itemSupplier);
        slotItems.remove(slot);
        return self();
    }

    /** 按槽位设置静态物品并绑定点击回调。 */
    public M set(int slot, ItemStack item, Consumer<ZeoliteInventoryClickEvent> clickListener) {
        set(slot, item);
        onClick(slot, clickListener);
        return self();
    }

    //-------- 回调 --------

    /** 绑定字符点击：命中即自动取消，不处理拖拽；只匹配菜单本体槽位。 */
    public M onClick(char bind, Consumer<ZeoliteInventoryClickEvent> listener) {
        checkBindChar(bind);
        checkListener(listener);
        charClicks.add(new CharClickBinding(bind, listener));
        return self();
    }

    /** 绑定 rawSlot 点击：命中即自动取消，不处理拖拽；rawSlot 覆盖整个视图（含玩家背包）。 */
    public M onClick(int rawSlot, Consumer<ZeoliteInventoryClickEvent> listener) {
        checkSlot(rawSlot);
        checkListener(listener);
        slotClicks.add(new SlotClickBinding(rawSlot, listener));
        return self();
    }

    /** 全局点击回调（= {@code onClick(true, listener)}）。 */
    public M onClick(Consumer<ZeoliteInventoryClickEvent> listener) {
        return onClick(true, listener);
    }

    /** 全局点击回调：视图内所有点击都收到；lock=true 自动取消，在绑定式回调之后执行、后写覆盖先写。 */
    public M onClick(boolean lock, Consumer<ZeoliteInventoryClickEvent> listener) {
        checkListener(listener);
        globalClicks.add(new GlobalClickBinding(lock, listener));
        return self();
    }

    /** 拖拽回调：视图内拖拽都收到，不自动取消，是否拦截由回调决定。 */
    public M onDrag(Consumer<ZeoliteInventoryDragEvent> listener) {
        checkListener(listener);
        dragListeners.add(listener);
        return self();
    }

    /** 构建回调：createInventory() 填充物品之后同步执行，每次构建都执行。 */
    public M onBuild(Consumer<Inventory> listener) {
        checkListener(listener);
        buildCallbacks.add(listener);
        return self();
    }

    /** 关闭回调：玩家关闭/菜单切换/服务器关闭时触发，收到原生事件。 */
    public M onClose(Consumer<InventoryCloseEvent> listener) {
        checkListener(listener);
        closeCallbacks.add(listener);
        return self();
    }

    //-------- 创建与打开 --------

    /**
     * 创建并装配 Inventory：createMenuHolder → createContainer 回绑 → 填充物品 → onBuild 回调。
     * 每次调用产生全新 Inventory，首次调用后配置冻结；异常原样上抛。
     */
    public Inventory createInventory() {
        if (getSize() <= 0) {
            throw new IllegalStateException("container size undetermined, complete setup before createInventory (title: " + title + ")");
        }
        if (layout.size() * getRowWidth() > getSize()) {
            throw new IllegalStateException("layout of " + layout.size() + " rows = " + (layout.size() * getRowWidth())
                + " slots exceeds container size " + getSize() + " (title: " + title + ")");
        }
        MenuHolder holder = createMenuHolder();
        Inventory inventory = createContainer(holder);
        holder.bind(inventory);
        fillItems(inventory);
        for (Consumer<Inventory> callback : buildCallbacks) {
            callback.accept(inventory);
        }
        lastInventory = inventory;
        built = true;
        return inventory;
    }

    /** 创建并打开菜单（先确保模块初始化）；主线程专用。 */
    public Inventory open(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null (InventoryMenu.open)");
        }
        //首次使用即初始化菜单模块（注册点击/拖拽/关闭路由）
        InventoryMenuManager.get();
        Inventory inventory = createInventory();
        player.openInventory(inventory);
        return inventory;
    }

    /** 最近一次 createInventory() 的 Inventory；未创建过为 null。 */
    public Inventory getLastInventory() {
        return lastInventory;
    }

    //-------- 事件路由（包内可见，仅供 InventoryMenuManager 调用）--------

    /** 点击分发：handLocked 默认取消 → 字符绑定 → 槽位绑定 → 全局回调，后写覆盖先写。 */
    void handleClick(ZeoliteInventoryClickEvent zEvent) {
        //handLocked 先取消，回调可 setCanceled(false) 放行（顺序保证回调的决定是最终决定）
        if (handLocked) {
            zEvent.setCanceled(true);
        }
        char clicked = getSlot(zEvent.getRawSlot());
        for (CharClickBinding binding : charClicks) {
            if (binding.bind() == clicked) {
                zEvent.setCanceled(true);
                binding.listener().accept(zEvent);
            }
        }
        for (SlotClickBinding binding : slotClicks) {
            if (binding.rawSlot() == zEvent.getRawSlot()) {
                zEvent.setCanceled(true);
                binding.listener().accept(zEvent);
            }
        }
        for (GlobalClickBinding binding : globalClicks) {
            if (binding.lock()) {
                zEvent.setCanceled(true);
            }
            binding.listener().accept(zEvent);
        }
    }

    /** 拖拽分发：涉及菜单本体的拖拽在 handLocked 下默认取消，回调可覆盖。 */
    void handleDrag(ZeoliteInventoryDragEvent zEvent) {
        //纯玩家背包内的拖拽不动菜单物品，放行
        if (handLocked && zEvent.affectsTopInventory()) {
            zEvent.setCanceled(true);
        }
        for (Consumer<ZeoliteInventoryDragEvent> listener : dragListeners) {
            listener.accept(zEvent);
        }
    }

    /** 关闭分发（MenuListener 经 Manager 转发）。 */
    void handleClose(InventoryCloseEvent event) {
        for (Consumer<InventoryCloseEvent> listener : closeCallbacks) {
            listener.accept(event);
        }
    }

    //-------- 内部工具 --------

    //填充顺序：字符静态 → 字符动态 → 槽位动态 → 槽位静态，后填充覆盖先填充
    private void fillItems(Inventory inventory) {
        int width = getRowWidth();
        for (int row = 0; row < layout.size(); row++) {
            char[] line = layout.get(row);
            for (int col = 0; col < width; col++) {
                char bind = line[col];
                if (bind == EMPTY) {
                    continue;
                }
                int slot = row * width + col;
                ItemStack item = charItems.get(bind);
                if (item != null) {
                    inventory.setItem(slot, item.clone());
                    continue;
                }
                Supplier<ItemStack> supplier = charSuppliers.get(bind);
                if (supplier != null) {
                    placeSupplied(inventory, slot, supplier.get());
                }
            }
        }
        for (Map.Entry<Integer, Supplier<ItemStack>> entry : slotSuppliers.entrySet()) {
            placeSupplied(inventory, entry.getKey(), entry.getValue().get());
        }
        for (Map.Entry<Integer, ItemStack> entry : slotItems.entrySet()) {
            checkSlotInRange(entry.getKey(), inventory.getSize());
            inventory.setItem(entry.getKey(), entry.getValue().clone());
        }
    }

    private void placeSupplied(Inventory inventory, int slot, ItemStack item) {
        //Supplier 返回 null = 本次该槽位留空
        if (item == null) {
            return;
        }
        checkSlotInRange(slot, inventory.getSize());
        inventory.setItem(slot, item.clone());
    }

    private void checkSlotInRange(int slot, int size) {
        if (slot >= size) {
            throw new IndexOutOfBoundsException("slot " + slot + " exceeds menu size " + size + " (title: " + title + ")");
        }
    }

    private void checkBindChar(char bind) {
        if (bind == EMPTY) {
            throw new IllegalArgumentException("space is reserved for empty slots and cannot be a bind char (InventoryMenu)");
        }
    }

    private void checkSlot(int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative, got: " + slot + " (InventoryMenu)");
        }
    }

    private void checkListener(Consumer<?> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null (InventoryMenu)");
        }
    }

    //回调绑定载体
    private record CharClickBinding(char bind, Consumer<ZeoliteInventoryClickEvent> listener) {
    }

    private record SlotClickBinding(int rawSlot, Consumer<ZeoliteInventoryClickEvent> listener) {
    }

    private record GlobalClickBinding(boolean lock, Consumer<ZeoliteInventoryClickEvent> listener) {
    }
}
