package dev.worldpanel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "worldpanel", value = Dist.CLIENT)
public final class ClientEvents {
    private static AbstractContainerScreen<?> panelScreen;
    private static Vec3 anchor;
    private static Quaternionf anchorRotation;
    private static boolean open;
    private static boolean lastE;
    private static boolean lastEscape;
    private static boolean mouseButtonHeld;
    private static int heldButton = -1;
    private static double heldMouseX;
    private static double heldMouseY;
    private static final float WORLD_SCALE = 0.0025F;
    private static final double PANEL_DISTANCE = 2.35D;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Keep the real vanilla screen/menu object. It is rendered on a world-space plane
        // instead of being installed as Minecraft's active fullscreen screen.
        panelScreen = screen;
        panelScreen.resize(mc, mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());

        var camera = mc.gameRenderer.getMainCamera();
        Vec3 look = new Vec3(camera.getLookVector().x, camera.getLookVector().y,
                camera.getLookVector().z);
        anchor = camera.getPosition().add(look.scale(PANEL_DISTANCE));
        anchorRotation = new Quaternionf(camera.rotation());
        open = true;
        long window = mc.getWindow().getWindow();
        lastE = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_E) == GLFW.GLFW_PRESS;
        lastEscape = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void renderWorldPanel(RenderLevelStageEvent event) {
        if (!open || panelScreen == null || anchor == null || anchorRotation == null) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            closePanel();
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        graphics.pose().mulPoseMatrix(event.getModelViewMatrix());
        graphics.pose().translate(anchor.x - camera.x, anchor.y - camera.y,
                anchor.z - camera.z);
        graphics.pose().mulPose(anchorRotation);
        graphics.pose().scale(WORLD_SCALE, -WORLD_SCALE, WORLD_SCALE);
        graphics.pose().translate(-panelScreen.width / 2.0F, -panelScreen.height / 2.0F, 0.0F);

        panelScreen.renderWithTooltip(graphics, -10000, -10000,
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
        graphics.flush();
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!open || panelScreen == null) return;
        if (event.getButton() < 0 || event.getButton() > 2) return;

        if (event.getAction() == GLFW.GLFW_RELEASE && mouseButtonHeld
                && heldButton == event.getButton()) {
            event.setCanceled(true);
            panelScreen.mouseReleased(heldMouseX, heldMouseY, event.getButton());
            mouseButtonHeld = false;
            heldButton = -1;
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        double[] point = intersectPanel();
        if (point == null) return;

        event.setCanceled(true);
        heldMouseX = point[0];
        heldMouseY = point[1];
        mouseButtonHeld = panelScreen.mouseClicked(point[0], point[1], event.getButton());
        heldButton = mouseButtonHeld ? event.getButton() : -1;
    }

    private static double[] intersectPanel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return null;
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 origin = camera.getPosition();
        Vector3f directionVector = new Vector3f(camera.getLookVector());
        Vec3 direction = new Vec3(directionVector.x, directionVector.y, directionVector.z);

        Vector3f normalVector = new Vector3f(0.0F, 0.0F, 1.0F).rotate(anchorRotation);
        Vec3 normal = new Vec3(normalVector.x, normalVector.y, normalVector.z);
        double denominator = direction.dot(normal);
        if (Math.abs(denominator) < 1.0E-5) return null;

        double distance = anchor.subtract(origin).dot(normal) / denominator;
        if (distance <= 0.0D || distance > 8.0D) return null;

        Vec3 hit = origin.add(direction.scale(distance));
        Vec3 relative = hit.subtract(anchor);
        Vector3f local = new Vector3f((float) relative.x, (float) relative.y,
                (float) relative.z).rotate(new Quaternionf(anchorRotation).invert());

        double x = local.x / WORLD_SCALE + panelScreen.width / 2.0D;
        double y = -local.y / WORLD_SCALE + panelScreen.height / 2.0D;
        if (x < 0 || y < 0 || x >= panelScreen.width || y >= panelScreen.height) return null;
        return new double[] { x, y };
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (!open || panelScreen == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            closePanel();
            return;
        }

        panelScreen.tick();
        long window = mc.getWindow().getWindow();
        boolean eDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_E) == GLFW.GLFW_PRESS;
        boolean escapeDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
        if ((eDown && !lastE) || (escapeDown && !lastEscape)) {
            closePanel();
        }
        lastE = eDown;
        lastEscape = escapeDown;
    }

    private static void closePanel() {
        Minecraft mc = Minecraft.getInstance();
        open = false;
        panelScreen = null;
        mouseButtonHeld = false;
        heldButton = -1;
        anchor = null;
        anchorRotation = null;
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.closeContainer();
        }
    }
}
