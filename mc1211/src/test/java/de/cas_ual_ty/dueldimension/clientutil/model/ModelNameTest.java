package de.cas_ual_ty.dueldimension.clientutil.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What counts as a model name, asked once so that three places cannot disagree.
 * <p>
 * They did. The loader refused names it thought unsafe, the picker offered
 * whatever was on disk, and the importer sanitised into a third alphabet — so
 * the editor could offer a file, save it into a definition, and then have the
 * loader refuse the name the editor itself had produced. The monster stayed a
 * sprite and nothing on screen said why.
 * <p>
 * A name becomes a path and it names a texture, so it has two jobs: it must not
 * be able to leave the models folder, and two different files must not be able
 * to claim one texture identifier.
 */
class ModelNameTest
{
    @Test
    void ordinaryNamesAreAccepted()
    {
        assertTrue(MonsterModels.plain("curse_of_dragon"));
        assertTrue(MonsterModels.plain("blue-eyes"));
        assertTrue(MonsterModels.plain("dragon.v2"));
        assertTrue(MonsterModels.plain("a"));
        assertTrue(MonsterModels.plain("28279543"));
    }

    @Test
    void nothingCanLeaveTheFolder()
    {
        assertFalse(MonsterModels.plain(".."));
        assertFalse(MonsterModels.plain("../secret"));
        assertFalse(MonsterModels.plain("a/../b"));
        assertFalse(MonsterModels.plain("sub/dragon"));
        assertFalse(MonsterModels.plain("sub\\dragon"));
        assertFalse(MonsterModels.plain("/etc/passwd"));
        assertFalse(MonsterModels.plain("\\\\server\\share\\x"));
    }

    @Test
    void aDriveLetterIsNotAPlainName()
    {
        // The one that got through the old guard: no "..", no backslash, no
        // leading slash -- and yet Path.resolve throws its base away when the
        // argument carries a root, so this reads a file from anywhere on the
        // disk. Rejected for the shape of the name rather than for the
        // characters in it, because "C:x" contains nothing suspicious.
        assertFalse(MonsterModels.plain("C:/Users/Public/anything"));
        assertFalse(MonsterModels.plain("c:anything"));
    }

    @Test
    void twoSpellingsOfOneNameCannotCoexist()
    {
        // Both of these once loaded, and both baked their textures under
        // dueldimension:model/blue_eyes_tex0 -- so whichever was baked second
        // made the other wear its skin. Only the already-sanitised spelling is
        // accepted, which is the one Import produces.
        assertTrue(MonsterModels.plain("blue_eyes"));
        assertFalse(MonsterModels.plain("Blue Eyes"));
        assertFalse(MonsterModels.plain("blue eyes"));
        assertFalse(MonsterModels.plain("BLUE_EYES"));
    }

    @Test
    void nothingIsNotAName()
    {
        assertFalse(MonsterModels.plain(null));
        assertFalse(MonsterModels.plain(""));
        assertFalse(MonsterModels.plain("   "));
    }
}
