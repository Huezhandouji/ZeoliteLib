package com.zeoliteLib.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.ApiStatus;

public class ItemInteractionHandle {

    private ItemInteractionDefinition definition;

    private ItemInteractionManager manager;

    @ApiStatus.Internal
    public ItemInteractionHandle(ItemInteractionManager manager, ItemInteractionDefinition definition) {
        this.manager = manager;
        this.definition = definition;
    }

    public ItemInteractionDefinition getDefinition() {
        return definition;
    }

    public boolean register(){
        return manager.registerDefinition(definition);
    }

    public void unregister(){
        manager.unregisterDefinition(definition.getId());
    }

    public void writePDC(ItemMeta meta){
        meta.getPersistentDataContainer().set(ItemInteractionManager.get().getItemInteractionPdcKey(),
                PersistentDataType.STRING, definition.getId());
    }

    public void writePDC(ItemStack itemStack){
        ItemMeta meta = itemStack.getItemMeta();
        writePDC(meta);
        itemStack.setItemMeta(meta);
    }

    public void erasePDC(ItemMeta meta){
        meta.getPersistentDataContainer().remove(ItemInteractionManager.get().getItemInteractionPdcKey());
    }

    public void erasePDC(ItemStack itemStack){
        ItemMeta meta = itemStack.getItemMeta();
        erasePDC(meta);
        itemStack.setItemMeta(meta);
    }

}
