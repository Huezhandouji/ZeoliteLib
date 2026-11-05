package com.zeoliteLib.item.event;

import com.zeoliteLib.internal.ZeoliteEvent;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public abstract class ZeoliteInventoryInteractionEvent extends ZeoliteEvent {

    private final InventoryInteractEvent rawEvent;
    private final HumanEntity clicker;
    private final ItemStack cursorItem;
    private final Inventory inventory;
    private final InventoryView inventoryView;

    protected ZeoliteInventoryInteractionEvent(InventoryInteractEvent rawEvent, ItemStack cursorItem) {
        this.rawEvent = rawEvent;
        this.clicker = rawEvent.getWhoClicked();
        this.cursorItem = cursorItem;
        this.inventory = rawEvent.getInventory();
        this.inventoryView = rawEvent.getView();
    }

    public InventoryInteractEvent getRawEvent() {
        return rawEvent;
    }

    public Player getPlayer() {
        if(clicker instanceof Player) {
            return (Player) clicker;
        }
        return null;
    }

    public ItemStack getCursorItem() {
        return cursorItem;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public InventoryView getInventoryView() {
        return inventoryView;
    }
}
