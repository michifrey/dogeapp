# Das Dog-E-Protokoll

## Ergebnis (aus der Original-App 1.1.4 ausgelesen)

Die Original-App ist mit Unity/IL2CPP gebaut. Ausgelesen wurden die Metadaten (`global-metadata.dat`)
und der ARM-Maschinencode (`libil2cpp.so`), mit den Skripten in `tools/reverse/` (siehe unten).
**Getestet am echten Hund ist es noch nicht.**

| Was | Wert |
|-----|------|
| Gerätename | `Mint Dog` |
| Senden | Service `FFE5`, Characteristic `FFE9`, *Write Without Response* |
| Empfangen | Service `FFE0`, Characteristic `FFE4`, Notify |
| OTA (nicht anfassen!) | Service `FD00`, Characteristics `FD01`/`FD02` |

**Paketformat:** `FF <len> <cmd> <daten…> <checksum>`
- `len` = Anzahl Bytes nach dem Längenbyte (cmd + daten + checksum)
- `checksum` = Summe aller Bytes von `len` bis zum letzten Datenbyte, nur die unteren 8 Bit

| Befehl | Bytes | Quelle in der Original-App |
|--------|-------|----------------------------|
| Motoren stopp | `FF 03 0A 00 0D` | `SendStopMotorsCommand` |
| Fahren (vorwärts) | `FF 05 0A 01 01 01 12` | Fahr-Schieber: `SendMotorSpeedCommand(1, 1)` |
| Kopf bewegen | `FF 05 0A 02 01 02 14` | Kopf-Knopf: `SendMotorSpeedCommand(2, 2)` |
| Sequenz abspielen | `FF <len> 05 01 <anzahl> <id lo> <id hi> … <cs>` | `SendPlaySequences` |
| LEDs setzen | `FF 16 06 <RGB rechtes Auge> <RGB linkes Auge> <RGB Brust> <RGB rechter Fuß> <RGB linker Fuß> <RGB Schwanz> <Dauer lo> <Dauer hi> <cs>` | `SendSetLed` – Dauer in Zehntelsekunden, die App nutzt 36000 (1 h) |
| Kopplung prüfen | `FF 09 01 00 <6 Byte Handy-ID> <cs>` | `RequestAuthentication` (nach dem Verbinden) |
| Kopplung anfordern | `FF 03 01 01 05` | `RequestPairing` (wenn nicht gekoppelt) |

Sequenz-IDs aus dem „Command Mode“: Bellen `0x6A` (106), Heulen `0x72` (114), Singen `0x73` (115),
Furzen `0x74` (116), Schwanzwedeln `0xA8` (168). Der Hund kennt noch viel mehr Sequenzen (Tricks, Sounds,
Tanz). Sie lassen sich in der App unter *Steuern → Sequenz ausprobieren* durchprobieren.

Antwort auf die Kopplungs-Prüfung: `FF .. 01 00 <status> ..`, `status = 1` bedeutet gekoppelt.
Antwort auf eine Kopplungs-Anfrage: `FF .. 01 01 <4|5> ..`.

Weitere bekannte Befehlsgruppen (noch nicht eingebaut): Lautstärke (`SendSetVolume`), Akku (`RequestBatteryLevel`), Firmware-Version, Schwanz-Animationen,
Spiele und Profile. Die Adressen findest du mit `tools/reverse/addrs.py`, den Code mit `tools/reverse/dis.py`.

### Analyse selbst wiederholen

```
unzip com.wowwee.doge_robot.apk -d base; unzip config.armeabi_v7a.apk -d split
pip install capstone pyelftools
M=base/assets/bin/Data/Managed/Metadata/global-metadata.dat; L=split/lib/armeabi-v7a/libil2cpp.so
python3 tools/reverse/meta.py  $M dump.txt                 # Klassen, Felder, Konstanten
python3 tools/reverse/addrs.py $M $L addrs.json            # Methoden -> Adressen
python3 tools/reverse/dis.py   $L addrs.json "BleManager.SendStopMotorsCommand(0)"
python3 tools/reverse/callers.py $L addrs.json 0x4e53d8    # wer ruft eine Methode auf
```

---

# Anhang: Wie man ein unbekanntes Protokoll herausfindet


Damit die Steuerung funktioniert, braucht die App drei Dinge:

1. die **Characteristic**, auf die Befehle geschrieben werden (Service-UUID + Characteristic-UUID),
2. die **Bytes** für jeden Befehl (vorwärts, Stopp, Sitz, …),
3. ob die Fahrbefehle **wiederholt** gesendet werden müssen, solange man fährt.

Am verlässlichsten ist Weg A: Er zeichnet auf, was die Original-App wirklich sendet.
Weg B ergänzt ihn und hilft, die Bedeutung der Bytes zu verstehen.

---

## Weg A: Bluetooth-Verkehr der Original-App mitschneiden (empfohlen)

Dafür brauchst du ein Android-Handy, auf dem die alte Dog-E-App noch läuft.
Notfalls installierst du sie auf einem älteren Gerät.

1. **Entwickleroptionen aktivieren:** *Einstellungen → Telefoninfo → 7× auf „Build-Nummer“ tippen.*
2. In den *Entwickleroptionen* **„Bluetooth HCI-Snoop-Log aktivieren“** einschalten.
   Danach Bluetooth einmal aus- und wieder einschalten (oder das Handy neu starten).
3. Die Dog-E-App öffnen, mit dem Hund verbinden und **jeden Befehl einzeln und langsam** ausführen.
   Schreib dir dabei Uhrzeit und Befehl auf, zum Beispiel:
   ```
   14:02:10 vorwärts 2 s
   14:02:20 Stopp
   14:02:30 links
   14:02:40 Sitz
   ```
   Tipp: Jede Aktion zwei- bis dreimal wiederholen. So erkennst du später, welche Bytes sich ändern.
4. Log holen:
   - Mit ADB (am PC): `adb bugreport bugreport.zip`. Im ZIP liegt die Datei unter
     `FS/data/misc/bluetooth/logs/btsnoop_hci.log` (der Pfad hängt vom Hersteller ab).
   - Manche Geräte legen sie auch direkt ab, z. B. unter `/sdcard/btsnoop_hci.log`.
5. Die Datei in **Wireshark** öffnen und mit diesem Filter nur die geschriebenen Werte anzeigen:
   ```
   btatt.opcode == 0x52 || btatt.opcode == 0x12
   ```
   (`0x52` = Write Command ohne Antwort, `0x12` = Write Request mit Antwort.)
   Antworten des Hundes findest du mit `btatt.opcode == 0x1b` (Notification).
6. Für jedes Paket stehen dort **Handle** und **Value**. Die Zeitstempel ordnest du deinen Notizen zu.
   Welcher UUID das Handle entspricht, zeigt Wireshark, wenn der Mitschnitt den Verbindungsaufbau enthält.
   Sonst zeigt es der Explorer-Tab dieser App.

### Darauf achten
- **Wird ein Fahrbefehl ständig wiederholt** (z. B. alle 50–100 ms), solange man in der App fährt?
  Dann in der App unter *Befehle* das Wiederhol-Intervall entsprechend einstellen.
- **Gibt es einen „Handshake“** direkt nach dem Verbinden, also Bytes, die die Original-App immer zuerst
  sendet? Manche Spielzeuge reagieren erst danach auf Befehle. Diesen Befehl als eigenen Eintrag anlegen
  (z. B. „Init“) und nach dem Verbinden einmal antippen.
- Enthalten die Bytes **Geschwindigkeit oder Dauer**? Das erkennst du, wenn du verschiedene Stärken am
  Joystick ausprobierst und vergleichst.
- Steht am Ende eine **Prüfsumme**? Meist ist es eine Summe oder ein XOR über die vorherigen Bytes.

---

## Weg B: Die Original-App dekompilieren

So siehst du die Befehle direkt im Code. Das ist für die Interoperabilität mit deinem eigenen Gerät gedacht.

1. Die APK vom eigenen Handy holen:
   ```
   adb shell pm path com.wowwee.doge_robot
   adb pull <pfad>/base.apk dog-e.apk
   ```
2. Mit **[jadx](https://github.com/skylot/jadx)** öffnen (`jadx-gui dog-e.apk`).
3. Suche nach:
   - `UUID.fromString`, `writeCharacteristic` und `BluetoothGatt`: so findest du Service und Characteristic.
   - Bytefolgen wie `new byte[]{...}` oder Konstanten mit Namen wie `CMD_`, `FORWARD`, `SIT`, `DANCE`.
   - Ist die App mit **Unity** gebaut (Ordner `assets/bin/Data`, Datei `libil2cpp.so`), liegt die Logik in
     C#. Dann mit [Il2CppDumper](https://github.com/Perfare/Il2CppDumper) die Namen herauslesen.
     Ist es Flutter oder React Native, steckt die Logik in `libapp.so` bzw. `index.android.bundle`.

Andere WowWee-Roboter (z. B. [MiP](https://github.com/WowWeeLabs/MiP-BLE-Protocol)) nutzen ein einfaches
Schema: ein Byte für den Befehl, danach die Parameter, geschrieben auf eine 0xFFxx-Characteristic.
Beim Dog-E muss das **nicht** genauso sein. Ein Blick in diese Dokumentation hilft aber beim Einordnen.

---

## Weg C: Ausprobieren mit dem Explorer

Ohne Original-App geht es auch, ist aber mühsamer:

1. In dieser App unter *Verbinden* den Hund wählen. Der Explorer listet dann alle Characteristics und
   abonniert automatisch alle Benachrichtigungen.
2. Kandidaten sind Characteristics mit `WRITE` oder `WRITE_NR` in einem herstellerspezifischen Service
   (also nicht `1800`, `1801`, `180A` oder `180F`).
3. Vorsichtig einzelne Bytes senden, z. B. `01`, `02` usw., und beobachten, ob der Hund reagiert oder
   etwas zurückmeldet. Am PC geht das auch mit `tools/doge_explorer.py`:
   ```
   pip install -r tools/requirements.txt
   python tools/doge_explorer.py scan
   python tools/doge_explorer.py dump AA:BB:CC:DD:EE:FF
   python tools/doge_explorer.py send AA:BB:CC:DD:EE:FF <char-uuid> "01"
   ```
   Dabei nie auf Characteristics schreiben, die nach Firmware-Update (OTA/DFU) aussehen.

---

## Ergebnis eintragen

- **In der App:** Im Explorer bei der richtigen Characteristic auf *Als Steuer-Kanal* tippen und unter
  *Befehle* die Bytes eintragen. Über *Export* kannst du die Konfiguration als JSON sichern oder teilen.
- **Fest im Code:** `app/src/main/java/de/dogeapp/data/DogeConfig.kt` öffnen und dort
  `writeServiceUuid`, `writeCharUuid` und `DEFAULT_COMMANDS` füllen.

Wenn du einen Mitschnitt oder die gefundenen UUIDs hast, kann ich die Befehle daraus auswerten und fest
in die App einbauen.
