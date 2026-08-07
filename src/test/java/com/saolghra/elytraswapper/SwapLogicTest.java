package com.saolghra.elytraswapper;

import static com.saolghra.elytraswapper.SwapLogic.NO_SLOT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * These run without a game: SwapLogic deliberately has no Minecraft types in it, so the interesting
 * decisions can be pinned down here instead of only in a live client.
 */
class SwapLogicTest {

    // Vanilla values, unchanged across the whole 1.20 - 26.2 range. Passed in explicitly rather than
    // read from Inventory so this stays runtime-free.
    private static final int HOTBAR = 9;
    private static final int INVENTORY = 36;
    private static final int OFFHAND = 40;

    private static int[] order() {
        return SwapLogic.searchOrder(HOTBAR, INVENTORY, OFFHAND);
    }

    @Nested
    @DisplayName("searchOrder")
    class SearchOrder {

        @Test
        @DisplayName("visits every slot the player can hold an item in, exactly once")
        void coversEverySlotOnce() {
            int[] actual = order().clone();
            Arrays.sort(actual);

            int[] expected = IntStream.concat(IntStream.range(0, INVENTORY), IntStream.of(OFFHAND))
                    .toArray();
            assertArrayEquals(expected, actual);
        }

        @Test
        @DisplayName("checks the main inventory, then the offhand, then the hotbar")
        void ordersMainThenOffhandThenHotbar() {
            int[] order = order();

            int offhandAt = indexOf(order, OFFHAND);
            for (int i = 0; i < offhandAt; i++) {
                assertTrue(order[i] >= HOTBAR && order[i] < INVENTORY,
                        "expected only main-inventory slots before the offhand, found " + order[i]);
            }
            for (int i = offhandAt + 1; i < order.length; i++) {
                assertTrue(order[i] < HOTBAR,
                        "expected only hotbar slots after the offhand, found " + order[i]);
            }
        }

        @Test
        @DisplayName("takes from the hotbar last, so a held item is the last thing consumed")
        void hotbarIsLast() {
            int[] order = order();
            for (int i = 0; i < HOTBAR; i++) {
                assertTrue(order[order.length - 1 - i] < HOTBAR);
            }
        }

        private int indexOf(int[] array, int value) {
            for (int i = 0; i < array.length; i++) {
                if (array[i] == value) {
                    return i;
                }
            }
            throw new AssertionError(value + " not present in " + Arrays.toString(array));
        }
    }

    @Nested
    @DisplayName("containerSlot")
    class ContainerSlot {

        private int map(int slot) {
            return SwapLogic.containerSlot(slot, HOTBAR, INVENTORY, OFFHAND);
        }

        @Test
        @DisplayName("leaves main-inventory slots where they are")
        void mainInventoryIsIdentity() {
            for (int slot = HOTBAR; slot < INVENTORY; slot++) {
                assertEquals(slot, map(slot));
            }
        }

        @Test
        @DisplayName("moves the hotbar to the end of the container")
        void hotbarMovesToTheEnd() {
            assertEquals(36, map(0));
            assertEquals(44, map(HOTBAR - 1));
        }

        @Test
        @DisplayName("puts the offhand past the hotbar")
        void offhandGoesLast() {
            assertEquals(45, map(OFFHAND));
        }

        @Test
        @DisplayName("never maps two different slots onto the same container slot")
        void isInjective() {
            int[] mapped = Arrays.stream(order()).map(this::map).sorted().toArray();
            assertEquals(mapped.length, Arrays.stream(mapped).distinct().count(),
                    "two inventory slots collided on one container slot: " + Arrays.toString(mapped));
        }

        @Test
        @DisplayName("never collides with the chest slot it clicks against")
        void neverCollidesWithTheChestSlot() {
            for (int slot : order()) {
                assertTrue(map(slot) != SwapLogic.UI_CHEST_SLOT,
                        "inventory slot " + slot + " maps onto the chest slot");
            }
        }
    }

    @Nested
    @DisplayName("slotToEquip")
    class SlotToEquip {

        private static final int ELYTRA = 12;
        private static final int CHESTPLATE = 27;

        @Test
        @DisplayName("wearing nothing, puts on the Elytra")
        void bareChestEquipsElytra() {
            assertEquals(ELYTRA, SwapLogic.slotToEquip(true, false, false, ELYTRA, CHESTPLATE));
        }

        @Test
        @DisplayName("wearing nothing with no Elytra to hand, does nothing")
        void bareChestWithoutElytraDoesNothing() {
            assertEquals(NO_SLOT, SwapLogic.slotToEquip(true, false, false, NO_SLOT, CHESTPLATE));
        }

        @Test
        @DisplayName("wearing the Elytra, swaps back to a chestplate")
        void elytraSwapsToChestplate() {
            assertEquals(CHESTPLATE, SwapLogic.slotToEquip(false, true, false, ELYTRA, CHESTPLATE));
        }

        @Test
        @DisplayName("wearing the Elytra with no chestplate to hand, keeps the Elytra on")
        void elytraWithoutChestplateDoesNothing() {
            assertEquals(NO_SLOT, SwapLogic.slotToEquip(false, true, false, ELYTRA, NO_SLOT));
        }

        @Test
        @DisplayName("wearing a chestplate, swaps to the Elytra")
        void chestplateSwapsToElytra() {
            assertEquals(ELYTRA, SwapLogic.slotToEquip(false, false, true, ELYTRA, CHESTPLATE));
        }

        @Test
        @DisplayName("wearing a chestplate with no Elytra to hand, keeps the chestplate on")
        void chestplateWithoutElytraDoesNothing() {
            assertEquals(NO_SLOT, SwapLogic.slotToEquip(false, false, true, NO_SLOT, CHESTPLATE));
        }

        @Test
        @DisplayName("wearing something that is neither, leaves it alone")
        void unknownChestItemIsLeftAlone() {
            assertEquals(NO_SLOT, SwapLogic.slotToEquip(false, false, false, ELYTRA, CHESTPLATE));
        }
    }
}
