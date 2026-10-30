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
systémové gesto. Všechny aktivity používají společnou komponentu `BackButton`
se stejnou šipkou, dotykovou plochou a lokalizovaným přístupným popiskem. Inkoustová modř, světlý papír a patkové nadpisy doplňují čitelné
systémové popisky. Podporován je tmavý režim, čeština a angličtina.

## Genealogie jako další pracovní modul

Pracovní návrh podle zadání z 29. 10. 2026: hlavním pohledem je **graf vazeb**,
po vzoru desktopového `RegisterGraphView`, bez povinné výchozí stránky matriky.
Nový rodokmen začne první osobou. Dokumentace se připojuje až ke konkrétní osobě;
existence skenu nebo matričního záznamu není podmínkou zadávání.

### Pracovní plocha telefonu

- Kompaktní horní lišta: Zpět do rodokmenů, název, hledání osoby a nabídka.
- Graf vyplní zbývající plochu. Posun prstem po pozadí, přiblížení dvěma prsty,
  tlačítko Přizpůsobit a návrat k vybrané osobě. Přesun boxu až po delším podržení,
  aby běžný posun pohledu omylem neměnil rozložení.
- Osobní box: jméno, životní data a případně portrét. Vybraná osoba se zvýrazní.
  Události jsou skryté datové záznamy bez vlastních uzlů. Graf zobrazuje
  přímé rodičovské a partnerské spojnice; význam a role událostí zůstávají v datech.
- Plovoucí + nabídne Osobu, Příbuzného k výběru a Událost. Pro příbuzného je
  vždy dostupné připojení existující osoby, aby nevznikaly zbytečné duplicity.
- Akce Zpět/Znovu jsou dostupné z kompaktního ovládání grafu. Automatické
  uspořádání je výslovný příkaz; běžná editace nepřeskládává ruční pozice.

Klepnutí na osobu otevře **spodní vysouvací panel** uvnitř stejné obrazovky
(Compose bottom sheet, nikoli samostatná Android Activity). V částečné výšce
ukáže jméno, data a akce Upravit / Přidat vazbu / Dokumentace. Roztažením se
otevře editor téměř přes celou obrazovku; při psaní respektuje klávesnici.
Sekce editoru: Osoba, Události a vazby, Dokumentace, Poznámky. Zavření zachová
výběr, posun a zoom grafu. Panel nesmí zakrýt vybranou osobu bez možnosti
přesunout pohled; zobrazení grafu pracuje s dostupnou nezakrytou plochou.

Rozpracované úpravy zůstávají v návrhu i při otočení telefonu. Uložit potvrdí
jednu operaci Zpět; Zrušit návrh zahodí. Gesto zpět nebo zavření při změnách
nabídne uložení/zahození. Zpět nejprve zavře editor/panel, potom opustí graf.

Na širokém okně / tabletu používá stejný editor pravý panel, takže graf i údaje
zůstávají viditelné. Seznam osob je hledatelný pomocný pohled pro orientaci,
nenahrazuje hlavní graf. Aplikace si pamatuje otevřený rodokmen a polohu grafu.

### Data a návaznost na desktop

Osoba, událost, role osoby v události, dokumentace a geometrie grafu jsou
oddělené. Vycházíme z desktopových `registergraphdata.h`, `GenealogyDraft`
a `eventrelationships`; neukládáme pouhé vizuální čáry bez významu.
Zjednodušené zobrazení rodinných vazeb nesmí vytvářet falešně datované narození
nebo sňatek. Stejná osoba může být ve více událostech; příbuzenské vazby
se validují proti duplicitám a cyklům rodičovství.

Dokumentace připojená u osoby má vlastní popisek, poznámku a odkaz na uložené
médium, případně normalizovaný výřez. Později lze upřesnit související událost.
Odpojení dokumentace nemaže zdrojový soubor. Při implementaci je nutné zachovat
význam desktopových referencí `genealogy.person` / `core.documentation`;
kompatibilita importu/exportu se ověří zvlášť nad kopiemi dat. Přímé otevírání
živých desktopových projektů ani synchronizace zatím nejsou slíbené.

Vykreslování navrhujeme pomocí Compose Canvas pro hrany a pozadí s interaktivními
boxy osob nad ním. Datové souřadnice jsou nezávislé na zoomu a hustotě
displeje. Viditelné oblasti omezují vykreslování; změna osoby nebo tažení boxu
nepřepočítává celý graf. Rozložení lze počítat mimo hlavní vlákno, zachovat
ruční pozice a výsledky použít pouze pro aktuální verzi dat. Konkrétní výkon
je třeba ověřit prototypem na skutečném telefonu.

### První implementační celek

Rodokmen → první osoba → graf → editor v panelu → přidání/připojení rodiče,
partnera nebo dítěte → události a role → dokumentace u osoby. Od začátku lokální
uložení, transakce a Zpět/Znovu, zachování pracovní plochy. Následuje hledání
osob, větší grafy a ověřený přenos dat s desktopem. První pracovní celek grafu a editoru je implementovaný od 1.0.0012;
aktuální ovládání a omezení popisuje [genealogy.md](genealogy.md). Od 1.0.0018 jsou dostupné portréty osob a import GEDCOM 5.5.1, GEDCOM 7,
GEDZIP 7 a genealogických grafů `.vestigia`; [postup a omezení](imports.md).
Synchronizace a export desktopového projektu zatím implementované nejsou.

## Badatelské pomůcky a správce stahování

Katalog má 13 mobilních databázových balíčků, bez desktopových šablon, hledání, oborové filtry a
přehled skutečně nainstalovaných pomůcek. Detail rozlišuje přenosovou velikost
a místo po instalaci a nabízí stažení, aktualizaci a potvrzenou odinstalaci.
Nainstalované pomůcky se otevírají přímo do samostatného sdíleného prohlížeče,
bez vazby na moduly. Textová a obrazová podoba sdílejí datovou vrstvu, hledání,
filtry, stránkování a detail. Obrázky na Commons vyžadují připojení; dříve načtené snímky mohou být
v mezipaměti. Desktopové heraldické šablony v Androidu nejsou.
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
Testy instalace: `./gradlew testDebugUnitTest` a `./scripts/test-device.sh CLASS[,CLASS]`.
UI testy vyžadují fyzický odemčený telefon; na zamčeném telefonu se přeskočí.
Emulátor se nepoužívá.


## Povinný účet v testovací distribuci

Před otevřením modulů i samostatných pomůcek je společná vstupní kontrola účtu.
Přihlášení/registrace používá web Vestigia v systémovém prohlížeči s potvrzením
shodného kódu. Účet bez testovacího povolení do aplikace nevstoupí, ani když má
Premium nebo administrátorskou roli. V horní liště je **Účet** se stavem,
termínem online ověření, ručním ověřením a odhlášením. Odebrání přístupu
zobrazí vstupní kontrolu bez smazání knihovny. Debug i Release používají stejné
podmínky a samostatné relace. Technické podrobnosti jsou v README.

Od 1.0.0019 mají v genealogickém grafu vlastní box pouze osoby. Události jsou
skryté datové záznamy upravitelné z přehledu osoby; rodiče a partneři mají přímé
spojnice. Přidání příbuzného je dostupné také přímo v editoru nové osoby.
