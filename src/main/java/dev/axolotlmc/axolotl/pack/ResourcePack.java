package dev.axolotlmc.axolotl.pack;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.axolotlmc.axolotl.AxolotlMod;
import dev.axolotlmc.axolotl.api.AxolotlSound;
import dev.axolotlmc.axolotl.api.Glyph;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static dev.axolotlmc.axolotl.AxolotlMod.GSON;

/**
 * @author Kenox
 */
@RequiredArgsConstructor
public class ResourcePack {
    private static final int MIN_CODE = 42000;
    private static final String[] DEFAULT_DIRS = new String[]{"textures", "lang", "shaders", "sounds",
            "blockstates", "optifine", "models"};
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

    @Getter private final AxolotlMod mod;
    @Getter private final File packDirectory;
    @Getter private String lastReceivedPackHash;
    @Getter private final List<Glyph> glyphs = new ArrayList<>();
    @Getter private List<AxolotlSound> axolotlSounds = new ArrayList<>();

    public void generate() throws IOException {
        // Generate all glyphs (files with ".json"-suffix inside "glyphs" directory)
        this.generateGlyphs();

        // Load sounds into "axolotl" namespace
        this.loadSounds();

        // Initializes final pack files
        final File compressedPack = new File(this.mod.getModFolder(), "pack.zip");

        final File prodDir = new File(this.mod.getModFolder(), "pack-prod");
        if (prodDir.exists())
            prodDir.delete();
        prodDir.mkdirs();

        // Generate "default.json" inside font directory
        this.generateFontFile();

        // Copy axolotl pack to a production directory, so we can proceed generating anything
        FileUtils.copyDirectory(this.packDirectory, prodDir);

        // Create default dirs and move them correctly
        for (final String defaultDir : DEFAULT_DIRS) {
            final File defaultDirFile = new File(this.mod.getModFolder(), "pack-prod/" + defaultDir);

            if (!defaultDirFile.exists())
                defaultDirFile.mkdirs();

            final File targetDir = new File(this.mod.getModFolder(), "pack-prod/assets/minecraft/" + defaultDir);

            if (targetDir.exists())
                targetDir.delete();

            FileUtils.moveDirectory(defaultDirFile, targetDir);
        }

        // Create sounds.json with "axolotl" as namespace
        final File namespaceDir = new File(this.mod.getModFolder(), "pack-prod/assets/axolotl");

        if (namespaceDir.exists())
            namespaceDir.delete();

        namespaceDir.mkdirs();

        final File prodSoundsFile = new File(namespaceDir, "sounds.json");

        if (prodSoundsFile.exists())
            prodSoundsFile.delete();

        prodSoundsFile.createNewFile();

        final JsonObject soundsObject = new JsonObject();

        this.axolotlSounds.forEach(axolotlSound -> {
            final JsonObject soundObject = new JsonObject();
            soundObject.add("category", new JsonPrimitive(axolotlSound.getSoundCategory().getName()));

            final JsonArray soundsArray = new JsonArray();
            soundsArray.add(new JsonPrimitive(axolotlSound.getName()));
            soundObject.add("sounds", soundsArray);

            soundsObject.add(axolotlSound.getName(), soundObject);
        });

        FileUtils.writeStringToFile(prodSoundsFile, soundsObject.toString(), StandardCharsets.UTF_8);

        ZipUtil.pack(prodDir, compressedPack, this.mod.getConfig().getResourcePackConfig().getPackCompression());

        FileUtils.deleteDirectory(prodDir);

        // Upload pack to bucket
        AxolotlMod.LOGGER.info("Uploading pack to bucket..");

        final RequestBody formBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", compressedPack.getName(),
                        RequestBody.create(compressedPack, MediaType.parse("application/zip")))
                .build();

        final Request request = new Request.Builder()
                .url(this.mod.getPackApiUrl())
                .addHeader("X-API-Key", this.mod.getConfig().getBucketConfig().getBucketApiKey())
                .put(formBody)
                .build();

        final long start = System.currentTimeMillis();

        HTTP_CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull final Call call, @NotNull final IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NotNull final Call call, @NotNull final Response response) throws IOException {
                final ResponseBody responseBody = response.body();

                if (responseBody == null) {
                    AxolotlMod.LOGGER.warn("Bucket responded with null as download URL!");
                    return;
                }

                final String hash = responseBody.string();
                response.close();

                AxolotlMod.LOGGER.info("Got new hash in " + (System.currentTimeMillis() - start) + "ms: " + hash);

                ResourcePack.this.lastReceivedPackHash = hash;
            }
        });
    }

    private void generateGlyphs() throws IOException {
        // Generate glyphs
        AxolotlMod.LOGGER.info("Generating glyphs..");

        final File glyphFolder = new File(this.mod.getModFolder(), "glyphs");

        if (!glyphFolder.exists())
            glyphFolder.mkdirs();

        for (final File file : glyphFolder.listFiles()) {
            if (file.isDirectory()) {
                AxolotlMod.LOGGER.warn("Directories for glyphs are currently not supported! (" + file.getAbsolutePath() + ")");
                continue;
            }

            if (!file.getName().endsWith(".json")) {
                AxolotlMod.LOGGER.warn("Skipping glyph file " + file.getName() + " as it does not end with '.json'");
                continue;
            }

            final List<Glyph> foundGlyphs = GSON.fromJson(FileUtils.readFileToString(file, StandardCharsets.UTF_8),
                    new TypeToken<List<Glyph>>() {
                    }.getType());

            foundGlyphs.forEach(glyph -> {
                this.glyphs.add(glyph);
                glyph.setCharacter((char) this.getFirstCode(MIN_CODE));
            });

            AxolotlMod.LOGGER.info("Found " + foundGlyphs.size() + " glyphs (" + file.getName() + ")");
        }
    }

    private void loadSounds() throws IOException {
        // Load sounds
        final File soundsFile = new File(this.mod.getModFolder(), "sounds.json");

        if (soundsFile.exists()) {
            this.axolotlSounds = GSON.fromJson(FileUtils.readFileToString(soundsFile, StandardCharsets.UTF_8),
                    new TypeToken<List<AxolotlSound>>() {
                    }.getType());

            AxolotlMod.LOGGER.info("Found " + this.axolotlSounds.size() + " custom sounds");
        }
    }

    private void generateFontFile() throws IOException {
        // Generate font file
        final File defaultFontDir = new File(this.mod.getModFolder(), "pack-prod/assets/minecraft/font/");
        defaultFontDir.mkdirs();
        final File defaultFontFile = new File(defaultFontDir, "default.json");
        defaultFontFile.createNewFile();

        final JsonObject fontObject = new JsonObject();
        final JsonArray providersArray = new JsonArray();

        this.glyphs.forEach(glyph -> {
            final JsonObject glyphObject = new JsonObject();

            final JsonArray charsArray = new JsonArray();
            charsArray.add(glyph.getCharacter().toString());
            glyphObject.add("chars", charsArray);

            glyphObject.add("file", new JsonPrimitive(glyph.getTexture()));
            glyphObject.add("ascent", new JsonPrimitive(glyph.getAscent()));
            glyphObject.add("height", new JsonPrimitive(glyph.getHeight()));
            glyphObject.add("type", new JsonPrimitive("bitmap"));

            providersArray.add(glyphObject);
        });

        fontObject.add("providers", providersArray);
        FileUtils.writeStringToFile(defaultFontFile, fontObject.toString(), StandardCharsets.UTF_8);
    }

    private int getFirstCode(final int i) {
        if (this.glyphs.stream().anyMatch(glyph -> glyph.getCharacter() != null && glyph.getCharacter() == (char) i))
            return this.getFirstCode(i + 1);
        return i;
    }

    public String shift(final int length) {
        return this.shift(length, false);
    }

    public String shift(int length, final boolean right) {
        final StringBuilder stringBuilder = new StringBuilder();

        while (length > 0) {
            final int highestOneBit = Integer.highestOneBit(length);
            final Optional<Glyph> optionalGlyph = this.getGlyph((right ? "right_" : "") + "shift_" + highestOneBit);

            if (optionalGlyph.isEmpty()) {
                AxolotlMod.LOGGER.warn("Glyph with length " + highestOneBit + " does not exist!");
                break;
            }

            final Glyph glyph = optionalGlyph.get();

            stringBuilder.append(glyph.getCharacter().toString());

            length -= highestOneBit;
        }

        return stringBuilder.toString();
    }

    public Optional<Glyph> getGlyph(final String name) {
        return this.glyphs.stream().filter(glyph -> glyph.getName().equals(name)).findFirst();
    }
}
