# Import rodokmenů (Android 1.0.0018)

Genealogie → seznam rodokmenů → **Importovat rodokmen**. Vyberte soubor:

- GEDCOM 5.5.1 (`.ged`): UTF-8, UTF-16 s rozpoznatelným pořadím bajtů, ASCII a standardní ANSEL.
- GEDCOM 7 (`.ged`): UTF-8.
- GEDZIP 7 (`.gdz`): ZIP s `gedcom.ged` v kořeni a přibalenými médii.
- Vestigia (`.vestigia`): desktopový SQLite projekt, datový formát 1–10;
  převádějí se genealogické grafy obsahující osoby. Nejde o otevření projektu pro společné úpravy.

Nejprve vznikne náhled s počty osob, událostí a portrétů, výběrem grafů a upozorněními.
Teprve **Potvrdit import** uloží nové rodokmeny. Stávající rodokmeny se nepřepisují
ani neslučují. Neplatné reference, konfliktní rodičovské role a cykly se odmítnou.
Zrušení náhledu nemění knihovnu. Příprava i kopírování probíhají mimo hlavní vlákno.

## Média

GEDZIP obsahuje média uvnitř archivu. Samostatný GEDCOM a `.vestigia` mohou
odkazovat na externí soubory: před výběrem souboru lze zvolit **složku médií**.
Zachovejte relativní cesty a podadresáře; pro přenos z desktopu použijte přenosný
export s jeho doprovodnou složkou médií. Absolutní lokální ani síťové cesty se
nenačítají. Chybějící média jsou uvedena v náhledu; aplikace nehádá podle názvu.

První podporovaný portrét osoby se zobrazí v jejím boxu a editoru. Média se
kopírují do soukromého úložiště aplikace, takže po importu nejsou závislá na
oprávnění k původní složce. Podporované obrázky, PDF, text, zvuk a video mohou
být připojeny jako doklady; otevření závisí na prohlížečích v zařízení.
Desktopové úpravy obrázků a normalizované výřezy se neaplikují: původní soubor
se připojí jako doklad a náhled na omezení upozorní.

Vlastní kopie portrétů jsou neměnné. **Odstranit obrázek** odpojí portrét od osoby,
nesmaže původní soubor a lze jej vrátit pomocí Zpět. Kopie mohou být sdílené mezi
osobami a zůstávají zachované pro historii. Nevzniká oprávnění k celé galerii.

## Převod údajů a omezení

Přenášejí se jména, osoby, běžné životní události, rodiče, partnerství/sňatky,
poznámky a základní citace. Partnerství bez data sňatku nevytváří datovaný sňatek.
Samotný rok zůstává rokem; přesná gregoriánská data se převedou na ISO.
Přibližná, rozsahová a další nepodporovaná data zůstávají původním textem.
GEDCOM dostane počáteční generační rozložení; Vestigia zachovává pozice boxů.

Android nemodeluje všechny struktury GEDCOM ani všechny desktopové události.
Adoptivní vazby se nesmějí změnit na biologické; zůstávají ve zdrojové kopii
s upozorněním. Neznámé typy událostí Vestigia mohou být obecnými událostmi,
více hlavních osob se může rozdělit do samostatných záznamů. Nepodporované role,
šablony, sestavy, další moduly a specifické atributy nejsou plně editovatelné.
Náhled uvádí zjištěná omezení; nejde o bezeztrátový obousměrný převod.

Úplný původní soubor se ukládá beze změny do soukromého
`noBackupFilesDir/genealogy/sources` a rodokmen na něj odkazuje. Zatím není
uživatelské rozhraní pro jeho export; soukromé uložení není záloha mimo telefon.
Debug a Release mají oddělené knihovny. Aktualizace instalací `-r` data zachovává,
odinstalace aplikace soukromá data odstraní.

Limity: vstup nejvýše 256 MiB, text GEDCOM 32 MiB / 250 000 řádků / 64 úrovní;
jedno médium 32 MiB, obrázek nejvýše 220 milionů pixelů. GEDZIP nejvýše 20 000
položek, 64 MiB na položku a 512 MiB deklarované rozbalené velikosti. Import
neextrahuje názvy cest do souborového systému a kontroluje CRC načtených položek.
Uložený snímek jednoho rodokmenu má limit 64 Mi znaků JSON; větší zápis se
odmítne před potvrzením databázové transakce. Velké grafy mohou být náročné na paměť a vykreslování; parserový test 4 000 osob
není zárukou stejné rychlosti interaktivního grafu.

Specifikace: [GEDCOM 5.5.1](https://gedcom.io/specifications/ged551.pdf),
[GEDCOM 7 a GEDZIP](https://gedcom.io/specifications/FamilySearchGEDCOMv7.html).
