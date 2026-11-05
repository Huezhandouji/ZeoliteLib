package com.zeoliteLib.api;

import com.zeoliteLib.item.ItemInteractionDefinition;
import com.zeoliteLib.item.ItemInteractionHand;
import com.zeoliteLib.item.ItemInteractionHandle;
import com.zeoliteLib.item.ItemInteractionManager;

import java.util.function.Consumer;

public class ZeoliteLibApiImpl implements ZeoliteLibApi {

    @Override
    public ItemInteractionDefinition.Builder createItemInteractionBuilder(String id) {
        return ItemInteractionManager.get().createItemInteractionBuilder(id);
    }

    @Override
    public ItemInteractionHandle createItemInteractionHandle(ItemInteractionDefinition definition) {
        return ItemInteractionManager.get().createItemInteractionHandle(definition);
    }

    @Override
    public ItemInteractionHandle createItemInteractionHandle(String id, Consumer<ItemInteractionDefinition.Builder> buildProcess) {
        ItemInteractionDefinition.Builder builder = ItemInteractionManager.get().createItemInteractionBuilder(id);
        buildProcess.accept(builder);
        return ItemInteractionManager.get().createItemInteractionHandle(builder.build());
    }
}
