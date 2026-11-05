package com.zeoliteLib.item.event;

import com.zeoliteLib.internal.ZeoliteEvent;
import com.zeoliteLib.item.ItemInteractionHand;
import com.zeoliteLib.item.ItemInteractionType;
import org.bukkit.entity.Item;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;


public final class ZeoliteItemInteractionEvent extends ZeoliteEvent {

    //PlayerInteractEvent 与 PlayerDropItemEvent 的公共父类，具体类型按 getType() 区分后自行下转
    private final PlayerEvent rawEvent;
    private final ItemStack item;
    private final ItemInteractionType type;
    //DROP 无手别概念，为 null
    private final ItemInteractionHand hand;
    //仅 DROP 有值，左键/右键为 null
    private final Item droppedItem;

    public ZeoliteItemInteractionEvent(PlayerInteractEvent nativeEvent, ItemInteractionType type, ItemInteractionHand hand) {
        this.rawEvent = nativeEvent;
        this.item = nativeEvent.getItem();
        this.type = type;
        this.hand = hand;
        this.droppedItem = null;
    }

    public ZeoliteItemInteractionEvent(PlayerDropItemEvent nativeEvent) {
        this.rawEvent = nativeEvent;
        this.item = nativeEvent.getItemDrop().getItemStack();
        this.type = ItemInteractionType.DROP;
        this.hand = null;
        this.droppedItem = nativeEvent.getItemDrop();
    }

    public PlayerEvent getRawEvent() {
        return rawEvent;
    }

    public ItemStack getItem() {
        return item;
    }

    public ItemInteractionType getType() {
        return type;
    }

    public ItemInteractionHand getHand() {
        return hand;
    }

    public Item getDroppedItem() {
        return droppedItem;
    }
}
