# Genealogie na Androidu — první pracovní celek

Graf vazeb je hlavní pohled. Rodokmen vzniká bez výchozí stránky matriky nebo
jiného pramene. Modul je přístupný přes společnou kontrolu účtu a testovacího
oprávnění, stejně jako pomůcky. Nepřidává žádnou výjimku přihlášení.

## Práce s rodokmenem

1. Moduly → Genealogie → Nový rodokmen. Vyplnit název.
2. + Osoba otevře editor. Povinné je jméno, volitelné pohlaví, životní data a
   poznámky. Data mohou být neúplná nebo přibližná; ukládají se jako zadaný text.
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
Osoby v horní liště otevřou hledatelný seznam; výběr přesune graf k osobě.
Běžné editace nepřeskládávají ostatní boxy. Osoby a události mají vlastní boxy,
čáry představují účast osoby v konkrétní roli události.

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

Android formát je **samostatný formát v1**, nikoli kompatibilní desktopový projekt.
Stabilní UUID, osoby, události, role, citace a geometrie jsou oddělené. Automatický
import/export desktopu, GEDCOM, normalizované výřezy, portréty, synchronizace,
pokročilé typy vztahů a automatické uspořádání nejsou v této etapě implementované.
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
