package de.cas_ual_ty.ydm.ocg;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads static card data for the rules engine from one or more BabelCDB /
 * EDOPro .cdb files (SQLite, "datas" table) into memory.
 * <p>
 * ~13k cards is a few MB — loading everything up front keeps the card reader
 * callback allocation-free and thread-trivial, and means the SQLite driver is
 * only needed during loading. Later files override earlier ones by card code
 * (same as EDOPro's load order, e.g. cards.cdb then patch cdbs).
 * <p>
 * Field packing follows the established cdb conventions (see EDOPro's
 * DataManager): four 16-bit setcodes packed into the 64-bit setcode column,
 * pendulum scales packed into the level column, and Link monsters storing
 * their markers in the def column.
 */
public class CdbCardProvider implements OcgDuel.CardProvider
{
    private final Map<Integer, OcgCard> cards = new HashMap<>();

    public CdbCardProvider(List<Path> cdbFiles) throws SQLException
    {
        for(Path cdb : cdbFiles)
        {
            loadFile(cdb);
        }
    }

    private void loadFile(Path cdb) throws SQLException
    {
        try(Connection connection = DriverManager.getConnection("jdbc:sqlite:" + cdb.toAbsolutePath());
            Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery("SELECT id, alias, setcode, type, atk, def, level, race, attribute FROM datas"))
        {
            while(rows.next())
            {
                OcgCard card = decodeRow(rows);
                cards.put(card.code(), card);
            }
        }
    }

    private static OcgCard decodeRow(ResultSet rows) throws SQLException
    {
        int code = rows.getInt("id");
        int alias = rows.getInt("alias");
        long setcodePacked = rows.getLong("setcode");
        int type = rows.getInt("type");
        int atk = rows.getInt("atk");
        int defRaw = rows.getInt("def");
        long levelPacked = rows.getLong("level");
        long race = rows.getLong("race");
        int attribute = rows.getInt("attribute");

        // Up to four 16-bit setcodes packed little-end-first.
        List<Integer> setcodes = new ArrayList<>(4);
        for(int i = 0; i < 4; i++)
        {
            int setcode = (int)((setcodePacked >>> (i * 16)) & 0xFFFF);
            if(setcode != 0)
            {
                setcodes.add(setcode);
            }
        }

        // level column: 0xLLRR00VV -> left scale, right scale, level/rank/rating.
        int level = (int)(levelPacked & 0xFFFF);
        int lscale = (int)((levelPacked >>> 24) & 0xFF);
        int rscale = (int)((levelPacked >>> 16) & 0xFF);

        // Link monsters: def column holds the link markers, DEF is always 0.
        int def = defRaw;
        int linkMarker = 0;
        if((type & OcgConstants.TYPE_LINK) != 0)
        {
            linkMarker = defRaw;
            def = 0;
        }

        return new OcgCard(code, alias,
            setcodes.stream().mapToInt(Integer::intValue).toArray(),
            type, level, attribute, race, atk, def, lscale, rscale, linkMarker);
    }

    @Override
    public OcgCard get(int code)
    {
        return cards.get(code);
    }

    public int size()
    {
        return cards.size();
    }
}
