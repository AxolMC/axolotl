package dev.axolotlmc.axolotl.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.minecraft.sound.SoundCategory;

/**
 * @author Kenox
 */
@Data
@AllArgsConstructor
public class AxolotlSound {

    @SerializedName("name")
    private final String name;

    @SerializedName("category")
    private final SoundCategory soundCategory;

    @SerializedName("file")
    private final String file;
}
