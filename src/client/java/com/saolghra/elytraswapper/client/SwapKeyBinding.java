package com.saolghra.elytraswapper.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public class SwapKeyBinding extends KeyMapping {

    private boolean bypassPressedState;

    public SwapKeyBinding(String translationKey, int defaultkey, KeyMapping.Category category) {
        super(
                translationKey, // The translation key of the keybinding's name
                defaultkey, // The keycode of the key
                category // The category of the keybinding
        );
        bypassPressedState = false;
    }

    @Override
    public void setDown(boolean pressed) {
        super.setDown(pressed);
        if(pressed) onPressed();
    }

    public void onPressed() {
        InventoryUtils.swapChestplate(Minecraft.getInstance());
    }

    public boolean isPressedBypass() {
        return bypassPressedState;
    }

    public void setPressedBypass(boolean pressed) {
        bypassPressedState = pressed;
        if (pressed) onPressBypass();
    }

    public void onPressBypass() {

        try {
            Minecraft client = Minecraft.getInstance();

            // If the the inventory screen, trigger swap
            if (client.screen instanceof InventoryScreen) {
                InventoryUtils.swapChestplate(client);
            }

//            //If in the creative screen, only trigger when in the inventory tab
//            if (client.currentScreen instanceof CreativeInventoryScreen) {
//                CreativeInventoryScreen cis = (CreativeInventoryScreen)client.currentScreen;
//                if(cis.isInventoryTabSelected()) {
//                    InventoryUtils.swapChestplate(client);
//                }
//            }

        } catch (Exception e) {
            System.out.println("ECS creative inventory swapping is not compatible with this version of Minecraft");
        }


    }

}