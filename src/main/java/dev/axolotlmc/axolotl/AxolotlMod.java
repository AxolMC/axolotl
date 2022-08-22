package dev.axolotlmc.axolotl;

import dev.axolotlmc.axolotl.pack.ResourcePack;
import dev.axolotlmc.axolotl.util.ZipUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.ResourcePackSendS2CPacket;
import net.minecraft.text.Text;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author Kenox
 */
public class AxolotlMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("axolotl");
    private static final String BUCKET_API_URL = "http://127.0.0.1:8080/v1/%s";
    public static final String BUCKET_MODFOLDER_API_URL = String.format(BUCKET_API_URL, "modfolder");
    public static final String BUCKET_PACK_API_URL = String.format(BUCKET_API_URL, "pack");
    public static final String BUCKET_DOWNLOAD_PACK_API_URL = String.format(BUCKET_API_URL, "pack?hash=%s");

    public static final String EXPOSED_API_KEY = "ca9edc42-b088-45bb-9583-8be7542019e7";

    private ResourcePack resourcePack;

    @Override
    public void onInitialize() {
        LOGGER.info("Axolotl is getting ready..");

        // Initialize mod folder
        File modFolder = new File("mods/Axolotl");

        if(!modFolder.exists()) {
            LOGGER.info("Mod directory does not exist. Starting download..");

            try {
                File outputFile = new File("mods/Axolotl.zip");
                this.downloadFile(new URL(BUCKET_MODFOLDER_API_URL), outputFile, EXPOSED_API_KEY);
                ZipUtil.unZip(outputFile, new File("mods/"));
                outputFile.delete();
                LOGGER.info("Mod directory download finished");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            LOGGER.info("Mod directory already exists");
        }

        // Initialize resource pack
        File packDirectory = new File("mods/Axolotl/pack");
        this.resourcePack = new ResourcePack(packDirectory);
        try {
            this.resourcePack.generate();
        } catch (IOException e) {
            LOGGER.error("Fatal error while generating resourcepack", e);
        }

        // DEBUG
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ResourcePackSendS2CPacket resourcePackSendS2CPacket = new ResourcePackSendS2CPacket(
                    String.format(BUCKET_DOWNLOAD_PACK_API_URL, this.resourcePack.getLastReceivedPackHash()),
                    this.resourcePack.getLastReceivedPackHash(),
                    true,
                    Text.literal("RESOURCEPACK NEEDED!")
            );
            sender.sendPacket(resourcePackSendS2CPacket);
        });

        // DEBUG
        /*ServerTickEvents.END_WORLD_TICK.register(new ServerTickEvents.EndWorldTick() {
            int i = 0;
            @Override
            public void onEndTick(ServerWorld world) {
                i++;

                if(i == 400) {
                    i = 0;

                    for (ServerPlayerEntity serverPlayerEntity : world.getServer().getPlayerManager().getPlayerList()) {
                        serverPlayerEntity.sendMessage(Text.literal("Opening inventory"));

                        if (serverPlayerEntity.currentScreenHandler != serverPlayerEntity.playerScreenHandler) {
                            serverPlayerEntity.closeHandledScreen();
                        }

                        serverPlayerEntity.incrementScreenHandlerSyncId();

                        ScreenHandler boxScreenHandler = ScreenHandlerType.GENERIC_9X1.create(1, serverPlayerEntity.getInventory());

                        boxScreenHandler.getSlot(0).setStack(Items.BEDROCK.getDefaultStack());

                        serverPlayerEntity.networkHandler.sendPacket(new OpenScreenS2CPacket(boxScreenHandler.syncId, boxScreenHandler.getType(), Text.literal("Display")));
                        serverPlayerEntity.onScreenHandlerOpened(boxScreenHandler);
                        serverPlayerEntity.currentScreenHandler = boxScreenHandler;
                    }
                }
            }
        });*/
    }

    private void downloadFile(URL url, File output, String apiKey) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("X-API-Key", apiKey);
        FileUtils.copyInputStreamToFile(connection.getInputStream(), output);
    }
}