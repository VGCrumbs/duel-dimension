"""Choosing from a pile names the card first.

EDOPro never summons straight out of the Extra Deck menu. Clicking a PILE rather
than a single card sets list_command, so pressing "Special Summon" does not
answer the engine -- it filters the pile to the cards carrying that command and
opens the card list, art and all, with a Cancel. Only clicking one of those cards
sends the response, and materials are chosen after that.

This tree answered immediately instead. With three summonable Extra Deck monsters
the menu showed three rows all reading "Special Summon", indistinguishable, and
committed blind on a click. With one it never said which monster it was about to
summon at all.

The card code is present on those entries -- playerop.cpp writes pcard->data.code
unconditionally, with no face-down or location masking -- so the list is built
client-side with nothing new from the engine. Nor does it leak anything: ocgcore
only lists what is legally summonable this instant, which is public in the real
game.
"""
import io

def sub(old, new, label):
    global s
    if new in s:
        print("  skip (already applied):", label)
        return
    assert old in s, "anchor missing: " + label
    s = s.replace(old, new, 1)
    print("  ok:", label)

p = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/EngineDuelScreen.java"
s = io.open(p, encoding="utf-8").read()

# ---------- state ----------
sub("""    private int previewCode;""",
    """    private int previewCode;
    /**
     * The options behind one command chosen from a pile, or null.
     * <p>
     * A pile is a stack of face-down cards, so "Special Summon" off the Extra
     * Deck is a verb with no visible subject. When one is picked, these are the
     * cards it could mean, and the picker shows them until one is chosen.
     */
    private List<Integer> pileChoices;
    /** What was picked to get here — "Special Summon" — for the picker's header. */
    private String pileChoicesLabel;""", "pileChoices state")

# ---------- the picker shows them ----------
sub("""        picker = hidden;""",
    """        // A pile's command list wins: the player has already said "Special
        // Summon" and is now being asked which card, and that question outlives
        // the rebuild that follows the click.
        picker = pileChoices != null ? pileChoices : hidden;""", "picker prefers pileChoices")

sub("""            selected.clear();
            sortOrder.clear();
            menuAnchor = null;
            answered = false;""",
    """            selected.clear();
            sortOrder.clear();
            menuAnchor = null;
            answered = false;
            // A new question; the old one's card list means nothing now.
            pileChoices = null;
            pileChoicesLabel = null;""", "clear on new prompt")

# ---------- the menu offers the verb once, and opens the list ----------
sub("""        EnginePrompt prompt = shownPrompt;
        List<MenuEntry> entries = new ArrayList<>();
        for(int index : actions)
        {
            EnginePrompt.Option option = prompt.options().get(index);
            entries.add(new MenuEntry(option.label(), option.command(), () -> choose(index)));
        }
        showMenu(hit, entries);""",
    """        EnginePrompt prompt = shownPrompt;
        List<MenuEntry> entries = new ArrayList<>();
        if(hit.isPile())
        {
            // One row per VERB, not per card. The pile's cards are face down,
            // so three summonable monsters produced three rows all reading
            // "Special Summon" with nothing to tell them apart. Picking the
            // verb now opens the list of cards it could mean -- which EDOPro
            // does even when there is only one, because "Special Summon" on its
            // own never says WHAT.
            java.util.LinkedHashMap<Integer, List<Integer>> byCommand =
                new java.util.LinkedHashMap<>();
            for(int index : actions)
            {
                byCommand.computeIfAbsent(prompt.options().get(index).command(),
                    command -> new ArrayList<>()).add(index);
            }
            for(java.util.Map.Entry<Integer, List<Integer>> group : byCommand.entrySet())
            {
                List<Integer> indices = group.getValue();
                String label = prompt.options().get(indices.get(0)).label();
                entries.add(new MenuEntry(label, group.getKey(),
                    () -> openPileChoices(label, indices)));
            }
        }
        else
        {
            for(int index : actions)
            {
                EnginePrompt.Option option = prompt.options().get(index);
                entries.add(new MenuEntry(option.label(), option.command(), () -> choose(index)));
            }
        }
        showMenu(hit, entries);""", "menu groups pile commands")

sub("""    private void showMenu(""",
    """    /**
     * Shows which cards a pile command could mean, and waits for one.
     * <p>
     * Always, even for a single card: a player told "Special Summon" and then
     * asked for tributes has been asked to pay for something they were never
     * shown.
     */
    private void openPileChoices(String label, List<Integer> indices)
    {
        closeMenu();
        pileChoices = List.copyOf(indices);
        pileChoicesLabel = label;
        pickerScroll = 0;
        rebuild();
    }

    /** Backs out of a pile's card list, returning to the board. */
    private boolean closePileChoices()
    {
        if(pileChoices == null)
        {
            return false;
        }
        pileChoices = null;
        pileChoicesLabel = null;
        rebuild();
        return true;
    }

    private void showMenu(""", "openPileChoices + close")

# ---------- header says what was asked ----------
sub("""        String title = prompt.title() == null || prompt.title().isBlank()
            ? "Select a card" : prompt.title();""",
    """        String title = pileChoicesLabel != null ? pileChoicesLabel
            : prompt.title() == null || prompt.title().isBlank()
            ? "Select a card" : prompt.title();""", "picker header")

# ---------- a way back out ----------
sub("""        List<FooterButton> buttons = new ArrayList<>();
        int y = at.y() + at.height() - FOOTER_H - 3;
        int x = at.x() + at.width() - PICKER_PAD - FOOTER_W;

        if(prompt.cancelable())""",
    """        List<FooterButton> buttons = new ArrayList<>();
        int y = at.y() + at.height() - FOOTER_H - 3;
        int x = at.x() + at.width() - PICKER_PAD - FOOTER_W;

        if(pileChoices != null)
        {
            // The idle prompt itself is not cancelable -- you must do SOMETHING
            // on your turn -- but changing your mind about WHICH card is always
            // allowed, and returns to the board rather than answering.
            buttons.add(new FooterButton("Back", x, y, true, this::closePileChoices));
            x -= FOOTER_W + 4;
        }
        if(prompt.cancelable())""", "Back button")

sub("""            if(pileView != null)
            {
                pileView = null;
                return true;
            }
            if(!menuButtons.isEmpty())
            {""",
    """            if(pileView != null)
            {
                pileView = null;
                return true;
            }
            if(closePileChoices())
            {
                return true;
            }
            if(!menuButtons.isEmpty())
            {""", "escape backs out")

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("done")
