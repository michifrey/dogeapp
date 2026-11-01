# Dog-E Control

Eine eigene Android-App, um den WowWee **Dog-E** Roboterhund per Bluetooth Low Energy (BLE) zu steuern.
Sie ist als Ersatz gedacht, weil die offizielle App (`com.wowwee.doge_robot`) für Android nicht mehr gepflegt wird.

> **Stand:** Das Bluetooth-Protokoll des Dog-E ist nicht öffentlich dokumentiert, und ich habe auch keine
> Community-Dokumentation dazu gefunden. Die App ist deshalb zweigeteilt:
> 1. ein **BLE-Explorer**, mit dem du das Protokoll herausfindest, und
> 2. eine **Fernsteuerung** (Steuerkreuz + Aktions-Tasten), deren Befehls-Bytes du in der App einträgst.
>
> Wie du an die Befehle kommst, steht in [`docs/PROTOKOLL.md`](docs/PROTOKOLL.md).

## Funktionen

| Tab | Was er macht |
|-----|--------------|
| **Verbinden** | Sucht BLE-Geräte, hebt den Hund hervor und verbindet sich. |
| **Steuern** | Steuerkreuz (gedrückt halten = fahren, loslassen = Stopp) und frei belegbare Aktions-Tasten. |
| **Explorer** | Zeigt alle Services und Characteristics. Du kannst rohe Hex-Bytes senden, Werte lesen und den Steuer-Kanal festlegen. Alle Benachrichtigungen des Hundes landen im Log, das du kopieren kannst. |
| **Befehle** | Befehlstabelle bearbeiten (Name + Hex-Bytes), Befehle hinzufügen und testen, Konfiguration als JSON exportieren und importieren. |

Die Einstellungen werden auf dem Gerät gespeichert. Ist das Protokoll einmal bekannt, kannst du die
Werte auch fest in `DogeConfig.DEFAULT_COMMANDS` eintragen, dann sind sie nach jeder Installation da.

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
  data/DogeConfig.kt     Protokoll-Konfiguration (UUIDs, Befehlstabelle), Speicherung als JSON
  data/Hex.kt            Hex-Strings parsen und formatieren
  ui/                    Compose-Screens (Verbinden, Steuern, Explorer, Befehle)
tools/doge_explorer.py   Gleicher Explorer für den PC (Python + bleak)
docs/PROTOKOLL.md        Anleitung: Wie man das Dog-E-Protokoll herausfindet
```

## Hinweis

Das ist ein privates Projekt ohne Verbindung zu WowWee. „Dog-E“ und „WowWee“ sind Marken ihrer jeweiligen Inhaber.
