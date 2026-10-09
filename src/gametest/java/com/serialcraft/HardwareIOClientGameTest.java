package com.serialcraft;

import com.serialcraft.block.IOSide;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.HardwareIOBlockEntity;
import com.serialcraft.board.IoMode;
import com.serialcraft.board.LogicMode;
import com.serialcraft.board.SignalType;
import com.serialcraft.board.HardwareSessions;
import com.serialcraft.client.ui.io.IoEditor;
import com.serialcraft.client.ui.io.IoEditorDraft;
import com.serialcraft.client.ui.io.IoEditorLayout;
import com.serialcraft.client.ui.widget.SectionTabButton;
import com.serialcraft.client.ui.widget.OptionButton;
import com.serialcraft.network.ConfigPayload;
import com.serialcraft.screen.PanelUI;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

/** Optional -PioUiTests fixture: actual rendering/input and a disposable integrated world. */
public final class HardwareIOClientGameTest implements FabricClientGameTest {
    @Override
    @SuppressWarnings("unchecked")
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            BlockPos pos = context.computeOnClient(mc -> mc.player.blockPosition().offset(2, 0, 0));
            int sides = IOSide.with(IOSide.with(0, 0, IOSide.INPUT), 4, IOSide.OUTPUT);
            world.getServer().runOnServer(server -> {
                var level = world.getConnection().getServerLevel();
                var owner = world.getConnection().getServerPlayer();
                for (int i = 0; i < 16; i++) {
                    BlockPos at = pos.offset(i % 4, 0, i / 4);
                    level.setBlockAndUpdate(at, ModBlocks.IO_BLOCK.defaultBlockState());
                    var io = (HardwareIOBlockEntity) level.getBlockEntity(at);
                    io.claim(owner);
                    io.applyConfig(i % 2 == 0 ? IoMode.OUTPUT : IoMode.INPUT, "led_" + i, SignalType.DIGITAL,
                            i == 0 || i % 3 != 0, "Module " + i, LogicMode.OR);
                    io.setSides(sides);
                }
            });
            context.waitFor(mc -> mc.level.getBlockEntity(pos) instanceof HardwareIOBlockEntity io && io.snapshot().sides() == sides);
            language(context, "es_es");
            context.setScreen(() -> new PanelUI(null, pos));
            context.waitTick();
            var editor = context.computeOnClient(mc -> editor((PanelUI) mc.gui.screen()));
            assertThat(editor != null, "direct editor opened without hardware");
            assertNavigation(context, editor);

            // Native keyboard edits and wrapped selection controls.
            context.runOnClient(mc -> {
                assertThat(((PanelUI) mc.gui.screen()).getFocused() == widget(editor, "name"), "initial focus is module name");
                ((EditBox) widget(editor, "name")).setValue("");
            });
            context.getInput().typeChars("UX module");
            context.waitTick();
            screenshot(context, "initial-keyboard-check");
            assertThat(context.computeOnClient(mc -> editor.draft().values().name().equals("UX module")), "keyboard name edit: " + editor.draft().values().name());
            context.runOnClient(mc -> ((EditBox) widget(editor, "channel")).setValue("pot_val"));
            for (int i = 0; i < 18; i++) {
                context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
                context.runOnClient(mc -> {
                    var screen = (PanelUI) mc.gui.screen();
                    if (screen.getFocused() instanceof AbstractWidget w)
                        assertThat(w.visible && w.getY() >= 0 && w.getBottom() <= mc.getWindow().getGuiScaledHeight(), "keyboard reveals focused control");
                });
            }
            resize(context, 640, 480, 2);
            context.runOnClient(mc -> {
                var screen = (PanelUI) mc.gui.screen();
                screen.setFocused(widget(editor, "name"));
                editor.afterKey();
                IoEditorLayout layout = field(editor, "layout");
                screen.mouseScrolled(layout.viewport().x() + 10, layout.viewport().y() + 10, 0, 100);
            });
            assertNavigation(context, editor);
            screenshot(context, "es-configuration-small");
            click(context, editor, "mode.INPUT");
            assertThat(editor.draft().values().mode() == IoMode.INPUT, "hardware to Minecraft selection");
            click(context, editor, "mode.OUTPUT");
            assertThat(editor.draft().values().mode() == IoMode.OUTPUT, "Minecraft to hardware selection");
            for (String topic : new String[]{"channel", "direction", "signal", "logic"}) help(context, editor, topic);

            resize(context, 1280, 720, 2);
            assertNavigation(context, editor);
            click(context, editor, "mode.INPUT");
            screenshot(context, "es-direction-rx-large");
            click(context, editor, "mode.OUTPUT");
            screenshot(context, "es-direction-tx-large");
            click(context, editor, "tab.DIAGNOSTICS");
            screenshot(context, "es-diagnostics-large");
            help(context, editor, "diagnostics");
            assertThat(editor.draft().values().name().equals("UX module") && editor.draft().values().sides() == sides,
                    "draft survived sections and help");

            for (int[] size : new int[][]{{854, 480, 2}, {1920, 1080, 4}, {1280, 720, 2}}) {
                resize(context, size[0], size[1], size[2]);
                assertNavigation(context, editor);
                assertThat(editor.draft().values().name().equals("UX module"), "draft survived resize");
                screenshot(context, "es-diagnostics-" + size[0] + "-scale-" + size[2]);
            }

            language(context, "en_us");
            context.runOnClient(mc -> ((PanelUI) mc.gui.screen()).refresh());
            click(context, editor, "tab.CONFIGURATION");
            screenshot(context, "en-configuration-large");
            click(context, editor, "help.channel");
            screenshot(context, "en-channel-help");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            assertThat(!editor.modal(), "Escape closes help only");
            // A real ConfigPayload/ConfigResult round trip, with hardware absent.
            click(context, editor, "save");
            context.waitFor(mc -> editor.draft().saveState() == IoEditorDraft.SaveState.ACCEPTED);
            assertThat(!editor.draft().dirty(), "only server acceptance clears unsaved state");
            screenshot(context, "en-save-accepted");
            world.getServer().runOnServer(server -> {
                var io = (HardwareIOBlockEntity) world.getConnection().getServerLevel().getBlockEntity(pos);
                assertThat(io.getBoardId().equals("UX module") && io.getTargetData().equals("pot_val"), "server configuration persisted");
                assertThat(io.snapshot().sides() == sides, "saving preserves world connector configuration");
            });

            // Synthetic, server-authorized diagnostic data; no physical device or acknowledgement.
            var saved = editor.draft().values();
            world.getServer().runOnServer(server -> {
                var io = (HardwareIOBlockEntity) world.getConnection().getServerLevel().getBlockEntity(pos);
                HardwareSessions.update(io.getOwnerUUID(), true);
                io.applyConfig(IoMode.INPUT, "pot_val", SignalType.ANALOG, true, "UX module", LogicMode.OR);
                io.setSides(IOSide.with(0, 4, IOSide.OUTPUT));
                io.acceptSerialInput("pot_val:128"); io.tickServer();
            });
            context.waitFor(mc -> ((HardwareIOBlockEntity) mc.level.getBlockEntity(pos)).snapshot().received() == 128);
            click(context, editor, "tab.DIAGNOSTICS");
            context.runOnClient(mc -> {
                var sample = ((HardwareIOBlockEntity) mc.level.getBlockEntity(pos)).snapshot();
                assertThat(sample.processed() == 8 && sample.emitted() == 8 && sample.receivedAgeTicks() >= 0,
                        "diagnostics reflect authoritative sample and RX age");
                assertThat(editor.draft().values().equals(saved), "live session values do not replace draft");
            });
            screenshot(context, "en-diagnostics-synthetic-rx128");
            world.getServer().runOnServer(server -> {
                var io = (HardwareIOBlockEntity) world.getConnection().getServerLevel().getBlockEntity(pos);
                HardwareSessions.update(io.getOwnerUUID(), false);
                io.applyConfig(saved.mode(), saved.channel(), saved.signal(), saved.enabled(), saved.name(), saved.logic());
                io.setSides(saved.sides());
            });
            click(context, editor, "tab.CONFIGURATION");

            context.runOnClient(mc -> ((EditBox) widget(editor, "name")).setValue("keep rejected draft"));
            world.getServer().runOnServer(server -> world.getConnection().getServerLevel().removeBlock(pos, false));
            click(context, editor, "save");
            context.waitFor(mc -> editor.draft().saveState() == IoEditorDraft.SaveState.REJECTED);
            assertThat(editor.draft().values().name().equals("keep rejected draft"), "rejection retained draft");
            screenshot(context, "en-save-rejected");

            // Controlled missing response: the actual editor waits its existing five-second deadline.
            var receiver = world.getServer().computeOnServer(server -> {
                var previous = (ServerPlayNetworking.PlayPayloadHandler<ConfigPayload>) ServerPlayNetworking.unregisterGlobalReceiver(ConfigPayload.TYPE.id());
                ServerPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, netContext) -> {});
                return previous;
            });
            try {
                context.waitTicks(3);
                click(context, editor, "save");
                context.waitFor(mc -> editor.draft().saveState() == IoEditorDraft.SaveState.TIMED_OUT, 200);
                assertThat(editor.draft().dirty(), "timeout retained draft");
                screenshot(context, "en-save-timeout");
            } finally {
                world.getServer().runOnServer(server -> {
                    ServerPlayNetworking.unregisterGlobalReceiver(ConfigPayload.TYPE.id());
                    ServerPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, receiver);
                });
            }
            click(context, editor, "cancel");
            assertThat(editor.modal(), "unsaved close asks to discard");
            screenshot(context, "en-discard-confirmation");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            assertThat(!editor.modal() && editor.draft().dirty(), "Escape keeps unsaved changes");
            click(context, editor, "cancel");
            context.runOnClient(mc -> clickModalButton((PanelUI) mc.gui.screen(), 1));
            context.waitFor(mc -> editor((PanelUI) mc.gui.screen()) == null);
            context.waitTicks(3);
            screenshot(context, "en-module-list");
            context.runOnClient(mc -> ((PanelUI) mc.gui.screen()).mouseScrolled(300, 200, 0, -6));
            screenshot(context, "en-module-list-scrolled");
            resize(context, 640, 480, 2);
            context.runOnClient(mc -> ((PanelUI) mc.gui.screen()).mouseScrolled(200, 150, 0, 100));
            screenshot(context, "en-module-list-small");
            language(context, "es_es");
            context.runOnClient(mc -> ((PanelUI) mc.gui.screen()).refresh());
            screenshot(context, "es-module-list-small");
            resize(context, 1280, 720, 2);
            screenshot(context, "es-module-list-large");
            for (int i = 0; i < 20; i++) {
                context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
                context.runOnClient(mc -> {
                    if (((PanelUI) mc.gui.screen()).getFocused() instanceof AbstractWidget w)
                        assertThat(w.visible && w.getY() >= 0 && w.getBottom() <= mc.getWindow().getGuiScaledHeight(),
                                "list keyboard focus stays visible after compacting rows");
                });
            }
            context.runOnClient(mc -> {
                for (var child : ((PanelUI) mc.gui.screen()).children()) if (child instanceof AbstractWidget w && w.visible)
                    assertThat(w.getY() >= 0 && w.getBottom() <= mc.getWindow().getGuiScaledHeight(), "visible list widget inside viewport");
            });
        }
    }

    private static void language(ClientGameTestContext context, String locale) {
        var reload = context.computeOnClient(mc -> {
            mc.options.languageCode = locale; mc.getLanguageManager().setSelected(locale);
            return mc.reloadResourcePacks();
        });
        context.waitFor(mc -> reload.isDone(), 600);
        reload.join();
        context.waitFor(mc -> mc.gui.overlay() == null, 600);
        context.waitTick();
    }
    private static void resize(ClientGameTestContext context, int width, int height, int scale) {
        context.getInput().resizeWindow(width, height);
        context.runOnClient(mc -> { mc.options.guiScale().set(scale); mc.resizeGui(); });
        context.waitTick();
    }
    private static void assertNavigation(ClientGameTestContext context, IoEditor editor) {
        context.runOnClient(mc -> {
            IoEditorLayout layout = field(editor, "layout");
            var configuration = widget(editor, "tab.CONFIGURATION");
            var diagnostics = widget(editor, "tab.DIAGNOSTICS");
            assertThat(configuration instanceof SectionTabButton && diagnostics instanceof SectionTabButton,
                    "section navigation is distinct from form options");
            assertThat(diagnostics.getRight() == layout.panel().right(), "navigation aligned to editor right edge");
            assertThat(configuration.getRight() < diagnostics.getX() && diagnostics.getBottom() < layout.viewport().y(),
                    "navigation controls do not overlap each other or form content");
            if (editor.draft().section() == IoEditorDraft.Section.CONFIGURATION)
                assertThat(widget(editor, "mode.INPUT") instanceof OptionButton, "form options retain selection controls");
        });
    }
    private static void help(ClientGameTestContext context, IoEditor editor, String topic) {
        click(context, editor, "help." + topic);
        assertThat(editor.modal(), "help topic " + topic + " opened");
        var before = editor.draft().values();
        context.runOnClient(mc -> {
            var screen = (PanelUI) mc.gui.screen();
            screen.mouseClicked(new MouseButtonEvent(20, 20, new MouseButtonInfo(0, 0)), false);
            assertThat(editor.draft().values().equals(before), "modal blocks background controls");
        });
        screenshot(context, "help-" + topic);
        if (topic.equals("channel")) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            assertThat(!editor.modal(), "focused close help works with Enter");
            click(context, editor, "help." + topic);
            context.runOnClient(mc -> clickModalButton((PanelUI) mc.gui.screen(), 0));
            assertThat(!editor.modal(), "close help button works with mouse");
            click(context, editor, "help." + topic);
        }
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        assertThat(!editor.modal() && editor.draft().values().equals(before), "help closes without changing draft");
    }
    private static void click(ClientGameTestContext context, IoEditor editor, String key) {
        context.runOnClient(mc -> {
            PanelUI screen = (PanelUI) mc.gui.screen();
            IoEditorLayout layout = field(editor, "layout");
            AbstractWidget w = widget(editor, key);
            if (!w.visible) {
                screen.mouseScrolled(layout.viewport().x() + 10, layout.viewport().y() + 10, 0, 100);
                for (int i = 0; i < 40 && !w.visible; i++) screen.mouseScrolled(layout.viewport().x() + 10, layout.viewport().y() + 10, 0, -1);
            }
            assertThat(w.visible && w.active, "clickable control " + key);
            double x = w.getX() + w.getWidth() / 2d, y = w.getY() + w.getHeight() / 2d;
            var event = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
            screen.mouseClicked(event, false); screen.mouseReleased(event);
        });
        context.waitTick();
    }
    private static void clickModalButton(PanelUI screen, int index) {
        var button = (AbstractWidget) screen.children().get(index);
        var event = new MouseButtonEvent(button.getX() + button.getWidth() / 2d, button.getY() + button.getHeight() / 2d, new MouseButtonInfo(0, 0));
        screen.mouseClicked(event, false); screen.mouseReleased(event);
    }
    private static void screenshot(ClientGameTestContext context, String name) {
        context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(Path.of("../../ui-verification/card-actions").toAbsolutePath().normalize()));
    }
    private static AbstractWidget widget(IoEditor editor, String key) {
        Map<String, AbstractWidget> widgets = field(editor, "focusWidgets");
        var widget = widgets.get(key);
        assertThat(widget != null, "control present " + key);
        return widget;
    }
    private static IoEditor editor(PanelUI panel) { return field(field(panel, "boardsPage"), "editor"); }
    @SuppressWarnings("unchecked")
    private static <T> T field(Object instance, String name) {
        try { Field f = instance.getClass().getDeclaredField(name); f.setAccessible(true); return (T) f.get(instance); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void assertThat(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
