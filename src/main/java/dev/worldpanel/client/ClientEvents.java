package dev.worldpanel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "worldpanel", value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean open;
    private static AbstractContainerMenu menu;
    private static net.minecraft.network.chat.Component title;
    private static int minX, minY, maxX, maxY;
    private static int panelX, panelY;
    private static double lastMouseX = Double.NaN;
    private static double lastMouseY = Double.NaN;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getNewScreen() instanceof PauseScreen && open) {
            event.setCanceled(true);
            closePanel();
            return;
        }
        if (event.getNewScreen() instanceof AbstractContainerScreen<?> screen) {
            AbstractContainerMenu nextMenu = screen.getMenu();
            if (open && nextMenu instanceof InventoryMenu) {
                event.setCanceled(true);
                closePanel();
                return;
            }
            menu = nextMenu;
            title = screen.getTitle();
            open = true;
            calculateBounds();
            event.setCanceled(true);
            if (mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
            lastMouseX = lastMouseY = Double.NaN;
        }
    }

    private static void calculateBounds() {
        minX = Integer.MAX_VALUE;
        minY = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        maxY = Integer.MIN_VALUE;
        if (menu == null) return;
        for (Slot slot : menu.slots) {
            minX = Math.min(minX, slot.x);
            minY = Math.min(minY, slot.y);
            maxX = Math.max(maxX, slot.x + 16);
            maxY = Math.max(maxY, slot.y + 16);
        }
        if (menu.slots.isEmpty()) {
            minX = minY = 8;
            maxX = 176;
            maxY = 166;
        }
    }

    private static int panelWidth() { return maxX - minX + 32; }
    private static int panelHeight() { return maxY - minY + 54; }
    private static int slotX(Slot slot) { return panelX + 16 + slot.x - minX; }
    private static int slotY(Slot slot) { return panelY + 38 + slot.y - minY; }

    private static double mouseX(Minecraft mc) {
        return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
    }
    private static double mouseY(Minecraft mc) {
        return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
    }

    private static boolean inPanel(double x, double y) {
        return x >= panelX && x < panelX + panelWidth()
            && y >= panelY && y < panelY + panelHeight();
    }

    @SubscribeEvent
    public static void renderPanel(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!open || menu == null || mc.player == null || mc.level == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        panelX = (w - panelWidth()) / 2;
        panelY = (h - panelHeight()) / 2;

        g.fill(panelX, panelY, panelX + panelWidth(), panelY + panelHeight(), 0xB80B111A);
        g.fill(panelX, panelY, panelX + panelWidth(), panelY + 25, 0xE51B2938);
        g.drawString(mc.font, title == null ? "Inventory" : title, panelX + 12, panelY + 9, 0xFFFFFFFF, false);
        g.fill(panelX + 7, panelY + 29, panelX + panelWidth() - 7, panelY + 30, 0x667D9AB5);

        int mx = (int) mouseX(mc);
        int my = (int) mouseY(mc);
        for (Slot slot : menu.slots) {
            int x = slotX(slot);
            int y = slotY(slot);
            boolean hovered = mx >= x && mx < x + 18 && my >= y && my < y + 18;
            g.fill(x, y, x + 18, y + 18, hovered ? 0xDD91AFC9 : 0xAA344454);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0xAA101923);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 1, y + 1);
                g.renderItemDecorations(mc.font, stack, x + 1, y + 1);
            }
        }
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            g.renderItem(carried, mx - 8, my - 8);
            g.renderItemDecorations(mc.font, carried, mx - 8, my - 8);
        }
        if (inPanel(mx, my)) {
            for (Slot slot : menu.slots) {
                int x = slotX(slot), y = slotY(slot);
                if (mx >= x && mx < x + 18 && my >= y && my < y + 18 && !slot.getItem().isEmpty()) {
                    g.renderTooltip(mc.font, slot.getItem(), mx, my);
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!open || menu == null || mc.player == null) return;
        double mx = mouseX(mc), my = mouseY(mc);
        if (!inPanel(mx, my)) return;
        event.setCanceled(true);
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        for (Slot slot : menu.slots) {
            int x = slotX(slot), y = slotY(slot);
            if (mx >= x && mx < x + 18 && my >= y && my < y + 18) {
                if (mc.gameMode != null) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index,
                        event.getButton(), hasShiftDown(mc) ? net.minecraft.world.inventory.ClickType.QUICK_MOVE
                            : net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                }
                return;
            }
        }
    }

    private static boolean hasShiftDown(Minecraft mc) {
        return GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!open || mc.player == null || mc.screen != null) return;
        double x = mouseX(mc), y = mouseY(mc);
        if (!Double.isNaN(lastMouseX) && !inPanel(x, y)) {
            double dx = x - lastMouseX;
            double dy = y - lastMouseY;
            if (Math.abs(dx) + Math.abs(dy) < 120) mc.player.turn(dx * 0.5, dy * 0.5);
        }
        lastMouseX = x;
        lastMouseY = y;
    }

    private static void closePanel() {
        Minecraft mc = Minecraft.getInstance();
        open = false;
        lastMouseX = lastMouseY = Double.NaN;
        if (mc.player != null && menu != null && menu != mc.player.inventoryMenu) {
            mc.player.closeContainer();
        }
        menu = null;
        title = null;
        if (mc.mouseHandler != null && !mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
    }
}
