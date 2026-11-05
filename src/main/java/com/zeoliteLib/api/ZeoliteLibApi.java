package com.zeoliteLib.api;

import com.zeoliteLib.item.ItemInteractionDefinition;
import com.zeoliteLib.item.ItemInteractionHand;
import com.zeoliteLib.item.ItemInteractionHandle;

import java.util.function.Consumer;

public interface ZeoliteLibApi {

    ItemInteractionDefinition.Builder createItemInteractionBuilder(String id);

    ItemInteractionHandle createItemInteractionHandle(ItemInteractionDefinition itemStack);

    ItemInteractionHandle createItemInteractionHandle(String id, Consumer<ItemInteractionDefinition.Builder> buildProcess);

}
