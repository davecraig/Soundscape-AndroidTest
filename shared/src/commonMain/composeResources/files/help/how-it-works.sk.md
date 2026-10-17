---
title: Technológia za aplikáciou Soundscape
layout: page
parent: "Používanie Soundscape"
has_toc: false
nav_order: 200
lang: sk
permalink: /users/how-it-works.html
machine-translated: true
translated-from: adca9a12417b9862100cb0f837baedb969eded43
---

# Technológia za aplikáciou Soundscape
Cieľom tejto stránky je priblížiť, ako aplikácia Soundscape funguje vnútri. Aby ste aplikáciu mohli používať, čítať ju nemusíte, ale vznikla z niekoľkých dôvodov:

1. Aby záujemcom, ktorí s aplikáciou začínajú, pomohla pochopiť, kde sú jej obmedzenia
1. Aby používatelia získali predstavu, čo ďalšie by sa dalo s novými funkciami urobiť
1. Aby vývojári získali prehľad o tom, ako aplikácia funguje

Aplikáciu umožňujú dve technológie: GPS a dáta OpenStreetMap. GPS nám dáva dobrú predstavu o tom, kde telefón je a kde bol. Dáta OpenStreetMap potom slúžia na to, aby sme zistili, čo je v blízkosti, a mohli to používateľovi opísať.

## Zvukové majáky
Z technického hľadiska je to v mnohých ohľadoch to najjednoduchšie. Ak poznáme polohu telefónu a smer, ktorým telefón mieri, môžeme zvuk majáka upraviť tak, aby znel, akoby prichádzal z daného smeru. Na umiestnenie zvuku v priestore používame knižnicu Steam Audio, ktorá využíva prenosové funkcie vzťahujúce sa na hlavu (HRTF), aby umiestnenie znelo čo najlepšie. Jediné, čo robíme navyše, je, že zvuk majáka meníme tak, aby sa prehrával iný zvuk podľa uhla medzi smerom používateľa a polohou majáka. Uhly sa líšia podľa zvoleného majáka (*Hmatový*, *Záblesk*, *Ping* atď.) a niektoré majú viac zvukov ako iné. A to sú zvukové majáky v najjednoduchšej podobe.

Jedinou ďalšou zložitosťou sú predpoklady o polohe telefónu a o smere, ktorým používateľ mieri. Pozrime sa na ne postupne.

### Poloha
Poloha, ktorú vracia GPS, môže mať dosť veľkú chybu. Tá závisí od toho, koľko oblohy GPS v telefóne vidí a koľko stromov a vysokých budov odráža signál GPS na ceste k telefónu.

Polohu filtrujeme pomocou takzvaného prichytenia k mape (map matching). Predpokladá sa, že sa používateľ najpravdepodobnejšie pohybuje po zmapovanej ceste alebo ulici; pre všetky cesty, poľné cesty a chodníky používame pojem „cesta“ (anglicky „Way“). Prichytenie k mape sleduje, kadiaľ používateľ prešiel, a podľa smeru pohybu a miestnych mapových dát vyberie najpravdepodobnejšiu polohu na niektorej ceste. Tento prístup vyrovnáva nielen chyby GPS, ale aj chyby v mapových dátach. Nie všetky cesty sú zmapované presne, takže aj ony majú chyby. Aby algoritmus určil, na ktorej ceste používateľ najpravdepodobnejšie je, berie do úvahy:
* Ako blízko je cesta k polohe GPS a k predchádzajúcim polohám GPS
* Smer pohybu: či sa používateľ pohybuje rovnakým smerom ako cesta
* Či je možné dostať sa z poslednej prichytenej polohy do novej polohy po sieti ciest. To je potrebné, aby sa vylúčilo preskakovanie medzi cestami, ktoré v skutočnosti spojené nie sú, napr. jedna vedie nad druhou po moste alebo pod ňou tunelom.
Prichytenie k mape môže usúdiť, že v blízkosti žiadna cesta nie je, alebo si nie je isté, na ktorej používateľ je. V takom prípade jednoducho počká na ďalšiu polohu GPS a skúsi to znova, kým si isté nebude.

### Smer
V softvéri sledujeme niekoľko smerov:

1. Smer, ktorým mieri telefón. Používame ho, keď je telefón odomknutý a aplikácia sa používa, ale aj keď je telefón zamknutý, ak ho držíte vodorovne obrazovkou k oblohe. Je dobré na to myslieť, keď telefón dávate do tašky. Ak ho položíte naplocho na dno tašky, ktorú nesiete zvislo, aplikácia by použila náhodný smer, ktorým práve mieri taška.
1. Smer, ktorým sa telefón pohybuje.
1. Smer zo slúchadiel so sledovaním pohybu hlavy. Ten zatiaľ nepoužívame, hoci aplikácia pre iOS ho podporovala. Technológiu na jeho pridanie v budúcnosti však pripravenú máme.

Keď je telefón zamknutý a v taške, aplikácia použije smer pohybu. Ak sa však používateľ nepohybuje, žiadny smer nie je k dispozícii. V takom prípade zvukové majáky stíchnu, aby bolo jasné, že aktuálny smer k majáku nemožno zistiť: používateľ sa môže otáčať bez toho, aby zmenil polohu.

Pri niektorých použitiach smeru aplikácia smer „prichytí“ k smeru cesty, ku ktorej bol prichytený k mape. Ak teda používateľ ide zhruba v smere cesty, predpokladá sa, že správny je skutočný smer cesty, a ten sa v týchto výpočtoch použije.

### Záver
Hoci sa zvukové majáky na prvý pohľad zdajú jednoduché, použitie prichytenia k mape na odstránenie chýb polohy a smeru prináša dosť veľa zložitosti.

## Mapové dáta
Mapové dáta, ktoré aplikácia používa, pochádzajú takmer výhradne z projektu OpenStreetMap. Prevádzkujeme server s mapou celého sveta v niekoľkých úrovniach priblíženia. Každá úroveň priblíženia je rozdelená na dlaždice. Úroveň 0 obsahuje 1 dlaždicu, úroveň 1 obsahuje 4 dlaždice, úroveň 2 obsahuje 16 dlaždíc a tak ďalej až po úroveň 14, ktorá obsahuje asi 268 miliónov dlaždíc pokrývajúcich celú planétu. Každá dlaždica má niekoľko vrstiev a každá vrstva obsahuje body, čiary a mnohouholníky, z ktorých sa dá nakresliť grafická mapa. Túto grafickú mapu používateľ vidí na obrazovke aplikácie. Každý bod, čiara a mnohouholník má metadáta, ktoré opisujú, čo to je. Väčšinou pochádzajú priamo z dát OpenStreetMap, takže čiara môže byť `footway`, ktorá je `sidewalk`, alebo `road`, ktorá je `minor`.

Dáta sa na grafickú mapu prevádzajú pomocou „štýlu“, ktorý má pravidlá, ako kresliť rôzne body, čiary a mnohouholníky v každej vrstve, napr. ako nakresliť chodník, les alebo autobusovú zastávku. Pravidlá sa môžu líšiť podľa úrovne priblíženia, a preto sa pri približovaní zobrazuje stále viac bodov a čiar, ktoré pri oddialení vidieť nie sú, napr. autobusové zastávky a chodníky.

Zmenou štýlu môžeme zmeniť vzhľad mapy na obrazovke. Odtiaľ pochádza „prístupná mapa“, ktorú skúšame. Má mať vyšší kontrast a výraznejšie čiary a text. Štýl je súčasťou aplikácie, takže na zmenu vzhľadu nemusíme meniť mapu na serveri.

Ako však mapové dáta používame pre zvuk?

### Využitie mapových dát pre zvuk
Na vytvorenie zvukového rozhrania zatiaľ používame pomerne malú časť mapových dát. Takmer celé zvukové rozhranie používa len dlaždice na najvyššej úrovni priblíženia. Aplikácia spojí mriežku 2 × 2 dlaždíc okolo miesta, kde používateľ je, a pozrie sa len na niekoľko vrstiev:

* `transportation` - pre všetky druhy ciest vrátane ulíc, chodníkov, železníc a električkových tratí.
* `poi` - body záujmu, napr. obchody, športové centrá, lavičky, poštové schránky, autobusové zastávky atď.
* `building` - pre `poi`, ktoré sú zmapované ako viac než len bod, napr. veľké supermarkety alebo radnice.

Spojí čiary a mnohouholníky cez hranice dlaždíc a zo všetkých ciest vytvorí prepojené úseky ciest a križovatky. To je dôležité, pretože vďaka tomu môžeme hľadať pozdĺž cesty, kam sa po nej dá dostať.

Všetky spracované dáta sa tiež ukladajú vo formáte, v ktorom sa ľahko vyhľadáva, aby aplikácia mohla ľahko zistiť, ktoré prvky mapy sú v blízkosti. V tomto kroku sa dáta rozdelia do kategórií. Súčasné kategórie sú:

* Ulice a cesty
* Ulice a chodníky (všetky cesty)
* Križovatky: miesta, kde sa cesty pretínajú
* Vchody: body na budove označené ako vchod.
* Priechody: priechody cez cestu
* Body záujmu: všetky body záujmu
* Zastávky verejnej dopravy: autobusové zastávky, železničné stanice, električkové zastávky a podobne.
* Podkategórie bodov záujmu:
  * Informácie
  * Objekt
  * Miesto
  * Orientačný bod
  * Mobilita
  * Bezpečnosť
* Sídla a podkategórie (pozri ďalšiu časť)
  * Veľkomestá
  * Mestá
  * Dediny
  * Osady

Vďaka tomu aplikácia pre akúkoľvek polohu ľahko nájde

* „Všetky zastávky verejnej dopravy do 50 m“ alebo
 * „Najbližšiu križovatku predo mnou“ alebo
 * „Najbližšiu osadu, dedinu, mesto alebo veľkomesto“

Vďaka tomu je vytváranie hlásení len otázkou dopytu na dáta podľa aktuálnej polohy a smeru. Keď sa používateľ pohybuje po mriežke dlaždíc, aplikácia ju aktualizuje, aby bola vždy vycentrovaná okolo aktuálnej polohy.

### Viac dát
Jedným z problémov našej veľmi miestnej mriežky mapových dát je, že „vidíme“ najviac asi 1 km v ľubovoľnom smere. To stačí na opis toho, čo je pred nami, ale niekedy by sme radi poskytli viac súvislostí. Hlavným príkladom je situácia, keď sa aplikácia používa a používateľ nejde pešo.

Keď aplikácia zistí, že sa používateľ pohybuje rýchlosťou vyššou ako 5 metrov za sekundu, zmení spôsob, akým svet opisuje. Namiesto hlásenia každej križovatky a každého bodu záujmu hlási menej často a len blízke cesty. Problém je, že poznať názov cesty veľmi nepomôže, keď neviete, v ktorom meste je.

Aby sme to vyriešili, spracúvame teraz mapové dáta aj na nižšej úrovni priblíženia a získavame dáta z vrstvy `place`. Tá obsahuje názvy miest, veľkomiest, štvrtí, dedín a podobne. Jednou z ťažkostí pri mapovaní je, že medzi týmito miestami nie je vždy jasná hranica. OpenStreetMap má v databáze niekedy hranice miest, ale aj vtedy sa táto informácia často stratí, kým sa dostane do našej dlaždicovej mapy. Máme však polohu, kde sa názvy miest na mape kreslia. Tie sa rozdelia do kategórií a aplikácia potom nájde osadu, dedinu, mesto alebo veľkomesto najbližšie k používateľovi a ohlási ho.

Pri mnohých veľkomestách sa ich názov nikdy neohlási, pretože väčšina veľkomiest je rozdelená na menšie časti, ako sú štvrte, ale tie dávajú ďalšie súvislosti a sú veľmi užitočné. Len pamätajte, že keď aplikácia ohlási, že ste blízko ulice v určitej štvrti, znamená to len, že najbližším bodom je popis tejto štvrte. Ten môže byť nesprávny, alebo dokonca na druhom brehu rieky.

### Viac súvislostí
Čím viac súvislostí sa do opisov zmestí, tým lepšie, ak zostanú stručné a predvídateľné. Jedným z problémov, ktoré sme pri opise križovatiek videli, bolo, že v nich často boli cesty „bez názvu“. To sú cesty, ktoré nemajú meno. V mapových dátach to môže byť len poľná cesta, chodník alebo obslužná komunikácia, ale bez ďalších súvislostí to v textových opisoch veľmi nepomôže. Našťastie sa to dá urobiť lepšie: kedykoľvek sa aplikácia chystá ohlásiť cestu bez názvu, skúsi pre ňu zistiť viac súvislostí.

* **Je to chodník pri ceste?**
Mnoho oblastí v OpenStreetMap má dnes chodníky zmapované oddelene od ciest. Zvyčajne sú označené ako `sidewalk`, ale väčšinou neuvádzajú, ku ktorej ceste patria.

Keď aplikácia narazí na chodník bez názvu, vyhľadá cestu, o ktorej sa domnieva, že vedie vedľa neho, a podľa nej chodník pomenuje. Ukázalo sa, že je to pre naše hlásenia veľmi dôležité. Namiesto hlásenia každej križovatky chodníka sa pri pohybe po chodníku hlási, akoby sme sa pohybovali po pridruženej ceste. Namiesto *„Pohyb na západ po chodníku“* počujeme *„Pohyb na západ po Moor Road“*. Používateľ je na zmapovanom chodníku, ale opis dáva väčší zmysel.

* **Končí pri ceste s názvom?**
Veľmi často existujú pešie chodníky, ktoré spájajú dve cesty. Keď sa pozrieme na oba konce chodníka, môžeme túto súvislosť ľahko pridať: v jednom smere to môže byť *„Chodník k Moor Road“* a pri príchode z druhého konca *„Chodník k Roselea Drive“*. Robíme to len tam, kde sa chodník nerozvetvuje; ak sa rozdelí na dva chodníky bez názvu, túto súvislosť pridať neskúšame.

* **Končí blízko značky?**
Ak cesta bez názvu začína alebo končí blízko značky, použije sa na jej opis, napr. *„Chodník ku križovatke pri veľkom strome“*. Používateľ môže pridávať značky, kam chce, a keď ich pridá pozdĺž siete chodníkov, môže dať súvislosti celej trase.

* **Vedie do bodu záujmu alebo z neho?**
Ak cesta bez názvu začína mimo bodu záujmu a končí vnútri neho (alebo naopak), môžeme túto súvislosť pridať, napr. *„Poľná cesta do Lennox Park“*.

* **Končí blízko vchodu?**
Ak cesta bez názvu začína alebo končí bližšie pri vchode, môžeme túto súvislosť pridať, napr. *„Obslužná komunikácia k Best Buy“*.

* **Končí blízko orientačného bodu alebo miesta?**
Ak cesta bez názvu začína alebo končí bližšie pri orientačnom bode, môžeme pridať aj túto súvislosť, napr. *„Obslužná komunikácia ku Katedrále sv. Egídia“*.

* **Je to slepá ulica?**
Aplikácia označí ako slepú ulicu každú cestu bez názvu, ktorá nikam nevedie.

* **Vedie cez most, tunelom alebo po schodoch?**
Ak cesta bez názvu vedie cez most, tunelom alebo hore či dole po schodoch, zaznamená sa to a pridá do súvislostí. Je to nezávislé od cieľa, takže je možná aj súvislosť ako *„Chodník cez most do Lennox Park“*.

Tieto súvislosti sa pridávajú postupne, takže v jednom smere môže zaznieť *„Chodník k Park Lane po schodoch“* a v druhom *„Chodník do Lennox Park po schodoch“*. Pri odchode z parku má prednosť ulica s názvom, ale pri vstupe doň sa použije park.

#### Budúce súvislosti
V budúcnosti by sme chceli pridať rad ďalších súvislostí, napríklad:
* Súvislosti pre cesty pozdĺž vodných tokov, napr. *„Chodník pozdĺž rieky Dee“*
* Súvislosti pre cesty pozdĺž brehov vodných plôch, napr. *„Chodník pozdĺž nádrže Milngavie“*
* Súvislosti pre cesty pozdĺž železníc, napr. *„Chodník pozdĺž železnice“*. Mohol by sa uvádzať aj názov železničnej trate
* Viac informácií o mostoch a tuneloch, teda nad čím alebo pod čím vedú, napr. *„Chodník cez most nad železnicou k Moor Road“*

## Hlásenia
Teraz, keď máme mapové dáta vo formáte, s ktorým sa ľahko pracuje, je vytváranie hlásení naozaj celkom jednoduché.

### Hlásenia pri chôdzi
Pri chôdzi môžu zaznieť tieto hlásenia (v poradí podľa priority):

1. Ako ďaleko je aktuálny cieľ
1. Opis blížiacej sa križovatky
1. Opis 5 najbližších bodov záujmu

Všetky hlásenia majú obmedzenú frekvenciu, aby sa neopakovali príliš často. Keď sa používateľ prestane pohybovať, hlásenia ustanú, a ani pri pohybe sa hlásenie neopakuje s každou novou polohou GPS. Frekvencia je rovnaká ako v aplikácii pre iOS:

* Každých 60 sekúnd pre aktuálny cieľ
* Každých 30 sekúnd pre blížiacu sa križovatku
* Každých 60 sekúnd pre bod záujmu

Hlásenia sa dajú filtrovať v ponuke *Nastavenia* a toto správanie by sa určite dalo rozšíriť.

### Hlásenia pri rýchlejšom pohybe
Pri pohybe rýchlosťou vyššou ako 5 metrov za sekundu hlásenie aktuálneho cieľa zostáva, ale hlásenia križovatiek a bodov záujmu nahradí hlásenie, ktoré zhruba opisuje, kde používateľ je. Uvedie blízku zastávku verejnej dopravy, bod záujmu, v ktorom sa nachádzame (napr. vnútri veľkého parku), alebo blízku cestu a sídlo. Vychádzajú z dát opísaných vyššie a v budúcnosti je zjavne možné umožniť ich prispôsobenie.

## Značky a trasy
Značky a trasy sú z veľkej časti len funkciou používateľského rozhrania, ktorá nezávisí od GPS a vlastne ani od mapových dát. Značky sú pomenované miesta, ktoré si používateľ chce uložiť, a trasy sú zoradené zoznamy týchto značiek. Používateľské rozhranie na vytváranie oboch je prevzaté priamo z verzie pre iOS.

### Prehrávanie trasy
Pri prehrávaní trasy trasy ožívajú. Keď sa trasa prehráva, na prvej značke trasy sa vytvorí zvukový maják. Keď sa používateľ k tejto značke priblíži, trasa automaticky presunie zvukový maják na ďalšiu značku. Ak už žiadne ďalšie značky nie sú, prehrávanie trasy sa skončí.

## Záver
Dúfame, že vám táto stránka priblížila, ako aplikácia funguje. Aplikácia sa neustále vyvíja podľa spätnej väzby od používateľov, preto sa nám, prosím, ozvite, ak vás napadne, čo by sa dalo pridať.
