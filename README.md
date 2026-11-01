# Dog-E Control

Eine eigene Android-App, um den WowWee **Dog-E** Roboterhund per Bluetooth Low Energy (BLE) zu steuern.
Sie ist als Ersatz gedacht, weil die offizielle App (`com.wowwee.doge_robot`) für Android nicht mehr gepflegt wird.

> **Stand:** Das Bluetooth-Protokoll habe ich aus der Original-App (Version 1.1.4) ausgelesen und eingebaut.
> Dazu gehören Fahren, Kopf bewegen, Stopp, Tricks (Bellen, Schwanzwedeln, Heulen, Singen, Furzen) und die
> Kopplung. Am echten Hund ist das noch **ungetestet**. Details stehen in [`docs/PROTOKOLL.md`](docs/PROTOKOLL.md).

## Funktionen

| Tab | Was er macht |
|-----|--------------|
| **Verbinden** | Sucht BLE-Geräte, hebt den Hund hervor und verbindet sich. |
| **Steuern** | Fahren und Kopf (gedrückt halten, loslassen = Stopp), Trick-Knöpfe und ein Sequenz-Tester, um weitere Tricks und Sounds per Nummer zu finden. |
| **Licht** | Farbrad + Helligkeit für alle sechs LEDs (Augen, Brust, Schwanz, Füße) einzeln oder zusammen, Schnellfarben und Regenbogen. |
| **Explorer** | Zeigt alle Services und Characteristics. Du kannst rohe Hex-Bytes senden, Werte lesen und den Steuer-Kanal festlegen. Alle Benachrichtigungen des Hundes landen im Log, das du kopieren kannst. |
| **Befehle** | Befehlstabelle bearbeiten (Name + Hex-Bytes), Befehle hinzufügen und testen, Konfiguration als JSON exportieren und importieren. |

**Sprachsteuerung** (Tab *Steuern*): Schalter einschalten, Mikrofon erlauben, dann z. B. „Bell!“, „Furz mal!“,
„Komm her“, „Stopp“, „Augen blau“, „Licht aus“ oder „Regenbogen“ sagen. Die App hört dauerhaft zu, solange
sie offen ist, und nutzt die Spracherkennung des Geräts (bei Samsung meist Google-Spracheingabe; ohne
Internet braucht es das deutsche Offline-Sprachpaket).

**Tablets** (z. B. Samsung Galaxy Tab): Navigation an der Seite, zweispaltige Ansicht, größere Tasten;
Hoch- und Querformat.

Die Befehle sind vorbelegt (`data/DogeProtocol.kt`) und lassen sich in der App ändern oder ergänzen.

## APK bekommen

**Ohne Android Studio:** Bei jedem Push baut GitHub Actions die App. Im Tab *Actions* den letzten Lauf von
„Build APK“ öffnen, das Artefakt `dog-e-control-debug` herunterladen, entpacken und die APK auf dem
Handy installieren („Installation aus unbekannten Quellen“ erlauben).

**Mit Android Studio:** Projekt öffnen, Handy per USB verbinden und auf *Run* klicken.
Oder auf der Kommandozeile: `./gradlew assembleDebug`. Die APK liegt dann unter `app/build/outputs/apk/debug/`.

Voraussetzungen: Android 8.0 oder neuer, Bluetooth LE. Für den Build JDK 17 und das Android SDK (API 35).

## Projektstruktur

```
app/src/main/java/de/dogeapp/
  MainActivity.kt        Berechtigungen, Navigation
  ble/BleManager.kt      Scan, Verbindung, GATT-Warteschlange, Schreiben, Benachrichtigungen
  data/DogeProtocol.kt   Dog-E-Protokoll: Paketformat, Prüfsumme, Befehle, Kopplung
  data/DogeConfig.kt     Konfiguration (Kanal, Befehlstabelle), Speicherung als JSON
  data/Hex.kt            Hex-Strings parsen und formatieren
  ui/                    Compose-Screens (Verbinden, Steuern, Licht, Explorer, Befehle)
  voice/                 Spracherkennung (Dauer-Zuhören) und Sprachbefehle
tools/doge_explorer.py   Gleicher Explorer für den PC (Python + bleak)
tools/reverse/           Skripte, mit denen das Protokoll aus der Original-App gelesen wurde
docs/PROTOKOLL.md        Das Protokoll und wie es gefunden wurde
```

## Hinweis

Das ist ein privates Projekt ohne Verbindung zu WowWee. „Dog-E“ und „WowWee“ sind Marken ihrer jeweiligen Inhaber.
