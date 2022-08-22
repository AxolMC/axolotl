package dev.axolotlmc.axolotl.api.config.bucket;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kenox
 */
@Data
public class ResourcePackConfig {

    @SerializedName("send_pack_on_join")
    private final boolean sendPackOnJoin;

    @SerializedName("force_pack")
    private final boolean forcePack;

    @SerializedName("prompt_message")
    @Nullable private String promptMessage;

    @SerializedName("pack_compression")
    private final int packCompression;
}
