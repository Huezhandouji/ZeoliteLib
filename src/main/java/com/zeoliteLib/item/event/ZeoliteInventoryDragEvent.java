package com.zeoliteLib.item.event;

import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;

public final class ZeoliteInventoryDragEvent extends ZeoliteInventoryInteractionEvent {

    //拖拽开始前光标持有的物品；父类 cursorItem 是 getCursor() 即拖拽结束后的结果光标
    private final ItemStack oldCursor;
    //rawSlot → 该格将被放入的物品
    private final Map<Integer, ItemStack> newItems;
    //本次拖拽覆盖的全部 rawSlot
    private final Set<Integer> rawSlots;

    public ZeoliteInventoryDragEvent(InventoryDragEvent rawEvent) {
        super(rawEvent, rawEvent.getCursor());
        this.oldCursor = rawEvent.getOldCursor();
        this.newItems = rawEvent.getNewItems();
        this.rawSlots = rawEvent.getRawSlots();
    }

    @Override
    public InventoryDragEvent getRawEvent() {
        return (InventoryDragEvent) super.getRawEvent();
    }

    public ItemStack getOldCursor() {
        return oldCursor;
    }

    public Map<Integer, ItemStack> getNewItems() {
        return newItems;
    }

    public Set<Integer> getRawSlots() {
        return rawSlots;
    }

    //查询某个 rawSlot 将被放入的物品；该格不在本次拖拽范围内时返回 null
    public ItemStack getNewItem(int rawSlot) {
        return newItems.get(rawSlot);
    }

    //rawSlot 小于顶部背包尺寸即属于顶部背包（界面本体），与 InventoryView.convertSlot 的分区规则一致
    public boolean affectsTopInventory() {
        int topSize = getInventoryView().getTopInventory().getSize();
        for (int rawSlot : rawSlots) {
            if (rawSlot < topSize) {
                return true;
            }
        }
        return false;
    }

    public boolean affectsBottomInventory() {
        int topSize = getInventoryView().getTopInventory().getSize();
        for (int rawSlot : rawSlots) {
            if (rawSlot >= topSize) {
                return true;
            }
        }
        return false;
    }
}
