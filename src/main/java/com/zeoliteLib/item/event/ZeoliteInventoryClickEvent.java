package com.zeoliteLib.item.event;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ZeoliteInventoryClickEvent extends ZeoliteInventoryInteractionEvent {

    private final ItemStack currentItem;
    private final int rawSlot;
    private final int slot;

    public ZeoliteInventoryClickEvent(InventoryClickEvent rawEvent) {
        super(rawEvent, rawEvent.getCursor());
        this.currentItem = rawEvent.getCurrentItem();
        this.rawSlot = rawEvent.getRawSlot();
        this.slot = rawEvent.getSlot();
    }

    @Override
    public InventoryClickEvent getRawEvent() {
        return (InventoryClickEvent) super.getRawEvent();
    }

    public ItemStack getCurrentItem() {
        return currentItem;
    }

    public int getRawSlot() {
        return rawSlot;
    }

    public int getSlot() {
        return slot;
    }

    /** 是否 shift 点击（SHIFT_LEFT / SHIFT_RIGHT）。 */
    public boolean isShiftClick() {
        return getRawEvent().getClick().isShiftClick();
    }

    /**
     * 检查这次点击是否是一次最常见的点击类型 的 便利方法
     * <p>
     * 如果是 普通左/右键（含 shift 快速移动）拿起或点按该物品 则返回 {@code true}，否则返回 {@code false}
     * <p>
     * 排除一切作用于别处物品的点击，例如，光标有物品时的放入/交换/堆叠（要放物品）、
     * 窗口外点击丢光标物品（WINDOW_BORDER）、Q / Ctrl+Q 丢弃（DROP / CONTROL_DROP）、
     * F 键交换副手（SWAP_OFFHAND）、数字键（NUMBER_KEY）、双击收集（DOUBLE_CLICK）与中键。
     */
    public boolean isNormalClick() {
        ClickType click = getRawEvent().getClick();
        ItemStack cursor = getCursorItem();
        return (click == ClickType.LEFT || click == ClickType.RIGHT
                || click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT)
                && currentItem != null && !currentItem.isEmpty()
                && (cursor == null || cursor.isEmpty());
    }
}
