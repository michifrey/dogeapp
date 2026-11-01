import json, sys, struct, bisect
from elftools.elf.elffile import ELFFile
so_path, addrs_path, target = sys.argv[1], sys.argv[2], int(sys.argv[3], 16)
so = open(so_path, 'rb').read()
elf = ELFFile(open(so_path, 'rb'))
text = elf.get_section_by_name('il2cpp')
base, off, size = text['sh_addr'], text['sh_offset'], text['sh_size']
addrs = json.load(open(addrs_path)); byaddr = {a: n for n, a in addrs.items()}; starts = sorted(byaddr)
for i in range(0, size - 3, 4):
    w = struct.unpack_from('<I', so, off + i)[0]
    if (w & 0x0e000000) == 0x0a000000:  # B/BL
        imm = w & 0xffffff
        if imm & 0x800000: imm -= 1 << 24
        pc = base + i
        if pc + 8 + imm * 4 == target:
            j = bisect.bisect_right(starts, pc) - 1
            print(hex(pc), byaddr[starts[j]] if j >= 0 else '?')
