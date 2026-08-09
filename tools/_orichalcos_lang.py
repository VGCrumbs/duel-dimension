import io, json, collections
p = "src/main/resources/assets/dueldimension/lang/en_us.json"
s = io.open(p, encoding="utf-8").read()
d = json.loads(s, object_pairs_hook=collections.OrderedDict)
key = "death.attack.orichalcos"
if key in d:
    print("already present")
else:
    d[key] = "%1$s had their soul taken by the Seal of Orichalcos"
    io.open(p, "w", encoding="utf-8", newline="\n").write(
        json.dumps(d, indent=2, ensure_ascii=False) + "\n")
    print("added", key, "-- entries:", len(d))
