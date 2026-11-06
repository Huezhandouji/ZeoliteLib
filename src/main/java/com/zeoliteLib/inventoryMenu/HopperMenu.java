package com.zeoliteLib.inventoryMenu;

import com.zeoliteLib.inventoryMenu.internal.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

/**
 * 漏斗菜单：固定 5 格、单行 5 字符布局，按 {@link InventoryType#HOPPER} 创建，不设行数概念。
 * 其余能力继承 {@link InventoryMenu}，完整语义见 docs/inventory-menu.md。
 */
public class HopperMenu extends InventoryMenu<HopperMenu> {

    private static final int SIZE = 5;

    public HopperMenu(String title) {
        super(title);
    }

    @Override
    protected int getRowWidth() {
        return SIZE;
    }

    @Override
    public int getSize() {
        return SIZE;
    }

    //固定尺寸：不反哺配置；map 超容由基类 map() 校验拦截

    @Override
    protected Inventory createContainer(MenuHolder holder) {
        return Bukkit.createInventory(holder, InventoryType.HOPPER, titleComponent());
    }
}
