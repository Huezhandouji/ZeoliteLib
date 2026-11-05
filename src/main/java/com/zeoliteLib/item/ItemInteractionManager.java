package com.zeoliteLib.item;

import com.zeoliteLib.internal.LazyModuleHost;
import com.zeoliteLib.internal.ModuleBootstrap;
import com.zeoliteLib.internal.ZeoliteEventBus;
import com.zeoliteLib.item.event.ZeoliteInventoryClickEvent;
import com.zeoliteLib.item.event.ZeoliteInventoryDragEvent;
import com.zeoliteLib.item.event.ZeoliteItemInteractionEvent;
import com.zeoliteLib.item.internal.InventoryInteractionListener;
import com.zeoliteLib.item.internal.ItemInteractionListener;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class ItemInteractionManager {

    private static final LazyModuleHost<ItemInteractionManager> HOST = LazyModuleHost.of("item_interaction_manager", ItemInteractionManager::create);

    private final JavaPlugin plugin;

    private NamespacedKey itemInteractionPdcKey;

    private final HashMap<String, ItemInteractionDefinition> registeredDefinitions = new HashMap<>();

    private ItemInteractionManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }


    public static ItemInteractionManager get() {
        return HOST.get();
    }

    private static ItemInteractionManager create(){
        JavaPlugin plugin = ModuleBootstrap.plugin();
        plugin.getServer().getPluginManager().registerEvents(new ItemInteractionListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new InventoryInteractionListener(), plugin);
        ItemInteractionManager instance = new ItemInteractionManager(plugin);
        instance.itemInteractionPdcKey = new NamespacedKey(plugin, "item_interaction");


        ZeoliteEventBus.get().subscribe(ZeoliteItemInteractionEvent.class, instance::onItemInteractionZEvent);
        ZeoliteEventBus.get().subscribe(ZeoliteInventoryClickEvent.class, instance::onInventoryClickZEvent);
        ZeoliteEventBus.get().subscribe(ZeoliteInventoryDragEvent.class, instance::onInventoryDragZEvent);

        return instance;
    }

    public NamespacedKey getItemInteractionPdcKey() {
        return itemInteractionPdcKey;
    }

    public ItemInteractionDefinition.Builder createItemInteractionBuilder(String id) {
        return ItemInteractionDefinition.createBuilder(id);
    }

    public boolean registerDefinition(@NotNull ItemInteractionDefinition definition) {
        if(registeredDefinitions.containsKey(definition.getId())) return false;
        registeredDefinitions.put(definition.getId(), definition);
        return true;
    }

    public void unregisterDefinition(@NotNull String id) {
        registeredDefinitions.remove(id);
    }

    public ItemInteractionHandle createItemInteractionHandle(ItemInteractionDefinition definition) {
        return new ItemInteractionHandle(this, definition);
    }


    public void onItemInteractionZEvent(ZeoliteItemInteractionEvent zEvent) {
        ItemInteractionDefinition definition = resolveDefinition(zEvent.getItem());
        if(definition == null) return;

        for(Consumer<ZeoliteItemInteractionEvent> listener : definition.getItemInteractionListeners()){
            listener.accept(zEvent);
        }
    }

    public void onInventoryClickZEvent(ZeoliteInventoryClickEvent zEvent) {
        //一次点击可能有多件物品参与:被点格物品(currentItem)恒参与;
        //光标物品在放置/交换/收集(DOUBLE_CLICK)与丢出窗口(WINDOW_BORDER_*)时参与;
        //数字键(NUMBER_KEY)换入的是热键栏物品,F 键(SWAP_OFFHAND)换入的是副手物品。
        //任一参与物品带交互标记即分发到对应定义,同一定义只分发一次
        LinkedHashSet<ItemInteractionDefinition> definitions = new LinkedHashSet<>();
        addResolvedDefinition(zEvent.getCurrentItem(), definitions);

        ClickType click = zEvent.getRawEvent().getClick();
        switch (click) {
            case LEFT, RIGHT, DOUBLE_CLICK, WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT ->
                    addResolvedDefinition(zEvent.getCursorItem(), definitions);
            case NUMBER_KEY -> addResolvedDefinition(getHotbarButtonItem(zEvent), definitions);
            case SWAP_OFFHAND -> addResolvedDefinition(zEvent.getRawEvent().getWhoClicked().getInventory().getItemInOffHand(), definitions);
            default -> { }
        }

        for(ItemInteractionDefinition definition : definitions){
            for(Consumer<ZeoliteInventoryClickEvent> listener : definition.getClickListeners()){
                listener.accept(zEvent);
            }
        }
    }

    public void onInventoryDragZEvent(ZeoliteInventoryDragEvent zEvent) {
        //被拖的是 oldCursor(拖拽开始前光标持有的物品);cursorItem 是拖拽结束后的光标残留(通常已空),识别不到被拖物品
        ItemInteractionDefinition definition = resolveDefinition(zEvent.getOldCursor());
        if(definition == null) return;

        for(Consumer<ZeoliteInventoryDragEvent> listener : definition.getDragListeners()){
            listener.accept(zEvent);
        }
    }

    //item 带交互标记且对应定义已注册时收入 definitions(LinkedHashSet 按定义去重)
    private void addResolvedDefinition(ItemStack item, LinkedHashSet<ItemInteractionDefinition> definitions) {
        ItemInteractionDefinition definition = resolveDefinition(item);
        if(definition != null) {
            definitions.add(definition);
        }
    }

    //NUMBER_KEY 点击被换入被点格的热键栏物品;非数字键点击返回 null
    private ItemStack getHotbarButtonItem(ZeoliteInventoryClickEvent zEvent) {
        int button = zEvent.getRawEvent().getHotbarButton();
        if(button < 0) return null;
        return zEvent.getRawEvent().getWhoClicked().getInventory().getItem(button);
    }

    private ItemInteractionDefinition resolveDefinition(ItemStack item) {
        if(item == null || item.isEmpty()) return null;
        String itemInteractionId = item.getPersistentDataContainer().get(itemInteractionPdcKey, PersistentDataType.STRING);
        if(itemInteractionId == null) return null;
        return registeredDefinitions.get(itemInteractionId);
    }

}

