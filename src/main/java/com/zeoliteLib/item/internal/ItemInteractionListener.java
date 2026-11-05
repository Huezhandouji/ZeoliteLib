package com.zeoliteLib.item.internal;

import com.zeoliteLib.internal.ZeoliteEventBus;
import com.zeoliteLib.item.ItemInteractionHand;
import com.zeoliteLib.item.ItemInteractionType;
import com.zeoliteLib.item.event.ZeoliteItemInteractionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ApiStatus.Internal
public class ItemInteractionListener implements Listener {

    private final JavaPlugin plugin;

    public ItemInteractionListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    //由于mc在丢出物品时触发挥手,进而触发左键事件,所以记录丢出物品的tick,丢出所在tick内的左键事件一律忽略
    private final Map<UUID, Integer> lastDropTick = new HashMap<>();
    //由于mc右键方块未被客户端判定为已消耗时,会在同一tick内紧接着补发一个右键空气数据包,所以记录右键方块的tick,
    //同tick内的右键空气事件视为同一次点击忽略;按手别分开记录,避免连带吞掉另一只手同tick的合法右键空气
    private final Map<UUID, EnumMap<EquipmentSlot, Integer>> lastRightClickTick = new HashMap<>();

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        UUID pid = event.getPlayer().getUniqueId();
        lastDropTick.put(pid, plugin.getServer().getCurrentTick());

        ZeoliteItemInteractionEvent zEvent = ZeoliteEventBus.get().publish(new ZeoliteItemInteractionEvent(event));
        if(zEvent.isCanceled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        EquipmentSlot hand = event.getHand();
        //PHYSICAL等事件的getHand()可能为null(可空签名),null说明不是左右键点击,直接忽略,防止switch NPE
        if(hand == null) {
            return;
        }
        switch (hand){
            case EquipmentSlot.HAND -> handleInteract(event, ItemInteractionHand.MAIN_HAND);
            case EquipmentSlot.OFF_HAND -> handleInteract(event, ItemInteractionHand.OFF_HAND);
        }
    }

    private void handleInteract(final PlayerInteractEvent event, final ItemInteractionHand hand) {
        UUID pid = event.getPlayer().getUniqueId();
        int currentTick = plugin.getServer().getCurrentTick();
        Action action = event.getAction();

        if(action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            //丢出物品触发的挥手不是真正的左键,丢出所在tick内的左键事件直接忽略
            if(currentTick == lastDropTick.getOrDefault(pid, -1)) {
                return;
            }
            publishInteraction(event, ItemInteractionType.LEFT_CLICK, hand);
        }
        else if(action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            //右键方块未被消耗时客户端会补发右键空气包,同tick内同手的右键空气视为同一次点击,忽略
            if(action == Action.RIGHT_CLICK_BLOCK) {
                lastRightClickTick.computeIfAbsent(pid, k -> new EnumMap<>(EquipmentSlot.class))
                        .put(event.getHand(), currentTick);
            }
            else if(isSameTickRightClickBlock(pid, event.getHand(), currentTick)) {
                return;
            }
            publishInteraction(event, ItemInteractionType.RIGHT_CLICK, hand);
        }
        //PHYSICAL(踩压力板/绊线等)不是玩家点击,不转发
    }

    private void publishInteraction(final PlayerInteractEvent event, final ItemInteractionType type, final ItemInteractionHand hand) {
        ZeoliteItemInteractionEvent zEvent = ZeoliteEventBus.get().publish(
                new ZeoliteItemInteractionEvent(event, type, hand));
        if(zEvent.isCanceled()) {
            event.setCancelled(true);
        }
    }

    //该玩家该手在本 tick 内是否右键过方块(补发的右键空气与之一致才算同一次点击)
    private boolean isSameTickRightClickBlock(final UUID pid, final EquipmentSlot hand, final int currentTick) {
        final EnumMap<EquipmentSlot, Integer> handTicks = lastRightClickTick.get(pid);
        return handTicks != null && currentTick == handTicks.getOrDefault(hand, -1);
    }

    //玩家退出后不会再有交互事件,清掉去重记录,防止 Map 随人数无限增长
    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final UUID pid = event.getPlayer().getUniqueId();
        lastDropTick.remove(pid);
        lastRightClickTick.remove(pid);
    }

}
