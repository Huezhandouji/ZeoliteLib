package com.zeoliteLib.inventoryMenu;

import com.zeoliteLib.internal.LazyModuleHost;
import com.zeoliteLib.internal.ModuleBootstrap;
import com.zeoliteLib.internal.ZeoliteEventBus;
import com.zeoliteLib.inventoryMenu.internal.MenuHolder;
import com.zeoliteLib.inventoryMenu.internal.MenuListener;
import com.zeoliteLib.item.ItemInteractionManager;
import com.zeoliteLib.item.event.ZeoliteInventoryClickEvent;
import com.zeoliteLib.item.event.ZeoliteInventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;

/**
 * 菜单模块管理器：唯一的点击/拖拽/关闭路由入口。点击/拖拽经事件总线订阅 item 公共面事件，
 * 关闭与手部锁定由本模块 MenuListener 直接监听 Bukkit 原生事件（架构见 docs/inventory-menu.md）。
 * 外部插件只须使用 {@code XxxMenu.open(player)}，无需触碰本类。
 */
@ApiStatus.Internal
public final class InventoryMenuManager {

    private static final LazyModuleHost<InventoryMenuManager> HOST =
        LazyModuleHost.of("inventory_menu_manager", InventoryMenuManager::create);

    private InventoryMenuManager() {
    }

    public static InventoryMenuManager get() {
        return HOST.get();
    }

    private static InventoryMenuManager create() {
        JavaPlugin plugin = ModuleBootstrap.plugin();
        //点击/拖拽搭 item 事件车：先确保其监听器已注册，否则总线上永远没有事件可订阅
        ItemInteractionManager.get();
        InventoryMenuManager instance = new InventoryMenuManager();
        plugin.getServer().getPluginManager().registerEvents(new MenuListener(), plugin);
        ZeoliteEventBus.get().subscribe(ZeoliteInventoryClickEvent.class, instance::onInventoryClick);
        ZeoliteEventBus.get().subscribe(ZeoliteInventoryDragEvent.class, instance::onInventoryDrag);
        return instance;
    }

    //-------- 事件路由（零兼容承诺，不构成公共 API）--------

    /** 总线路由：点击 → 顶部背包是菜单时交给菜单实例分发。 */
    public void onInventoryClick(ZeoliteInventoryClickEvent zEvent) {
        InventoryMenu<?> menu = MenuHolder.menuOf(zEvent.getInventory());
        if (menu != null) {
            menu.handleClick(zEvent);
        }
    }

    /** 总线路由：拖拽 → 顶部背包是菜单时交给菜单实例分发。 */
    public void onInventoryDrag(ZeoliteInventoryDragEvent zEvent) {
        InventoryMenu<?> menu = MenuHolder.menuOf(zEvent.getInventory());
        if (menu != null) {
            menu.handleDrag(zEvent);
        }
    }

    /** Bukkit 路由（MenuListener 转发）：菜单关闭回调。 */
    public void handleClose(InventoryCloseEvent event) {
        InventoryMenu<?> menu = MenuHolder.menuOf(event.getInventory());
        if (menu != null) {
            menu.handleClose(event);
        }
    }
}
