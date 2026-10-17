---
title: Technologie za aplikací Soundscape
layout: page
parent: "Používání Soundscape"
has_toc: false
nav_order: 200
lang: cs
permalink: /users/how-it-works.html
machine-translated: true
translated-from: adca9a12417b9862100cb0f837baedb969eded43
---

# Technologie za aplikací Soundscape
Cílem této stránky je přiblížit, jak aplikace Soundscape funguje uvnitř. Abyste aplikaci mohli používat, číst ji nemusíte, ale vznikla z několika důvodů:

1. Aby zájemcům, kteří s aplikací začínají, pomohla pochopit, kde jsou její omezení
1. Aby uživatelé získali představu, co dalšího by šlo s novými funkcemi udělat
1. Aby vývojáři získali přehled o tom, jak aplikace funguje

Aplikaci umožňují dvě technologie: GPS a data OpenStreetMap. GPS nám dává dobrou představu o tom, kde telefon je a kde byl. Data OpenStreetMap pak slouží k tomu, abychom zjistili, co je poblíž, a mohli to uživateli popsat.

## Zvukové majáky
Z technického hlediska je to v mnoha ohledech to nejjednodušší. Pokud známe polohu telefonu a směr, kterým telefon míří, můžeme zvuk majáku upravit tak, aby zněl, jako by přicházel z daného směru. K umístění zvuku v prostoru používáme knihovnu Steam Audio, která využívá přenosové funkce vztažené k hlavě (HRTF), aby umístění znělo co nejlépe. Jediné, co děláme navíc, je, že zvuk majáku měníme tak, aby se přehrával jiný zvuk podle úhlu mezi směrem uživatele a polohou majáku. Úhly se liší podle zvoleného majáku (*Hmatový*, *Záblesk*, *Ping* atd.) a některé mají víc zvuků než jiné. A to jsou zvukové majáky v nejjednodušší podobě.

Jedinou další složitostí jsou předpoklady o poloze telefonu a o směru, kterým uživatel míří. Podívejme se na ně postupně.

### Poloha
Poloha, kterou vrací GPS, může mít dost velkou chybu. Ta závisí na tom, kolik oblohy GPS v telefonu vidí a kolik stromů a vysokých budov odráží signál GPS na cestě k telefonu.

Polohu filtrujeme pomocí takzvaného přichycení k mapě (map matching). Předpokládá se, že se uživatel nejspíš pohybuje po zmapované cestě nebo silnici; pro všechny silnice, polní cesty a stezky používáme pojem „cesta“ (anglicky „Way“). Přichycení k mapě sleduje, kudy uživatel prošel, a podle směru pohybu a místních mapových dat vybere nejpravděpodobnější polohu na nějaké cestě. Tento přístup vyrovnává nejen chyby GPS, ale i chyby v mapových datech. Ne všechny cesty jsou zmapované přesně, takže i ony mají chyby. Aby algoritmus určil, na které cestě uživatel nejspíš je, bere v úvahu:
* Jak blízko je cesta k poloze GPS a k předchozím polohám GPS
* Směr pohybu: zda se uživatel pohybuje stejným směrem jako cesta
* Zda je možné dostat se z poslední přichycené polohy do nové polohy po síti cest. To je potřeba, aby se vyloučilo přeskakování mezi cestami, které ve skutečnosti spojené nejsou, např. jedna vede nad druhou po mostě nebo pod ní tunelem.
Přichycení k mapě může usoudit, že poblíž žádná cesta není, nebo si není jisté, na které uživatel je. V takovém případě jednoduše počká na další polohu GPS a zkusí to znovu, dokud si jisté nebude.

### Směr
V softwaru sledujeme několik směrů:

1. Směr, kterým míří telefon. Používáme ho, když je telefon odemčený a aplikace se používá, ale také když je telefon zamčený, pokud ho držíte vodorovně obrazovkou k obloze. Je dobré na to myslet, když telefon dáváte do tašky. Pokud ho položíte naplocho na dno tašky, kterou nesete svisle, aplikace by použila náhodný směr, kterým zrovna míří taška.
1. Směr, kterým se telefon pohybuje.
1. Směr ze sluchátek se sledováním pohybu hlavy. Ten zatím nepoužíváme, i když aplikace pro iOS ho podporovala. Technologii pro jeho přidání v budoucnu ale připravenou máme.

Když je telefon zamčený a v tašce, aplikace použije směr pohybu. Pokud se ale uživatel nepohybuje, žádný směr k dispozici není. V takovém případě zvukové majáky ztichnou, aby bylo jasné, že aktuální směr k majáku nelze zjistit: uživatel se může otáčet, aniž by změnil polohu.

Při některých použitích směru aplikace směr „přichytí“ ke směru cesty, ke které byl přichycen k mapě. Pokud tedy uživatel jde zhruba ve směru cesty, předpokládá se, že správný je skutečný směr cesty, a ten se v těchto výpočtech použije.

### Závěr
I když se zvukové majáky na první pohled zdají jednoduché, použití přichycení k mapě k odstranění chyb polohy a směru přináší docela dost složitosti.

## Mapová data
Mapová data, která aplikace používá, pocházejí téměř výhradně z projektu OpenStreetMap. Provozujeme server s mapou celého světa v několika úrovních přiblížení. Každá úroveň přiblížení je rozdělená na dlaždice. Úroveň 0 obsahuje 1 dlaždici, úroveň 1 obsahuje 4 dlaždice, úroveň 2 obsahuje 16 dlaždic a tak dále až po úroveň 14, která obsahuje asi 268 milionů dlaždic pokrývajících celou planetu. Každá dlaždice má několik vrstev a každá vrstva obsahuje body, čáry a mnohoúhelníky, ze kterých lze nakreslit grafickou mapu. Tuto grafickou mapu uživatel vidí na obrazovce aplikace. Každý bod, čára a mnohoúhelník má metadata, která popisují, co to je. Většinou pocházejí přímo z dat OpenStreetMap, takže čára může být `footway`, která je `sidewalk`, nebo `road`, která je `minor`.

Data se na grafickou mapu převádějí pomocí „stylu“, který má pravidla, jak kreslit různé body, čáry a mnohoúhelníky v každé vrstvě, např. jak nakreslit stezku, les nebo autobusovou zastávku. Pravidla se mohou lišit podle úrovně přiblížení, a proto se při přibližování zobrazuje stále víc bodů a čar, které při oddálení vidět nejsou, např. autobusové zastávky a stezky.

Změnou stylu můžeme změnit vzhled mapy na obrazovce. Odtud pochází „přístupná mapa“, kterou zkoušíme. Má mít vyšší kontrast a výraznější čáry a text. Styl je součástí aplikace, takže pro změnu vzhledu nemusíme měnit mapu na serveru.

Jak ale mapová data používáme pro zvuk?

### Využití mapových dat pro zvuk
Pro vytvoření zvukového rozhraní zatím používáme poměrně malou část mapových dat. Téměř celé zvukové rozhraní používá jen dlaždice na nejvyšší úrovni přiblížení. Aplikace spojí mřížku 2 × 2 dlaždic kolem místa, kde uživatel je, a podívá se jen na několik vrstev:

* `transportation` - pro všechny druhy cest včetně silnic, stezek, železnic a tramvajových tratí.
* `poi` - body zájmu, např. obchody, sportovní centra, lavičky, poštovní schránky, autobusové zastávky atd.
* `building` - pro `poi`, které jsou zmapované jako víc než jen bod, např. velké supermarkety nebo radnice.

Spojí čáry a mnohoúhelníky přes hranice dlaždic a ze všech cest vytvoří propojené úseky cest a křižovatky. To je důležité, protože díky tomu můžeme hledat podél cesty, kam se po ní dá dostat.

Všechna zpracovaná data se také ukládají ve formátu, ve kterém se snadno hledá, aby aplikace mohla snadno zjistit, které prvky mapy jsou poblíž. V tomto kroku se data rozdělí do kategorií. Současné kategorie jsou:

* Silnice
* Silnice a stezky (všechny cesty)
* Křižovatky: místa, kde se cesty protínají
* Vchody: body na budově označené jako vchod.
* Přechody: přechody přes silnici
* Body zájmu: všechny body zájmu
* Zastávky veřejné dopravy: autobusové zastávky, železniční stanice, tramvajové zastávky a podobně.
* Podkategorie bodů zájmu:
  * Informace
  * Objekt
  * Místo
  * Orientační bod
  * Mobilita
  * Bezpečnost
* Sídla a podkategorie (viz další oddíl)
  * Velkoměsta
  * Města
  * Vesnice
  * Osady

Díky tomu aplikace pro jakoukoli polohu snadno najde

* „Všechny zastávky veřejné dopravy do 50 m“ nebo
 * „Nejbližší křižovatku přede mnou“ nebo
 * „Nejbližší osadu, vesnici, město nebo velkoměsto“

Díky tomu je vytváření hlášení jen otázkou dotazu na data podle aktuální polohy a směru. Když se uživatel pohybuje po mřížce dlaždic, aplikace ji aktualizuje, aby byla vždy vystředěná kolem aktuální polohy.

### Více dat
Jedním z problémů naší velmi místní mřížky mapových dat je, že „vidíme“ nanejvýš asi 1 km v libovolném směru. To stačí k popisu toho, co je před námi, ale někdy bychom rádi poskytli víc souvislostí. Hlavním příkladem je situace, kdy se aplikace používá a uživatel nejde pěšky.

Když aplikace zjistí, že se uživatel pohybuje rychlostí vyšší než 5 metrů za sekundu, změní způsob, jakým svět popisuje. Místo hlášení každé křižovatky a každého bodu zájmu hlásí méně často a jen blízké silnice. Problém je, že znát název silnice moc nepomůže, když nevíte, ve kterém městě je.

Abychom to vyřešili, zpracováváme nyní mapová data i na nižší úrovni přiblížení a získáváme data z vrstvy `place`. Ta obsahuje názvy měst, velkoměst, čtvrtí, vesnic a podobně. Jednou z potíží při mapování je, že mezi těmito místy není vždy jasná hranice. OpenStreetMap má v databázi někdy hranice měst, ale i tehdy se tato informace často ztratí, než se dostane do naší dlaždicové mapy. Máme ale polohu, kde se názvy míst na mapě kreslí. Ty se rozdělí do kategorií a aplikace pak najde osadu, vesnici, město nebo velkoměsto nejbližší uživateli a ohlásí ho.

U mnoha velkoměst se jejich název nikdy neohlásí, protože většina velkoměst je rozdělená na menší části, jako jsou čtvrti, ale ty dávají další souvislosti a jsou velmi užitečné. Jen pamatujte, že když aplikace ohlásí, že jste poblíž ulice v určité čtvrti, znamená to jen, že nejbližším bodem je popisek této čtvrti. Ten může být nesprávný, nebo dokonce na druhém břehu řeky.

### Více souvislostí
Čím víc souvislostí se do popisů vejde, tím lépe, pokud zůstanou stručné a předvídatelné. Jedním z problémů, které jsme u popisu křižovatek viděli, bylo, že v nich často byly cesty „bez názvu“. To jsou cesty, které nemají jméno. V mapových datech to může být jen polní cesta, stezka nebo obslužná komunikace, ale bez dalších souvislostí to v textových popisech moc nepomůže. Naštěstí to jde udělat lépe: kdykoli se aplikace chystá ohlásit cestu bez názvu, zkusí pro ni zjistit víc souvislostí.

* **Je to chodník?**
Mnoho oblastí v OpenStreetMap má dnes chodníky zmapované odděleně od silnic. Obvykle jsou označené jako `sidewalk`, ale většinou neuvádějí, ke které silnici patří.

Když aplikace narazí na chodník bez názvu, vyhledá silnici, o které se domnívá, že vede vedle něj, a podle ní chodník pojmenuje. Ukázalo se, že je to pro naše hlášení velmi důležité. Místo hlášení každé křižovatky chodníku se při pohybu po chodníku hlásí, jako bychom se pohybovali po přidružené silnici. Místo *„Pohyb na západ po stezce“* slyšíme *„Pohyb na západ po Moor Road“*. Uživatel je na zmapovaném chodníku, ale popis dává větší smysl.

* **Končí u cesty s názvem?**
Velmi často existují pěší stezky, které spojují dvě silnice. Když se podíváme na oba konce stezky, můžeme tuto souvislost snadno přidat: v jednom směru to může být *„Stezka k Moor Road“* a při příchodu z druhého konce *„Stezka k Roselea Drive“*. Děláme to jen tam, kde se stezka nevětví; pokud se rozdělí na dvě stezky bez názvu, tuto souvislost přidat nezkoušíme.

* **Končí poblíž značky?**
Pokud cesta bez názvu začíná nebo končí poblíž značky, použije se k jejímu popisu, např. *„Stezka ke křižovatce u velkého stromu“*. Uživatel může přidávat značky, kam chce, a když je přidá podél sítě stezek, může dát souvislosti celé trase.

* **Vede do bodu zájmu, nebo z něj?**
Pokud cesta bez názvu začíná mimo bod zájmu a končí uvnitř něj (nebo naopak), můžeme tuto souvislost přidat, např. *„Polní cesta do Lennox Park“*.

* **Končí poblíž vchodu?**
Pokud cesta bez názvu začíná nebo končí blíž ke vchodu, můžeme tuto souvislost přidat, např. *„Obslužná komunikace k Best Buy“*.

* **Končí poblíž orientačního bodu nebo místa?**
Pokud cesta bez názvu začíná nebo končí blíž k orientačnímu bodu, můžeme přidat i tuto souvislost, např. *„Obslužná komunikace ke katedrále sv. Jiljí“*.

* **Je to slepá ulice?**
Aplikace označí jako slepou ulici každou cestu bez názvu, která nikam nevede.

* **Vede přes most, tunelem nebo po schodech?**
Pokud cesta bez názvu vede přes most, tunelem nebo nahoru či dolů po schodech, zaznamená se to a přidá do souvislostí. Je to nezávislé na cíli, takže je možná i souvislost jako *„Stezka přes most do Lennox Park“*.

Tyto souvislosti se přidávají postupně, takže v jednom směru může zaznít *„Stezka k Park Lane po schodech“* a v druhém *„Stezka do Lennox Park po schodech“*. Při odchodu z parku má přednost ulice s názvem, ale při vstupu do něj se použije park.

#### Budoucí souvislosti
V budoucnu bychom chtěli přidat řadu dalších souvislostí, například:
* Souvislosti pro cesty podél vodních toků, např. *„Stezka podél řeky Dee“*
* Souvislosti pro cesty podél břehů vodních ploch, např. *„Stezka podél nádrže Milngavie“*
* Souvislosti pro cesty podél železnic, např. *„Stezka podél železnice“*. Mohl by se uvádět i název železniční trati
* Více informací o mostech a tunelech, tedy nad čím nebo pod čím vedou, např. *„Stezka přes most nad železnicí k Moor Road“*

## Hlášení
Teď, když máme mapová data ve formátu, se kterým se snadno pracuje, je vytváření hlášení opravdu docela jednoduché.

### Hlášení při chůzi
Při chůzi mohou zaznít tato hlášení (v pořadí podle priority):

1. Jak daleko je aktuální cíl
1. Popis blížící se křižovatky
1. Popis 5 nejbližších bodů zájmu

Všechna hlášení mají omezenou frekvenci, aby se neopakovala příliš často. Když se uživatel přestane pohybovat, hlášení ustanou, a ani při pohybu se hlášení neopakuje s každou novou polohou GPS. Frekvence je stejná jako v aplikaci pro iOS:

* Každých 60 sekund pro aktuální cíl
* Každých 30 sekund pro blížící se křižovatku
* Každých 60 sekund pro bod zájmu

Hlášení lze filtrovat v nabídce *Nastavení* a toto chování by určitě šlo rozšířit.

### Hlášení při rychlejším pohybu
Při pohybu rychlostí vyšší než 5 metrů za sekundu hlášení aktuálního cíle zůstává, ale hlášení křižovatek a bodů zájmu nahradí hlášení, které zhruba popisuje, kde uživatel je. Uvede blízkou zastávku veřejné dopravy, bod zájmu, ve kterém se nacházíme (např. uvnitř velkého parku), nebo blízkou silnici a sídlo. Vycházejí z dat popsaných výše a v budoucnu je zjevně možné umožnit jejich přizpůsobení.

## Značky a trasy
Značky a trasy jsou z velké části jen funkcí uživatelského rozhraní, která nezávisí na GPS a vlastně ani na mapových datech. Značky jsou pojmenovaná místa, která si uživatel chce uložit, a trasy jsou seřazené seznamy těchto značek. Uživatelské rozhraní pro vytváření obojího je převzaté přímo z verze pro iOS.

### Přehrávání trasy
Při přehrávání trasy trasy ožívají. Když se trasa přehrává, na první značce trasy se vytvoří zvukový maják. Jakmile se uživatel k této značce přiblíží, trasa automaticky přesune zvukový maják na další značku. Pokud už žádné další značky nejsou, přehrávání trasy skončí.

## Závěr
Doufáme, že vám tato stránka přiblížila, jak aplikace funguje. Aplikace se neustále vyvíjí podle zpětné vazby od uživatelů, takže se nám prosím ozvěte, pokud vás napadne, co by se dalo přidat.
