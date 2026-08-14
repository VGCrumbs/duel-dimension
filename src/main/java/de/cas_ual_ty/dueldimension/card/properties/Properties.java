package de.cas_ual_ty.dueldimension.card.properties;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.util.JsonKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.List;

public class Properties
{
    public static final Properties DUMMY = new Properties()
    {
        @Override
        public String getImageName(byte imageIndex)
        {
            return "blanc_card";
        }
        
        @Override
        public void addCardType(List<Component> list)
        {
            
        }
    };
    
    static
    {
        Properties.DUMMY.isHardcoded = true;
        Properties.DUMMY.name = "Dummy";
        Properties.DUMMY.id = 0;
        Properties.DUMMY.isIllegal = false;
        Properties.DUMMY.isCustom = true;
        Properties.DUMMY.text = "This is a replacement card!";
        Properties.DUMMY.type = null;
        Properties.DUMMY.images = null;
    }
    
    public boolean isHardcoded;
    public String name;
    public long id;
    public boolean isIllegal;
    public boolean isCustom;
    public String text;
    public Type type;
    public String[] images;

    /**
     * Adds artworks after the printed one.
     * <p>
     * Appended, never inserted: index 0 stays the art this card has always had,
     * so a deck that dressed a copy keeps meaning what it meant. Lives here
     * rather than on the caller because {@code imageIndicesAmt} is derived from
     * this array and the two must not drift.
     */
    public void addArtwork(String[] urls)
    {
        if(urls == null || urls.length == 0)
        {
            return;
        }
        String[] combined = java.util.Arrays.copyOf(images, images.length + urls.length);
        System.arraycopy(urls, 0, combined, images.length, urls.length);
        images = combined;
        imageIndicesAmt = combined.length;
    }
    
    protected int imageIndicesAmt;
    
    public Properties(Properties p0)
    {
        isHardcoded = false;
        name = p0.name;
        id = p0.id;
        isIllegal = p0.isIllegal;
        isCustom = p0.isCustom;
        text = p0.text;
        type = p0.type;
        images = p0.images;
        imageIndicesAmt = images.length;
    }
    
    public Properties(JsonObject j)
    {
        isHardcoded = false;
        // No `imageIndicesAmt = 1` after this call. It used to sit here and it
        // overwrote what readProperties had just worked out from the images
        // array, which made that line dead: the count has to come from the
        // array, and the array is filled in there.
        readAllProperties(j);
    }
    
    public Properties()
    {
        isHardcoded = false;
        imageIndicesAmt = 1;
    }
    
    public void postDBInit()
    {
        
    }
    
    public void readAllProperties(JsonObject j)
    {
        readProperties(j);
    }
    
    public void writeAllProperties(JsonObject j)
    {
        writeProperties(j);
    }
    
    public void readProperties(JsonObject j)
    {
        name = j.get(JsonKeys.NAME).getAsString();
        id = j.get(JsonKeys.ID).getAsLong();
        isIllegal = j.get(JsonKeys.IS_ILLEGAL).getAsBoolean();
        isCustom = j.get(JsonKeys.IS_CUSTOM).getAsBoolean();
        text = j.get(JsonKeys.TEXT).getAsString();
        type = Type.fromString(j.get(JsonKeys.TYPE).getAsString());
        
        JsonArray images = j.get(JsonKeys.IMAGES).getAsJsonArray();
        this.images = new String[images.size()];
        for(int i = 0; i < this.images.length; ++i)
        {
            this.images[i] = images.get(i).getAsString();
        }
        // Set HERE, where the array is filled, rather than in a constructor.
        // A database card reaches its final form through the COPY constructor
        // (DdUtil.buildProperties wraps a plain Properties in a Spell/Trap/
        // Monster one), and that constructor has always used images.length --
        // which is why alternate artwork resolves at all. This line is for the
        // paths that do not: buildProperties returns the plain object itself
        // for a card it cannot classify, and that object used to claim one
        // artwork however many it had, so isAcceptedImageIndex refused every
        // index but 0 and adjustImageIndex folded them back to the printed art.
        this.imageIndicesAmt = this.images.length;
    }
    
    public void writeProperties(JsonObject j)
    {
        j.addProperty(JsonKeys.NAME, name);
        j.addProperty(JsonKeys.ID, id);
        j.addProperty(JsonKeys.IS_ILLEGAL, isIllegal);
        j.addProperty(JsonKeys.IS_CUSTOM, isCustom);
        j.addProperty(JsonKeys.TEXT, text);
        j.addProperty(JsonKeys.TYPE, type.name);
        
        JsonArray images = new JsonArray();
        for(String image : this.images)
        {
            images.add(image);
        }
        j.add(JsonKeys.IMAGES, images);
    }
    
    public boolean getIsHardcoded()
    {
        return isHardcoded;
    }
    
    public boolean getIsSpell()
    {
        return getType() == Type.SPELL;
    }
    
    public boolean getIsTrap()
    {
        return getType() == Type.TRAP;
    }
    
    public boolean getIsMonster()
    {
        return getType() == Type.MONSTER;
    }
    
    public boolean getIsInExtraDeck()
    {
        return false;
    }
    
    public int getImageIndicesAmt()
    {
        return imageIndicesAmt;
    }
    
    public boolean isAcceptedImageIndex(byte imageIndex)
    {
        return imageIndex >= 0 && imageIndex < getImageIndicesAmt();
    }
    
    public byte adjustImageIndex(byte imageIndex)
    {
        if(!isAcceptedImageIndex(imageIndex))
        {
            return 0;
        }
        else
        {
            return imageIndex;
        }
    }
    
    /**
     * Where this card's art can be downloaded from, or null if nowhere.
     * <p>
     * A CUSTOM card legitimately has no source: its art is authored and put on
     * disk directly, so its {@code images} list is empty. Indexing that blindly
     * threw {@code ArrayIndexOutOfBoundsException} out of the deck editor's
     * draw, taking the client down the first time such a card was shown -- and
     * the URL is only ever needed to FETCH a file that is already there.
     */
    public String getImageURL(byte imageIndex)
    {
        String[] images = getImages();
        if(images == null || images.length == 0)
        {
            return null;
        }
        int index = adjustImageIndex(imageIndex);
        return index >= 0 && index < images.length ? images[index] : null;
    }
    
    public String getImageName(byte imageIndex)
    {
        return getId() + "_" + adjustImageIndex(imageIndex);
    }
    
    public String getInfoImageName(byte imageIndex)
    {
        return DuelDimension.proxy.addCardInfoTag(getImageName(imageIndex));
    }
    
    public String getItemImageName(byte imageIndex)
    {
        return DuelDimension.proxy.addCardItemTag(getImageName(imageIndex));
    }
    
    public String getMainImageName(byte imageIndex)
    {
        return DuelDimension.proxy.addCardMainTag(getImageName(imageIndex));
    }
    
    public Identifier getInfoImageResourceLocation(byte imageIndex)
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/item/" + DuelDimension.proxy.getCardInfoReplacementImage(this, adjustImageIndex(imageIndex)) + ".png");
    }
    
    public Identifier getItemImageResourceLocation(byte imageIndex)
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "item/" + getItemImageName(imageIndex));
    }
    
    public Identifier getMainImageResourceLocation(byte imageIndex)
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/item/" + DuelDimension.proxy.getCardMainReplacementImage(this, adjustImageIndex(imageIndex)) + ".png");
    }
    
    public void addInformation(List<Component> list)
    {
        addHeader(list);
        list.add(Component.empty());
        addText(list);
    }
    
    /**
     * Everything the card IS, without its name: its classifications and its
     * stats, in the order a printed card lists them.
     * <p>
     * Separate from {@link #addHeader} because that one is the tooltip's
     * shape -- name first, then the type -- and because it leaves a monster's
     * species out. The species lives in {@code addMonsterTextHeader}, which
     * {@link #addText} calls, so anything that took its facts from the header
     * and its body from {@code getText()} silently dropped "Spellcaster" and
     * showed a monster as no more than "Effect Monster". Three screens did.
     * <p>
     * Overridden rather than instanceof-tested, so a card kind added later
     * says what it is here and every preview picks it up.
     */
    public void addFacts(List<Component> list)
    {
        if(getCustom())
        {
            list.add(Component.literal("Custom Card")
                .setStyle(Style.EMPTY.applyFormat(ChatFormatting.RED)));
        }
        addFactLines(list);
    }

    /** The classification and stat lines; see {@link #addFacts}. */
    protected void addFactLines(List<Component> list)
    {
        addCardType(list);
    }

    public void addHeader(List<Component> list)
    {
        list.add(Component.literal(getName()));
        
        if(isCustom)
        {
            list.add(Component.literal("Custom Card").setStyle(Style.EMPTY.applyFormat(ChatFormatting.RED)));
        }
        
        list.add(Component.empty());
        addCardType(list);
    }
    
    public void addText(List<Component> list)
    {
        list.add(Component.literal(getText()));
    }
    
    public void addCardType(List<Component> list)
    {
        list.add(Component.literal(type.name));
    }
    
    // --- Getters ---
    
    public String getName()
    {
        return name;
    }
    
    public long getId()
    {
        return id;
    }
    
    public boolean getIllegal()
    {
        return isIllegal;
    }
    
    public boolean getCustom()
    {
        return isCustom;
    }
    
    public String getText()
    {
        return text;
    }
    
    public Type getType()
    {
        return type;
    }
    
    public String[] getImages()
    {
        return images;
    }
}
