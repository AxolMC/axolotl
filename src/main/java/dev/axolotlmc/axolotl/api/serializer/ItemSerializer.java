package dev.axolotlmc.axolotl.api.serializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.minecraft.item.Item;
import net.minecraft.util.registry.Registry;

import java.lang.reflect.Type;
import java.util.Optional;

/**
 * @author Kenox
 */
public class ItemSerializer implements JsonDeserializer<Item> {

    @Override
    public Item deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Optional<Item> optionalItem = Registry.ITEM.stream()
                .filter(item -> Registry.ITEM.getId(item).getPath().equals(json.getAsString()))
                .findFirst();

        return optionalItem.orElse(null);
    }
}
