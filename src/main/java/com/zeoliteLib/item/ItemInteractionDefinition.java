package com.zeoliteLib.item;

import com.zeoliteLib.item.event.ZeoliteInventoryClickEvent;
import com.zeoliteLib.item.event.ZeoliteInventoryDragEvent;
import com.zeoliteLib.item.event.ZeoliteItemInteractionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.ApiStatus;

public class ItemInteractionDefinition {

    private String id;

    private final List<Consumer<ZeoliteItemInteractionEvent>> itemInteractionListeners;
    private final List<Consumer<ZeoliteInventoryClickEvent>> clickListeners;
    private final List<Consumer<ZeoliteInventoryDragEvent>> dragListeners;

    private ItemInteractionDefinition(String id, List<Consumer<ZeoliteItemInteractionEvent>> itemInteractionListeners,
                                     List<Consumer<ZeoliteInventoryClickEvent>> clickHandlers,
                                     List<Consumer<ZeoliteInventoryDragEvent>> dragHandlers) {
        this.id = id;
        this.itemInteractionListeners = itemInteractionListeners;
        this.clickListeners = clickHandlers;
        this.dragListeners = dragHandlers;
    }

    public static Builder createBuilder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private String id;
        private List<Consumer<ZeoliteItemInteractionEvent>> itemInteractionListeners = new ArrayList<>();
        private List<Consumer<ZeoliteInventoryClickEvent>> clickListeners = new ArrayList<>();
        private List<Consumer<ZeoliteInventoryDragEvent>> dragListeners = new ArrayList<>();

        //-------给便捷方法用的--------
        private boolean isCanDrop = false;
        private boolean isCanMoveInInventory = false;

        public ItemInteractionDefinition build() {
            if(!isCanDrop){
                addItemInteractionListener(new DropBlocker());
                //背包内 Q/Ctrl+Q 丢弃悬停格物品、把光标物品丢出窗口(WINDOW_BORDER_*)都不触发
                //PlayerDropItemEvent,DropBlocker 管不到,须在点击事件里一并拦截
                addInventoryClickListener(new InventoryDropBlocker());
            }
            if(!isCanMoveInInventory){
                addInventoryClickListener(new InventoryMoveBlocker());
                //拖拽同样能把物品挪出原格,单独拦截
                addInventoryDragListener(new InventoryDragBlocker());
            }

            return new ItemInteractionDefinition(id, itemInteractionListeners, clickListeners, dragListeners);
        }

        private Builder(String id) {
            this.id = id;
        }

        //--------加监听器--------

        public Builder addItemInteractionListener(Consumer<ZeoliteItemInteractionEvent> itemInteractionListener) {
            this.itemInteractionListeners.add(itemInteractionListener);
            return this;
        }

        public Builder addInventoryClickListener(Consumer<ZeoliteInventoryClickEvent> clickListener) {
            this.clickListeners.add(clickListener);
            return this;
        }

        public Builder addInventoryDragListener(Consumer<ZeoliteInventoryDragEvent> dragListener) {
            this.dragListeners.add(dragListener);
            return this;
        }

        //--------便捷方法--------

        public Builder canDrop(boolean is) {
            this.isCanDrop = is;
            return this;
        }

        public Builder canMoveInInventory(boolean is) {
            this.isCanMoveInInventory = is;
            return this;
        }
    }

    //--------GETTERS--------

    public String getId() {
        return id;
    }

    public List<Consumer<ZeoliteItemInteractionEvent>> getItemInteractionListeners() {
        return itemInteractionListeners;
    }

    public List<Consumer<ZeoliteInventoryClickEvent>> getClickListeners() {
        return clickListeners;
    }

    public List<Consumer<ZeoliteInventoryDragEvent>> getDragListeners() {
        return dragListeners;
    }

    private static final class DropBlocker implements Consumer<ZeoliteItemInteractionEvent> {
        @Override
        public void accept(ZeoliteItemInteractionEvent zEvent) {
            if(zEvent.getType() == ItemInteractionType.DROP){
                zEvent.setCanceled(true);
            }
        }
    }

    private static final class InventoryMoveBlocker implements Consumer<ZeoliteInventoryClickEvent> {
        @Override
        public void accept(ZeoliteInventoryClickEvent zEvent) {
            zEvent.setCanceled(true);
        }
    }

    //拦截背包内的丢弃:Q/Ctrl+Q 丢悬停格物品,WINDOW_BORDER_* 把光标物品丢到窗口外
    private static final class InventoryDropBlocker implements Consumer<ZeoliteInventoryClickEvent> {
        @Override
        public void accept(ZeoliteInventoryClickEvent zEvent) {
            ClickType click = zEvent.getRawEvent().getClick();
            if(click == ClickType.DROP || click == ClickType.CONTROL_DROP
                    || click == ClickType.WINDOW_BORDER_LEFT || click == ClickType.WINDOW_BORDER_RIGHT){
                zEvent.setCanceled(true);
            }
        }
    }

    //拦截拖拽移动(分发到本类即说明被拖物品带本定义的标记)
    private static final class InventoryDragBlocker implements Consumer<ZeoliteInventoryDragEvent> {
        @Override
        public void accept(ZeoliteInventoryDragEvent zEvent) {
            zEvent.setCanceled(true);
        }
    }
}
