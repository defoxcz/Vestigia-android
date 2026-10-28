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
u písařských zkratek; druh u heraldických šablon. Hledání je zpožděno o 250 ms,
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
- `AidReaderActivity`: jedna neveřejná aktivita pro všech 14 známých ID.

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
| Heraldické šablony | templates + template_translations | Obrazy |
| Numismatika a sfragistika | objects + object_search + images | Obrazy |
| Šlechtické náhrobníky | monuments + entities + inscriptions + media | Obrazy |

Detail ukazuje čitelné názvy, související texty a prameny; interní JSON a
geometrie zůstávají implementační údaje. Český a anglický text rozhraní jsou
oddělené od jazyků samotných původních dat.

## Obrázky a offline režim

Heraldické šablony mají PNG přímo v databázi a fungují zcela offline. Ostatní
katalogy většinou obsahují jména souborů na Wikimedia Commons, nikoli obrazová
data. Tyto obrázky vyžadují internet, dokud nejsou v mezipaměti. Rozhraní tento
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

JVM testy ověřují registr 14 schémat a bezpečné zpracování hledání.
Skutečné databáze všech 14 pomůcek byly ověřeny readonly na hostiteli pro cs/en,
včetně seznamu, detailu, hledání, filtrů a mediálních dotazů (28 kombinací).
Postup opakování nad vlastními kopiemi veřejných databází:

```sh
VESTIGIA_READER_PROBES=/tmp/reader-probes.tsv ./gradlew testDebugUnitTest --rerun-tasks
python3 scripts/verify-reader-schemas.py /cesta/k/verejnym/databazim /tmp/reader-probes.tsv
```

Pro fyzický telefon jsou připraveny izolované zmenšené fixture databáze:

```sh
python3 scripts/prepare-reader-fixtures.py /cesta/k/verejnym/databazim
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vestigiascriptorium.android.aids.reader.ReaderDeviceTest,com.vestigiascriptorium.android.aids.reader.ReaderUiTest,com.vestigiascriptorium.android.ui.main.LibraryUiTest
```

Generované fixture DB jsou ignorované Gitem a patří jen do testovacího APK.
Testy čtou jedinečný cache adresář, nikdy uživatelovu knihovnu. GUI testy vyžadují
odemčený telefon. Stálá Debug aplikace se aktualizuje pouze `adb install -r`,
neodinstalovává se a její data zůstávají zachována.

## Náhledy a stav ověření

[Textový prohlížeč](previews/reader-text.png) a [obrazový prohlížeč](previews/reader-images.png)
jsou vykreslené Compose náhledy se vzorovými řádky a skutečnými náhledy šablon.
Deset JVM testů, sestavení aplikace i testovacího APK a Android Lint prošly.
Všech 28 kombinací databáze/jazyka prošlo readonly ověřením dotazů nad plnými
veřejnými databázemi včetně rozšiřujících údajů. Nové testy na fyzickém telefonu
zatím nebyly spuštěny, protože zařízení nebylo při této změně připojené.
