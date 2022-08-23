package dev.axolotlmc.axolotl.api;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import net.minecraft.item.Item;

import java.util.List;

/**
 * @author Kenox
 */
@Data
public class CustomItem {

    @SerializedName("name")
    private final String name;

    @SerializedName("display_name")
    private final String displayName;

    @SerializedName("item")
    private final Item item;

    @SerializedName("generate_model")
    private final boolean generateModel;

    @SerializedName("model")
    private final String model;

    @SerializedName("custom_model_data")
    private final int customModelData;

    @SerializedName("parent_model")
    private final String parentModel;

    @SerializedName("textures")
    private final List<String> textures;

}
