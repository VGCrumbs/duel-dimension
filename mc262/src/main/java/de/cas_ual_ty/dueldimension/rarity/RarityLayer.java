package de.cas_ual_ty.dueldimension.rarity;

import com.google.gson.JsonObject;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.util.JsonKeys;
import net.minecraft.resources.Identifier;

public class RarityLayer
{
    public String texture;
    public RarityLayerType type;
    
    public RarityLayer(String texture, RarityLayerType type)
    {
        this.texture = texture;
        this.type = type;
    }
    
    public RarityLayer(JsonObject json)
    {
        this(json.get(JsonKeys.IMAGE).getAsString(), RarityLayerType.fromString(json.get(JsonKeys.TYPE).getAsString()));
    }
    
    public Identifier getMainImageResourceLocation()
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/item/" + DuelDimension.proxy.getRarityMainImage(this) + ".png");
    }
    
    public Identifier getInfoImageResourceLocation()
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/item/" + DuelDimension.proxy.getRarityInfoImage(this) + ".png");
    }
    
    @Override
    public String toString()
    {
        return "RarityLayer{" +
                "texture='" + texture + '\'' +
                ", type=" + type +
                '}';
    }
}
