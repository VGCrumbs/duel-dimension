package de.cas_ual_ty.ydm.ocg;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * A duel, fully determined: rule flags, seed, both decks, and the ordered
 * responses. Replaying these against the same engine bundle reproduces the
 * duel exactly — which makes this one format serve three jobs: fuzz-failure
 * repro, regression fixtures, and (later) in-game replay/spectating.
 * <p>
 * Format is line-based text, {@code key=value}, so a failing case can be
 * pasted into a bug report and diffed. Version it whenever fields change:
 * a replay from an older engine bundle is not guaranteed to reproduce.
 */
public record Replay(int version, long flags, long[] seed, List<Integer> mainDeck0, List<Integer> extraDeck0,
    List<Integer> mainDeck1, List<Integer> extraDeck1, List<byte[]> responses)
{
    public static final int VERSION = 1;

    public static Replay of(long flags, long[] seed, HeadlessDuelRunner.Deck deck0, HeadlessDuelRunner.Deck deck1,
        List<byte[]> responses)
    {
        return new Replay(VERSION, flags, seed, deck0.main(), deck0.extra(), deck1.main(), deck1.extra(), responses);
    }

    public String encode()
    {
        StringBuilder out = new StringBuilder();
        out.append("version=").append(version).append('\n');
        out.append("flags=").append(Long.toUnsignedString(flags)).append('\n');
        out.append("seed=").append(join(seed)).append('\n');
        out.append("main0=").append(joinInts(mainDeck0)).append('\n');
        out.append("extra0=").append(joinInts(extraDeck0)).append('\n');
        out.append("main1=").append(joinInts(mainDeck1)).append('\n');
        out.append("extra1=").append(joinInts(extraDeck1)).append('\n');
        for(byte[] response : responses)
        {
            out.append("r=").append(Base64.getEncoder().encodeToString(response)).append('\n');
        }
        return out.toString();
    }

    public static Replay decode(String text)
    {
        int version = 0;
        long flags = 0;
        long[] seed = new long[4];
        List<Integer> main0 = List.of();
        List<Integer> extra0 = List.of();
        List<Integer> main1 = List.of();
        List<Integer> extra1 = List.of();
        List<byte[]> responses = new ArrayList<>();

        for(String line : text.split("\n"))
        {
            line = line.trim();
            int split = line.indexOf('=');
            if(split < 0)
            {
                continue;
            }
            String key = line.substring(0, split);
            String value = line.substring(split + 1);
            switch(key)
            {
                case "version" -> version = Integer.parseInt(value);
                case "flags" -> flags = Long.parseUnsignedLong(value);
                case "seed" ->
                {
                    String[] parts = value.split(",");
                    for(int i = 0; i < seed.length && i < parts.length; i++)
                    {
                        seed[i] = Long.parseUnsignedLong(parts[i]);
                    }
                }
                case "main0" -> main0 = parseInts(value);
                case "extra0" -> extra0 = parseInts(value);
                case "main1" -> main1 = parseInts(value);
                case "extra1" -> extra1 = parseInts(value);
                case "r" -> responses.add(Base64.getDecoder().decode(value));
                default ->
                {
                }
            }
        }
        return new Replay(version, flags, seed, main0, extra0, main1, extra1, responses);
    }

    /** Replays the recorded responses in order; extra prompts get no answer (run aborts). */
    public ResponseSource asResponseSource()
    {
        return new ResponseSource()
        {
            private int at;

            @Override
            public byte[] respond(RawMessage prompt)
            {
                return at < responses.size() ? responses.get(at++) : null;
            }
        };
    }

    private static String join(long[] values)
    {
        StringBuilder out = new StringBuilder();
        for(int i = 0; i < values.length; i++)
        {
            out.append(i == 0 ? "" : ",").append(Long.toUnsignedString(values[i]));
        }
        return out.toString();
    }

    private static String joinInts(List<Integer> values)
    {
        StringBuilder out = new StringBuilder();
        for(int i = 0; i < values.size(); i++)
        {
            out.append(i == 0 ? "" : ",").append(values.get(i));
        }
        return out.toString();
    }

    private static List<Integer> parseInts(String value)
    {
        List<Integer> result = new ArrayList<>();
        if(value.isEmpty())
        {
            return result;
        }
        for(String part : value.split(","))
        {
            result.add(Integer.parseInt(part));
        }
        return result;
    }
}
