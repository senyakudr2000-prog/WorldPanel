package dev.worldpanel.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * A transparent, world-visible inventory panel. The actual menu remains the
 * server's menu; clicks are sent through the normal inventory interaction path.
 */
public final class PanelScreen extends Screen {
    private final Screen vanillaScreen;
    private final AbstractContainerMenu menu;
    private int minSlotX, minSlotY, maxSlotX, maxSlotY;
    private int panelX, panelY;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    public PanelScreen(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> original) {
        super(original.getTitle());
        this.vanillaScreen = original;
        this.menu = original.getMenu();
    }

    @Override
    protected void init() {
        minSlotX = Integer.MAX_VALUE;
        minSlotY = Integer.MAX_VALUE;
        maxSlotX = Integer.MIN_VALUE;
        maxSlotY = Integer.MIN_VALUE;
        for (Slot slot : menu.slots) {
            minSlotX = Math.min(minSlotX, slot.x);
            minSlotY = Math.min(minSlotY, slot.y);
            maxSlotX = Math.max(maxSlotX, slot.x + 16);
            maxSlotY = Math.max(maxSlotY, slot.y + 16);
        }
        if (menu.slots.isEmpty()) {
            minSlotX = minSlotY = 8;
            maxSlotX = 176;
            maxSlotY = 166;
        }
        int panelWidth = maxSlotX - minSlotX + 28;
        int panelHeight = maxSlotY - minSlotY + 48;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
    }

    private int slotScreenX(Slot slot) {
        return panelX + 14 + slot.x - minSlotX;
    }

    private int slotScreenY(Slot slot) {
        return panelY + 34 + slot.y - minSlotY;
    }

    private boolean insidePanel(double x, double y) {
        return x >= panelX && x < panelX + maxSlotX - minSlotX + 28
            && y >= panelY && y < panelY + maxSlotY - minSlotY + 48;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally do not draw the vanilla full-screen dim or opaque menu background.
        graphics.fill(panelX, panelY, panelX + maxSlotX - minSlotX + 28,
            panelY + 25, 0x990B1018);
        graphics.drawString(font, title, panelX + 14, panelY + 9, 0xFFFFFFFF, false);

        for (Slot slot : menu.slots) {
            int x = slotScreenX(slot);
            int y = slotScreenY(slot);
            boolean hovered = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
            graphics.fill(x, y, x + 18, y + 18, hovered ? 0xAA71879A : 0x77505B68);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x990B1119);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 1);
                graphics.renderItemDecorations(font, stack, x + 1, y + 1);
            }
        }

        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            graphics.renderItem(carried, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(font, carried, mouseX - 8, mouseY - 8);
        }

        if (insidePanel(mouseX, mouseY)) {
            for (Slot slot : menu.slots) {
                int x = slotScreenX(slot);
                int y = slotScreenY(slot);
                if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18
                    && !slot.getItem().isEmpty()) {
                    graphics.renderTooltip(font, slot.getItem(), mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (insidePanel(mouseX, mouseY)) {
            for (Slot slot : menu.slots) {
                int x = slotScreenX(slot);
                int y = slotScreenY(slot);
                if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                    if (minecraft.gameMode != null && minecraft.player != null) {
                        ClickType type = hasShiftDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP;
                        minecraft.gameMode.handleInventoryMouseClick(menu.containerId,
                            slot.index, button, type, minecraft.player);
                    }
                    return true;
                }
            }
            return true;
        }
        setMouseKey(button, true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        setMouseKey(button, false);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return insidePanel(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return insidePanel(mouseX, mouseY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!Double.isNaN(lastMouseX) && !insidePanel(mouseX, mouseY)
            && minecraft != null && minecraft.player != null) {
            double sensitivity = minecraft.options.sensitivity().get();
            double scale = Math.pow(sensitivity * 0.6 + 0.2, 3.0) * 8.0 * 0.15;
            minecraft.player.turn((mouseX - lastMouseX) * scale, (mouseY - lastMouseY) * scale);
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (movementKey(key)) {
            setMovementKey(key, true);
            return true;
        }
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (movementKey(key)) {
            setMovementKey(key, false);
            return true;
        }
        return true;
    }

    private boolean movementKey(int key) {
        return key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A
            || key == GLFW.GLFW_KEY_S || key == GLFW.GLFW_KEY_D
            || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_LEFT_SHIFT
            || key == GLFW.GLFW_KEY_LEFT_CONTROL;
    }

    private void setMovementKey(int key, boolean down) {
        if (minecraft == null) return;
        KeyMapping mapping = switch (key) {
            case GLFW.GLFW_KEY_W -> minecraft.options.keyUp;
            case GLFW.GLFW_KEY_A -> minecraft.options.keyLeft;
            case GLFW.GLFW_KEY_S -> minecraft.options.keyDown;
            case GLFW.GLFW_KEY_D -> minecraft.options.keyRight;
            case GLFW.GLFW_KEY_SPACE -> minecraft.options.keyJump;
            case GLFW.GLFW_KEY_LEFT_SHIFT -> minecraft.options.keyShift;
            case GLFW.GLFW_KEY_LEFT_CONTROL -> minecraft.options.keySprint;
            default -> null;
        };
        if (mapping != null) mapping.setDown(down);
    }

    private void setMouseKey(int button, boolean down) {
        if (minecraft == null) return;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) minecraft.options.keyAttack.setDown(down);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) minecraft.options.keyUse.setDown(down);
    }

    @Override
    public void onClose() {
        vanillaScreen.onClose();
        if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
    }

    @Override
    public void removed() {
        for (int key : new int[] { GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S,
            GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT,
            GLFW.GLFW_KEY_LEFT_CONTROL }) setMovementKey(key, false);
        setMouseKey(GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        setMouseKey(GLFW.GLFW_MOUSE_BUTTON_RIGHT, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
