package com.saolghra.elytraswapper;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

//? if >=1.21.2 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.equipment.Equippable;
//?} else {
/*import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ElytraItem;
*///?}

//? if >=26.1 {
import net.minecraft.world.inventory.ContainerInput;
//?} else {
/*import net.minecraft.world.inventory.ClickType;
*///?}

/**
 * Swaps a worn Elytra for a Chestplate and back.
 *
 * Only two things here vary across Minecraft 1.20 - 26.2: how an Elytra/Chestplate is recognised
 * (1.21.2 replaced the ElytraItem/ArmorItem classes with data components) and the name of the
 * container-click API (26.1 renamed ClickType to ContainerInput). Everything else — the Inventory
 * slot constants, getItemBySlot, getInventory().getItem — is identical across the whole range.
 * Both boundaries were verified against the Mojmap jars for every release in the range.
 */
public final class InventoryUtils {

    private InventoryUtils() {
    }

    /** The chest armour slot's index in the player's inventory container UI. */
    private static final int UI_CHEST_SLOT = 6;

    public static void swapChestplate(Minecraft client) {
        // Minecraft.player is already declared LocalPlayer, so an instanceof pattern here is
        // provably always-true and is a hard compile error on the older nodes. Only nullability and
        // liveness actually need checking.
        LocalPlayer player = client.player;
        if (player == null || !player.isAlive()) {
            return;
        }

        int elytraSlot = -1;
        int chestplateSlot = -1;

        // Search order matters: main inventory first, then offhand, then hotbar. Taking from the
        // hotbar last means a hotbar item is only consumed when nothing else holds a candidate.
        for (int slot : searchOrder()) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (elytraSlot < 0 && isElytra(stack)) {
                elytraSlot = slot;
            } else if (chestplateSlot < 0 && isChestplate(stack)) {
                chestplateSlot = slot;
            }
        }

        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);

        if (worn.isEmpty() && elytraSlot >= 0) {
            sendSwapPackets(elytraSlot, client, player);
        } else if (isElytra(worn) && chestplateSlot >= 0) {
            sendSwapPackets(chestplateSlot, client, player);
        } else if (isChestplate(worn) && elytraSlot >= 0) {
            sendSwapPackets(elytraSlot, client, player);
        }
    }

    /** Inventory slot indices in the order they should be considered. */
    private static int[] searchOrder() {
        final int hotbar = Inventory.getSelectionSize();
        final int main = Inventory.INVENTORY_SIZE;
        int[] order = new int[main + 1];
        int i = 0;
        for (int slot = hotbar; slot < main; slot++) {
            order[i++] = slot;
        }
        order[i++] = Inventory.SLOT_OFFHAND;
        for (int slot = 0; slot < hotbar; slot++) {
            order[i++] = slot;
        }
        return order;
    }

    private static boolean isElytra(ItemStack stack) {
        //? if >=1.21.2 {
        return stack.get(DataComponents.GLIDER) != null;
        //?} else {
        /*return stack.getItem() instanceof ElytraItem;
        *///?}
    }

    private static boolean isChestplate(ItemStack stack) {
        //? if >=1.21.2 {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
        //?} else {
        /*return stack.getItem() instanceof ArmorItem armor
                && armor.getEquipmentSlot() == EquipmentSlot.CHEST;
        *///?}
    }

    /**
     * Performs the swap as three simulated container clicks — pick up the replacement, drop it into
     * the chest slot (picking up whatever was worn), then put that back where the replacement came
     * from.
     *
     * Deliberately not a direct inventory mutation: inventory state is server-authoritative, and
     * assigning stacks client-side desyncs immediately in multiplayer. Do not "simplify" this.
     */
    private static void sendSwapPackets(int slot, Minecraft client, Player player) {
        MultiPlayerGameMode gameMode = client.gameMode;
        if (gameMode == null) {
            return;
        }

        // Inventory indices and container-UI indices are not the same numbering.
        int uiSlot = slot;
        if (uiSlot == Inventory.SLOT_OFFHAND) {
            uiSlot += 5;
        }
        if (uiSlot < Inventory.getSelectionSize()) {
            uiSlot += Inventory.INVENTORY_SIZE;
        }

        Player target = Objects.requireNonNull(player);
        //? if >=26.1 {
        gameMode.handleContainerInput(0, uiSlot, 0, ContainerInput.PICKUP, target);
        gameMode.handleContainerInput(0, UI_CHEST_SLOT, 0, ContainerInput.PICKUP, target);
        gameMode.handleContainerInput(0, uiSlot, 0, ContainerInput.PICKUP, target);
        //?} else {
        /*gameMode.handleInventoryMouseClick(0, uiSlot, 0, ClickType.PICKUP, target);
        gameMode.handleInventoryMouseClick(0, UI_CHEST_SLOT, 0, ClickType.PICKUP, target);
        gameMode.handleInventoryMouseClick(0, uiSlot, 0, ClickType.PICKUP, target);
        *///?}
    }
}
