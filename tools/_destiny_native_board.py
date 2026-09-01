"""The board asks the Destiny Draw itself, on its own pointer, and the icons stay crisp."""
import sys
import json

CAN_OLD = '''    public static boolean boardCanAnswer(EnginePrompt prompt)
    {
        if(prompt == null)
        {
            return false;
        }'''

CAN_NEW = '''    public static boolean boardCanAnswer(EnginePrompt prompt)
    {
        if(prompt == null)
        {
            return false;
        }
        // THE DESTINY DRAW, stated rather than derived.
        //
        // Its two options name no card, no zone and no phase, so every test
        // below reads it as a question with nothing to point at and hands it to
        // the flat screen -- which is what yanked a duellist off the board to
        // answer it. That reasoning was right before the board had a panel for
        // it and is wrong now that it does: see DestinyPrompt, which both duel
        // views draw from.
        if(de.cas_ual_ty.dueldimension.clientutil.DestinyPrompt.isOffered(prompt))
        {
            return true;
        }'''

OPEN_OLD = '''    public static void openScreenForPrompt()
    {
        if(de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.locked()
            && !de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.screenPreferred()
            && PromptOptions.boardCanAnswer(prompt))
        {
            return;
        }
        openScreen();
    }'''

OPEN_NEW = '''    public static void openScreenForPrompt()
    {
        boolean onBoard =
            de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.locked()
                && !de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField
                    .screenPreferred();
        // The Destiny Draw is the one board question with nothing on the board
        // to point at, so unlike the rest it needs a surface OPENED for it --
        // and the surface is the board's own pointer, not the flat screen. The
        // pointer draws the panel and answers the click; see BoardPointerScreen.
        //
        // Only when one is not already up: a duellist who is holding the cursor
        // when the offer arrives should keep the cursor they are holding.
        if(onBoard && de.cas_ual_ty.dueldimension.clientutil.DestinyPrompt.isOffered(prompt))
        {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if(!(minecraft.screen
                instanceof de.cas_ual_ty.dueldimension.clientutil.overworld.BoardPointerScreen))
            {
                minecraft.setScreen(
                    new de.cas_ual_ty.dueldimension.clientutil.overworld.BoardPointerScreen());
            }
            return;
        }
        if(onBoard && PromptOptions.boardCanAnswer(prompt))
        {
            return;
        }
        openScreen();
    }'''

for tree in ('mc1211', 'mc262'):
    base = tree + '/src/main/java/de/cas_ual_ty/dueldimension/clientutil/'
    for path, old, new in ((base + 'PromptOptions.java', CAN_OLD, CAN_NEW),
                           (base + 'DuelClientState.java', OPEN_OLD, OPEN_NEW)):
        raw = open(path, 'rb').read().decode('utf-8')
        crlf = '\r\n' in raw
        s = raw.replace('\r\n', '\n')
        if s.count(old) != 1:
            sys.exit('%s: %d matches for %r' % (path, s.count(old), old[:60]))
        s = s.replace(old, new)
        open(path, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))
    print('patched', tree)

# The icons are drawn at their own 32 pixels, so every GUI scale is already a
# whole-number multiple of the source -- 3x at scale 3, 4x at scale 4. What made
# them soft was the FILTER: bilinear resamples even an exact integer upscale, so
# the artwork arrived blurred while the text beside it stayed sharp.
gui = 'shared/resources/assets/dueldimension/textures/gui/common/'
for name in ('destiny_card.png.mcmeta', 'normal_draw.png.mcmeta'):
    p = gui + name
    meta = json.load(open(p, encoding='utf-8'))
    meta.setdefault('texture', {})['blur'] = False
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(meta, f, separators=(', ', ': '))
    print('nearest-neighbour', name)
