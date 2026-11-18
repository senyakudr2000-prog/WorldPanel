package dev.worldpanel.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Wraps a vanilla menu while keeping the game unpaused and visible. */
public final class PanelScreen extends Screen {
    private final Screen content;
    private boolean initialized;

    public PanelScreen(Screen content) {
        super(Component.literal("WorldPanel"));
        this.content = content;
    }

    @Override
    protected void init() {
        if (!initialized) {
            content.init(minecraft, width, height);
            initialized = true;
        } else {
            content.resize(minecraft, width, height);
        }
    }

    @Override public void tick() { content.tick(); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        content.renderWithTooltip(graphics, mouseX, mouseY, partialTick);
    }

    private boolean overPanel(double x, double y) {
        int hw = Math.min(220, width / 2);
        int hh = Math.min(170, height / 2);
        return x >= width / 2.0 - hw && x <= width / 2.0 + hw
            && y >= height / 2.0 - hh && y <= height / 2.0 + hh;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (overPanel(x, y)) return content.mouseClicked(x, y, button);
        setMouseKey(button, true);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (overPanel(x, y)) return content.mouseReleased(x, y, button);
        setMouseKey(button, false);
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return overPanel(x, y) && content.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return overPanel(x, y) && content.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (movementKey(key)) {
            setMovementKey(key, true);
            return true;
        }
        return content.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (movementKey(key)) {
            setMovementKey(key, false);
            return true;
        }
        return content.keyReleased(key, scanCode, modifiers);
    }

    @Override public boolean charTyped(char c, int modifiers) {
        return content.charTyped(c, modifiers);
    }

    private boolean movementKey(int key) {
        return key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A
            || key == GLFW.GLFW_KEY_S || key == GLFW.GLFW_KEY_D
            || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_LEFT_SHIFT
            || key == GLFW.GLFW_KEY_LEFT_CONTROL;
    }

    private void setMovementKey(int key, boolean down) {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping mapping = switch (key) {
            case GLFW.GLFW_KEY_W -> mc.options.keyUp;
            case GLFW.GLFW_KEY_A -> mc.options.keyLeft;
            case GLFW.GLFW_KEY_S -> mc.options.keyDown;
            case GLFW.GLFW_KEY_D -> mc.options.keyRight;
            case GLFW.GLFW_KEY_SPACE -> mc.options.keyJump;
            case GLFW.GLFW_KEY_LEFT_SHIFT -> mc.options.keyShift;
            case GLFW.GLFW_KEY_LEFT_CONTROL -> mc.options.keySprint;
            default -> null;
        };
        if (mapping != null) mapping.setDown(down);
    }

    private void setMouseKey(int button, boolean down) {
        Minecraft mc = Minecraft.getInstance();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) mc.options.keyAttack.setDown(down);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) mc.options.keyUse.setDown(down);
    }

    @Override
    public void onClose() {
        content.onClose();
        if (Minecraft.getInstance().screen == this) Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        content.removed();
        for (int key : new int[] { GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S,
                GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT,
                GLFW.GLFW_KEY_LEFT_CONTROL }) setMovementKey(key, false);
        setMouseKey(GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        setMouseKey(GLFW.GLFW_MOUSE_BUTTON_RIGHT, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
