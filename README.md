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
adb -d shell pidof com.vestigiascriptorium.android.debug
adb -d logcat --pid=ZDE_PID
```

## F5: automatické sestavení, instalace a debugger

V panelu Run and Debug vyberte **Android: VestigiaScriptorium Debug na telefonu**.
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
k uživatelským databázím desktopu. Přihlášení používá společný účet Vestigia.
Git remote: `https://github.com/defoxcz/Vestigia-android`. Publikace v obchodě není nastavena.

## Debug a trvalá Release instalace

| Varianta | Application ID | Název v telefonu |
| --- | --- | --- |
| Debug | `com.vestigiascriptorium.android.debug` | Vestigia Scriptorium Debug |
| Release | `com.vestigiascriptorium.android.release` | Vestigia Scriptorium Release |

Obě aplikace mohou běžet současně a mají samostatná data i stažené pomůcky.
F5 a `scripts/run-device.sh` sestavují, aktualizují a restartují pouze Debug.
Před instalací skripty ověří ID, příznak ladění a platný podpis APK.
Instalace vždy používá `adb install -r`, nikdy odinstalaci ani mazání dat.
Původní aplikace s ID `com.vestigiascriptorium.android` zůstává zachována;
její data se do nových variant automaticky nepřenášejí.

Release sestavíte `./gradlew assembleRelease` a nainstalujete
`./scripts/run-release-device.sh` nebo úlohou **Android: nainstalovat a spustit Release**.
APK je `app/build/outputs/apk/release/app-release.apk`. Release neumožňuje debugger.

Trvalý podpisový klíč je lokálně v `~/.config/vestigia-android/release.keystore`.
Konfigurace `~/.config/vestigia-android/release-signing.properties` obsahuje
`storeFile`, `keyAlias`, `storePassword` a `keyPassword`. Cestu ke konfiguraci lze
změnit proměnnou `VESTIGIA_ANDROID_SIGNING_PROPERTIES`. Zálohujte bezpečně oba
soubory: další aktualizace Release vyžadují stejný klíč. Klíč ani hesla nepatří do Gitu.
Na jiném počítači bez konfigurace Gradle vytvoří nepodepsané Release APK;
instalační skript jej odmítne. Nejprve přeneste bezpečně podpisovou konfiguraci.

## Účet a testovací přístup

Debug i Release nyní vyžadují přihlášení; není zde vývojová výjimka.
Tlačítko **Přihlásit se / Registrovat** otevře systémový prohlížeč se stávajícím
webovým přihlášením/registrací. Uživatel porovná osmimístný kód a potvrdí
připojení. Heslo ani Firebase token aplikace nezískává. Registrace sama nestačí:
web musí potvrdit aktivní účet, ověřený e-mail, individuální `testAccess` a
zapnutý globální testovací program. Role admin ani Premium testování nenahrazují.

Android využívá již nasazený společný protokol `/api/desktop/license` a
`/cs/account/connect`. Názvy endpointů, audience `vestigia-desktop` i označení
webového potvrzení zůstávají kvůli kompatibilitě stejné. Android si generuje
vlastní náhodný údaj, nikdy nepřebírá přihlášení desktopu. Není nutná změna webu
ani účtů. Registrace a reset hesla probíhají existujícím webovým postupem.

Podpis RSA-SHA256 se ověřuje veřejným klíčem v assets/account-public.der
(SPKI, převedený z veřejného klíče desktopu). Kontrolují se také audience,
identita, vazba na náhodný údaj, časy a explicitní podepsané testovací oprávnění.
Obálka a přihlašovací údaj se ukládají pomocí AES-256-GCM a Android Keystore
mimo zálohování (`no_backup/account-v1.enc`). Debug a Release mají vlastní
relaci, úložiště i klíč. Tajné údaje se nelogují ani nevkládají do URL;
fragment odkazu obsahuje pouze SHA-256 challenge.

Ověření probíhá při návratu do aplikace a každých 15 minut v popředí.
Offline oprávnění vydané serverem platí nejvýše 24 hodin, přihlašovací relace
nejvýše 90 dní. Neúspěšné spojení offline platnost neprodlužuje; potvrzené
odvolání relace nebo testování přístup zablokuje. Monotónní čas omezuje návrat
hodin během běhu, uložený nejvyšší čas mezi spuštěními. Nejde o absolutní
ochranu proti úpravě zařízení nebo binárky. Po vypršení se zobrazí vstupní
kontrola také nad otevřeným prohlížečem pomůcky. Bez oprávnění se neprovádí
instalace balíčků na pozadí. Odhlášení a ztráta přístupu nikdy nemažou pomůcky.
**Účet** v horní liště nabízí stav, ruční ověření a odhlášení. Při offline
odhlášení se odstraní místní údaj; serverovou relaci lze odvolat pouze online.

Identita, edice a testovací povolení jsou oddělené. Základ pro budoucí
freemium/premium používá podepsaná pole `edition` a `kind`; aktuální vstup
stále vyžaduje testovací povolení. Placené funkce ani platby zatím nejsou zapnuté.

Ověření přihlašování: `./gradlew testDebugUnitTest lintDebug lintRelease` a na
skutečném telefonu `./gradlew connectedDebugAndroidTest
-Pandroid.testInstrumentationRunnerArguments.class=com.vestigiascriptorium.android.account.AccountDeviceTest,com.vestigiascriptorium.android.account.AccountGateUiTest`
(příkaz zadejte na jeden řádek). Testy Keystore používají vlastní dočasný alias;
produkční síťový test pouze ověřuje odmítnutí neschváleného náhodného požadavku.
Dokončení přihlášení skutečného účtu vyžaduje potvrzení uživatele v prohlížeči.

## Verzování

Obě varianty používají verzi `1.0.XXXX`, kde `XXXX` je číslo buildu doplněné
zleva nulami na čtyři číslice. Aktuální build je **1.0.0002** (`versionCode=2`);
předchozí pracovní APK měly `versionCode=1`. Debug/Release se rozlišují názvem,
ID a podpisem, nikoli příponou čísla verze. Verzi najdete také na přihlašovací
obrazovce a v nabídce **Účet**.

Jediný zdroj čísla je kořenový `version.properties`, položka `buildNumber`.
Před každým novým distribuovaným buildem číslo zvýšit a commitnout se změnami.
Gradle z něj odvodí `versionName` i `versionCode`; F5 i všechny sestavovací
skripty používají stejnou hodnotu. Opakované sestavení téhož buildu ani lint
číslo automaticky nemění. Debug a Release stejného buildu mají shodnou verzi.
Podporovaný rozsah je 1–9999. Před přechodem na další řadu zachovat rostoucí
Android `versionCode`.
