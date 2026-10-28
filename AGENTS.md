# VestigiaScriptorium Android

- Kotlin + Jetpack Compose, samostatný projekt od webu i Qt desktopu.
- Nepoužívat ani neinstalovat emulátor nebo systémové obrazy. Testování výhradně
  na skutečném zařízení přes ADB. Bez připojeného zařízení uvést tento limit.
- Před změnami přečíst README.md. Ověření: ./gradlew assembleDebug lintDebug.
- Při změně datových formátů nebo API přečíst související desktopovou/webovou
  dokumentaci a AGENTS.md; nenarušit existující produkční kontrakty.
- Nepracovat přímo s uživatelskými daty desktopové aplikace. Používat kopie.
- Android Studio není závislost prostředí. Build přes Gradle Wrapper a SDK CLI.

- Po dokončení a ověření práce vždy commit a push do
  https://github.com/defoxcz/Vestigia-android. Nezahrnovat lokální cesty SDK,
  podpisové klíče, cache, APK, uživatelská data ani tajné údaje.

- Release slouží jako stálá instalace, Debug má samostatné ID s příponou `.debug`.
  Zachovat také původní instalaci bez přípony. Všechny varianty aktualizovat výhradně
  pomocí `adb install -r`; bez výslovného pokynu nikdy neodinstalovat aplikaci,
  nemazat její data ani existující pomůcky. Testy prohlížečů používají izolované
  testovací databáze, nikoli uživatelovu knihovnu.

- Debug i Release vyžadují skutečný účet a podepsané testovací oprávnění.
  Nevytvářet lokální přepínač obcházející přihlášení ani neměnit uživatelská
  oprávnění kvůli testům. Přihlašování probíhá v systémovém prohlížeči;
  nečíst přihlašovací údaje desktopu nebo webového prohlížeče.
  Testy používají vlastní dočasný Keystore alias a soubor; skutečnou relaci
  nikdy nemažou. Úspěšný test nepřihlášeného vstupu není důkaz přihlášení účtu.

- Verze aplikace: `1.0.XXXX`, zdroj `version.properties` (`buildNumber`).
  Při nové distribuované změně zvýšit číslo buildu a commitnout je společně
  s kódem. Debug a Release stejného buildu mají shodnou verzi. Samotné ověření
  nebo opakované sestavení téže změny číslo nezvyšuje.

- Testy na telefonu spouštět přes `scripts/test-device.sh CLASS[,CLASS]`. Skript
  instaluje obě Debug APK jen s `adb install -r` a spouští instrumentaci přímo;
  nepoužívat Gradle `connectedDebugAndroidTest`, který může cílovou testovanou
  aplikaci po testu odinstalovat. Release se při testech nikdy nemění.
