#!/usr/bin/env python3
"""Dog-E BLE explorer for the PC (Windows, macOS, Linux).

    pip install -r tools/requirements.txt
    python tools/doge_explorer.py scan
    python tools/doge_explorer.py dump  <ADDRESS>
    python tools/doge_explorer.py send  <ADDRESS> <CHAR_UUID> "01 02 03" [--listen 5]
    python tools/doge_explorer.py listen <ADDRESS> [--seconds 30]

"dump" lists all services/characteristics and reads every readable value.
"listen" subscribes to every notify characteristic and prints what the dog sends.
"""
import argparse
import asyncio

from bleak import BleakClient, BleakScanner


def parse_hex(s: str) -> bytes:
    s = s.lower().replace("0x", "")
    for ch in " ,:-":
        s = s.replace(ch, "")
    return bytes.fromhex(s)


def fmt(b: bytes) -> str:
    return " ".join(f"{x:02X}" for x in b)


async def scan(seconds: float) -> None:
    found = await BleakScanner.discover(timeout=seconds, return_adv=True)
    for dev, adv in sorted(found.values(), key=lambda t: -t[1].rssi):
        print(f"{dev.address}  {adv.rssi:4d} dBm  {adv.local_name or dev.name or '(ohne Namen)'}")
        if adv.service_uuids:
            print(f"    services: {', '.join(adv.service_uuids)}")
        for company, data in adv.manufacturer_data.items():
            print(f"    manufacturer {company:04X}: {fmt(data)}")


async def subscribe_all(client: BleakClient) -> None:
    def handler(char, data: bytearray) -> None:
        print(f"<- {char.uuid}: {fmt(data)}")

    for service in client.services:
        for char in service.characteristics:
            if {"notify", "indicate"} & set(char.properties):
                try:
                    await client.start_notify(char, handler)
                    print(f"   subscribed {char.uuid}")
                except Exception as e:  # noqa: BLE001
                    print(f"   could not subscribe {char.uuid}: {e}")


async def dump(address: str) -> None:
    async with BleakClient(address) as client:
        for service in client.services:
            print(f"Service {service.uuid}  ({service.description})")
            for char in service.characteristics:
                line = f"   {char.uuid}  [{', '.join(char.properties)}]"
                if "read" in char.properties:
                    try:
                        value = await client.read_gatt_char(char)
                        line += f"  = {fmt(value)}  {value!r}"
                    except Exception as e:  # noqa: BLE001
                        line += f"  (read failed: {e})"
                print(line)


async def send(address: str, char_uuid: str, data: str, listen: float, with_response: bool) -> None:
    async with BleakClient(address) as client:
        await subscribe_all(client)
        payload = parse_hex(data)
        print(f"-> {char_uuid}: {fmt(payload)}")
        await client.write_gatt_char(char_uuid, payload, response=with_response)
        await asyncio.sleep(listen)


async def listen(address: str, seconds: float) -> None:
    async with BleakClient(address) as client:
        await subscribe_all(client)
        print(f"listening for {seconds} s …")
        await asyncio.sleep(seconds)


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("scan")
    s.add_argument("--seconds", type=float, default=10)
    d = sub.add_parser("dump")
    d.add_argument("address")
    w = sub.add_parser("send")
    w.add_argument("address")
    w.add_argument("char_uuid")
    w.add_argument("hex")
    w.add_argument("--listen", type=float, default=3)
    w.add_argument("--with-response", action="store_true")
    li = sub.add_parser("listen")
    li.add_argument("address")
    li.add_argument("--seconds", type=float, default=30)
    a = p.parse_args()

    if a.cmd == "scan":
        asyncio.run(scan(a.seconds))
    elif a.cmd == "dump":
        asyncio.run(dump(a.address))
    elif a.cmd == "send":
        asyncio.run(send(a.address, a.char_uuid, a.hex, a.listen, a.with_response))
    elif a.cmd == "listen":
        asyncio.run(listen(a.address, a.seconds))


if __name__ == "__main__":
    main()
