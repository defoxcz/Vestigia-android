# Mobilní prostředí Vestigia Scriptorium

## Hlavní obrazovka a navigace

Moduly odpovídají registru desktopové aplikace:

- Genealogie
- Rukopisy
- Heraldika
- Sfragistika
- Epigrafika
- Paleografika

Na hlavní obrazovce není souhrnná položka Další moduly ani štítky Připravujeme.
Genealogie má zvýrazněný vstup, ostatní obory mají vlastní kartu. Mobilní editory
budou implementovány postupně; zahrnutí karty není tvrzením, že je editor hotový.
Numismatika je nyní součástí badatelských pomůcek, ne samostatný registrovaný
modul desktopu.

Telefon má tři stálé cíle: Moduly, Pomůcky, Stahování. Od šířky okna 700 dp se
spodní navigace mění na boční lištu. Detaily podporují horní tlačítko Zpět i
systémové gesto. Inkoustová modř, světlý papír a patkové nadpisy doplňují čitelné
systémové popisky. Podporován je tmavý režim, čeština a angličtina.

## Genealogie jako další pracovní modul

Vstupní obrazovka má začátek nového výzkumu. Následující implementace:
Rodokmeny → Seznam osob → Detail osoby → Vazby, události, místa a prameny.
Diagram doplní seznam vhodný pro telefon. Nejprve vznikne založení projektu,
osoby a příbuzenského vztahu. Před přenosem desktopových dat musí být ověřen
formát, identita záznamů a kompatibilita. Editor ani import nejsou zatím hotové.

## Badatelské pomůcky a správce stahování

Katalog má všech 14 stávajících databázových balíčků, hledání, oborové filtry a
přehled skutečně nainstalovaných pomůcek. Detail rozlišuje přenosovou velikost
a místo po instalaci a nabízí stažení, aktualizaci a potvrzenou odinstalaci.
Nainstalované pomůcky se otevírají přímo do samostatného sdíleného prohlížeče,
bez vazby na moduly. Textová a obrazová podoba sdílejí datovou vrstvu, hledání,
filtry, stránkování a detail. U obrazů je nutné rozlišit vložené obrázky šablon
od online obrázků na Commons; dříve načtené snímky mohou být v mezipaměti.
Podrobnosti: [readers.md](readers.md).

Stahování už není simulace. WorkManager provádí přenos na pozadí, ověření a
instalaci. Obrazovka ukazuje skutečné bajty, frontu, chyby, nainstalované balíčky
a využité místo. Síťová podmínka neměřeného připojení je skutečná. Pozastavení
zastaví přenos a uklidí částečný archiv; pokračování stahuje od začátku.

Odinstalace je v detailu i ve správci. Dialog jasně pojmenuje balíček a rozsah
mazání. Po potvrzení se zastaví jeho úloha a odstraní pouze jeho adresář.
Rodokmeny, osobní dokumenty a ostatní pomůcky se nemění. Odinstalovanou pomůcku
lze kdykoli znovu stáhnout.

Technický kontrakt, bezpečné ověření, aktualizace, kompatibilita se starším
webovým katalogem a testy jsou popsány v [downloads.md](downloads.md).

## Náhledy

- [Moduly](previews/modules.png)
- [Badatelské pomůcky](previews/aids.png)
- [Stahování](previews/downloads.png)
- [Vstup do Genealogie](previews/genealogy.png)
- [Tmavý režim](previews/dark.png)
- [Tablet](previews/tablet.png)
- [Textový prohlížeč pomůcky](previews/reader-text.png)
- [Obrazový prohlížeč pomůcky](previews/reader-images.png)

Compose náhledy jsou pouze vykreslené obrazovky; nespouštějí skutečná stahování.
Sestavení a statická kontrola: `./gradlew assembleDebug lintDebug`.
Testy instalace: `./gradlew testDebugUnitTest connectedDebugAndroidTest`.
UI testy vyžadují fyzický odemčený telefon; na zamčeném telefonu se přeskočí.
Emulátor se nepoužívá.
