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

- Debug aplikace slouží uživateli jako stálá instalace. Aktualizovat výhradně
  pomocí `adb install -r`; bez výslovného pokynu nikdy neodinstalovat aplikaci,
  nemazat její data ani existující pomůcky. Testy prohlížečů používají izolované
  testovací databáze, nikoli uživatelovu knihovnu.
