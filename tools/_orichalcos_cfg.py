import io
p = "src/main/java/de/cas_ual_ty/dueldimension/CommonConfig.java"
s = io.open(p, encoding="utf-8").read()

old = '        logBinderIO = bool(json, "logInfiniteBinders", false);'
new = ('        logBinderIO = bool(json, "logInfiniteBinders", false);\n'
       '        sealOfOrichalcosDeath = bool(json, "sealOfOrichalcosDeath", false);')
assert old in s and new not in s, "assignment anchor"
s = s.replace(old, new, 1)

old2 = '        json.addProperty("logInfiniteBinders", logBinderIO.get());'
new2 = ('        json.addProperty("logInfiniteBinders", logBinderIO.get());\n'
        '        json.addProperty("sealOfOrichalcosDeath", sealOfOrichalcosDeath.get());')
assert old2 in s and new2 not in s, "write anchor"
s = s.replace(old2, new2, 1)

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("CommonConfig: sealOfOrichalcosDeath wired")
