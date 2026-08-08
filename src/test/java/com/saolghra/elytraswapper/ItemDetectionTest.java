package com.saolghra.elytraswapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Recognition tests against real ItemStacks, on whichever Minecraft version this node targets.
 *
 * This is the half of the mod that {@link SwapLogicTest} cannot reach and the jar audit can only
 * check by name. isElytra/isChestplate are rewritten wholesale at 1.21.2 — class checks became data
 * component lookups — and "both arms compile" says nothing about both arms *agreeing*. Booting the
 * registries is enough to build real stacks; no window and no world are needed.
 */
class ItemDetectionTest {

    private static boolean stacksAvailable;
    private static String unavailableReason;

    @BeforeAll
    static void bootMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // 26.x binds item components through a DataComponentLookup that Bootstrap.bootStrap() alone
        // does not populate, so building an ItemStack there throws "Components not bound yet".
        // Probed rather than version-gated: the day that init path changes, these start running
        // again on their own instead of staying switched off because of a stale version check.
        try {
            new ItemStack(Items.ELYTRA);
            stacksAvailable = true;
        } catch (Throwable t) {
            unavailableReason = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    /**
     * Skips rather than passes when stacks cannot be built. A green tick for a test that never ran
     * is worse than an amber one — the same >=1.21.2 source arm is exercised for real on 1.21.2
     * through 1.21.11, so the logic still has genuine coverage.
     */
    @BeforeEach
    void requireRealItemStacks() {
        assumeTrue(stacksAvailable,
                () -> "ItemStacks cannot be built on this version in a bare test JVM — " + unavailableReason);
    }

    @Test
    @DisplayName("an Elytra is an Elytra")
    void elytraIsElytra() {
        assertTrue(InventoryUtils.isElytra(new ItemStack(Items.ELYTRA)));
    }

    @Test
    @DisplayName("a chestplate is a chestplate")
    void chestplateIsChestplate() {
        assertTrue(InventoryUtils.isChestplate(new ItemStack(Items.DIAMOND_CHESTPLATE)));
        assertTrue(InventoryUtils.isChestplate(new ItemStack(Items.LEATHER_CHESTPLATE)));
    }

    @Test
    @DisplayName("a chestplate is not an Elytra")
    void chestplateIsNotElytra() {
        assertFalse(InventoryUtils.isElytra(new ItemStack(Items.DIAMOND_CHESTPLATE)));
    }

    /**
     * The one that actually differs between versions.
     *
     * An Elytra IS equippable in the chest slot, so the 1.21.2+ component check matches it unless
     * gliders are excluded — while the pre-1.21.2 check never did, because ElytraItem was not an
     * ArmorItem. Left alone, the same inventory behaves differently either side of 1.21.2.
     */
    @Test
    @DisplayName("an Elytra is not treated as a chestplate")
    void elytraIsNotChestplate() {
        assertFalse(InventoryUtils.isChestplate(new ItemStack(Items.ELYTRA)),
                "an Elytra counting as a chestplate makes the mod swap an Elytra for another Elytra");
    }

    @Test
    @DisplayName("armour for other slots is left alone")
    void otherArmourSlotsAreIgnored() {
        for (var item : new net.minecraft.world.item.Item[] {
                Items.DIAMOND_HELMET, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS }) {
            ItemStack stack = new ItemStack(item);
            assertFalse(InventoryUtils.isChestplate(stack), item + " is not a chestplate");
            assertFalse(InventoryUtils.isElytra(stack), item + " is not an Elytra");
        }
    }

    @Test
    @DisplayName("ordinary items are neither")
    void ordinaryItemsAreNeither() {
        for (var item : new net.minecraft.world.item.Item[] {
                Items.DIAMOND_SWORD, Items.SHIELD, Items.STONE, Items.BREAD }) {
            ItemStack stack = new ItemStack(item);
            assertFalse(InventoryUtils.isElytra(stack), item + " is not an Elytra");
            assertFalse(InventoryUtils.isChestplate(stack), item + " is not a chestplate");
        }
    }

    @Test
    @DisplayName("an empty slot is neither")
    void emptyIsNeither() {
        assertFalse(InventoryUtils.isElytra(ItemStack.EMPTY));
        assertFalse(InventoryUtils.isChestplate(ItemStack.EMPTY));
    }
}
