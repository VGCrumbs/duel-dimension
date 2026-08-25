package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.CdbCardProvider;
import de.cas_ual_ty.dueldimension.ocg.HeadlessDuelRunner;
import de.cas_ual_ty.dueldimension.ocg.OcgApi;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.RawMessage;
import de.cas_ual_ty.dueldimension.ocg.ResponseSource;
import de.cas_ual_ty.dueldimension.ocg.bot.HeuristicBot;
import de.cas_ual_ty.dueldimension.ocg.deck.StarterDecks;
import de.cas_ual_ty.dueldimension.ocg.msg.DuelMessage;
import de.cas_ual_ty.dueldimension.ocg.query.BoardObserver;
import de.cas_ual_ty.dueldimension.ocg.text.DescriptionTable;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The GUI path has to satisfy two things at once: a player must be offered
 * something meaningful for every prompt, and whatever they click must encode
 * back into a response the engine accepts. This plays whole duels through the
 * translator, choosing options the way a person clicking would.
 */
class PromptTranslatorTest
{
    @Test
    void effectQuestionFillsEdoproWideStringPlaceholders()
    {
        assertEquals("Activate the Trigger Effect of \"Sangan\" from [Graveyard]?",
            PromptTranslator.formatEffectQuestion(
                "Activate the Trigger Effect of \"%ls\" from [%ls]?",
                "Sangan", "Graveyard"));
    }

    private static Path lib()
    {
        return Path.of(System.getProperty("ocg.lib", "native/ocgcore.dll"));
    }

    private static Path scripts()
    {
        return Path.of(System.getProperty("ocg.scripts", "C:/ProjectIgnis/script"));
    }

    private static Path cdb()
    {
        return Path.of(System.getProperty("ocg.cdb", "C:/ProjectIgnis/expansions/cards.cdb"));
    }

    private static Path stringsConf()
    {
        return Path.of(System.getProperty("ocg.strings", "C:/ProjectIgnis/config/strings.conf"));
    }

    /** Plays by picking randomly among the options the GUI would show. */
    private static class ClickingPlayer implements ResponseSource
    {
        private final PromptTranslator translator;
        private final Random random;
        private final java.util.Collection<de.cas_ual_ty.dueldimension.ocg.OcgCard> allCards;
        final Map<String, Integer> promptsSeen = new LinkedHashMap<>();
        final Map<String, Integer> unpresentable = new LinkedHashMap<>();
        final Map<String, Integer> autoAnswered = new LinkedHashMap<>();
        int answered;
        private BoardObserver board;

        ClickingPlayer(PromptTranslator translator, long seed,
            java.util.Collection<de.cas_ual_ty.dueldimension.ocg.OcgCard> allCards)
        {
            this.translator = translator;
            random = new Random(seed);
            this.allCards = allCards;
        }

        @Override
        public void onDuelStart(int playerIndex, BoardObserver observer)
        {
            board = observer;
        }

        @Override
        public byte[] respond(RawMessage raw)
        {
            DuelMessage decoded = DuelMessage.decode(raw);
            EnginePrompt prompt = translator.toPrompt(decoded, BoardSnapshot.of(board.observe()));
            if(prompt == null
                || (prompt.options().isEmpty() && prompt.kind() != EnginePrompt.Kind.DECLARE_CARD))
            {
                byte[] automatic = translator.autoAnswer(decoded);
                if(automatic != null)
                {
                    autoAnswered.merge(raw.name(), 1, Integer::sum);
                    return automatic;
                }
                unpresentable.merge(raw.name(), 1, Integer::sum);
                return null;
            }
            promptsSeen.merge(raw.name(), 1, Integer::sum);

            int[] chosen;
            int declared = 0;
            switch(prompt.kind())
            {
                case DECLARE_CARD ->
                {
                    // As the search box would: find any card the filter allows.
                    chosen = new int[] {0};
                    DuelMessage.AnnounceCard announce = (DuelMessage.AnnounceCard)decoded;
                    for(de.cas_ual_ty.dueldimension.ocg.OcgCard card : allCards)
                    {
                        if(de.cas_ual_ty.dueldimension.ocg.msg.DeclarableFilter
                            .isDeclarable(card, announce.filter()))
                        {
                            declared = card.code();
                            break;
                        }
                    }
                }
                case COUNTERS ->
                {
                    // Distribute the requested total greedily, as +/- would.
                    chosen = new int[prompt.options().size()];
                    int remaining = prompt.minSelect();
                    for(int i = 0; i < chosen.length && remaining > 0; i++)
                    {
                        chosen[i] = Math.min(remaining, prompt.options().get(i).max());
                        remaining -= chosen[i];
                    }
                }
                case SORT ->
                {
                    chosen = new int[prompt.options().size()];
                    for(int i = 0; i < chosen.length; i++)
                    {
                        chosen[i] = i; // keep order — any permutation is legal
                    }
                }
                default ->
                {
                    if(prompt.isSingleChoice())
                    {
                        chosen = new int[] {random.nextInt(prompt.options().size())};
                    }
                    else
                    {
                        int count = Math.max(prompt.minSelect(), 1);
                        count = Math.min(count, prompt.options().size());
                        chosen = new int[count];
                        List<Integer> pool = new java.util.ArrayList<>();
                        for(int i = 0; i < prompt.options().size(); i++)
                        {
                            pool.add(i);
                        }
                        java.util.Collections.shuffle(pool, random);
                        for(int i = 0; i < count; i++)
                        {
                            chosen[i] = pool.get(i);
                        }
                    }
                }
            }
            answered++;
            return translator.toResponse(decoded, chosen, declared);
        }
    }

    @Test
    void everyPromptBecomesClickableOptionsAndValidResponses() throws Exception
    {
        assumeTrue(Files.isRegularFile(lib()), "native core not present");
        assumeTrue(Files.isRegularFile(scripts().resolve("constant.lua")), "CardScripts not present");
        assumeTrue(Files.isRegularFile(cdb()), "cards.cdb not present");

        OcgApi api = OcgApi.load(lib());
        CdbCardProvider cards = new CdbCardProvider(List.of(cdb()));
        DescriptionTable text = new DescriptionTable(
            Files.isRegularFile(stringsConf()) ? stringsConf() : null, List.of(cdb()));
        PromptTranslator translator = new PromptTranslator(cards, text);

        Map<String, Integer> allPrompts = new LinkedHashMap<>();
        Map<String, Integer> allUnpresentable = new LinkedHashMap<>();
        Map<String, Integer> allAuto = new LinkedHashMap<>();
        int completed = 0;
        int totalAnswers = 0;

        for(int seed = 1; seed <= 8; seed++)
        {
            ClickingPlayer human = new ClickingPlayer(translator, seed * 977L, cards.all());
            HeadlessDuelRunner.DuelTrace trace = HeadlessDuelRunner.builder(api)
                .seed(new long[] {seed, seed * 31 + 5, seed * 131 + 9, ~seed})
                .cards(cards)
                .scripts(HeadlessDuelRunner.cardScriptsDirectory(scripts()))
                .deck(0, StarterDecks.YUGI.load().toRunnerDeck())
                .deck(1, StarterDecks.KAIBA.load().toRunnerDeck())
                .responder(0, human)
                .responder(1, new HeuristicBot(seed * 3L, cards, cards.all()))
                .build()
                .run(20000);

            assertFalse(trace.sawMessage(OcgConstants.MSG_RETRY),
                "seed " + seed + ": engine rejected a response built from a GUI click");

            human.promptsSeen.forEach((name, count) -> allPrompts.merge(name, count, Integer::sum));
            human.unpresentable.forEach((name, count) -> allUnpresentable.merge(name, count, Integer::sum));
            human.autoAnswered.forEach((name, count) -> allAuto.merge(name, count, Integer::sum));
            totalAnswers += human.answered;
            if(trace.completed)
            {
                completed++;
            }
        }

        System.out.println("Prompt types presented to the player: " + allPrompts);
        System.out.println("Prompt types answered automatically: " + allAuto);
        System.out.println("Prompt types NOT presentable: " + allUnpresentable);
        System.out.println("Total clicks answered: " + totalAnswers + ", duels completed: " + completed + "/8");

        assertTrue(allPrompts.containsKey("MSG_SELECT_IDLECMD"), "never offered a main-phase choice");
        assertTrue(allPrompts.containsKey("MSG_SELECT_BATTLECMD"), "never offered a battle choice");
        assertTrue(totalAnswers > 100, "suspiciously few decisions: " + totalAnswers);
        assertTrue(allUnpresentable.isEmpty(),
            "some prompts could not be shown to a player: " + allUnpresentable);
        assertTrue(completed >= 6, "only " + completed + "/8 duels finished with a human seat");
    }
}
