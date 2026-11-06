package com.zeoliteLib.inventoryMenu.internal;

import com.zeoliteLib.inventoryMenu.InventoryMenu;
import com.zeoliteLib.inventoryMenu.InventoryMenuManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Bukkit 原生事件监听：关闭回调（经 Manager 路由）与手部锁定的玩家事件
 * （Q 丢弃 / 滚轮切换手持 / F 换手）；点击与拖拽走事件总线，不在此监听。
 */
@ApiStatus.Internal
public final class MenuListener implements Listener {

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        //先经 holder 判断，无关关闭不得触发模块懒初始化
        if (MenuHolder.menuOf(event.getInventory()) == null) {
            return;
        }
        InventoryMenuManager.get().handleClose(event);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        cancelIfHandLocked(event.getPlayer(), () -> event.setCancelled(true));
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        cancelIfHandLocked(event.getPlayer(), () -> event.setCancelled(true));
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        cancelIfHandLocked(event.getPlayer(), () -> event.setCancelled(true));
    }

    //菜单打开且 handLocked 时取消；未开菜单时 holder 不是菜单，直接放行
    private void cancelIfHandLocked(Player player, Runnable cancel) {
        InventoryMenu<?> menu = MenuHolder.menuOf(player.getOpenInventory().getTopInventory());
        if (menu != null && menu.isHandLocked()) {
            cancel.run();
        }
    }
}
