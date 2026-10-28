# Samostatné prohlížeče badatelských pomůcek

## Ovládání

Po klepnutí na nainstalovanou pomůcku se otevře přímo její obsah v jednotné
`AidReaderActivity`. Pomůcky jsou samostatné funkce. Neotevírají ani nemění
projekt, nevyžadují modul Genealogie, Rukopisy apod. a nevytvářejí vazby na moduly.
Obory v katalogu slouží jen k orientaci.

Informace o velikosti a stažení se zobrazují pouze před instalací. Aktualizace,
odinstalace a přehled místa zůstávají ve Stahování. Otevřením prohlížeče se nic
nestahuje ani neodinstalovává z knihovny databází.

Obě podoby prohlížeče používají stejné hledání, stránkování po 24 záznamech,
návrat ze záznamu do seznamu, zdrojové odkazy a čtení pouze pro čtení:

- Text: seznam s krátkým souhrnem a detail s kopírovatelnými údaji.
- Obrazy: adaptivní galerie, filtr záznamů s dostupným obrázkem, stejný detail
  doplněný o fotografie/náhledy a přiblížení dvěma prsty. Tlačítko 100 % vrací
  obraz do výchozího měřítka.

Filtry odpovídají obsahu: země u místopisu, historických měr a heraldiky; jazyk
u písařských zkratek; druh u heraldických šablon. Hledání je zpožděno o 60 ms,
ruší předchozí dotaz a probíhá na IO vlákně. U rozsáhlých numismatických a
sfragistických dat a náhrobníků se využije existující FTS5, pokud jej systémová
SQLite podporuje. Jinak je k dispozici textové hledání. Android 11+ podporuje
normalizované hledání bez diakritiky přes lokální SQL funkci; Android 8–10
používá literální LIKE s převodem na malá písmena. Speciální znaky a FTS tokeny
se nepovažují za SQL kód, hodnoty jsou vždy vázané parametry.

## Sdílená implementace

- `AidSchemas`: explicitní adaptéry ověřených schémat, názvy polí, filtry a SQL.
- `AidReaderSource`: společný stránkovaný dotaz, detail, média a licence. Krátká
  spojení používají `OPEN_READONLY`; dotazy nemění referenční ani vlastní data.
- `AidReaderScreen`: jeden stav hledání, filtru, stránky, výběru a chyb pro oba
  způsoby prezentace. Zpět vrátí detail do seznamu bez ztráty hledání.
- `AidImages`: jeden loader, velikost náhledu, disková mezipaměť, chybový stav,
  opakování a zvětšení pro všechny obrazové databáze.
- `AidReaderActivity`: jedna neveřejná aktivita pro všech 13 mobilních ID.

Schémata vycházejí z `src/ui/reference/referencedatamodel.cpp` a
`src/ui/reference/heraldrytemplatesview.cpp` desktopu a ze skutečných
publikovaných ZIPů. Databáze ani jejich formáty nejsou kvůli Androidu měněny.

| Pomůcka | Adaptér / hlavní tabulka | Podoba |
| --- | --- | --- |
| Historické míry a váhy | units + unit_types | Text |
| Kalendář světců | saints + saint_names + feast_days | Text |
| Historická topografie | places + place_names | Text |
| Písařské zkratky | abbreviations | Text |
| Šest heraldických katalogů | entities + entity_labels + images | Obrazy |
| Numismatika a sfragistika | objects + object_search + images | Obrazy |
| Šlechtické náhrobníky | monuments + entities + inscriptions + media | Obrazy |

Detail ukazuje čitelné názvy, související texty a prameny; interní JSON a
geometrie zůstávají implementační údaje. Český a anglický text rozhraní jsou
oddělené od jazyků samotných původních dat.

## Obrázky a offline režim

Desktopové heraldické šablony nejsou v nabídce ani mezi podporovanými adaptéry
Androidu. Obrazové katalogy většinou obsahují jména souborů na Wikimedia Commons,
nikoli obrazová data. Tyto obrázky vyžadují internet, dokud nejsou v mezipaměti. Rozhraní tento
rozdíl pojmenovává a při nedostupnosti nabízí opakování a dostupný zdrojový odkaz.

Coil zajišťuje dekódování PNG/JPEG/WebP/SVG a zmenšení náhledů; obrázky se drží
v samostatné diskové cache do 64 MiB. Jde o dočasnou cache, ne záruku trvalého
stažení všech obrázků. Odinstalace databáze neodstraňuje společnou obrazovou
cache, protože stejný obrázek může používat více katalogů. Android ji může
uvolnit sám.

Respektují se publikované příznaky `public_domain` / `attribution_required`.
Obrázky vyžadující ruční kontrolu nebo s neznámou politikou se automaticky
nenačítají. Detail zobrazuje dostupného autora, kredit, licenci, podmínky,
omezení a odkazy na licenci a původní soubor. Odkazy se otevírají jen pro
HTTP/HTTPS; HTML se v textových údajích nespouští.

## Ověření

JVM testy ověřují registr 13 schémat a bezpečné zpracování hledání.
Skutečné databáze všech 13 mobilních pomůcek byly ověřeny readonly na hostiteli pro cs/en,
včetně seznamu, detailu, hledání, filtrů a mediálních dotazů (26 kombinací).
Postup opakování nad vlastními kopiemi veřejných databází:

```sh
VESTIGIA_READER_PROBES=/tmp/reader-probes.tsv ./gradlew testDebugUnitTest --rerun-tasks
python3 scripts/verify-reader-schemas.py /cesta/k/verejnym/databazim /tmp/reader-probes.tsv
```

Pro fyzický telefon jsou připraveny izolované zmenšené fixture databáze:

```sh
python3 scripts/prepare-reader-fixtures.py /cesta/k/verejnym/databazim
./scripts/test-device.sh com.vestigiascriptorium.android.aids.reader.ReaderDeviceTest,com.vestigiascriptorium.android.aids.reader.ReaderUiTest,com.vestigiascriptorium.android.ui.main.LibraryUiTest
```

Generované fixture DB jsou ignorované Gitem a patří jen do testovacího APK.
Testy čtou jedinečný cache adresář, nikdy uživatelovu knihovnu. GUI testy vyžadují
odemčený telefon. Stálá Debug aplikace se aktualizuje pouze `adb install -r`,
neodinstalovává se a její data zůstávají zachována.

## Náhledy a stav ověření

[Textový prohlížeč](previews/reader-text.png) a [obrazový prohlížeč](previews/reader-images.png)
jsou vykreslené Compose náhledy se vzorovými řádky a záznamy heraldického katalogu.
Jedenáct JVM testů, sestavení aplikace i testovacího APK a Android Lint prošly.
Všech 26 kombinací databáze/jazyka prošlo readonly ověřením dotazů nad plnými
veřejnými databázemi včetně rozšiřujících údajů. Na fyzickém Samsungu / Android 17 prošlo všech devět testů prohlížečů,
rušení dotazů, navigace a odinstalačního dialogu. Testy používají izolované kopie
13 veřejných databází a nezasahují do nainstalovaných pomůcek uživatele.

## Přechod ze starší Android verze se šablonami

Katalog na serveru může dál obsahovat `heraldry-templates` pro desktop. Android
jej odfiltruje i ze staré lokální cache a neumožní nové stažení. Rozpracovaná
úloha šablon se zruší, ale nainstalované ani dočasné soubory se automaticky
nemažou. Pokud existují, Stahování zobrazí pouze desktopovou položku s možností
potvrzené odinstalace, bez otevření, aktualizace a opětovného stažení.


### Hledání obcí (historické chování 1.0.0003–1.0.0005)

Filtr obcí porovnává aktuální i historické/jazykové názvy; text správních
jednotek není názvem obce a při hledání se neporovnává. Proto dotaz Břeclav
nevrací všechny obce okresu Břeclav. Hledání podle normalizovaných variant
zachovává zadávání bez diakritiky. Změny textu včetně náhrady návrhem IME
spustí nový dotaz, oříznou okolní mezery a během načítání skryjí staré výsledky.
PlaceSearchTest ověřuje postupné prefixy, diakritiku, historické názvy,
kombinaci s filtrem země a nahrazení breclav → Břeclav s koncovou mezerou.
Testovací databáze je samostatná dočasná kopie bez zásahu do knihovny.


### Obce na mapě (1.0.0004)

Detail obce obsahuje tlačítko Zobrazit na mapě. Předává ověřené souřadnice
z databáze, případně hledá podle názvu a země. Android nabídne výběr aplikace
podporující geo: odkazy i při nastavené výchozí aplikaci. Pokud mapová aplikace
není dostupná nebo před otevřením zmizí, nabídne se webová mapa OpenStreetMap
v prohlížeči. Pokud není ani prohlížeč, zobrazí se srozumitelná zpráva.
Bez oprávnění k poloze a bez změny databáze; údaje místa se předají externí
aplikaci teprve po stisknutí tlačítka. Pro ostatní pomůcky se tlačítko nepřidává.
Manifest deklaruje viditelnost pouze aplikací pro geo: a HTTPS odkazy.
Podklad: https://developer.android.com/guide/components/intents-common#Maps


### Rozsah a rychlost hledání (1.0.0006)

Výchozí **Pouze názvy** porovnává pouze název, který je zobrazený v seznamu.
Historické názvy, popis ani správní jednotky v tomto režimu výsledek nerozšiřují.
**Všechna pole** zahrnují názvy, varianty a obsah polí detailu. Změna rozsahu,
textu (včetně návrhu IME) a země přepočítá výsledky a vrátí první stránku.

Topografie používá odvozený SQLite index v cache `reader-search`, oddělený
od stažené databáze. Názvy a všechna pole mají samostatné sloupce fulltextového indexu FTS4 s prefixovými indexy pro 1–4 znaky; diakritika
se normalizuje jednou při přípravě, nikoli pomocí Kotlin callbacku nad každým
záznamem při každém dotazu. Index pro řazení a země zrychlují stránkování a filtry. Příprava a načítání používají omezené dávky, aby opakované plnění Android CursorWindow neprocházelo znovu celou databázi.
Topografie vyhledává začátky slov, nezávisle na velikosti písmen a diakritice. Více zadaných slov musí odpovídat současně.
Při prvním otevření po aktualizaci dat se zobrazí jednorázová příprava indexu.
Další otevření jej opakovaně používají; změna cesty/verze DB vytvoří nový index.
Smazání cache vede jen k nové přípravě, nikoli ke ztrátě pomůcky. Příprava je
atomická a změna dotazu ji nerestartuje. Uchovávají se maximálně dva indexy.

Výkon se ověřuje na skutečném Samsungu nad plnou veřejnou databází 212 166 obcí,
nikoli pouze nad dvousetřádkovými fixtures. Pro tento test zkopírovat veřejný ZIP
`/tmp/vestigia-reader-fixtures/places.zip` do ignorované složky
`app/src/androidTest/assets/performance/places.zip` a spustit:

```sh
./scripts/test-device.sh com.vestigiascriptorium.android.aids.reader.PlaceSearchTest,com.vestigiascriptorium.android.aids.reader.PlaceSearchPerformanceTest,com.vestigiascriptorium.android.aids.reader.ReaderDeviceTest
adb logcat -d -s VestigiaSearchPerf:I '*:S'
```

Testy používají vlastní dočasné DB a cache. Kontrolují názvy vs. varianty/okres,
rozsah v UI, diakritiku, více zemí a opětovné použití indexu. Výkonnostní limity
pro připravený index na testovaném telefonu jsou 250 ms pro názvy a 500 ms pro
všechna pole; první příprava se měří odděleně. Nový testovací skript neprovádí
odinstalaci ani mazání dat žádné varianty aplikace.

Naměřeno na Samsung SM-S938B (Android 17): názvy 21–85 ms, všechna pole
20–76 ms pro postupné B → Bř → Bře → Břec → Břeclav, přes všechny země.
Časy nezahrnují 60ms prodlevu pro psaní. Jednorázová příprava plného indexu
byla při aktivním telefonu přibližně 11 sekund; při uspání systému může trvat
déle. Výkonnostní test proto během měření drží dočasný CPU wake lock.


### Obrazové pomůcky (1.0.0010)

Sdílené požadavky na Wikimedia Commons identifikují aplikaci pomocí User-Agent;
původní požadavek na telefonu vracel HTTP 403. Stejná adresa a disková cache
se používají v galerii, detailu i zoomu; náhled se dekóduje v menší velikosti.
Klepnutí na obrázek v detailu otevře společný celoobrazovkový prohlížeč
s přiblížením prsty, posunem a tlačítky −/+. Údaj měřítka vrací obraz na 100 %.
Záznamy čekající na kontrolu podmínek použití mají výslovné vysvětlení místo
prázdného náhledu; pravidla zveřejnění obrázků se nemění.

Na fyzickém telefonu prošlo načtení jednoho reprezentativního obrázku pro
každou z devíti obrazových pomůcek včetně následného načtení pouze z diskové
cache. UI test heraldiky ověřil otevření detailu, zoom, zvětšení a reset.
Nejde o kontrolu každého obrázku v katalogu.
