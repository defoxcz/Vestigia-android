# Vestigia Scriptorium pro Android

Samostatná aplikace v Kotlinu a Jetpack Compose. Hlavní obrazovka obsahuje všech
šest modulů desktopu: Genealogie, Rukopisy, Heraldika, Sfragistika, Epigrafika a
Paleografika. Genealogie má zatím vstupní obrazovku; editory jednotlivých modulů
budou implementovány postupně. Na hlavní obrazovce nejsou štítky Připravujeme.

Badatelské pomůcky se skutečně stahují, ověřují a instalují. Správce zobrazuje
reálný průběh, síťovou podmínku, chyby a nainstalované balíčky. Odinstalace maže
jen potvrzenou pomůcku a nezasahuje do vlastních badatelských dat.
Mobilní katalog obsahuje 13 pomůcek; heraldické šablony jsou pouze pro desktop.
Nainstalované pomůcky se otevírají přímo do společného prohlížeče: textový seznam
nebo obrazová galerie, hledání, filtry, stránkování, detaily a zvětšení obrázků.
Jsou samostatnými funkcemi a nevyžadují otevření žádného pracovního modulu.
Podrobnosti: [Prohlížeče](docs/readers.md), [Stahování a odinstalace](docs/downloads.md),
[Návrh GUI](docs/gui-design.md).
Minimum Android 8.0 (API 26), compile/target SDK 36.

## Otevření ve VS Code

```sh
code /Users/defox/Development/Android/VestigiaScriptorium
```

Po změně prostředí otevřete nový terminál. Doporučené rozšíření je oficiální
Kotlin by JetBrains (`JetBrains.kotlin-server`); jeho Android podpora je zatím
experimentální. Sestavení nezávisí na jazykovém serveru ani Android Studiu.
Rozšíření může při prvním spuštění požádat o region a nastavení sdílení dat.

## Sestavení a kontrola

```sh
./gradlew assembleDebug
./gradlew lintDebug
```

JDK 21 běží z Homebrew; Java/Kotlin bytecode cílí na 17. Používáme připnutý
Gradle Wrapper 8.13, Android Gradle Plugin 8.13.2, Kotlin/Compose compiler 2.2.21
 a Compose BOM 2025.10.01. SDK je lokálně v `~/Library/Android/sdk`.
`local.properties` obsahuje cestu konkrétního počítače a není určeno do Gitu.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Skutečný telefon přes ADB

1. V telefonu zapněte možnosti pro vývojáře a Ladění USB.
2. Připojte telefon datovým kabelem, odemkněte jej a potvrďte RSA klíč počítače.
3. Ověřte `adb devices -l`. Zařízení musí mít stav `device`, nikoli `unauthorized`.
4. Spusťte `./scripts/run-device.sh` nebo úlohu VS Code
   **Android: nainstalovat a spustit na telefonu**.

Při více zařízeních zadejte `./scripts/run-device.sh SERIAL`.
Skript sestaví debug APK, nainstaluje jej pomocí `adb install -r` a otevře aplikaci.
Instalace zachovává existující data aplikace. Emulátor ani systémové obrazy nejsou
součástí prostředí a nepoužívají se. Další VS Code úlohy jsou dostupné přes
**Terminal → Run Task**; výchozí sestavení také přes Cmd+Shift+B.

Pro log konkrétního procesu po spuštění:

```sh
adb -d shell pidof com.vestigiascriptorium.android
adb -d logcat --pid=ZDE_PID
```

## F5: automatické sestavení, instalace a debugger

V panelu Run and Debug vyberte **Android: VestigiaScriptorium na telefonu**.
Nastavte breakpoint například na prvním vykonávaném řádku `onCreate()` v
`MainActivity.kt` a stiskněte **F5** (podle nastavení Macu **Fn+F5**).

Před připojením debuggeru proběhne `assembleDebug`, instalace APK se zachováním
dat a restart aplikace v režimu čekání na debugger. ADB přesměruje JDWP daného
procesu na `localhost:5005`; připojí se debugger nainstalovaného Kotlin rozšíření.
F10 krokuje, F11 vstupuje do metody, F5 pokračuje, Shift+F5 odpojí debugger.
Před každým novým spuštěním se aplikace znovu sestaví a nainstaluje.

Telefon musí být připojený a autorizovaný (USB nebo Wi-Fi). Při více zařízeních
nastavte `ANDROID_SERIAL` v prostředí před spuštěním VS Code. Port 5005 musí být
volný. Po ukončení debug session se přesměrování odstraní; při nedokončeném
připojení použijte úlohu **Android: odpojit debug port** a spusťte F5 znovu.
Pokud jazykový server ještě nabíhá nebo požaduje úvodní nastavení, dokončete je
v panelu rozšíření Kotlin. Android podpora rozšíření je experimentální.

## Postup přenosu Vestigie

1. Vybrat první malý ucelený scénář pro telefon a navrhnout jeho Compose obrazovky.
2. Popsat datový formát a vazby na desktop před implementací importu/exportu.
3. Přidat lokální úložiště (například Room) a ViewModel pro daný scénář.
4. Ověřit skutečná data v kopiích a provoz bez připojení.
5. Teprve podle potřeby doplnit přihlášení a komunikaci s existujícím webovým API.

C++/Qt UI se nepřenáší přímo. Sdílet můžeme specifikace formátů, testovací příklady
 a API kontrakty; Android UI a aplikační logiku implementujeme v Kotlinu.
Desktop: `/Users/defox/Development/Desktop/Vestigia`.
Web: `/Users/defox/Development/Web/Vestigia`.
Aplikace používá veřejné webové API katalogu a stahování pomůcek. Nepřistupuje
k uživatelským databázím desktopu ani účtům.
Application ID `com.vestigiascriptorium.android` je pracovní; před distribucí jej
potvrdit. Git remote: `https://github.com/defoxcz/Vestigia-android`.
Produkční podpis a publikace v obchodě nejsou nastaveny.
