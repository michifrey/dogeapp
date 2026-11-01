"""Find Il2CppCodeGenModule for Assembly-CSharp.dll in libil2cpp.so (ARM32) and map method names to addresses."""
import struct, sys, json
from elftools.elf.elffile import ELFFile

meta = open(sys.argv[1], 'rb').read()
so_path = sys.argv[2]
so = open(so_path, 'rb').read()
elf = ELFFile(open(so_path, 'rb'))
segs = [(s['p_vaddr'], s['p_offset'], s['p_filesz']) for s in elf.iter_segments() if s['p_type'] == 'PT_LOAD']
def v2o(v):
    for va, off, sz in segs:
        if va <= v < va + sz: return v - va + off
def o2v(o):
    for va, off, sz in segs:
        if off <= o < off + sz: return o - off + va

# relocations: build map vaddr->value for R_ARM_RELATIVE (REL: addend in place) and ABS
relmap = {}
for sec in elf.iter_sections():
    if sec.header['sh_type'] in ('SHT_REL', 'SHT_RELA'):
        for r in sec.iter_relocations():
            if r['r_info_type'] == 23:  # R_ARM_RELATIVE
                relmap[r['r_offset']] = None
print('relative relocs:', len(relmap))
def ptr(v):
    o = v2o(v)
    return struct.unpack_from('<I', so, o)[0]

hdr = struct.unpack_from('<64i', meta, 8)
sec = lambda i: (hdr[2*i], hdr[2*i+1])
def cstr(idx):
    off = sec(2)[0] + idx
    return meta[off:meta.index(b'\0', off)].decode()

# images: 40 bytes
io, isz = sec(20)
images = []
for i in range(isz // 40):
    v = struct.unpack_from('<10i', meta, io + 40*i)
    images.append((cstr(v[0]), v[2], v[3]))
img = next(x for x in images if x[0] == 'Assembly-CSharp.dll')
print('image', img)

# count methods of image
to = sec(19)[0]; mo = sec(5)[0]
names = {}
for t in range(img[1], img[1] + img[2]):
    v = struct.unpack_from('<16i8HII', meta, to + 88*t)
    tname = cstr(v[0]); mstart = v[9]; mcount = v[16]
    for k in range(mcount):
        m = struct.unpack_from('<iiiiiIHHHH', meta, mo + 32*(mstart+k))
        names[m[5] & 0xFFFFFF] = f'{tname}.{cstr(m[0])}({m[9]})'
maxrid = max(names)
print('methods in image', len(names), 'max rid', maxrid)

needle = b'Assembly-CSharp.dll\0'
pos = 0; found = None
while True:
    pos = so.find(needle, pos)
    if pos < 0: break
    sv = o2v(pos)
    pat = struct.pack('<I', sv)
    p2 = 0
    while True:
        p2 = so.find(pat, p2)
        if p2 < 0: break
        cnt, mp = struct.unpack_from('<II', so, p2 + 4)
        if cnt == maxrid or abs(cnt - maxrid) < 5:
            found = (p2, cnt, mp)
        p2 += 4
    pos += 1
print('module', found)
p2, cnt, mp = found
out = {}
for rid, n in names.items():
    a = struct.unpack_from('<I', so, v2o(mp) + 4*(rid-1))[0]
    out[n] = a
json.dump(out, open(sys.argv[3], 'w'), indent=0)
for k, a in out.items():
    if k.startswith(('BleManager.', 'BlePacket.', 'BleCode.')): print(hex(a), k)
