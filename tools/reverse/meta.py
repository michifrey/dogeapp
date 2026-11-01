"""Minimal reader for Unity IL2CPP global-metadata.dat v29: dumps types, fields (with raw default
values), methods and string literals as text. Read-only, pure Python."""
import struct, sys

d = open(sys.argv[1], 'rb').read()
out = sys.argv[2]
hdr = struct.unpack_from('<64i', d, 8)
sec = lambda i: (hdr[2*i], hdr[2*i+1])

def cstr(idx):
    off = sec(2)[0] + idx
    return d[off:d.index(b'\0', off)].decode('utf-8', 'replace')

# string literals
lo, ls = sec(0); do, _ = sec(1)
lits = []
for i in range(ls // 8):
    ln, di = struct.unpack_from('<Ii', d, lo + 8*i)
    lits.append(d[do+di:do+di+ln].decode('utf-8', 'replace'))

# fields
fo, fs = sec(11)
fields = [struct.unpack_from('<iiI', d, fo + 12*i) for i in range(fs // 12)]
# field defaults: fieldIndex,typeIndex,dataIndex
dvo, dvs = sec(7); ddo, dds = sec(8)
defs = [struct.unpack_from('<iii', d, dvo + 12*i) for i in range(dvs // 12)]
order = sorted(set(x[2] for x in defs if x[2] >= 0)) + [dds]
nxt = {order[i]: order[i+1] for i in range(len(order)-1)}
fdef = {}
for fi, ti, di in defs:
    if di < 0: continue
    fdef[fi] = d[ddo+di:ddo+nxt[di]]

# methods (v29: 32 bytes: name, declaringType, returnType, parameterStart, genericContainer, token, flags u16, iflags u16, slot u16, paramCount u16)
mo, ms = sec(5)
MS = 32
methods = [struct.unpack_from('<iiiiiIHHHH', d, mo + MS*i) for i in range(ms // MS)]

to, ts = sec(19); TS = 88
with open(out, 'w') as f:
    for t in range(ts // TS):
        v = struct.unpack_from('<16i8HII', d, to + TS*t)
        name, ns = cstr(v[0]), cstr(v[1])
        fieldStart, methodStart = v[8], v[9]
        mcount, fcount = v[16], v[18]
        f.write(f'\n## {ns}.{name}  (type {t})\n')
        for k in range(fcount):
            fi = fieldStart + k
            nm = cstr(fields[fi][0])
            val = fdef.get(fi)
            f.write(f'  field {nm}' + (f' = {val.hex(" ")}' if val is not None else '') + '\n')
        for k in range(mcount):
            m = methods[methodStart + k]
            f.write(f'  method {cstr(m[0])}({m[9]})\n')
with open(out + '.literals', 'w') as f:
    for i, s in enumerate(lits):
        f.write(f'{i}\t{s!r}\n')
print(len(lits), 'literals', ts // TS, 'types', len(fields), 'fields', len(methods), 'methods')
