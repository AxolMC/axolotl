package dev.axolotlmc.axolotl.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Setter;

/**
 * @author Kenox
 */
@Data
@AllArgsConstructor
public class Glyph {

    @SerializedName("name")
    private final String name;

    @SerializedName("texture")
    private final String texture;

    @SerializedName("ascent")
    private final int ascent;

    @SerializedName("height")
    private final int height;

    @SerializedName("char")
    @Setter private Character character;
}
