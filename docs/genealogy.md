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
6. Událost v přehledu osoby otevře editor typu, názvu, data, místa, poznámky a osob v rolích.
   Typ lze změnit pouze na typ se stejnou sadou podporovaných rolí.

Graf se posouvá po pozadí prstem a přibližuje dvěma prsty. Delší podržení boxu
zahájí přesun. Přizpůsobit zahrne všechny boxy do dostupné pracovní plochy.
Osoby v horní liště otevřou samostatnou aktivitu se seznamem a našeptáváním;
výběr vrátí uživatele do grafu a otevře přehled vybrané osoby.
Svislou polohu boxů určuje generační řádek; vodorovné pozice lze upravovat. Vlastní boxy mají pouze osoby. Rodičovské i partnerské vztahy se zobrazují
přímými spojnicemi osob. Události zůstávají datovými záznamy bez vlastních boxů.

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

## Editor osoby a graf (1.0.0019)

Celé datum přijímá den a měsíc s jednou i dvěma číslicemi (např. `21.3.1691`
a `21.03.1691`). Pořadí i oddělovače určuje locale systému; kontrola skutečných
kalendářních dnů a přesnost Pouze rok zůstávají zachované.

**Přidat příbuzného** je dostupné také při zakládání a editaci osoby. Akce uloží
platné údaje osoby a ihned otevře výběr příbuzného. Pokud uživatel výběr zruší,
uložená osoba a její změny zůstanou zachované. Neplatné datum nebo prázdné jméno
akci blokuje. Selhání zápisu ponechá návrh v editoru a výběr neotevře.

Pole **Hledat existující osobu** zobrazuje výsledky přímo pod zadávaným textem.
Používá společný PeopleIndex: více slov, bez rozlišení diakritiky a velikosti
písmen, jméno i rok narození/připomínán od. Klepnutí vybere konkrétní UUID;
další změna textu včetně nahrazení klávesnicí starý výběr zruší. Prvních 20
výsledků doprovází výzva ke zpřesnění při větším počtu.

V grafu jsou pouze boxy osob. Rodiče se propojují přímo s dítětem; více záznamů
narození/křtu/rodičovství pro tutéž vazbu nevytváří další čáry. Údaje událostí,
role, geometrie a UUID v databázi zůstávají zachované; jejich editory se otevírají
z přehledu osoby. Přizpůsobení a hledání volného místa ignorují skryté události.

Ověření 1.0.0019: 43 JVM testů a Lint obou variant prošly; sestavení Debug,
Release a instrumentačních testů prošlo. Nový UI scénář pokrývá založení osoby
přes Přidat příbuzného, datum bez nul, vyhledání bez diakritiky, nahrazení dotazu,
připojení existujícího UUID a nepřítomnost boxů událostí. Po připojení telefonu
30. 10. 2026 prošlo šest regresních UI scénářů a nový scénář příbuzného.
První běh nového testu měl nejednoznačný selektor (stejné jméno v grafu i dialogu);
po omezení selektoru na dialog scénář prošel. Graf s přímou rodičovskou
spojnicí byl také vizuálně zkontrolován. Release 1.0.0019 aktualizován přes
`adb install -r` bez odinstalace nebo mazání dat.

## Stabilita spodního panelu (1.0.0020)

Výška editoru vychází z rozměru okna (90 %), nikoli z procenta proměnlivých
vnitřních omezení spodního panelu. Posun a spotřebování systémových insetů tak
nemění měřenou výšku obsahu a neposouvají horní kotvu při gestu. Kontrola zavření
má stabilní identitu a čte aktuální stav návrhu a ukládání, aby běžná změna
formuláře nevytvářela nový Material3 SheetState. Roztažení editoru a potvrzení
zahození neuložených změn zůstávají zachované.

`SheetGestureUiTest` používá izolovaný rodokmen na skutečném telefonu: rychlé
vytažení z částečné výšky, tři další prudká gesta proti hornímu dorazu, kontrola
stejné polohy po ustálení a po dalších snímcích animace; navíc zachování
rozepsaného jména a potvrzení zavření. Neotevírá uživatelské rodokmeny ani pomůcky.

Ověření 30. 10. 2026 na fyzickém Samsung SM-S938B: test rychlých gest a dva
regresní scénáře editoru prošly. Prošlo také 43 JVM testů, sestavení Debug/Release
a Lint obou variant. Aktualizace používá `adb install -r` se zachováním dat.


## Volná výška panelu a rodinné větve (1.0.0021)

Spodní panel se mění tažením horního úchytu a po puštění zůstává v nastavené
výšce. Nemá automatické dorazy ani setrvačný dojezd. Rozsah je 25–95 % dostupné
výšky; výška se pamatuje během práce v obrazovce včetně obnovení uloženého stavu.
Přechod do editace nebo na událost ji automaticky nemění. Obsah panelu má vlastní
posouvání, klávesnice respektuje dostupný prostor. Přístupnost nabízí také akce
Zvětšit panel / Zmenšit panel. Zavření dál chrání neuložené údaje.

Děti se seskupují podle skutečně zadané sady rodičů. U dvou rodičů vede společný
kmen ze středu jejich spojnice na vodorovnou sourozeneckou větev a z ní větve
k jednotlivým dětem. Další dítě stejné dvojice naváže na stejnou větev. Není-li
zaznamenané partnerství, rodičovská spojnice se vykreslí i tak, ale nevytváří se
fiktivní sňatek. Polorodí sourozenci s jinými rodiči mají samostatné větve.
Při jediném známém rodiči vychází společná větev od něj; druhého rodiče aplikace
neodhaduje. V Přidat příbuzného → Dítě lze výslovně zvolit druhého rodiče z
partnerů. Oba rodiče a připojení dítěte se uloží v jedné transakci a jednom kroku
Zpět. Existující vazby se znovu vykreslí automaticky bez hromadné změny dat.

Ověření 30. 10. 2026: 44 JVM testů, Lint Debug/Release, sedm UI scénářů
na fyzickém telefonu (ruční výška, editory, hledání, rodina se dvěma dětmi,
portrét a import) prošly. Výšku test kontroluje podle délky tahu s tolerancí
systémového prahu gesta a ověřuje nulový následný dojezd. Rodinná větev byla
znovu ověřena samostatným testem a vizuálně ze snímku po dokončení animací Androidu.
Release aktualizován instalací `-r` se zachováním dat.


## Generační řádky (1.0.0022)

Zobrazení grafu řadí osoby do generačních řádků podle rodičovských vazeb.
Partneři a známí společní rodiče jsou ve stejné úrovni; potomci v následujících
úrovních. Také další sourozenec po zaplnění původních pěti pozic zůstane na
stejném řádku. Boxy se vodorovně oddělují, aby se nepřekrývaly. Vyšší boxy
s portrétem a dalšími partnery určují potřebnou výšku řádku. Mezera mezi řádky
má od 1.0.0023 třetinu původních 160 jednotek grafu (160/3, přibližně 53,33). Sourozenecká vodorovná větev je přesně ve středu mezery
mezi dolním okrajem nejvyššího boxu rodičovské generace a horním okrajem boxů
potomků; nevychází z výšky partnerské spojnice uvnitř rodičovských boxů.

Zarovnání se promítne i do existujících a importovaných rodokmenů bez přepsání
jejich uložených souřadnic, osob nebo vazeb. Ruční přesun osoby mění vodorovnou
pozici; svislou polohu určuje generační řádek. Přizpůsobit a výběr osoby používají
skutečné promítnuté boxy. Kamera, měření, spojnice i dotykové cíle sdílejí stejnou
geometrii. Nestandardní křížení partnerství napříč generacemi se zpracuje konečným
výpočtem; taková data nemusí připouštět jednoznačné generační uspořádání.


Ověření 30. 10. 2026: 46 JVM testů a Lint obou variant prošly. Devět regresních
UI scénářů prošlo; poté byl test rodiny rozšířen na šest sourozenců včetně
původního přetečení na další řádek a opět prošel. Snímek telefonu potvrdil
zarovnání i větve dosahující k boxům. Grafové boxy nemají vynucené Material3
48dp rozměrové odsazení, které při oddálení vytvářelo neviditelné okraje;
měření tak odpovídá viditelným hranám. Test partnerů byl po této úpravě zopakován.


## Ikony a manželky ve společném boxu (1.0.0024)

Spodní lišta používá ikony Vrátit změnu, Znovu a Přizpůsobit. Mají přístupné
popisky, nápovědu při podržení a zachovávají stav povolení příkazů. Vedle nich
je přepínací ikona dvou osob v jednom boxu **Manželky v boxu manžela**. Výchozí
zobrazení má samostatné boxy; zapnutou volbu si lokální genealogické úložiště
pamatuje i při novém spuštění a pro ostatní rodokmeny. Nastavení je oddělené
od historie editací a nesahá do dat osob ani pomůcek.

Ve společném zobrazení obsahuje box manžela jména manželek, jejich dostupné
životní letopočty a data svateb. Podtržené jméno otevře přehled/editaci konkrétní
ženy; dostupná je také v samostatném seznamu Osoby. Rodinné větve pro společné
děti začínají uprostřed spodní hrany boxu otce. Zachované jsou generační řádky,
mezery a sourozenecké větve. Přepnutí zpět obnoví samostatné boxy bez migrace,
změny UUID, rodičů, citací, portrétů nebo uložených souřadnic.

Pro seskupení musí být pohlaví nebo rodičovské role rozpoznatelné. Žena s více
partnery a matka dítěte, u kterého není uveden právě její manžel jako otec,
zůstává samostatně: společný box by mohl naznačovat nesprávné rodičovství.
Manžel s více manželkami se může seskupit s každou ženou, která má jediného
partnera a splňuje toto pravidlo. Samostatný pár bez dětí lze také seskupit.
Box reprezentuje oba členy domácnosti; vlastní osoby a vazby zůstávají oddělené
v datech i při připojení předků manželky.

Ověření 30. 10. 2026: 48 JVM testů a Lint obou variant prošly z čistého sestavení.
Na telefonu prošel nový scénář ikon, seskupení, otevření/editace manželky,
Undo/Redo, uložení předvolby a obnovení samostatných boxů; prošly také čtyři
regresní scénáře editoru a partnerů. Finální scénář byl zopakován po doplnění
popisku Manželka a podtrženého odkazu. Konflikt cache při souběžných Gradle
bězích byl odstraněn čistou kompilací a obnovením Debug kompilace; běžný
`scripts/test-device.sh` následně prošel. Sestavení se ověřovalo postupně.


## Vystředění společného boxu (1.0.0025)

Po seskupení manželky se box otce vodorovně vystředí nad celou šířkou boxů jeho
přímých potomků (střed mezi levým okrajem prvního a pravým okrajem posledního).
Jediné dítě je přímo pod otcem; u více manželek se zahrnou jejich společné děti.
Výpočet postupuje od dolních generací nahoru, takže předci používají konečné
pozice vystředěných potomků. Jde o projekci zobrazení, která nepřepisuje zdrojové
souřadnice. Spojnice, měření, výběr a Přizpůsobit používají tyto stejné boxy.

Pokud osobě chybí rok narození/úmrtí a zobrazují se pouze roky připomínky,
box obsahuje jen `1830–1880`, případně samotný známý rok. Slova připomínán,
od a do se v této zkrácené řádce nepíšou; popisky polí v editoru zůstávají.

Ověření 1.0.0025: 49 JVM testů, Lint Debug/Release a tři UI testy na skutečném
telefonu prošly. Test záměrně umístí dítě mimo původní osu otce a po seskupení
porovnává skutečné středy obou boxů. Kontroluje také řádek `1830–1880`, editaci
manželky, Undo/Redo a návrat k samostatným boxům. Geometrický test pokrývá
jediné dítě i vystředění nad dětmi z více manželství a zachování zdrojových boxů.

## Obrázky z Google Disku (1.0.0026)

Editor portrétu i dialog Dokumentace mají tlačítko **Google Disk / soubory**.
Otevře systémovou nabídku aplikací pro výběr obrázku, včetně Google Disku,
pokud je nainstalovaný a přihlášený. Stejnou cestou lze použít i jiné dostupné
poskytovatele. Původní výběr souboru zůstává dostupný. Používá se Android
`ACTION_GET_CONTENT` s `image/*` a bez omezení jen na místní soubory; nevyžaduje
přihlašovací údaje ke Google účtu, Google API ani oprávnění k celému úložišti.

Vybraný obrázek se načte jako stream mimo hlavní vlákno a uloží do vlastního
soukromého úložiště. Není potřeba trvalé oprávnění k URI. Poskytuje-li zdroj
místo přímého souboru obrazovou reprezentaci, načítání umí použít typovaný
obrazový stream. Probíhá stejná kontrola skutečného obrázku, rozměrů a limitu
32 MiB jako u místního portrétu. Ukládání osoby/dokladu počká na dokončení
kopie. Přerušený, nedostupný nebo nepodporovaný vstup nevymění předchozí obrázek.

Obrázky vložené touto volbou do Dokumentace jsou také vlastní kopie; standardní
výběr dokumentu nadále zachovává existující chování s odkazem a SAF oprávněním.
Po úspěšném zkopírování není obrázek závislý na dostupnosti původního cloudového
souboru. Originál na Disku aplikace nemění.

Podklady: [Android — výběr obsahu](https://developer.android.com/guide/components/intents-common#Storage)
a [přístup k souborům a obrazovým reprezentacím](https://developer.android.com/training/data-storage/shared/documents-files).


Ověření 1.0.0026: na skutečném telefonu prošlo pět UI scénářů, včetně nového
výběru a kopírování cloudového obrázku do portrétu i dokumentace. Syntetický
poskytovatel v testovacím APK odmítá přímé otevření a poskytne až typovaný
obrazový stream přes pipe bez známé délky. Test kontroluje vlastní kopii a MIME
PNG, nikoli trvalý přístup k původnímu URI. Na telefonu byla ověřena dostupnost
Google Disku pro `GET_CONTENT image/*` s `CATEGORY_OPENABLE`; uživatelovy skutečné
soukromé soubory z Disku se při testování nevybíraly ani nečetly.

Finální sestavení 1.0.0026: Debug/Release, všech 49 JVM testů a Lint obou variant
prošly. Obě varianty jsou aktualizované výhradně instalací `-r`, bez odinstalace
nebo mazání uživatelských dat.

## Kruhové portréty (1.0.0027)

V editoru osoby jsou tlačítka Vložit/Změnit obrázek a Google Disk / soubory
vedle sebe ve stejně širokých a vysokých tlačítkách. Odstranění obrázku je
samostatně pod nimi. Portréty v grafových boxech mají kruhový výřez vyplněný
obrázkem; uložená původní kopie se nemění. Při seskupení manželek v boxu
manžela se u každé manželky s obrázkem zobrazí také její kruhový portrét.
Klepnutí na něj otevře příslušnou osobu stejně jako její jméno. Skutečná výška
obsahu dál určuje geometrii boxů, generačních řádků a spojnic.

Ověření: `assembleDebug lintDebug` a čtyři UI scénáře na fyzickém Samsungu
SM-S938B prošly. Kontrola porovnává polohu obou tlačítek a ověřuje přítomnost
portrétu manželky ve společném boxu; regresně pokrývá cloudovou kopii,
odebrání/Undo, import a editaci manželky. Kruhové výřezy byly zkontrolovány
ze snímku telefonu. Debug byl aktualizován pouze přes `adb install -r`.

Release 1.0.0027 byl následně sestaven, ověřen kontrolou podpisu a aktualizován
na fyzickém telefonu přes `adb install -r`; spuštění aktivity prošlo.
Podepsané APK je zveřejněné jako
`vestigia-installations/testing/VestigiaScriptorium-1.0.0027-release.apk`.
Předchozí 1.0.0026 je zachovaná v `vestigia-installations/archive/android/`.
Veřejný HTTPS GET byl porovnán s lokálním APK a má shodný SHA-256:
`04430c2c8bcf1470489c099f0560ecc6e3e21c6d5dc77ccaf8862e1b5d45167c`.

## Portréty vedle sebe (1.0.0028)

Portréty mají kruhový výřez také v přehledu a editoru osoby. Ve společném
boxu jsou portréty v jedné vodorovné řadě: nejprve manžel, potom manželky
v pořadí jejich údajů. Zobrazují se jen existující obrázky. Velikost se při
větším počtu přizpůsobí šířce boxu a všechny portréty v řadě jsou stejně velké.
Klepnutí na portrét otevírá příslušnou osobu; texty manželek zůstávají pod řadou.

Ověření 1.0.0028: `assembleDebug lintDebug` a podepsané `assembleRelease`
prošly. Obě varianty byly aktualizovány pomocí `adb install -r`.
Čtyři UI scénáře zastavila vstupní kontrola zamčeného telefonu; dotykové,
geometrické a vizuální ověření nového uspořádání proto zatím není dokončené.
Release APK je v `vestigia-installations/testing/`, předchozí 1.0.0027 v
`vestigia-installations/archive/android/`. Veřejný GET se shoduje s lokálním
APK; SHA-256:
`1414e6d0812cb979e0bdf3b5aa05c693462c3ab11bc6a0d0fba400e6410070d0`.

Po odemčení telefonu prošly všechny čtyři UI scénáře 1.0.0028 bez přeskočení.
Geometrická kontrola potvrdila čtvercové rozměry obou portrétů, stejnou svislou
osu a umístění manželky napravo od manžela. Snímek fyzického telefonu potvrdil
kruhové výřezy vedle sebe i navazující rodinnou větev. Prošly také regresní
scénáře kopírování cloudového obrázku, odebrání/Undo a potvrzení importu.

## Doklady: kopie nebo zdroj, náhledy (1.0.0029)

Při připojování dokladu lze zvolit vlastní kopii (výchozí) nebo odkaz na zdroj.
Volba platí pro místní soubory i Google Disk / soubory. Odkaz používá systémový
výběr dokumentu a pouze trvalé oprávnění ke čtení; poskytovatel bez této podpory
se odmítne s chybou, nevzniká skrytá náhradní kopie. Změna režimu zruší aktuální
výběr a vyžaduje nové vybrání souboru. Samotnou citaci lze uložit bez souboru.

Vlastní kopie jsou v soukromém `noBackupFilesDir/genealogy/media` příslušné
varianty Vestigie, nikoli ve veřejných Obrázcích nebo Dokumentech. Kopie se
kontrolují a načítají mimo UI, limit je 32 MiB. Odinstalace aplikace odstraní
její soukromé kopie spolu s daty, aktualizace `adb install -r` je zachová.
Zdrojové soubory se nikdy nemění a u odkazů se nekopírují do spravované knihovny.
Portréty a importované soubory nadále používají vlastní kopie. Kopie se zachovávají
pro historii Zpět; zrušení dialogu může ponechat nepřipojenou soukromou kopii.

Obrázkové doklady mají náhled přímo u osoby a v dialogu připojení. Klepnutí
otevře velký náhled s posunem a přiblížením. PDF má náhled první strany
vykreslený pouze v paměti, stejné velké zobrazení a možnost otevřít celý dokument
v externím prohlížeči. Nelze-li PDF vykreslit (např. šifrovaný nebo neposuvný
cloudový stream), zůstává karta pro otevření externím prohlížečem; jiné soubory
mají také dokumentovou kartu. Obrazovky neposkytují úpravu obrázku ani zápis
ke zdroji. Vlastní soubory se externě zpřístupňují jen s oprávněním ke čtení.

V přehledu událostí rodiče se rodičovská role vztahuje výslovně k dítěti,
např. „Matka dítěte Marie: Anna“. Nadpis události obsahuje také jméno dítěte.
Samotné partnerství nepřidává rodičovské vazby; tato změna pouze upřesňuje
zobrazení a nemigruje uložené vztahy.

Ověření 1.0.0029: Debug/Release sestavení, Lint Debug a 50 JVM testů prošly.
Na fyzickém Samsungu SM-S938B prošlo devět UI scénářů (šest portréty/doklady/import,
dva partnerský graf, jeden domácnost). Odkazový scénář používá vlastní syntetické
URI s trvalým oprávněním ke čtení, ověřuje zachování URI a prázdné spravované
úložiště. Scénáře kopií ověřují místní i cloudový obrazový stream, MIME a vlastní
soubor. PDF scénář vytváří vlastní dočasný dokument a ověřuje první stranu a zoom.
Snímky velkého obrazového a PDF náhledu byly vizuálně zkontrolovány po dokončení
systémových animací. Test domácnosti byl rozšířen o výslovný popisek otce dítěte
v přehledu manželky a znovu prošel. Testy neotevírají uživatelské rodokmeny.

Obě varianty byly aktualizovány pouze přes `adb install -r`. Podepsané Release
APK je v `vestigia-installations/testing/VestigiaScriptorium-1.0.0029-release.apk`,
předchozí 1.0.0028 v `vestigia-installations/archive/android/`. Veřejné HTTPS
stažení je shodné s lokálním APK; SHA-256:
`f1e96036a22804782410111ad91ebbd24c2a999ebd6952b6d5f549958fa11f6e`.

## Obce, dokumentace a zobrazení na šířku (1.0.0032)

Editor osoby má pole Obec narození a Obec úmrtí. Pro každý existující sňatek
nabízí samostatnou Obec svatby s partnerem a datem; místo svatby je také přímo
v editoru události Sňatek. Používá se stávající `LifeEvent.place`, bez změny
formátu nebo migrace. Samotná obec nevyžaduje datum a nevytváří fiktivní
letopočet. Úpravy osoby a jejích obcí jsou jedním uložením a jedním krokem Zpět.
Starší volání editace zachovávají uložená místa a ostatní role událostí.

Od dvou znaků se u aktivního pole nabízejí nejvýše šest výsledků z nainstalované
Historické topografie. Dotaz běží mimo UI po krátké prodlevě pro psaní. Používá
stejný místní rejstřík jako prohlížeč pomůcky; hledá i historické názvy a nehledí
na velikost písmen nebo diakritiku. Vlastní názvy obcí mají přednost před shodami
v doplňujících údajích, například okrese. Při prvním použití může příprava rejstříku
trvat déle a je vidět průběh. Návrh se převezme pouze klepnutím, nikdy sám
nepřepíše rozepsané místo. Bez pomůcky nebo při její nedostupnosti lze vše
zadat ručně; aplikace kvůli našeptávání nic nestahuje. Ukládá se text obce,
nikoli závislost rodokmenu na instalaci pomůcky.

Dokumentace osoby má na výšku tři sloupce. Na šířku používá podle šířky panelu
čtyři až šest sloupců. Náhled, popisek a poznámka zůstávají u konkrétního dokladu;
klepnutí zachovává velké zobrazení a zoom. PDF nabízí i otevření celého souboru.

Na širokém okně a telefonu na šířku od 600 dp je editor v pravém panelu;
jeho šířka využije přibližně 48 % okna, v rozsahu 360–520 dp. Graf využívá
zbývající prostor. Přizpůsobit centruje rodokmen vodorovně i svisle a totéž se
provede po změně velikosti pracovní plochy, včetně otočení nebo otevření bočního
panelu. Při obyčejné editaci nebo posunu grafu se kamera znovu nepřizpůsobuje.
Na výšku zůstává ručně nastavitelný spodní panel a zachování rozpracovaných polí.
Při psaní na šířku se hlavní horní a spodní lišta dočasně skryjí a pracovní
plocha respektuje klávesnici jako celek. Editor tak zachová dostupné Uložit
osobu i posuvný formulář; po zavření klávesnice se lišty obnoví.

Ověření prvního distribuovaného sestavení této změny 1.0.0031: 53 JVM testů,
17 UI scénářů na fyzickém Samsungu SM-S938B, sestavení obou variant a Lint
Debug/Release prošly. Nové scénáře ověřují ruční obce, zachování návrhu po
obnově, společné uložení a Zpět, historický název a nahrazení dotazu IME,
tři/čtyři sloupce náhledů i skutečné otočení telefonu, centrování a uložení
s otevřenou klávesnicí. Našeptávání používá izolovanou syntetickou databázi,
ne uživatelovu instalovanou pomůcku. Rozložení na šířku i formulář s klávesnicí
byly vizuálně zkontrolovány ze snímků telefonu.

Debug i Release 1.0.0031 byly aktualizovány pouze přes `adb install -r`.
Podepsané APK bylo zveřejněné a veřejně stažené shodně s lokální kopií;
SHA-256: `68064eaff8809545f946e51b2ce634f6f974a414155b3acdcdb1530556cebebb`.

Finální 1.0.0032 upřednostňuje shody ve vlastním názvu před doplňujícími údaji;
výsledky se doplňují historickými názvy a deduplikují podle ID pomůcky. Test
porovnává přímo Brno s Adamovem, který obsahuje Brno jen v údaji o okrese.
Prošlo znovu všech 53 JVM testů, Debug sestavení a lint, čtyři scénáře
`PlacesAndLayoutUiTest` na telefonu a podepsaný Release s Lint Release.
Regresní sada před tím prošla všech 17 scénářů; po změně řazení byly zopakovány
čtyři dotčené scénáře. Testy formulářů explicitně posouvají zadávaná pole do
viditelné části panelu a kontrolují dostupné tlačítko Uložit.

Debug i Release jsou na telefonu ve verzi 1.0.0032, aktualizované pouze přes
`adb install -r`. Aktuální podepsané APK je veřejně dostupné jako
`vestigia-installations/testing/VestigiaScriptorium-1.0.0032-release.apk`.
Předchozí APK 1.0.0030 a 1.0.0031 jsou zachované v archivu Androidu.
Veřejně stažená 1.0.0032 je shodná s lokálním APK; SHA-256:
`8b07c5ba3fbf87848c04a6a5b2e0bfd3ba52967fb68f5942c85b35bb481ec533`.

## Přehled jen pro čtení a odkazovaný portrét (1.0.0033)

Podrobnosti osoby pouze zobrazují uložené údaje, role, události a dokumentaci.
Náhledy lze dál otevřít a přiblížit. Zadávací pole, připojení dokumentace,
příbuzných a nové události jsou dostupné až po tlačítku Upravit. Událost otevřená
ze spojnice má také nejprve přehled a vlastní Upravit. Přechod z editoru osoby
na doklad, příbuzného nebo událost nejdříve uloží platný návrh osoby; při chybě
ponechá návrh na místě a cílovou akci neotevře. Vytvoření nové události spolu
s rozepsanými údaji osoby je jedna transakce a jedna operace Zpět.

V editoru portrétu je volba Vytvořit kopii ve Vestigii / Odkazovat na zdroj.
Platí pro další výběr obrázku; samotné přepnutí nemění stávající portrét.
Obě tlačítka pro výběr zůstávají vedle sebe. Odkaz používá systémový výběr
s trvalým oprávněním pouze ke čtení a ověřuje skutečný obrázek, ne jen MIME.
Při kopii se dál používá dosavadní vlastní neměnný soubor; při odkazu nevzniká
kopie ve složce médií Vestigie. Původní obrázek se neupravuje. Ztracené oprávnění
nebo nedostupný zdroj ukáže chybějící obrázek, neodstraňuje osobu ani její vazby.
Odkazovaný portrét se zobrazuje také v grafu a ve společném boxu manželů.

Samostatné Android úložiště má formát 4: `Person.portrait` přijímá dosavadní
SHA-256 klíč `.img` nebo `content://` URI s neprázdnou autoritou, bez userinfo
a fragmentu. Schéma tabulek je zachováno, SQLite user_version a JSON format
jsou 4. Čtou se formáty 1–3 a původní portréty se nepřekopírovávají ani nemažou.
Starší Android čtečka nový formát odmítne namísto neúplného otevření.
Desktopové `.vestigia`, GEDCOM/GEDZIP ani webové API se nemění; importované
portréty zůstávají vlastními kopiemi. Před změnou byly zkontrolovány související
pokyny desktopu/webu a desktopová dokumentace připojování médií.

Ověření 1.0.0033: Debug/Release sestavení, Lint obou variant a 54 JVM testů
prošly. Na fyzickém Samsungu SM-S938B prošlo všech 24 genealogických UI a
databázových scénářů bez přeskočení. Nové kontroly ověřují nepřítomnost
editačních polí/akcí v přehledu, odkazovaný portrét bez souboru v médiích,
persistenci URI po novém načtení, Undo/Redo, čtení v3 s původní kopií a zápis
v4 s odkazem. Přechod na dokumentaci zachová rozepsané jméno; vytvoření události
s poznámkou osoby se vrátí jedním Zpět. Přehled události vyžaduje Upravit.
Snímek telefonu ověřil odkazovaný portrét v grafu i přehledu pouze pro čtení.
Testy používají vlastní rodokmeny a vlastní testovací URI/oprávnění ke čtení;
uživatelská data, skutečné relace a zdrojové soubory se neměnily.

Debug i Release byly aktualizovány pouze přes `adb install -r` na 1.0.0033.
Podepsané APK je v `vestigia-installations/testing/`, předchozí 1.0.0032 je
zachovaná v `vestigia-installations/archive/android/`. Veřejné stažení bylo
porovnáno s lokální APK a má SHA-256:
`d2364037777e1b5d7006b04801f1b63e713c50db30883ef2db1a46a20af1c048`.
