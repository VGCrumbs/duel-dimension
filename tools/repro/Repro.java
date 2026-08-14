import de.cas_ual_ty.dueldimension.ocg.*;
import de.cas_ual_ty.dueldimension.ocg.query.CardView;
import de.cas_ual_ty.dueldimension.ocg.query.QueryParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Repro
{
    static final int ARION = 40706444;

    public static void main(String[] args) throws Exception
    {
        Path lib = Path.of(args[0]);
        Path cdb = Path.of(args[1]);
        Path scripts = Path.of(args[2]);

        OcgApi api = OcgApi.load(lib);
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb));
        System.out.println("cdb=" + cdb + " rows=" + cards.size()
            + " arion=" + cards.get(ARION));

        OcgDuel.ScriptProvider sp = HeadlessDuelRunner.cardScriptsDirectory(scripts);
        List<String> engineLog = new ArrayList<>();
        boolean pseudo = args.length > 3 && args[3].equals("pseudo");
        int arionAt = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        System.out.println("variant: pseudoShuffle=" + pseudo + " arionExtraIndex=" + arionAt);
        OcgDuel duel = OcgDuel.create(api, new long[] {1, 2, 3, 4},
            OcgConstants.DUEL_MODE_MR5 | (pseudo ? OcgConstants.DUEL_PSEUDO_SHUFFLE : 0),
            OcgDuel.PlayerConfig.DEFAULT, OcgDuel.PlayerConfig.DEFAULT,
            cards, sp, (m, t) -> engineLog.add("[" + t + "] " + m));

        // 40 main-deck cards that DO exist in the cdb (Starter Deck: Yugi).
        int[] mainCodes = {
            93221206, 86325596, 91152256, 41218256, 77087109, 28279543, 46986414,
            66672569, 46128076, 6368038, 13039848, 13429800, 46474915, 54652250,
            47060154, 15025844, 26202165, 49218300, 70781052, 87557188, 46461247,
            13945283, 87796900, 36304921, 40619825, 14087893, 91595718, 72892473,
            4031928, 53129443, 19159413, 84257640, 66788016, 85602018, 83764719,
            51482758, 68005187, 59197169, 44209392, 50045299};
        List<Integer> main = new ArrayList<>();
        for(int c : mainCodes)
        {
            main.add(c);
        }
        // Extra deck: three real Xyz/Fusion the cdb knows, plus Arion LAST in the
        // list -- index 0 is what registerDecks pushes last.
        List<Integer> extra = new ArrayList<>(List.of(59969392, 17377751, 95040215));
        extra.add(Math.min(arionAt, extra.size()), ARION);
        System.out.println("extra list as registered (index 0 == pushed last): " + extra);

        for(int p = 0; p < 2; p++)
        {
            for(int i = main.size() - 1; i >= 0; i--)
            {
                duel.newCard(p, 0, main.get(i), p, OcgConstants.LOCATION_DECK, 0,
                    OcgConstants.POS_FACEDOWN_DEFENSE);
            }
            for(int i = extra.size() - 1; i >= 0; i--)
            {
                duel.newCard(p, 0, extra.get(i), p, OcgConstants.LOCATION_EXTRA, 0,
                    OcgConstants.POS_FACEDOWN_DEFENSE);
            }
        }

        System.out.println("registered: main=" + main.size() + " extra=" + extra.size());
        System.out.println("BEFORE start  deck=" + duel.queryCount(0, OcgConstants.LOCATION_DECK)
            + " extra=" + duel.queryCount(0, OcgConstants.LOCATION_EXTRA));

        duel.start();
        int status = duel.process();
        duel.getMessages();

        System.out.println("AFTER start   deck=" + duel.queryCount(0, OcgConstants.LOCATION_DECK)
            + " extra=" + duel.queryCount(0, OcgConstants.LOCATION_EXTRA)
            + " hand=" + duel.queryCount(0, OcgConstants.LOCATION_HAND)
            + " status=" + status);

        System.out.println("HAND: " + codes(duel, OcgConstants.LOCATION_HAND));
        System.out.println("EXTRA: " + codes(duel, OcgConstants.LOCATION_EXTRA));

        for(String line : engineLog)
        {
            if(line.contains(String.valueOf(ARION)) || line.contains("unknown"))
            {
                System.out.println("ENGINE LOG " + line);
            }
        }
        duel.close();
    }

    static List<Integer> codes(OcgDuel duel, int location)
    {
        OcgStructs.OcgQueryInfo info = new OcgStructs.OcgQueryInfo();
        info.flags = OcgConstants.QUERY_CODE;
        info.con = (byte)0;
        info.loc = location;
        info.seq = 0;
        info.overlay_seq = 0;
        List<Integer> out = new ArrayList<>();
        for(CardView v : QueryParser.parseLocation(duel.queryLocation(info)))
        {
            out.add(v == null ? null : v.code());
        }
        return out;
    }
}
