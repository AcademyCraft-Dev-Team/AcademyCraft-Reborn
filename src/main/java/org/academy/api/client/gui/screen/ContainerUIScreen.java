package org.academy.api.client.gui.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.academy.AcademyCraft;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.event.*;
import org.academy.api.client.gui.imgui.ImGuiUtilApi;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.render.UIRenderContext;
import org.academy.api.client.gui.widget.*;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class ContainerUIScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> implements IUIScreen,IAnimationScreen {
    protected final FrameLayoutWidget rootContainer = new FrameLayoutWidget();

    private boolean handleContainer = true;
    private boolean renderInventory = true;
    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();
    protected final UIRenderContext uiRenderContext = new UIRenderContext();
    private Supplier<Float> invHeightSupplier = () -> 1f;
    private Supplier<Float> invTranslationYSupplier = () -> 1f;
    private Consumer<Boolean> invVisibleSetter = ignore -> {
    };
    @Nullable
    protected RenderTarget renderTarget;

    protected ContainerUIScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    @Nullable
    public RenderTarget getRenderTarget() {
        return renderTarget;
    }

    @Override
    public WidgetContainer getRootContainer() {
        return rootContainer;
    }

    @Override
    public UIRenderContext getUIRenderContext() {
        return uiRenderContext;
    }

    @Override
    public void onClose() {
        super.onClose();
        cancelAllAnimations();
        if (renderTarget != null) renderTarget.destroyBuffers();
        uiRenderContext.close();
    }

    private void initializeRenderResources() {
        var window = Minecraft.getInstance().getWindow();
        if (renderTarget != null) {
            renderTarget.resize(window.getWidth(), window.getHeight());
        } else {
            renderTarget = new TextureTarget(null, window.getWidth(), window.getHeight(), true);
        }
        ImGuiUtilApi.clearEventsQueue();
    }

    @Override
    public List<Animator> getScreenAnimations() {
        return screenAnimations;
    }

    @Override
    public Map<Widget, List<Animator>> getTrackedAnimations() {
        return trackedAnimations;
    }

    @Override
    protected void init() {
        super.init();
        var minecraft = Minecraft.getInstance();
        minecraft.execute(this::initializeRenderResources);

        rootContainer.setName("root");
        rootContainer.clearChildren();

        var finalHeight = 187f;
        var duration = 600L;

        var main = new LinearLayoutWidget();
        main.setOrientation(Orientation.HORIZONTAL);
        main.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.WRAP_CONTENT)
                        .heightMode(SizeMode.WRAP_CONTENT)
                        .margin(leftPos - 16, topPos - 22, 0, 0)
        );
        rootContainer.addChild("main", main);
        playAnimation(
                ObjectAnimator
                        .ofFloat(main::setAlpha, 0, 1.0f)
                        .setDuration(duration)
                        .setInterpolator(EasingFunctions.EASE_OUT_EXPO)
        );
        playAnimation(
                ObjectAnimator
                        .ofFloat(main::setHeight, 0, finalHeight)
                        .setDuration(duration)
                        .setInterpolator(EasingFunctions.EASE_OUT_EXPO)
        );
        {
            var pageButtons = new RadioGroupWidget();
            pageButtons.setOrientation(Orientation.VERTICAL);
            pageButtons.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .width(16)
                            .heightMode(SizeMode.WRAP_CONTENT)
            );
            main.addChild("radio_group_page_button", pageButtons);
            ImageRadioButtonWidget invButton;
            {
                invButton = new ImageRadioButtonWidget(Resource.Textures.ICON_INV);
                invButton.setLayoutParams(
                        new WidgetContainer.LayoutParams()
                                .widthMode(SizeMode.WRAP_CONTENT)
                                .height(16)
                );
                pageButtons.addChild("inv", invButton);
                pageButtons.selectButton(invButton);
            }

            var content = new FrameLayoutWidget();
            content.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .width(imageWidth)
                            .height(187)
            );
            main.addChild("content", content);
            {
                var invPage = new FrameLayoutWidget();
                invTranslationYSupplier = invPage::getTranslationY;
                invHeightSupplier = invPage::getHeight;
                invVisibleSetter = invPage::setVisible;
                invPage.setLayoutParams(
                        new FrameLayoutWidget.LayoutParams()
                                .widthMode(SizeMode.MATCH_PARENT)
                                .heightMode(SizeMode.MATCH_PARENT)
                );
                content.addChild("page_inv", invPage);
                playAnimation(ObjectAnimator.ofFloat(invPage::setHeight, 0, finalHeight).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO));
                {
                    var back = new BlendQuadWidget();
                    back.setLayoutParams(
                            new FrameLayoutWidget.LayoutParams()
                                    .widthMode(SizeMode.MATCH_PARENT)
                                    .heightMode(SizeMode.MATCH_PARENT)
                    );
                    back.setAlpha(0.5f);
                    invPage.addChild("back", back);

                    var inv = new ImageWidget(Resource.Textures.UI_INVENTORY);
                    inv.setLayoutParams(
                            new FrameLayoutWidget.LayoutParams()
                                    .widthMode(SizeMode.MATCH_PARENT)
                                    .heightMode(SizeMode.MATCH_PARENT)
                    );
                    invPage.addChild("inv", inv);

                    onInit(pageButtons, invButton, content, invPage);
                }
            }
        }
    }

    protected abstract void onInit(RadioGroupWidget pageButtons, ImageRadioButtonWidget invButton, FrameLayoutWidget content, FrameLayoutWidget invPage);

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (renderTarget == null) return;
        var colorTextureView = renderTarget.getColorTextureView();
        if (colorTextureView == null) return;

        guiGraphics.submitBlit(RenderPipelines.GUI_TEXTURED, colorTextureView, 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0, 1, 1, 0, -1);

        if (isRenderInventory()) {
            var originHeight = 187f;
            var currentHeight = invHeightSupplier.get();
            var scaleY = currentHeight / originHeight;
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(0, topPos);
            guiGraphics.pose().scale(1, scaleY);
            guiGraphics.pose().translate(0, -topPos + invTranslationYSupplier.get());

            renderContents(guiGraphics, mouseX, mouseY, partialTick);
            renderCarriedItem(guiGraphics, mouseX, mouseY);
            renderSnapbackItem(guiGraphics);

            guiGraphics.pose().popMatrix();
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderSlotContents(GuiGraphics guiGraphics, ItemStack itemstack, Slot slot, @Nullable String countString) {
        var pose = guiGraphics.pose();
        pose.pushMatrix();

        pose.translate(slot.x, slot.y);
        pose.translate(8.0F, 8.0F);

        pose.scale(2 / 3f);

        pose.translate(-8.0F, -8.0F);
        pose.translate(-slot.x, -slot.y);

        super.renderSlotContents(guiGraphics, itemstack, slot, countString);

        pose.popMatrix();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    protected void containerTick() {
        rootContainer.tick();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (ImGuiUtilApi.wantCaptureMouse()) return;

        var event = MouseEvent.createMoveEvent(mouseX, mouseY);
        rootContainer.dispatchEvent(event);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (ImGuiUtilApi.wantCaptureMouse()) return true;

        var event = new ScrollEvent(mouseX, mouseY, scrollY);
        rootContainer.dispatchEvent(event);
        var rootResult = event.isConsumed();

        var superResult = false;
        if (isHandleContainer()) superResult = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        return rootResult || superResult;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (ImGuiUtilApi.wantCaptureMouse()) return true;

        var event = MouseEvent.createReleaseEvent(e.x(), e.y(), e.button());
        rootContainer.dispatchEvent(event);
        var rootResult = event.isConsumed();

        var superResult = false;
        if (isHandleContainer()) superResult = super.mouseReleased(e);

        return rootResult || superResult;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double mouseX, double mouseY) {
        if (ImGuiUtilApi.wantCaptureMouse()) return true;

        var event = MouseEvent.createDragEvent(e.x(), e.y(), e.button(), mouseX, mouseY);
        rootContainer.dispatchEvent(event);
        var rootResult = event.isConsumed();

        var superResult = false;
        if (isHandleContainer()) superResult = super.mouseDragged(e, mouseX, mouseY);

        return rootResult || superResult;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean isDoubleClick) {
        if (ImGuiUtilApi.wantCaptureMouse()) return true;

        var event = MouseEvent.createPressEvent(e.x(), e.y(), e.button());
        rootContainer.dispatchEvent(event);
        var rootResult = event.isConsumed();

        var superResult = false;
        if (isHandleContainer()) superResult = super.mouseClicked(e, isDoubleClick);

        return rootResult || superResult;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (ImGuiUtilApi.wantCaptureKeyboard()) return true;

        if (e.key() == GLFW.GLFW_KEY_F12) {
            AcademyCraft.DEBUG_UI = !AcademyCraft.DEBUG_UI;
            return true;
        }

        var event = new KeyEvent(EventType.KEY_PRESSED, e.key(), e.scancode(), e.modifiers());
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) return true;
        if (e.key() == GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        return isHandleContainer() && super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (ImGuiUtilApi.wantCaptureKeyboard()) return true;

        var event = new CharTypedEvent(e.codepoint(), e.modifiers());
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) return true;
        return isHandleContainer() && super.charTyped(e);
    }

    public void setHandleContainer(boolean handleContainer) {
        this.handleContainer = handleContainer;
    }

    public void setRenderInventory(boolean renderInventory) {
        this.renderInventory = renderInventory;
        invVisibleSetter.accept(renderInventory);
    }

    public boolean isRenderInventory() {
        return renderInventory;
    }

    public boolean isHandleContainer() {
        return handleContainer;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop) {
        return mouseX < (double) guiLeft || mouseY < (double) guiTop - 22 || mouseX >= (double) (guiLeft + imageWidth) || mouseY >= (double) (guiTop + 187);
    }
}