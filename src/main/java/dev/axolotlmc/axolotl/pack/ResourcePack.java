package dev.axolotlmc.axolotl.pack;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import dev.axolotlmc.axolotl.AxolotlMod;
import dev.axolotlmc.axolotl.api.Glyph;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

/**
 * @author Kenox
 */
@RequiredArgsConstructor
public class ResourcePack {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MIN_CODE = 42000;
    private static final String[] DEFAULT_DIRS = new String[]{"textures", "lang", "shaders", "sounds",
            "blockstates", "optifine", "models"};
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

    @Getter private final File packDirectory;
    @Getter private String lastReceivedPackHash;
    @Getter private final List<Glyph> glyphs = new ArrayList<>();

    public void generate() throws IOException {
        // Generate glyphs
        AxolotlMod.LOGGER.info("Generating glyphs..");

        File glyphFolder = new File("mods/Axolotl/glyphs");

        if(!glyphFolder.exists())
            glyphFolder.mkdirs();

        for (File file : glyphFolder.listFiles()) {
            if(file.isDirectory()) {
                AxolotlMod.LOGGER.warn("Directories for glyphs are currently not supported! (" + file.getAbsolutePath() + ")");
                continue;
            }

            if(!file.getName().endsWith(".json")) {
                AxolotlMod.LOGGER.warn("Skipping glyph file " + file.getName() + " as it does not end with '.json'");
                continue;
            }

            List<Glyph> foundGlyphs = GSON.fromJson(FileUtils.readFileToString(file, StandardCharsets.UTF_8.name()),
                    new TypeToken<List<Glyph>>(){}.getType());

            foundGlyphs.forEach(glyph -> {
                this.glyphs.add(glyph);
                glyph.setCharacter((char) this.getFirstCode(MIN_CODE));
            });

            AxolotlMod.LOGGER.info("Found " + foundGlyphs.size() + " glyphs (" + file.getName() + ")");
        }

        // Compress pack
        AxolotlMod.LOGGER.info("Compressing pack..");

        File compressedPack = new File("mods/Axolotl/pack.zip");

        File prodDir = new File("mods/Axolotl/pack-prod");
        if(prodDir.exists())
            prodDir.delete();
        prodDir.mkdirs();

        // Generate font file
        File defaultFontDir = new File("mods/Axolotl/pack-prod/assets/minecraft/font/");
        defaultFontDir.mkdirs();
        File defaultFontFile = new File(defaultFontDir, "default.json");
        defaultFontFile.createNewFile();

        JsonObject fontObject = new JsonObject();
        JsonArray providersArray = new JsonArray();

        this.glyphs.forEach(glyph -> {
            JsonObject glyphObject = new JsonObject();

            JsonArray charsArray = new JsonArray();
            charsArray.add(glyph.getCharacter().toString());
            glyphObject.add("chars", charsArray);

            glyphObject.add("file", new JsonPrimitive(glyph.getTexture()));
            glyphObject.add("ascent", new JsonPrimitive(glyph.getAscent()));
            glyphObject.add("height", new JsonPrimitive(glyph.getHeight()));
            glyphObject.add("type", new JsonPrimitive("bitmap"));

            providersArray.add(glyphObject);
        });

        fontObject.add("providers", providersArray);
        org.apache.commons.io.FileUtils.writeStringToFile(defaultFontFile, fontObject.toString(), StandardCharsets.UTF_8);

        org.apache.commons.io.FileUtils.copyDirectory(this.packDirectory, prodDir);

        // Create default dirs and move them correctly
        for (String defaultDir : DEFAULT_DIRS) {
            File defaultDirFile = new File("mods/Axolotl/pack-prod/" + defaultDir);

            if(!defaultDirFile.exists())
                defaultDirFile.mkdirs();

            File targetDir = new File("mods/Axolotl/pack-prod/assets/minecraft/" + defaultDir);

            if(targetDir.exists())
                targetDir.delete();

            FileUtils.moveDirectory(defaultDirFile, targetDir);
        }

        ZipUtil.pack(prodDir, compressedPack, Deflater.BEST_COMPRESSION);

        FileUtils.deleteDirectory(prodDir);

        // Upload pack to bucket
        AxolotlMod.LOGGER.info("Uploading pack to bucket..");

        // Upload world to backblaze
        RequestBody formBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", compressedPack.getName(),
                        RequestBody.create(compressedPack, MediaType.parse("application/zip")))
                .build();

        Request request = new Request.Builder()
                .url(AxolotlMod.BUCKET_PACK_API_URL)
                .addHeader("X-API-Key", AxolotlMod.EXPOSED_API_KEY)
                .put(formBody)
                .build();

        long start = System.currentTimeMillis();

        HTTP_CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                ResponseBody responseBody = response.body();

                if(responseBody == null) {
                    AxolotlMod.LOGGER.warn("Bucket responded with null as download URL!");
                    return;
                }

                String hash = responseBody.string();
                response.close();

                AxolotlMod.LOGGER.info("Got new hash in " + (System.currentTimeMillis() - start) + "ms: " + hash);

                ResourcePack.this.lastReceivedPackHash = hash;
            }
        });
    }

    private int getFirstCode(int i) {
        if(this.glyphs.stream().anyMatch(glyph -> glyph.getCharacter() != null && glyph.getCharacter() == (char) i))
            return this.getFirstCode(i + 1);
        return i;
    }
}
