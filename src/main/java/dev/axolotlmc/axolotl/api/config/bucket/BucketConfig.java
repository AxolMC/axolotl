package dev.axolotlmc.axolotl.api.config.bucket;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * @author Kenox
 */
@Data
public class BucketConfig {

    @SerializedName("upload")
    private final boolean upload;

    @SerializedName("bucket_api_url")
    private final String bucketApiUrl;

    @SerializedName("bucket_api_key")
    private final String bucketApiKey;
}
