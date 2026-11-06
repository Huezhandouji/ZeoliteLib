package com.zeoliteLib.inventoryMenu.internal;

import com.zeoliteLib.inventoryMenu.InventoryMenu;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * 菜单身份载体：绑定在 Inventory 的 holder 上，事件层经 {@link #menuOf} 取回菜单，
 * 普通方块容器（BlockState holder）不会误判；不自建 Inventory，由菜单创建流程回绑真实界面。
 */
@ApiStatus.Internal
public final class MenuHolder implements InventoryHolder {

    private final InventoryMenu<?> menu;
    private Inventory inventory;

    public MenuHolder(InventoryMenu<?> menu) {
        this.menu = menu;
    }

    /** 仅供 createInventory() 流程回绑真实界面。 */
    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @Nullable Inventory getInventory() {
        return inventory;
    }

    public InventoryMenu<?> getMenu() {
        return menu;
    }

    /** 顶部背包属于本库菜单时返回菜单实例，否则 null。 */
    public static @Nullable InventoryMenu<?> menuOf(@Nullable Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof MenuHolder menuHolder ? menuHolder.menu : null;
    }
}
