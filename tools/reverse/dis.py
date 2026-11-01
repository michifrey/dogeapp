import json, sys, struct, bisect
from capstone import Cs, CS_ARCH_ARM, CS_MODE_ARM, CS_MODE_THUMB
from elftools.elf.elffile import ELFFile
so_path, addrs_path = sys.argv[1], sys.argv[2]
so = open(so_path, 'rb').read()
elf = ELFFile(open(so_path, 'rb'))
segs = [(s['p_vaddr'], s['p_offset'], s['p_filesz']) for s in elf.iter_segments() if s['p_type'] == 'PT_LOAD']
def v2o(v):
    for va, off, sz in segs:
        if va <= v < va + sz: return v - va + off
addrs = json.load(open(addrs_path))
byaddr = {}
for n, a in addrs.items(): byaddr.setdefault(a, n)
starts = sorted(byaddr)
mode = CS_MODE_THUMB if '--thumb' in sys.argv else CS_MODE_ARM
md = Cs(CS_ARCH_ARM, mode)
for name in [a for a in sys.argv[3:] if not a.startswith('--')]:
    a = addrs[name]
    i = bisect.bisect_right(starts, a)
    end = starts[i] if i < len(starts) else a + 0x400
    end = min(end, a + 0x1000)
    print(f'===== {name} @ {a:#x} ({end-a} bytes)')
    o = v2o(a)
    for ins in md.disasm(so[o:o + (end - a)], a):
        extra = ''
        if ins.mnemonic in ('bl', 'blx', 'b') and ins.op_str.startswith('#'):
            t = int(ins.op_str[1:], 16)
            if t in byaddr: extra = '   ; ' + byaddr[t]
        print(f'{ins.address:8x}: {ins.mnemonic:8s} {ins.op_str}{extra}')
