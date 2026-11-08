package com.zeoliteLib.item.event;

import com.zeoliteLib.internal.ZeoliteEvent;
import com.zeoliteLib.item.ItemInteractionType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;


public final class ZeoliteItemInteractionEvent extends ZeoliteEvent {

    private final Player player;

    //PlayerInteractEvent/PlayerDropItemEvent/PlayerSwapHandItemsEvent 的公共父类，具体类型按 getType() 区分后自行下转
    private final PlayerEvent rawEvent;
    private final ItemStack item;
    private final ItemInteractionType type;
    //DROP、SWAP_HAND 无单一手别概念，为 null
    private final EquipmentSlot hand;
    //仅 DROP 有值，其余类型为 null
    private final Item droppedItem;
    //仅 SWAP_HAND 有值：换手前副手物品（getItem() 即换手前主手物品）；
    //注意 PlayerSwapHandItemsEvent 同名方法返回的是交换后的物品，本类统一取交换前视角
    private final ItemStack offHandItem;

    public ZeoliteItemInteractionEvent(PlayerInteractEvent rawEvent, ItemInteractionType type, EquipmentSlot hand) {
        this.rawEvent = rawEvent;
        this.player = rawEvent.getPlayer();
        this.item = rawEvent.getItem();
        this.type = type;
        this.hand = hand;
        this.droppedItem = null;
        this.offHandItem = null;
    }

    public ZeoliteItemInteractionEvent(PlayerDropItemEvent rawEvent) {
        this.rawEvent = rawEvent;
        this.player = rawEvent.getPlayer();
        this.item = rawEvent.getItemDrop().getItemStack();
        this.type = ItemInteractionType.DROP;
        this.hand = null;
        this.droppedItem = rawEvent.getItemDrop();
        this.offHandItem = null;
    }

    public ZeoliteItemInteractionEvent(PlayerSwapHandItemsEvent rawEvent) {
        this.rawEvent = rawEvent;
        this.player = rawEvent.getPlayer();
        //Bukkit 两 getter 返回交换后的物品：交换前主手 = getOffHandItem()，交换前副手 = getMainHandItem()
        this.item = rawEvent.getOffHandItem();
        this.type = ItemInteractionType.SWAP_HAND;
        this.hand = null;
        this.droppedItem = null;
        this.offHandItem = rawEvent.getMainHandItem();
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

    public EquipmentSlot getHand() {
        return hand;
    }

    public Item getDroppedItem() {
        return droppedItem;
    }

    /** 仅 SWAP_HAND 有值：换手前副手物品；其余类型为 null（getItem() 即换手前主手物品）。 */
    public ItemStack getOffHandItem() {
        return offHandItem;
    }
}
