# Genealogie na Androidu — první pracovní celek

Graf vazeb je hlavní pohled. Rodokmen vzniká bez výchozí stránky matriky nebo
jiného pramene. Modul je přístupný přes společnou kontrolu účtu a testovacího
oprávnění, stejně jako pomůcky. Nepřidává žádnou výjimku přihlášení.

## Práce s rodokmenem

1. Moduly → Genealogie → Nový rodokmen. Vyplnit název.
2. + Osoba otevře editor. Jméno je rozdělené na jméno, druhé jméno a příjmení; stačí alespoň jedna
   neprázdná část. Volitelné jsou pohlaví, životní data, připomínán od/do a
   poznámky. Data podporují celé datum nebo samotný rok podle systémového locale.
3. Uložit osobu vloží box do grafu. Klepnutí na box otevře přehled a akci Upravit.
4. Přidat příbuzného nabídne otce, matku, partnera nebo dítě s výslovně vybranou
   rodičovskou rolí. Je možné vyhledat a připojit existující osobu nebo ji založit.
   Založení osoby a vazby je jedna transakce a jedna operace Zpět.
5. Dokumentace u osoby uloží citaci/popisek a poznámku, volitelně odkaz na soubor
   vybraný systémovým dialogem. Soubor je možné otevřít v příslušné aplikaci.
6. Box události otevře editor typu, názvu, data, místa, poznámky a osob v rolích.
   Typ lze změnit pouze na typ se stejnou sadou podporovaných rolí.

Graf se posouvá po pozadí prstem a přibližuje dvěma prsty. Delší podržení boxu
zahájí přesun. Přizpůsobit zahrne všechny boxy do dostupné pracovní plochy.
Osoby v horní liště otevřou samostatnou aktivitu se seznamem a našeptáváním;
výběr vrátí uživatele do grafu a otevře přehled vybrané osoby.
Běžné editace nepřeskládávají ostatní boxy. Osoby a běžné události mají vlastní boxy. Úplný partnerský vztah/sňatek
se zobrazuje přímou spojnicí osob; ostatní čáry představují účast v roli události.

Na telefonu je přehled/editor ve vysouvacím spodním panelu, na širokém okně
v pravém panelu. Uložit potvrdí návrh; zavření změněného editoru žádá potvrzení
zahození. Rozpracovaná pole používají uložitelný Compose stav včetně změn
konfigurace. Při ukončení procesu systémem může Android obnovit uložený návrh
v rámci obnovení úlohy; nejde o náhradu tlačítka Uložit.

Zpět/Znovu má až 40 datových kroků v aktuálně otevřeném rodokmenu během běhu
modulu. Přesun boxu je jeden krok. Výsledek každého kroku se okamžitě uloží;
historie samotná se po ukončení modulu nebo přepnutí rodokmenu neobnovuje.
Uložena je také poslední otevřená rodina a poloha/zoom/výběr grafu. Kamera se
ukládá s krátkým odkladem při manipulaci a při návratu do seznamu rodokmenů.

## Význam dat a vazba na desktop

Podklady: desktopové `registergraphdata.h`, `eventrelationships.{h,cpp}`,
`GenealogyDraft` a definice událostí `GenealogyModule::eventTypes()`.

- Rodičovství bez narození je `genealogy.parentage`, role dítěte a
  `genealogy.father` / `genealogy.mother`.
- Partnerský vztah bez doloženého sňatku je `genealogy.spousal-record`.
- Narození, křest, sňatek, úmrtí, pohřeb a zmínka mají samostatné typy.
- Přidání příbuzného nevymýšlí datum ani doklad narození/sňatku.
- Při editaci se kontrolují existující osoby, jedinečnost rolí, obsazený rodič,
  vazba na sebe a cykly rodičovství. Neplatná změna nevstoupí do úložiště ani
  historie. Validace platí také při přímé změně role v editoru události.

Android formát je **samostatný formát v3 s načítáním v1 a v2**, nikoli kompatibilní desktopový projekt.
Stabilní UUID, osoby, události, role, citace a geometrie jsou oddělené. Import GEDCOM 5.5.1, GEDCOM 7, GEDZIP 7 a desktopových genealogických grafů
Vestigia popisuje [imports.md](imports.md). Editor podporuje vložení, změnu
a odebrání portrétu; portrét zobrazuje také box osoby. Export, synchronizace,
normalizované výřezy a pokročilé typy vztahů zatím implementované nejsou.
Datum zadané v editoru osoby aktualizuje její první odpovídající událost, ostatní
role a vlastnosti této události zachovává.

## Uložení a dokumentace

`noBackupFilesDir/genealogy/genealogy.sqlite` je oddělený od badatelských pomůcek.
SQLite transakce uloží celý konzistentní snímek rodokmenu. Paměťový stav a historie
se změní až po úspěšném uložení. Kamera je samostatná tabulka a nevytváří datové
kroky Zpět. IO probíhá mimo hlavní vlákno a příkazy se zpracovávají postupně.
Debug a Release mají vlastní databázi. Aktualizace pomocí `adb install -r` ji nemaže;
odinstalace aplikace odstraní její soukromé rodokmeny. Export/záloha bude další etapa.

Doklady se zatím **nekopírují do projektu**: ukládá se `content://` odkaz a trvalé
oprávnění ke čtení přes Android Storage Access Framework. Přesunutí souboru,
odpojení poskytovatele nebo odvolání oprávnění může odkaz znepřístupnit; otevření
potom zobrazí chybu. Aplikace nemění ani nemaže původní soubory, nevyžaduje plošný
přístup k úložišti a jiné aplikaci předává pouze oprávnění ke čtení vybraného dokladu.

## Ověření

JVM `GenealogyEditsTest`: význam nedatovaných vztahů, sdílená událost rodičů,
cykly, duplicitní/obsazené role, zachování rolí při změně data a izolace geometrie.

```sh
./gradlew testDebugUnitTest lintDebug lintRelease
./scripts/test-device.sh com.vestigiascriptorium.android.genealogy.GenealogyDeviceTest,com.vestigiascriptorium.android.genealogy.GenealogyUiTest
```

Device testy používají vlastní unikátní adresář v cache. Nikdy neotevírají skutečné
rodokmeny nebo knihovnu pomůcek. Ověřují obnovení DB, citace, kameru a historii
včetně trvalého uložení Undo/Redo a odmítnutí neplatného zápisu. UI test vyžaduje
odemčený fyzický telefon a prochází založení rodokmenu, osobu, otce, Zpět/Znovu
a opětovné otevření. Emulátor se nepoužívá.

Ověřeno 29. 10. 2026 na Samsung SM-S938B / Android 17: všech 26 JVM testů
(včetně pěti nových genealogických), dva databázové a dva genealogické UI testy
prošly. Prošly také čtyři regresní testy společného katalogu/modulů. Editor byl
ověřen i po obnově uloženého Compose stavu, s potvrzením pokračování v úpravách,
uložením citace, životního data a místa události. Graf a rozbalený editor byly
vizuálně zkontrolovány ze snímků telefonu. Jeden meziběh UI runneru ztratil
Compose hierarchii; následné úplné opakování obou UI scénářů prošlo.
Sestavení Debug/Release a Lint obou variant prošly. Tablet a otevírání konkrétních
externích poskytovatelů dokladů nebyly fyzicky ověřené.


## Jména, data a samostatný seznam osob (1.0.0013)

Jméno má `givenName`, `middleName`, `surname`. Stejná pole mají nová osoba
a průvodce připojením příbuzného. Původní celá jména z formátu v1 jsou uložena
beze změny v `legacyName`; aplikace neodhaduje rozdělení víceslovných příjmení.
V editoru se ukáže dosavadní zápis. Vyplnění strukturovaných polí jej při uložení
nahradí. Samotná změna poznámky původní jméno zachová. UUID, vazby, doklady
a geometrie se nemění. Staré záznamy se převádějí až při běžném uložení,
bez hromadného přepisování knihovny.

Sdílený datumový editor v osobě i události má přesnost Celé datum / Pouze rok.
Používá pořadí a oddělovače systémového formátovacího locale, čtyřmístný rok
a kontrolu skutečného kalendářního dne (včetně přestupných let). Uložení je
nezávislé na locale: ISO `YYYY-MM-DD`, případně `YYYY`. Samotný rok se nikdy
nepřevádí na 1. leden. Přepnutí roku na celé datum vyžaduje doplnění nového
data; prázdný údaj znamená neznámé. Neplatný vstup blokuje Uložit. Starší
volné zápisy typu kolem 1820 zůstávají beze změny; pro nahrazení uživatel
výslovně zvolí novou přesnost. Nejde o novou podporu jiných kalendářů.

Připomínán od/do jsou samostatné atributy osoby, také s přesností dne nebo roku.
Kontroluje se pořadí intervalu; rok představuje celý rok pro porovnání mezí,
nikoli vymyšlené uložené datum.

`PeopleActivity` čte aktuální uložený rodokmen v IO vlákně a zachovává společnou
kontrolu účtu. Seznam má sloupce Příjmení, Jméno (včetně druhého jména), Rok.
Primárně se řadí podle příjmení s locale-aware kolací. Rok je rok narození,
jinak Připomínán od s označením př. Hledání porovnává jméno a orientační rok,
ignoruje velikost písmen a diakritiku a více slov musí odpovídat současně.
Našeptávač nabízí nejvýše šest konkrétních osob; doporučení i seznam vycházejí
z aktuálního textu včetně nahrazení návrhem IME. Výběr pracuje s UUID a vrací
se přes výsledek aktivity k osobě v původním grafu, bez ztráty historie.

JVM testy pokrývají cs-CZ, en-US, en-GB, de-DE a fr-FR, přesnost roku, neplatné
dny, interval připomínání, řazení a hledání. Device test ověřuje načtení v1,
úpravu a opětovné načtení v2 se zachovaným celým jménem a historickým datem.
UI testy navíc ověřují tři části jména, připomínání, obnovu návrhu, přepínače
přesnosti, živé našeptávání a nahrazení textu s diakritikou.

Ověření 1.0.0013: všech 31 JVM testů, sedm genealogických device/UI testů,
sestavení obou variant a Lint Debug/Release prošly. Testy pracovaly jen
s vlastními dočasnými rodokmeny. Seznam osob byl vizuálně zkontrolován
ze snímku skutečného telefonu. Locale varianty byly ověřeny jednotkově;
systémové jazykové nastavení telefonu se při testech neměnilo.


## Obsah osobních boxů a přímé partnerské spojnice (1.0.0015)

Box osoby ukazuje pouze letopočty, například `* 1820  + 1890`. Pokud je znám
jen jeden z obou roků, zobrazí pouze tento údaj. Když oba chybí, použije období
z atributů připomínán od/do (také jako letopočty). Původní přesné či historické
zápisy se tím nemění a jsou nadále dostupné v editoru.

Následují jednotliví partneři: `Partner: jméno`, `2. partner: jméno` atd.
U každého se ukazuje datum svatby v systémovém locale, případně jen uložený rok.
Chybějící datum je `Svatba: —`. Za datum svatby se považuje pouze datum události
`genealogy.marriage`, nikoli libovolné datum záznamu partnerského vztahu.
Partneři se řadí podle známých svateb, u nedatovaných podle pořadí záznamů.
Číslování bez známých dat tedy není důkaz historického pořadí vztahů. Opakované
sňatky se stejnou osobou zůstávají u jednoho partnera s více daty; stejná osoba
se kvůli souběhu partnerského záznamu a sňatku nevypisuje dvakrát.

Úplné partnerské záznamy a sňatky se nevykreslují jako boxy. Každou dvojici
spojuje jedna přímá čára. Klepnutí na ni otevře přednostně datovaný sňatek, jinak
existující vztah; střed čáry má také přístupný cíl pro čtečku obrazovky. Všechny
původní záznamy lze samostatně otevřít i v přehledu událostí u osoby. V editoru
vztahu lze výslovně zvolit Sňatek a zadat Datum svatby. Neúplná událost, které
chybí druhá osoba, si ponechá viditelný box, aby šla opravit.

Jde o projekci stávajících dat, nikoli migraci nebo mazání vztahů. Uplatní se
i na již uložené rodokmeny. Boxy mají výšku podle skutečně vysázených textů
a všech partnerů; kotvy čar, ořez viewportu, posun, výběr a Přizpůsobit používají
jejich aktuální rozměry. Skryté uložené pozice partnerských záznamů neovlivňují
přizpůsobení grafu. Existující ruční pozice ostatních boxů se nepřeskládávají.

Ověření 1.0.0015: 35 JVM testů, tři databázové testy na fyzickém telefonu,
Debug/Release build a Lint obou variant prošly. Nové UI scénáře pro partnerskou
spojnici, datum svatby, posun grafu a obsah boxů jsou připravené v
`PartnerGraphUiTest`. Jejich běh spolu s dosavadními GUI scénáři byl zastaven
kontrolou zamčeného telefonu ještě před otevřením obrazovek; vizuální a dotykové
ověření nové podoby proto zatím není dokončené. Testovací databáze jsou izolované,
uživatelské rodokmeny ani pomůcky se při testech neotevírají a nemažou.

## Dokončení ověření a oprava výšky boxů (1.0.0016)

Po odemčení telefonu prošly všechny čtyři UI scénáře partnerského grafu
a dosavadního genealogického editoru. Vizuální kontrola přesto odhalila,
že předběžně spočtená výška v 1.0.0015 ořízla spodní řádek druhého partnera.
Nová kontrola skutečné výšky jednotlivých textových řádků chybu zopakovala
(poslední řádek měl asi 8 dp oproti 14 dp u stejného stylu).

Boxy nyní měří přímo Compose Layout podle přirozené výšky skutečného obsahu,
s pevnou šířkou grafového boxu a bez horního omezení výšky obrazovkou. Odpadl
předběžný součet textových metrik. Skutečné rozměry sdílejí kotvy spojnic,
dotykové cíle a Přizpůsobit. Rozměry přiblíženého boxu se neomezují na šířku
viewportu; ořezává se až zobrazená část celé grafové plochy.

Nová kontrola po opravě prošla, stejně jako opakování obou partnerských UI
scénářů včetně trojnásobného přiblížení. Ověřeny jsou také fyzické klepnutí na
spojnici, změna Partnerský vztah → Sňatek, uložení data svatby a následný posun
grafu. Boxy a celé poslední řádky byly znovu vizuálně zkontrolovány ze snímku
telefonu. Testy nadále používají jen izolované dočasné rodokmeny.

Finální sestavení 1.0.0016: 35 JVM testů a Lint Debug/Release prošly. Čtyři
UI scénáře po opravě prošly; dva partnerské scénáře byly následně zopakovány
s doplněnou kontrolou celého řádku i trojnásobného přiblížení, opět úspěšně.

## Portréty a import (1.0.0018)

Portréty jsou vlastní kopie v `noBackupFilesDir/genealogy/media`, nejvýše 32 MiB
na soubor. Odebrání obrázku od osoby je vratná editace; originál ani vlastní
kopie pro historii se nemažou. Galerie nevyžaduje plošné oprávnění k fotografiím.
Importované doklady jsou vlastní kopie a při otevření se předává pouze dočasné
oprávnění ke čtení jednoho souboru. Ručně přidané doklady nadále používají SAF odkaz.

Ověření: 6 nových JVM scénářů GEDCOM včetně kódování, neplatných vazeb a
4 000 osob; 5 instrumentačních testů importu a úložiště a 2 UI scénáře
portrétu (vložení, odebrání, Undo) a potvrzení importu na fyzickém Samsungu.
Testy používají výhradně vlastní dočasná data.

Finální sestavení 1.0.0018: 41 JVM testů, Lint Debug/Release a osm
databázových testů na zařízení prošly. Po odemčení telefonu prošlo také všech osm UI scénářů: graf a editor,
partnerství, seznam osob a portrét/import. Aktualizace obou variant používá
výhradně instalaci `-r` se zachováním dat.
