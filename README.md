# Dog-E Control

Eine eigene Android-App, um den WowWee **Dog-E** Roboterhund per Bluetooth Low Energy (BLE) zu steuern.
Sie ist als Ersatz gedacht, weil die offizielle App (`com.wowwee.doge_robot`) für Android nicht mehr gepflegt wird.

> **Stand:** Das Bluetooth-Protokoll habe ich aus der Original-App (Version 1.1.4) ausgelesen und eingebaut.
> Dazu gehören Fahren, Kopf bewegen, Stopp, Tricks (Bellen, Schwanzwedeln, Heulen, Singen, Furzen) und die
> Kopplung. Am echten Hund ist das noch **ungetestet**. Details stehen in [`docs/PROTOKOLL.md`](docs/PROTOKOLL.md).

## ⬇️ Download

**[Neueste Version herunterladen (dog-e-control.apk)](https://github.com/michifrey/dogeapp/releases/latest/download/dog-e-control.apk)**

Den Link auf dem Handy oder Tablet öffnen, die Datei herunterladen und antippen. Beim ersten Mal fragt
Android, ob Apps aus dieser Quelle installiert werden dürfen – erlauben. Eine ältere Version wird dabei
einfach ersetzt, Einstellungen bleiben erhalten.

Der Link zeigt immer auf den neuesten Build: Bei jedem Push baut GitHub Actions die App, lässt die Tests
laufen und legt die APK im Release [„latest“](https://github.com/michifrey/dogeapp/releases/tag/latest) ab.

**Selbst bauen:** Projekt in Android Studio öffnen und *Run* drücken, oder `./gradlew assembleDebug`
(die APK liegt dann unter `app/build/outputs/apk/debug/`). Voraussetzungen: Android 8.0 oder neuer mit
Bluetooth LE; zum Bauen JDK 17 und Android SDK (API 35).

## Funktionen

| Tab | Was er macht |
|-----|--------------|
| **Verbinden** | Sucht BLE-Geräte, hebt den Hund hervor und verbindet sich. |
| **Steuern** | Begrüßung mit dem Namen des Kindes, Akkustand, Lautstärke, Fahren und Kopf (gedrückt halten, loslassen = Stopp), Trick-Knöpfe und ein Sequenz-Tester, um weitere Tricks und Sounds per Nummer zu finden. |
| **Licht** | Farbrad + Helligkeit für alle sechs LEDs (Augen, Brust, Schwanz, Füße) einzeln oder zusammen, Schnellfarben und Regenbogen. |
| **Explorer** | Zeigt alle Services und Characteristics. Du kannst rohe Hex-Bytes senden, Werte lesen und den Steuer-Kanal festlegen. Alle Benachrichtigungen des Hundes landen im Log, das du kopieren kannst. |
| **Einstellungen** | Name des Kindes für die Begrüßung, Befehlstabelle bearbeiten (Name + Hex-Bytes), Befehle hinzufügen und testen, Konfiguration als JSON exportieren und importieren. |

**Sprachsteuerung** (Tab *Steuern*): Schalter einschalten, Mikrofon erlauben, dann z. B. „Bell!“, „Furz mal!“,
„Komm her“, „Stopp“, „Lauter“, „Leiser“, „Augen blau“, „Licht aus“ oder „Regenbogen“ sagen. Die App hört dauerhaft zu, solange
sie offen ist, und nutzt die Spracherkennung des Geräts (bei Samsung meist Google-Spracheingabe; ohne
Internet braucht es das deutsche Offline-Sprachpaket).

**Gesprächsmodus**: Mit eingeschalteter Sprachsteuerung reagiert Dog-E auf „Hallo“, „Ball spielen“, „Wie geht's?“,
„Wie heißt du?“, „Braver Hund“, „Hab dich lieb“, „Hunger“, „Tanzen“, „Witz“, „Gute Nacht“ und „Tschüss“.
Das Tablet antwortet mit einer Hundestimme (Sprachausgabe, abschaltbar in den Einstellungen), der Hund
bellt, wedelt, fährt oder leuchtet dazu. Die Reaktionen stehen in `voice/Conversation.kt` und lassen sich
leicht erweitern. Ohne Sprache: Im Tab *Steuern* unter „Gespräch“ antippen.

**Internet: Witze & Wetter** – „Erzähl einen Witz“ holt kindgerechte Hundewitze aus
[`content/witze.json`](content/witze.json) (hier im Projekt; neue Witze einfach dort eintragen, die App lädt
sie einmal am Tag bzw. über *Einstellungen → Witze aktualisieren*). Ohne Internet gibt es ein paar
eingebaute Witze. „Wie ist das Wetter?“ sagt das aktuelle Wetter für den Wohnort aus den Einstellungen
(Daten von [Open-Meteo](https://open-meteo.com), kostenlos, ohne Konto und ohne GPS) mit einem Tipp
wie „Zieh eine Jacke an“; die LEDs leuchten passend (Sonne gelb, Regen blau, Schnee eisblau …).

**Tablets** (z. B. Samsung Galaxy Tab): Navigation an der Seite, zweispaltige Ansicht, größere Tasten;
Hoch- und Querformat.

Die Befehle sind vorbelegt (`data/DogeProtocol.kt`) und lassen sich in der App ändern oder ergänzen.

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
