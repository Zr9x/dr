package modularhud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ModularHudClient implements ClientModInitializer {
    public static final String MOD_ID = "modularhud";
    private static KeyBinding openConfigKey;
    private static KeyBinding toggleSoundKey;

    @Override
    public void onInitializeClient() {
        HudConfig.get();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

        // Right Shift opens the HUD settings
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.modularhud.open_config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        // J toggles the sound detector on/off (rebindable)
        toggleSoundKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.modularhud.toggle_sound", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, category));

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of(MOD_ID, "hud"),
                HudRenderer::render
        );

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            for (HudModule m : Modules.ALL) {
                m.tick(mc);
            }
            SoundDetector.tick(mc);

            while (openConfigKey.wasPressed()) {
                if (mc.currentScreen == null) {
                    mc.setScreen(new HudConfigScreen());
                }
            }
            while (toggleSoundKey.wasPressed()) {
                HudConfig cfg = HudConfig.get();
                cfg.soundEnabled = !cfg.soundEnabled;
                HudConfig.save();
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("Sound Detector: " + (cfg.soundEnabled ? "ON" : "OFF")), true);
                }
            }
        });
    }
}
