package com.zeoliteLib.inventoryMenu;

import com.zeoliteLib.inventoryMenu.internal.MenuHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;

/**
 * 标准箱子菜单：1-6 行 × 9 格，按尺寸创建容器（27 格 = rows(3)，54 格 = rows(6)）。
 * 其余能力继承 {@link InventoryMenu}，完整语义见 docs/inventory-menu.md。
 */
public class ChestMenu extends InventoryMenu<ChestMenu> {

    private static final int MAX_ROWS = 6;
    private static final int SLOTS_PER_ROW = 9;

    /** 行数；0 = 未确定。 */
    private int rows;

    public ChestMenu(Component title) {
        super(title);
    }

    /** 设置行数 1-6；map() 按布局行数自动抬升（只升不降）。 */
    public ChestMenu rows(int rows) {
        checkNotBuilt();
        if (rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("rows must be between 1 and " + MAX_ROWS + ", got: " + rows + " (ChestMenu.rows)");
        }
        this.rows = rows;
        return this;
    }

    public int getRows() {
        return rows;
    }

    @Override
    protected int getRowWidth() {
        return SLOTS_PER_ROW;
    }

    @Override
    public int getSize() {
        return rows * SLOTS_PER_ROW;
    }

    //布局行数抬升 rows；rows 被显式调低后布局超容由 createInventory() 兜底拒绝
    @Override
    protected void onMapApplied(int rowCount) {
        if (rowCount > MAX_ROWS) {
            throw new IllegalArgumentException("layout row count " + rowCount + " exceeds max " + MAX_ROWS + " (ChestMenu)");
        }
        if (rows < rowCount) {
            rows = rowCount;
        }
    }

    @Override
    protected Inventory createContainer(MenuHolder holder) {
        return Bukkit.createInventory(holder, rows * SLOTS_PER_ROW, getTitle());
    }
}
