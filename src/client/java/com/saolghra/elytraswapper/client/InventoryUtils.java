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

public class InventoryUtils {

    @Environment(net.fabricmc.api.EnvType.CLIENT)
    public static void swapChestplate(MinecraftClient client){

        if (client.player == null) return;
        if (!(client.player instanceof ClientPlayerEntity)) return;
        if (client.player.isDead()) return;

        int elytraSlot = -1;
        int chestplateSlot = -1;

        int HOTBAR_SIZE = PlayerInventory.getHotbarSize(); // 9
        int MAIN_SIZE = PlayerInventory.MAIN_SIZE; // 36
        int TOTAL_SIZE = MAIN_SIZE + 1; // 37

        int[] range = new int[TOTAL_SIZE];

        // Main inventory
        for (int i = 0; i < MAIN_SIZE - HOTBAR_SIZE; i++) {
            range[i] = i + HOTBAR_SIZE;
        }

        // Offhand
        range[MAIN_SIZE - HOTBAR_SIZE] = PlayerInventory.OFF_HAND_SLOT; // 40

        // Hotbar
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            range[i + MAIN_SIZE - HOTBAR_SIZE + 1] = i;
        }

        for (int slot : range) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (stack.isEmpty()) {
                continue;
            } else {
                if (isElytra(stack) && elytraSlot < 0) {
                    elytraSlot = slot;
                } else if (isChestplate(stack) && chestplateSlot < 0) {
                    chestplateSlot = slot;
                }
            }
        }

        ItemStack wornItemStack = client.player.getInventory().getStack(38);
        if (wornItemStack.isEmpty() && elytraSlot >= 0) {
            sendSwapPackets(elytraSlot, client);
        }
        else if (isElytra(wornItemStack) && chestplateSlot >= 0) {
            sendSwapPackets(chestplateSlot, client);
        }
        else if (isChestplate(wornItemStack) && elytraSlot >= 0) {
            sendSwapPackets(elytraSlot, client);
        }
    }

    private static boolean isElytra(ItemStack stack) {
        // NEW: check for elytra component
        return stack.get(DataComponentTypes.GLIDER) != null;
    }

    private static boolean isChestplate(ItemStack stack) {
        EquippableComponent equipped = stack.get(DataComponentTypes.EQUIPPABLE);
        return equipped != null && equipped.slot() == EquipmentSlot.CHEST;
    }

    private static void sendSwapPackets(int slot, MinecraftClient client) {
        int sentSlot = slot;
        if (sentSlot == PlayerInventory.OFF_HAND_SLOT) sentSlot += 5; // Off Hand offset
        if (sentSlot < PlayerInventory.getHotbarSize()) sentSlot += PlayerInventory.MAIN_SIZE; // Toolbar offset

        client.interactionManager.clickSlot(0, sentSlot, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(0, 6, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(0, sentSlot, 0, SlotActionType.PICKUP, client.player);
    }
}
