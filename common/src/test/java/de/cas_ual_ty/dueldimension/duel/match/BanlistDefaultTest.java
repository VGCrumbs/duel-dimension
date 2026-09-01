package de.cas_ual_ty.dueldimension.duel.match;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Which list {@link Banlist#DEFAULT_ID} resolves to.
 *
 * <h2>Why this is tested in {@code common} and not beside {@code Banlists}</h2>
 * Because the rule has to give the same answer on the server, which reads
 * EDOPro's files off disk, and in the editor, which is sent a catalogue over the
 * wire. {@code BanlistsTest} covers the loading and needs a reference install to
 * do it; this covers the CHOOSING and needs nothing, so it runs everywhere and
 * on both platforms' shared engine.
 */
class BanlistDefaultTest
{
    private static Banlist named(String displayName)
    {
        return new Banlist(Banlist.idOf(displayName), displayName, Map.of());
    }

    /** The set a current EDOPro install actually offers, headers verbatim. */
    private static List<Banlist> edopro()
    {
        return List.of(
            Banlist.none(),
            named("2026.05 TCG"),
            named("2005.4 GOAT"),
            named("2026.07 OCG"),
            named("2026.07.01 Rush Prereleases"),
            named("2026.07.01 Rush Duel"),
            named("2025.07 Speed Duel"),
            named("2026.05 Traditional"),
            named("2026.05 Worlds"));
    }

    @Test
    void picksTheTcgListOutOfAFullInstall()
    {
        // The OCG and Rush lists are LATER than the TCG one, so anything that
        // took the newest list overall rather than the newest TCG list would
        // pass on a smaller fixture and fail here.
        assertEquals("2026.05 TCG", Banlist.mostRecentTcg(edopro()).displayName());
    }

    @Test
    void ocgIsNotTcg()
    {
        assertNull(Banlist.mostRecentTcg(List.of(named("2026.07 OCG"))));
    }

    @Test
    void neitherAreTheOtherFormats()
    {
        assertNull(Banlist.mostRecentTcg(List.of(
            Banlist.none(),
            named("2026.05 Traditional"),
            named("2026.05 Worlds"),
            named("2025.07 Speed Duel"),
            named("2026.07.01 Rush Duel"),
            named("2005.4 GOAT"))));
    }

    @Test
    void comparesDatesRatherThanOrder()
    {
        // Deliberately listed oldest-last and newest-first-but-one: the files
        // are read in FILENAME order, which says nothing about recency.
        assertEquals("2026.05 TCG", Banlist.mostRecentTcg(List.of(
            named("2014.10 TCG"),
            named("2026.05 TCG"),
            named("2004.03 TCG"))).displayName());
    }

    @Test
    void aDatedTcgListBeatsAnUndatedOne()
    {
        assertEquals("2019.01 TCG", Banlist.mostRecentTcg(List.of(
            named("TCG"),
            named("2019.01 TCG"))).displayName());
    }

    @Test
    void comparesComponentsNumericallyRatherThanAsText()
    {
        // "2015.9" sorts AFTER "2015.11" as text and before it as a date. The
        // headers are not zero-padded -- "2005.4 GOAT" is EDOPro's own -- so
        // this is the real shape of the data rather than a contrived case.
        assertEquals("2015.11 TCG", Banlist.mostRecentTcg(List.of(
            named("2015.9 TCG"),
            named("2015.11 TCG"))).displayName());
    }

    @Test
    void aLongerDateBreaksATieWithAShorterOne()
    {
        assertEquals("2026.05.01 TCG", Banlist.mostRecentTcg(List.of(
            named("2026.05 TCG"),
            named("2026.05.01 TCG"))).displayName());
    }

    @Test
    void anOlderTcgListNamedSomethingElseIsStillATcgList()
    {
        // "2015.11 TCG Goat Format" is a TCG list and is not the current one.
        // It is a candidate, and the date is what rules it out -- which is the
        // reason the test above compares dates instead of taking the first hit.
        List<Banlist> lists = List.of(
            named("2015.11 TCG Goat Format"),
            named("2026.05 TCG"));
        assertEquals("2026.05 TCG", Banlist.mostRecentTcg(lists).displayName());
    }

    @Test
    void noListsAtAllIsNull()
    {
        assertNull(Banlist.mostRecentTcg(List.of()));
        assertNull(Banlist.mostRecentTcg(List.of(Banlist.none())));
    }

    @Test
    void theSentinelIsNotAlsoNoList()
    {
        // They are different answers -- "never asked" against "asked and said
        // no" -- and the whole default depends on nothing collapsing them.
        assertEquals("default", Banlist.DEFAULT_ID);
        assertEquals("none", Banlist.NO_BANLIST_ID);
    }

    @Test
    void theListReturnedIsOneOfTheOnesPassedIn()
    {
        // Identity, not equality: the caller uses the returned object's limits,
        // so a copy that happened to share a name would be a silent bug.
        List<Banlist> lists = edopro();
        assertSame(lists.get(1), Banlist.mostRecentTcg(lists));
    }
}
