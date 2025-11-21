package name.modid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ChatModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 1. Register Chat Listener
        // This fires when a player sends a chat message
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> {
            checkForKeywords(message.getString());
        });

        // This fires for system messages (console output, server info, etc.)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            checkForKeywords(message.getString());
        });

        // 2. Register a Command to open the GUI manually
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("openmod")
                    .executes(context -> {
                        // Set the screen on the client thread
                        MinecraftClient.getInstance().send(() ->
                                MinecraftClient.getInstance().setScreen(new ModScreen())
                        );
                        return 1;
                    }));
        });
    }

    private void checkForKeywords(String messageContent) {
        // Simple check: if the message contains "HELLO", log it or open GUI
        if (messageContent.toLowerCase().contains("secret_code")) {
            System.out.println("[Mod] Found secret code!");

            // Example: Send a feedback message to the local player only
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Detected Key Message!").formatted(Formatting.RED), false);
            }
        }
    }
}