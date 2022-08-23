package dev.axolotlmc.axolotl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.axolotlmc.axolotl.api.config.AxolotlConfig;
import dev.axolotlmc.axolotl.api.config.bucket.ResourcePackConfig;
import dev.axolotlmc.axolotl.api.serializer.ItemSerializer;
import dev.axolotlmc.axolotl.pack.ResourcePack;
import dev.axolotlmc.axolotl.util.ZipUtil;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ResourcePackSendS2CPacket;
import net.minecraft.text.Text;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * @author Kenox
 */
public class AxolotlMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("axolotl");
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Item.class, new ItemSerializer())
            .setPrettyPrinting()
            .create();

    @Getter private File modFolder;
    @Getter private File configFile;

    @Getter private AxolotlConfig config;
    @Getter private ResourcePack resourcePack;

    @Getter private String modFolderApiUrl;
    @Getter public String packApiUrl;
    @Getter public String downloadPackApiUrl;

    @Override
    public void onInitialize() {
        LOGGER.info("Axolotl is getting ready..");

        this.modFolder = new File("mods/Axolotl");

        // Initialize config
        this.configFile = new File(this.modFolder, "config.json");

        if (!this.configFile.exists()) {
            LOGGER.error("Config not found! Axolotl is not able to start!");
            return;
        }

        try {
            this.config = GSON.fromJson(FileUtils.readFileToString(this.configFile, StandardCharsets.UTF_8), AxolotlConfig.class);
        } catch (final IOException e) {
            e.printStackTrace();
        }

        // Initialize api urls
        final String bucketApiUrl = this.config.getBucketConfig().getBucketApiUrl();
        this.modFolderApiUrl = String.format(bucketApiUrl, "modfolder");
        this.packApiUrl = String.format(bucketApiUrl, "pack");
        this.downloadPackApiUrl = String.format(bucketApiUrl, "pack?hash=%s");

        // Initialize mod folder
        if (!this.modFolder.exists()) {
            LOGGER.info("Mod directory does not exist. Starting download..");

            try {
                final File outputFile = new File("mods/Axolotl.zip");
                this.downloadFile(new URL(this.modFolderApiUrl), outputFile, this.config.getBucketConfig().getBucketApiKey());
                ZipUtil.unZip(outputFile, new File("mods/"));
                outputFile.delete();
                LOGGER.info("Mod directory download finished");
            } catch (final IOException e) {
                e.printStackTrace();
            }
        } else {
            LOGGER.info("Mod directory already exists");
        }

        // Initialize resource pack
        final File packDirectory = new File(this.modFolder, "pack");
        this.resourcePack = new ResourcePack(this, packDirectory);
        try {
            this.resourcePack.generate();
        } catch (final IOException e) {
            LOGGER.error("Fatal error while generating resourcepack", e);
        }

        // Send pack to player
        final ResourcePackConfig resourcePackConfig = this.config.getResourcePackConfig();
        ServerPlayConnectionEvents.JOIN.register((handler, packetSender, server) -> {
            if (!this.config.getResourcePackConfig().isSendPackOnJoin()) {
                LOGGER.warn("Cancelling pack send to player due to configuration..");
                return;
            }

            if (this.config.getHash() == null || this.config.getHash().isEmpty()) {
                LOGGER.warn("Unable to send resource pack to player as hash is null or empty");
                return;
            }

            final ResourcePackSendS2CPacket resourcePackSendS2CPacket = new ResourcePackSendS2CPacket(
                    this.config.getPackUrl(),
                    this.config.getHash(),
                    resourcePackConfig.isForcePack(),
                    resourcePackConfig.getPromptMessage() == null ? null : Text.literal(resourcePackConfig.getPromptMessage())
            );
            packetSender.sendPacket(resourcePackSendS2CPacket);

            ItemStack paperItem = Items.IRON_INGOT.getDefaultStack();
            NbtCompound nbtCompound = paperItem.getOrCreateNbt();
            nbtCompound.putInt("CustomModelData", 4);

            ItemStack paperItem2 = Items.PAPER.getDefaultStack();
            NbtCompound nbtCompound2 = paperItem2.getOrCreateNbt();
            nbtCompound2.putInt("CustomModelData", 4);

            handler.player.getInventory().insertStack(paperItem);
            handler.player.getInventory().insertStack(paperItem2);


            handler.player.sendMessage(Text.literal(this.resourcePack.shift(67, true)).append(Text.literal("Hey!")));
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

    private void downloadFile(final URL url, final File output, final String apiKey) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("X-API-Key", apiKey);
        FileUtils.copyInputStreamToFile(connection.getInputStream(), output);
    }

    public void updatePackUrlAndHash(final String url, final String hash) throws IOException {
        this.config.setPackUrl(url);
        this.config.setHash(hash);
        FileUtils.writeStringToFile(this.configFile, GSON.toJson(this.config), StandardCharsets.UTF_8);
    }
}