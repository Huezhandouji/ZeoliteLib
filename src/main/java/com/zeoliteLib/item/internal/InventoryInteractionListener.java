package com.zeoliteLib.item.internal;


import com.zeoliteLib.internal.ZeoliteEventBus;
import com.zeoliteLib.item.event.ZeoliteInventoryClickEvent;
import com.zeoliteLib.item.event.ZeoliteInventoryDragEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class InventoryInteractionListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ZeoliteInventoryClickEvent zEvent = ZeoliteEventBus.get().publish(new ZeoliteInventoryClickEvent(event));
        if(zEvent.isCanceled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        ZeoliteInventoryDragEvent zEvent = ZeoliteEventBus.get().publish(new ZeoliteInventoryDragEvent(event));
        if(zEvent.isCanceled()) {
            event.setCancelled(true);
        }
    }
}
