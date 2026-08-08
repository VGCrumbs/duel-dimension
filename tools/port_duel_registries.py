"""Turns the three Forge DeferredRegisters into vanilla registry writes.

A DeferredRegister existed because Forge registration happened during a
mod-loading event, so a static field could not hold the object itself -- only a
RegistryObject standing in for one that did not exist yet. Every use then paid
for that with a `.get()`.

Fabric registers eagerly, so the indirection has nothing left to solve:

    RegistryObject<ZoneType> HAND = DEFERRED_REGISTER.register("hand", () -> new ZoneType())
        ->  ZoneType HAND = register("hand", new ZoneType())

    ZoneTypes.HAND.get()  ->  ZoneTypes.HAND

The suppliers go with it. `() -> new X(...)` was deferring construction; there
is nothing to defer.
"""
import io
import os
import re
import sys

BASE = "src/main/java/de/cas_ual_ty/dueldimension"

# file -> (element type, registry field on DdDuelRegistries)
REGISTRIES = {
    "duel/action/ActionTypes.java": ("ActionType", "ACTION_TYPES"),
    "duel/action/ActionIcons.java": ("ActionIcon", "ACTION_ICONS"),
    "duel/playfield/ZoneTypes.java": ("ZoneType", "ZONE_TYPES"),
}

HOLDERS = ("ActionTypes", "ActionIcons", "ZoneTypes")


def convert_registry(path, element, field):
    src = io.open(path, encoding="utf-8").read()

    # The deferred register itself has no successor.
    src = re.sub(r"^ *private static final DeferredRegister<%s> DEFERRED_REGISTER[^;]*;\n"
                 % element, "", src, flags=re.M)

    # Each entry: drop the RegistryObject wrapper and the supplier.
    entry = re.compile(
        r"public static final RegistryObject<%s> (\w+) = DEFERRED_REGISTER\.register\("
        r"(\"[^\"]+\"), \(\) -> " % element)
    src = entry.sub(r"public static final %s \1 = register(\2, " % element, src)

    # The registration hook. Forge handed the deferred register to the event
    # bus; here the entries are already in by the time anything can ask, so all
    # this has to do is exist -- being called is what loads the class.
    src = re.sub(
        r"    public static void register\(IEventBus bus\)\s*\n\s*\{\s*\n"
        r"\s*DEFERRED_REGISTER\.register\(bus\);\s*\n\s*\}",
        """    private static %s register(String name, %s entry)
    {
        return Registry.register(DdDuelRegistries.%s,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, name), entry);
    }

    /**
     * Loads this class, which is what registers everything in it.
     * <p>
     * The entries are static fields, so they are written to the registry by the
     * class initialiser. Forge needed an event bus here; this needs only to be
     * called.
     */
    public static void register()
    {
    }""" % (element, element, field), src)

    src = src.replace("import net.minecraftforge.eventbus.api.IEventBus;\n", "")
    src = src.replace("import net.minecraftforge.registries.DeferredRegister;\n", "")
    src = src.replace("import net.minecraftforge.registries.RegistryObject;\n", "")
    src = src.replace("import de.cas_ual_ty.dueldimension.DuelDimension;",
                      "import de.cas_ual_ty.dueldimension.DdDuelRegistries;\n"
                      "import de.cas_ual_ty.dueldimension.DuelDimension;\n"
                      "import net.minecraft.core.Registry;")
    io.open(path, "w", encoding="utf-8", newline="\n").write(src)
    return len(entry.findall(io.open(path, encoding="utf-8").read()))


def drop_gets(root):
    """`ZoneTypes.HAND.get()` -> `ZoneTypes.HAND`, everywhere."""
    pattern = re.compile(r"\b(%s)\.([A-Z][A-Z0-9_]*)\.get\(\)" % "|".join(HOLDERS))
    touched = 0
    for dirpath, _, names in os.walk(root):
        for name in names:
            if not name.endswith(".java"):
                continue
            p = os.path.join(dirpath, name)
            src = io.open(p, encoding="utf-8").read()
            new = pattern.sub(r"\1.\2", src)
            if new != src:
                io.open(p, "w", encoding="utf-8", newline="\n").write(new)
                touched += 1
    return touched


if __name__ == "__main__":
    for rel, (element, field) in REGISTRIES.items():
        path = os.path.join(BASE, rel)
        convert_registry(path, element, field)
        print("converted", rel)
    print("dropped .get() in %d files" % drop_gets(BASE))
