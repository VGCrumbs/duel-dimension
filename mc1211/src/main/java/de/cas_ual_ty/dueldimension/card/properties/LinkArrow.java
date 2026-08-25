package de.cas_ual_ty.dueldimension.card.properties;

import de.cas_ual_ty.dueldimension.card.CardLine;
import de.cas_ual_ty.dueldimension.util.DdUtil;

import java.util.ArrayList;
import java.util.List;

public enum LinkArrow
{
    TOP_LEFT("Top-Left", 0, "◸", "◤"), TOP("Top", 1, "△", "▲"), TOP_RIGHT("Top-Right", 2, "◹", "◥"), RIGHT("Right", 3, "▷", "▶"), BOTTOM_RIGHT("Bottom-Right", 4, "◿", "◢"), BOTTOM("Bottom", 5, "▽", "▼"), BOTTOM_LEFT("Bottom-Left", 6, "◺", "◣"), LEFT("Left", 7, "◁", "◀");
    
    public final String name;
    public final int index;
    public final int number;
    
    private final String symbolUnactive;
    private final String symbolActive;
    
    LinkArrow(String name, int index, String textUnactive, String textActive)
    {
        this.name = name;
        this.index = index;
        number = DdUtil.getPow2(index);
        symbolUnactive = textUnactive;
        symbolActive = textUnactive;
    }
    
    public boolean isContainedIn(short linkNumber)
    {
        return (linkNumber % DdUtil.getPow2(index + 1)) >= number;
    }
    
    public static final LinkArrow[] VALUES = LinkArrow.values();
    
    public static LinkArrow fromString(String s)
    {
        for(LinkArrow m : LinkArrow.VALUES)
        {
            if(m.name.equals(s))
            {
                return m;
            }
        }
        
        return null;
    }
    
    /**
     * The link arrows as three rows of symbols, lit where the card has one.
     * <p>
     * Produces {@link CardLine}s rather than components. Each row is ONE line
     * built from several differently-coloured pieces -- a lit arrow beside an
     * unlit one -- which is why a line is a list of segments and not a string
     * with a colour: splitting these into a line each would stack eight arrows
     * vertically instead of drawing the three-by-three box they represent.
     *
     * @param unactive the colour of an arrow this card does not have
     * @param active   the colour of one it does
     */
    public static List<CardLine> buildSymbolsString(List<LinkArrow> arrows,
        CardLine.Colour unactive, CardLine.Colour active, String joiner)
    {
        List<CardLine> list = new ArrayList<>(3);

        // Top row
        List<CardLine.Segment> row = new ArrayList<>();
        for(int i = 0; i < 3; ++i)
        {
            row.add(symbol(arrows, LinkArrow.VALUES[i], unactive, active));
            if(i < 2)
            {
                row.add(new CardLine.Segment(joiner, CardLine.Colour.DEFAULT));
            }
        }
        list.add(new CardLine(List.copyOf(row)));

        // Middle row -- left and right only, with the centre left as spacing.
        row = new ArrayList<>();
        row.add(symbol(arrows, LEFT, unactive, active));
        row.add(new CardLine.Segment(joiner + "" + joiner, CardLine.Colour.DEFAULT));
        row.add(symbol(arrows, RIGHT, unactive, active));
        list.add(new CardLine(List.copyOf(row)));

        // Bottom row, walked backwards so it reads left to right on screen.
        row = new ArrayList<>();
        for(int i = 6; i > 3; --i)
        {
            row.add(symbol(arrows, LinkArrow.VALUES[i], unactive, active));
            if(i > 4)
            {
                row.add(new CardLine.Segment(joiner, CardLine.Colour.DEFAULT));
            }
        }
        list.add(new CardLine(List.copyOf(row)));

        return list;
    }

    /** One arrow, lit or not. */
    private static CardLine.Segment symbol(List<LinkArrow> arrows, LinkArrow arrow,
        CardLine.Colour unactive, CardLine.Colour active)
    {
        return arrows.contains(arrow)
            ? new CardLine.Segment(arrow.symbolActive, active)
            : new CardLine.Segment(arrow.symbolUnactive, unactive);
    }
}
