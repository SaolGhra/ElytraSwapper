package com.saolghra.elytraswapper;

/**
 * The parts of the swap that are pure arithmetic and pure decision-making, with no Minecraft types
 * anywhere in the signatures.
 *
 * Split out from {@link InventoryUtils} so it can be unit-tested without a game runtime. The
 * numbers it works with — hotbar size, inventory size, offhand index — have not changed anywhere in
 * 1.20 to 26.2, but they are passed in rather than hardcoded so a future change shows up as a
 * failing caller rather than a silently wrong slot.
 */
public final class SwapLogic {

    /** Returned when no swap should happen. */
    public static final int NO_SLOT = -1;

    /** The chest armour slot's index in the player's inventory container UI. */
    public static final int UI_CHEST_SLOT = 6;

    private SwapLogic() {
    }

    /**
     * Inventory slot indices in the order they should be considered.
     *
     * Main inventory first, then the offhand, then the hotbar. Taking from the hotbar last means a
     * hotbar item is only consumed when nothing else holds a candidate — otherwise pressing the key
     * would quietly swallow whatever the player was holding.
     */
    public static int[] searchOrder(int hotbarSize, int inventorySize, int offhandSlot) {
        int[] order = new int[inventorySize + 1];
        int i = 0;
        for (int slot = hotbarSize; slot < inventorySize; slot++) {
            order[i++] = slot;
        }
        order[i++] = offhandSlot;
        for (int slot = 0; slot < hotbarSize; slot++) {
            order[i++] = slot;
        }
        return order;
    }

    /**
     * Translates an inventory slot index into the matching slot index in the player's inventory
     * container UI. The two are not the same numbering: the hotbar sits at the end of the container
     * and the offhand is past it again.
     */
    public static int containerSlot(int inventorySlot, int hotbarSize, int inventorySize, int offhandSlot) {
        if (inventorySlot == offhandSlot) {
            return inventorySlot + 5;
        }
        if (inventorySlot < hotbarSize) {
            return inventorySlot + inventorySize;
        }
        return inventorySlot;
    }

    /**
     * Decides which inventory slot to swap into the chest slot, or {@link #NO_SLOT} for no swap.
     *
     * Wearing nothing means put the Elytra on. Wearing the Elytra means go back to a chestplate.
     * Wearing a chestplate means go to the Elytra. Wearing anything else — some other mod's chest
     * item — is left alone rather than guessed at.
     */
    public static int slotToEquip(boolean wornEmpty, boolean wornIsElytra, boolean wornIsChestplate,
                                  int elytraSlot, int chestplateSlot) {
        if (wornEmpty) {
            return elytraSlot;
        }
        if (wornIsElytra) {
            return chestplateSlot;
        }
        if (wornIsChestplate) {
            return elytraSlot;
        }
        return NO_SLOT;
    }
}
