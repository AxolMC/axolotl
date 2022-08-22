package dev.axolotlmc.axolotl.api.config;

import com.google.gson.annotations.SerializedName;
import dev.axolotlmc.axolotl.api.config.bucket.BucketConfig;
import dev.axolotlmc.axolotl.api.config.bucket.ResourcePackConfig;
import lombok.Data;
import lombok.Setter;

/**
 * @author Kenox
 */
@Data
public class AxolotlConfig {

    @SerializedName("url")
    @Setter private String packUrl;

    @SerializedName("hash")
    @Setter private String hash;

    @SerializedName("bucket")
    private final BucketConfig bucketConfig;

    @SerializedName("resource_pack")
    private final ResourcePackConfig resourcePackConfig;
}
