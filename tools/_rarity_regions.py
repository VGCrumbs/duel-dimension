"""Rarities foil different PARTS of a card, not just more or less of it.

SOURCE, stated plainly: this is from knowledge of the physical cards, NOT from
anything verified online today. Web search and fetch are both unavailable in
this environment -- search returns a tool configuration error and fetches come
back 403 and 402 -- so nothing here was checked against a reference. The
region proportions in particular are eyeballed against the standard card frame
and are the first thing to correct if they look wrong.

What differs between rarities in the real game is WHERE the foil is:

  Common            nothing.
  Rare              the NAME only, in silver. The card is otherwise matte, which
                    is why a Rare looks almost like a Common until it is tilted.
  Super Rare        the ARTWORK only. Name stays black.
  Ultra Rare        artwork AND a gold name -- two regions, not one stronger one.
  Ultimate Rare     relief: the art and border are raised, so it catches light
                    in bands rather than sweeping.
  Secret Rare       the WHOLE card, in fine vertical lines.
  Gold / Platinum   name, border and frame in metal.
  Ghost / Starlight
  / Collector's     the whole card with a coarser, more scattered pattern.

Modelling that is what makes the rarities distinguishable at a glance. A single
brightness dial cannot: a Super Rare and an Ultra Rare differ by the name being
gold, and no amount of sheen says that.
"""
import io

p = "src/main/java/de/cas_ual_ty/dueldimension/cardbinder/CardRarityFoil.java"
s = io.open(p, encoding="utf-8").read()

old_enum_start = s.index("    /** No treatment. A common is a piece of cardboard and should look like one. */")
old_enum_end = s.index("    public static final Identifier FOIL =")

new = '''    /** No treatment. A common is a piece of cardboard and should look like one. */
    NONE(Region.NONE, 0F, 0F, 0F),
    /**
     * The name, in silver, and nothing else.
     * <p>
     * A Rare is the one people mistake for a Common until they tilt it, and
     * foiling the whole card here would erase that distinction entirely.
     */
    RARE(Region.NAME, 0.5F, 0F, 0.7F),
    /** The artwork, holographic. The name stays black. */
    SUPER(Region.ART, 0.55F, 0.25F, 1F),
    /** The artwork holographic AND the name in gold: two regions, not one louder. */
    ULTRA(Region.ART_AND_NAME, 0.6F, 0.3F, 1.1F),
    /**
     * Relief. The art and border are raised on a real Ultimate, so the light
     * comes in bands as the angle passes rather than sweeping smoothly -- which
     * is why this one travels fast and sparkles rather than glowing.
     */
    ULTIMATE(Region.ART_AND_BORDER, 0.45F, 0.7F, 2.2F),
    /** Fine lines across the whole card. */
    SECRET(Region.WHOLE, 0.7F, 0.45F, 1.5F),
    /** Metal on the name, border and frame. */
    GOLD(Region.NAME_AND_BORDER, 0.75F, 0.3F, 0.9F),
    /** The whole card, coarser and more scattered: the loud ones. */
    PRISMATIC(Region.WHOLE, 0.95F, 0.8F, 2.6F);

    /**
     * Which part of the card the foil covers, as a fraction of its face.
     * <p>
     * Proportions are against the standard card frame. They are approximate --
     * see this type's notes on sourcing -- and are deliberately in one place so
     * correcting them corrects every rarity at once.
     */
    public enum Region
    {
        NONE(),
        /** The title bar across the top. */
        NAME(0.06F, 0.04F, 0.94F, 0.115F),
        /** The picture window. */
        ART(0.10F, 0.165F, 0.90F, 0.62F),
        ART_AND_NAME(0.10F, 0.165F, 0.90F, 0.62F, 0.06F, 0.04F, 0.94F, 0.115F),
        /** Picture plus the frame around it. */
        ART_AND_BORDER(0.04F, 0.145F, 0.96F, 0.64F),
        NAME_AND_BORDER(0.03F, 0.03F, 0.97F, 0.13F, 0.03F, 0.62F, 0.97F, 0.97F),
        WHOLE(0F, 0F, 1F, 1F);

        /** Each run of four is one rectangle: x0, y0, x1, y1 across the face. */
        public final float[] rects;

        Region(float... rects)
        {
            this.rects = rects;
        }

        public boolean any()
        {
            return rects.length >= 4;
        }
    }

    public final Region region;

'''
s = s[:old_enum_start] + new + s[old_enum_end:]

# the fields the constructor now takes
s = s.replace("""    CardRarityFoil(float sheen, float sparkle, float travel)
    {
        this.sheen = sheen;
        this.sparkle = sparkle;
        this.travel = travel;
    }

    public boolean any()
    {
        return sheen > 0F || sparkle > 0F;
    }""",
"""    CardRarityFoil(Region region, float sheen, float sparkle, float travel)
    {
        this.region = region;
        this.sheen = sheen;
        this.sparkle = sparkle;
        this.travel = travel;
    }

    public boolean any()
    {
        return region.any() && (sheen > 0F || sparkle > 0F);
    }""", 1)

# the matcher, now naming the specific treatments
s = s.replace('''        if(name.contains("prismatic") || name.contains("starlight") || name.contains("ghost")
            || name.contains("collector"))
        {
            return PRISMATIC;
        }
        if(name.contains("secret") || name.contains("ultimate") || name.contains("platinum")
            || name.contains("gold") || name.contains("starfoil"))
        {
            return FULL;
        }
        if(name.contains("ultra") || name.contains("super") || name.contains("holographic"))
        {
            return HOLO;
        }
        // A plain "Rare" only. Checked last so it cannot swallow the rarities
        // above, every one of which also ends in the word.
        if(name.contains("rare") && !name.contains("common"))
        {
            return SUBTLE;
        }
        return NONE;''',
'''        if(name.contains("prismatic") || name.contains("starlight") || name.contains("ghost")
            || name.contains("collector"))
        {
            return PRISMATIC;
        }
        if(name.contains("ultimate") || name.contains("relief"))
        {
            return ULTIMATE;
        }
        if(name.contains("gold") || name.contains("platinum"))
        {
            return GOLD;
        }
        if(name.contains("secret"))
        {
            return SECRET;
        }
        // Ultra before Super: "Ultra Rare" contains neither the other word, but
        // keeping them adjacent and ordered is what stops a later edit swapping
        // them by accident.
        if(name.contains("ultra"))
        {
            return ULTRA;
        }
        if(name.contains("super") || name.contains("holographic") || name.contains("starfoil"))
        {
            return SUPER;
        }
        // A plain "Rare" only. Checked last so it cannot swallow the rarities
        // above, every one of which also ends in the word.
        if(name.contains("rare") && !name.contains("common"))
        {
            return RARE;
        }
        return NONE;''', 1)

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("CardRarityFoil: eight treatments over card regions")

# ---- the screen foils only the region ----
p = "src/main/java/de/cas_ual_ty/dueldimension/cardbinder/CardPreviewScreen.java"
s = io.open(p, encoding="utf-8").read()

s = s.replace("""        mesh(pose, collector, CardRarityFoil.FOIL, scale, slide, lift, 1F, 1F,
            foil.sheen * edgeOn());

        if(foil.sparkle > 0F)
        {
            // Faster and the other way, so the specks do not travel locked to
            // the colour bands and give the sheet away as one image.
            mesh(pose, collector, CardRarityFoil.SPARKLE, scale,
                -slide * 1.7F, lift * 1.3F, 1.5F, 1.5F, foil.sparkle * edgeOn());
        }""",
"""        // Only over the parts of the card this rarity actually foils. A Super
        // Rare shines in its picture and nowhere else; a Rare only in its name.
        // That difference is what makes the rarities tellable apart at a glance,
        // which brightness alone never manages.
        float[] rects = foil.region.rects;
        for(int r = 0; r + 3 < rects.length; r += 4)
        {
            float x0 = rects[r];
            float y0 = rects[r + 1];
            float x1 = rects[r + 2];
            float y1 = rects[r + 3];

            region(pose, collector, CardRarityFoil.FOIL, scale, x0, y0, x1, y1,
                slide, lift, 1F, 1F, foil.sheen * edgeOn());

            if(foil.sparkle > 0F)
            {
                // Faster and the other way, so the specks do not travel locked
                // to the colour bands and give the sheet away as one image.
                region(pose, collector, CardRarityFoil.SPARKLE, scale, x0, y0, x1, y1,
                    -slide * 1.7F, lift * 1.3F, 1.5F, 1.5F, foil.sparkle * edgeOn());
            }
        }""", 1)

# a region-limited mesh, on top of the full-face one
s = s.replace("""    /**
     * The card, as a grid of quads rather than one.""",
"""    /** A foil sheet over one rectangle of the card's face. */
    private void region(com.mojang.blaze3d.vertex.PoseStack pose,
        net.minecraft.client.renderer.SubmitNodeCollector collector, Identifier texture,
        float scale, float x0, float y0, float x1, float y1,
        float u0, float v0, float uSpan, float vSpan, float alpha)
    {
        for(int row = 0; row < STEPS; row++)
        {
            float ty = y0 + (y1 - y0) * (row / (float)STEPS);
            float ty1 = y0 + (y1 - y0) * ((row + 1) / (float)STEPS);
            for(int column = 0; column < STEPS; column++)
            {
                float tx = x0 + (x1 - x0) * (column / (float)STEPS);
                float tx1 = x0 + (x1 - x0) * ((column + 1) / (float)STEPS);

                float[] a = project(tx, ty, scale);
                float[] b = project(tx1, ty, scale);
                float[] c = project(tx1, ty1, scale);
                float[] d = project(tx, ty1, scale);

                // The sheet is sampled across the REGION, so a small region gets
                // a small slice of it rather than the whole rainbow squeezed
                // into a name bar.
                float su = column / (float)STEPS;
                float su1 = (column + 1) / (float)STEPS;
                float sv = row / (float)STEPS;
                float sv1 = (row + 1) / (float)STEPS;

                FieldQuad.drawCorners(pose, collector, texture,
                    new FieldQuad.Corners(a[0], a[1], b[0], b[1], c[0], c[1], d[0], d[1]),
                    u0 + uSpan * su, v0 + vSpan * sv,
                    u0 + uSpan * su1, v0 + vSpan * sv1, 1F, alpha);
            }
        }
    }

    /**
     * The card, as a grid of quads rather than one.""", 1)

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("CardPreviewScreen: foil applied per region")
