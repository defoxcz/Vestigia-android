# Badatelské pomůcky: skutečné stažení a odinstalace

Správce nyní skutečně stahuje a instaluje SQLite balíčky. Ukázkové úlohy a
simulovaný průběh byly odstraněny. Nainstalované pomůcky se otevírají přímo do
společného textového nebo obrazového prohlížeče. Jde o samostatné funkce bez vazby
na pracovní moduly; podrobnosti jsou v [readers.md](readers.md).

## Katalog a přenos

- Přehled má 13 mobilních balíčků, bez desktopových heraldických šablon, oddělené od rodokmenů a médií.
- Výchozí metadata jsou přibalena z webového katalogu. Aplikace je obnovuje přes
  `https://vestigiascriptorium.com/api/research-aids/catalog?scope=all` a ukládá
  poslední platnou odpověď pro použití offline. Bez přihlášení.
- Původní webové API vrací jen desktopové heraldické šablony, které Android
  ignoruje. Do nasazení rozšířeného API zachovává přibalená metadata mobilních
  balíčků; při neaktuálním hashi
  odmítne instalaci a zobrazí chybu místo instalace neověřeného obsahu.
- WorkManager uchovává frontu přes zavření aplikace a běžný restart systému;
  přenos pokračuje podle omezení Androidu. Násilné ukončení aplikace systémem
  může vyžadovat její znovuotevření. Každá pomůcka má jedinečnou úlohu.
- Dlouhý přenos běží jako foreground `dataSync` práce se systémovým oznámením.
- Přepínač neměřeného připojení nastavuje skutečnou síťovou podmínku úloh.
  Neměřené připojení je obvykle Wi-Fi, nikoli nutně každý Wi-Fi hotspot.
  Změna podmínky restartuje rozpracované přenosy s novým omezením.
- API vydá dočasnou HTTPS adresu konkrétního ZIPu. Tokeny se neukládají do dat,
  logů ani repozitáře. Přenos je omezen očekávanou velikostí a časovými limity.
- Po HTTP 409 se jednou obnoví katalog a opakuje stažení s aktuálním hashem.
- Pozastavení ruší přenos a uklízí rozpracované soubory. Pokračování stahuje od
  začátku; částečný HTTP Range přenos není předstírán. Chybu lze explicitně opakovat.

## Instalace a data

Před přenosem se kontroluje místo pro archiv, rozbalení a rezervu; původní verze
zůstává na disku. Po přenosu se ověří přesný počet bajtů a SHA-256. Ze ZIPu se
extrahuje pouze jedna očekávaná databáze a kontroluje její velikost a CRC.
Známé přídavné resource-fork soubory `__MACOSX/._<databáze>` do 64 KiB se ignorují
bez extrakce; jiné soubory, cesty a nejednoznačné databáze se odmítají.

SQLite se otevře readonly, ověří se `PRAGMA quick_check`, přítomnost tabulek a
případná publikovaná verze schématu. Desktopové heraldické šablony se v Androidu nestahují.
Nová databáze a její metadata se připraví v samostatné verzi. Až po kontrole
se atomicky přepne ukazatel `current`. Při chybě nebo zrušení zůstává stará
verze zachovaná. Nedokončené soubory se uklidí a odstraní se staré verze.

Úložiště je pouze `noBackupFilesDir/research-aids/<id>/`. ID a jména souborů
jsou validována. Soubory nejsou součástí uživatelských projektů ani sdíleného
úložiště a nepotřebují oprávnění pro přístup ke všem souborům.

## Odinstalace

Odinstalace je dostupná ve Stahování; detail nainstalované pomůcky slouží k prohlížení.
Vyžaduje potvrzení s názvem pomůcky. Nejprve se zruší její případná úloha,
vyčká se ukončení zápisu a potom se odstraní pouze adresář daného ID v úložišti
pomůcek včetně dočasných/stárších verzí. Ostatní pomůcky, rodokmeny a osobní
dokumenty zůstávají zachovány. Položka dál existuje v katalogu a lze ji znovu
stáhnout. Souhrn místa se počítá z reálně nainstalovaných databází.

## Ověření

- JVM: `./gradlew testDebugUnitTest` — instalace, přeinstalace, izolované mazání,
  neplatný hash, odmítnutí cest mimo archiv, známá macOS metadata, chybná velikost,
  neplatná databáze přes validační callback, zrušení a zkrácený přenos.
- Telefon: `./gradlew connectedDebugAndroidTest` — skutečné veřejné databáze,
  SQLite kontrola, trvalost po vytvoření nového správce, odinstalace a opětovná
  instalace; také skutečný foreground Worker a potvrzení odinstalace v GUI.
- Testy síťových balíčků pracují v izolovaném adresáři. Test Workeru neproběhne,
  pokud už je nějaká pomůcka nainstalována nebo existuje rozpracovaná fronta; vlastní testovací instalaci
  po sobě odstraní. Testy se spouštějí pouze na fyzickém telefonu.

### Výsledek ověření 28. 10. 2026

Sedm JVM testů prošlo. Na fyzickém Samsungu SM-S938B / Android 17 prošly
všechny čtyři testy: veřejné stahování, SQLite, skutečný Worker, odinstalace,
opětovná instalace, úplný seznam modulů a potvrzení odinstalace v GUI. První
běh UI testů byl přeskočen kvůli zamčenému telefonu; po odemčení prošla celá
sada bez přeskočení. Sestavení APK, Android Lint a šest Compose renderů prošly.
Rozšíření webového katalogu je v commitu 46d9805; produkční endpoint při kontrole
stále vracel původní katalog jedné položky. Stávající veřejné download endpointy
všech balíčků jsou použitelné s přibalenými ověřovacími metadaty.

Aktualizace pro mobilní prohlížeče: aktivní katalog má 13 pomůcek bez heraldických
šablon. Prošlo 11 JVM testů a 9 testů prohlížečů a přechodového chování na telefonu.
Starší šablony nejsou automaticky mazány; případná stará instalace nebo zbylé
soubory jsou ve Stahování dostupné pouze k potvrzené odinstalaci.


## Oznámení (od 1.0.0005)

Stažení pomůcky nevyvolává žádost o oprávnění k oznámením. Aplikace oprávnění
POST_NOTIFICATIONS nedeklaruje ani nevyžaduje. Průběh je dostupný v záložce
Stahování. Dlouhé přenosy dál používají WorkManager a foreground službu,
aby je Android mohl dokončit na pozadí. Služba předává systému povinnou
notifikaci; na Androidu 13+ bez oprávnění není v oznamovací liště, ale systém
může zobrazit informaci ve správci aktivních aplikací. Na starších verzích
Androidu může být průběhová notifikace viditelná. Žádné obcházení systémových
pravidel ani změny knihovny pomůcek.


## Ověřování zachování knihovny (1.0.0008)

Hlavní lišta výslovně ukazuje DEBUG nebo RELEASE. Varianty nemají společné
úložiště. Chybějící pomůcku nelze bez porovnání stavu před a po aktualizaci
považovat za důkaz smazání při instalaci APK.

`VestigiaAidStorage` zapisuje při otevření knihovny pouze variantu/verzi,
existenci složky, validitu ukazatele a metadat, názvy a velikosti souborů a
stavy úloh WorkManageru. Při dokončení/chybě stažení uvede stav a fázi chyby.
Nezapisuje obsah databází, přihlášení ani podepsané stahovací URL. Metoda
`AidPackageStore.storageStatus` je pouze pro čtení; diagnostika neobnovuje
ukazatele, nesmazává soubory a neprovádí automatické opakované stažení.

Na skutečném telefonu byla před kontrolou Release bez adresáře research-aids
(i bez historie stahovacích úloh); v Debug byla pomůcka přítomná. Starší příčina
se tím neprokázala. Následně byla přes UI výslovně stažena Historická topografie
v Release 1.0.0007: WorkManager SUCCEEDED, hash archivu začíná ebd33372,
places.db má 166670336 B. Následná instalace podepsané Release 1.0.0008 pomocí
`adb install -r` zachovala tutéž platnou instalaci, velikost i úspěšnou úlohu.
Při tomto ověřeném přechodu tedy ke ztrátě pomůcky nedošlo. Testy diagnostiky
používají výhradně vlastní TemporaryFolder.
