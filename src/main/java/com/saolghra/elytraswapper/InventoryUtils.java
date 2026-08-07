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
/*import net.minecraft.world.inventory.ContainerInput;
*///?} else {
import net.minecraft.world.inventory.ClickType;
//?}

/**
 * Swaps a worn Elytra for a Chestplate and back.
 *
 * Only two things here vary across Minecraft 1.20 - 26.2: how an Elytra/Chestplate is recognised
 * (1.21.2 replaced the ElytraItem/ArmorItem classes with data components) and the name of the
 * container-click API (26.1 renamed ClickType to ContainerInput). Everything else — the Inventory
 * slot constants, getItemBySlot, getInventory().getItem — is identical across the whole range.
 * Both boundaries were verified against the Mojmap jars for every release in the range.
 *
 * The slot arithmetic and the decide-what-to-equip rules live in {@link SwapLogic}, which has no
 * Minecraft types in it and so can be unit-tested without a running game. What is left here is
 * exactly the part that needs one.
 */
public final class InventoryUtils {

    private InventoryUtils() {
    }

    public static void swapChestplate(Minecraft client) {
        // Minecraft.player is already declared LocalPlayer, so an instanceof pattern here is
        // provably always-true and is a hard compile error on the older nodes. Only nullability and
        // liveness actually need checking.
        LocalPlayer player = client.player;
        if (player == null || !player.isAlive()) {
            return;
        }

        int elytraSlot = SwapLogic.NO_SLOT;
        int chestplateSlot = SwapLogic.NO_SLOT;

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

        int equip = SwapLogic.slotToEquip(
                worn.isEmpty(), isElytra(worn), isChestplate(worn), elytraSlot, chestplateSlot);
        if (equip != SwapLogic.NO_SLOT) {
            sendSwapPackets(equip, client, player);
        }
    }

    /** Inventory slot indices in the order they should be considered. */
    private static int[] searchOrder() {
        return SwapLogic.searchOrder(
                Inventory.getSelectionSize(), Inventory.INVENTORY_SIZE, Inventory.SLOT_OFFHAND);
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
        int uiSlot = SwapLogic.containerSlot(
                slot, Inventory.getSelectionSize(), Inventory.INVENTORY_SIZE, Inventory.SLOT_OFFHAND);
        int chestSlot = SwapLogic.UI_CHEST_SLOT;

        Player target = Objects.requireNonNull(player);
        //? if >=26.1 {
        /*gameMode.handleContainerInput(0, uiSlot, 0, ContainerInput.PICKUP, target);
        gameMode.handleContainerInput(0, chestSlot, 0, ContainerInput.PICKUP, target);
        gameMode.handleContainerInput(0, uiSlot, 0, ContainerInput.PICKUP, target);
        *///?} else {
        gameMode.handleInventoryMouseClick(0, uiSlot, 0, ClickType.PICKUP, target);
        gameMode.handleInventoryMouseClick(0, chestSlot, 0, ClickType.PICKUP, target);
        gameMode.handleInventoryMouseClick(0, uiSlot, 0, ClickType.PICKUP, target);
        //?}
    }
}
