package com.saolghra.elytraswapper.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.fabricmc.api.Environment;

/**
 * Utility class for handling inventory operations related to swapping elytra and chestplates.
 */
public class InventoryUtils {

    /**
     * Swaps between elytra and chestplate in the player's inventory.
     * 
     * @param client The Minecraft client instance
     */
    @Environment(net.fabricmc.api.EnvType.CLIENT)
    public static void swapChestplate(MinecraftClient client) {
        // Safety checks
        if (client.player == null || !(client.player instanceof ClientPlayerEntity) || client.player.isDead()) {
            return;
        }

        // Slots to track found items
        int elytraSlot = -1;
        int chestplateSlot = -1;

        // Inventory size constants
        int HOTBAR_SIZE = PlayerInventory.getHotbarSize();    // 9 slots
        int MAIN_SIZE = PlayerInventory.MAIN_SIZE;            // 36 slots (main inventory)
        int TOTAL_SIZE = MAIN_SIZE + 1;                       // 37 slots (including offhand)

        // Create an array with inventory slots in the order we want to search
        int[] range = new int[TOTAL_SIZE];

        // Fill the range array with slot indices in priority order:
        
        // 1. Main inventory (slots 9-35)
        for (int i = 0; i < MAIN_SIZE - HOTBAR_SIZE; i++) {
            range[i] = i + HOTBAR_SIZE;
        }

        // 2. Offhand slot (40)
        range[MAIN_SIZE - HOTBAR_SIZE] = PlayerInventory.OFF_HAND_SLOT;

        // 3. Hotbar (slots 0-8)
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            range[i + MAIN_SIZE - HOTBAR_SIZE + 1] = i;
        }

        // Search inventory for elytra and chestplate
        for (int slot : range) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (!stack.isEmpty()) {
                // Take first found elytra and chestplate
                if (isElytra(stack) && elytraSlot < 0) {
                    elytraSlot = slot;
                } else if (isChestplate(stack) && chestplateSlot < 0) {
                    chestplateSlot = slot;
                }
            }
        }

        // Check what's currently equipped in chest slot (slot 38 = armor chest slot)
        ItemStack wornItemStack = client.player.getInventory().getStack(38);
        
        // Perform the appropriate swap based on what's equipped
        if (wornItemStack.isEmpty() && elytraSlot >= 0) {
            // Nothing worn → equip elytra
            sendSwapPackets(elytraSlot, client);
        }
        else if (isElytra(wornItemStack) && chestplateSlot >= 0) {
            // Elytra worn → swap to chestplate
            sendSwapPackets(chestplateSlot, client);
        }
        else if (isChestplate(wornItemStack) && elytraSlot >= 0) {
            // Chestplate worn → swap to elytra
            sendSwapPackets(elytraSlot, client);
        }
    }

    /**
     * Checks if an item stack is an elytra.
     * 
     * @param stack The item stack to check
     * @return true if the item is an elytra, false otherwise
     */
    private static boolean isElytra(ItemStack stack) {
        return stack.get(DataComponentTypes.GLIDER) != null;
    }

    /**
     * Checks if an item stack is a chestplate.
     * 
     * @param stack The item stack to check
     * @return true if the item is a chestplate, false otherwise
     */
    private static boolean isChestplate(ItemStack stack) {
        EquippableComponent equipped = stack.get(DataComponentTypes.EQUIPPABLE);
        return equipped != null && equipped.slot() == EquipmentSlot.CHEST;
    }

    /**
     * Sends the necessary packets to swap items between inventory and armor slots.
     * Simulates the following clicks:
     * 1. Click on the inventory item to pick it up
     * 2. Click on the chest armor slot (slot 6 in the UI) to place it
     * 3. Click again on the inventory slot to pick up the previous armor
     * 
     * @param slot The inventory slot containing the item to swap
     * @param client The Minecraft client instance
     */
    private static void sendSwapPackets(int slot, MinecraftClient client) {
        int sentSlot = slot;
        
        // Apply offsets to convert inventory slot numbers to UI slot numbers
        if (sentSlot == PlayerInventory.OFF_HAND_SLOT) {
            sentSlot += 5; // Off Hand offset
        }
        if (sentSlot < PlayerInventory.getHotbarSize()) {
            sentSlot += PlayerInventory.MAIN_SIZE; // Hotbar offset
        }

        // Perform the three clicks needed for the swap
        client.interactionManager.clickSlot(0, sentSlot, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(0, 6, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(0, sentSlot, 0, SlotActionType.PICKUP, client.player);
    }
}
