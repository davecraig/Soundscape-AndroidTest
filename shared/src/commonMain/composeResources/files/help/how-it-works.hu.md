---
title: A Soundscape mögötti technológia
layout: page
parent: "A Soundscape használata"
has_toc: false
nav_order: 200
lang: hu
permalink: /users/how-it-works.html
machine-translated: true
translated-from: 1d7f81a05233463dbc2eb8bf62ea26979f5114d0
---

# A Soundscape mögötti technológia
Ennek az oldalnak az a célja, hogy általános képet adjon arról, hogyan működik a Soundscape alkalmazás a háttérben. Az alkalmazás használatához nem kell elolvasnia, de több okból is megírtuk:

1. Hogy segítsen az érdeklődő új felhasználóknak megérteni, hol vannak az alkalmazás korlátai
1. Hogy a felhasználók képet kapjanak arról, mi minden lenne még lehetséges új funkciókkal
1. Hogy a fejlesztők áttekintést kapjanak az alkalmazás működéséről

Két technológia teszi lehetővé az alkalmazást: a GPS és az OpenStreetMap adatai. A GPS jó képet ad arról, hol van a telefon, és hol járt. Az OpenStreetMap adataival aztán megkereshetjük, mi van a közelben, és leírhatjuk a felhasználónak.

## Irányjelző hangok
Technológiai szempontból ezek a legegyszerűbbek. Ha ismerjük a telefon helyét és azt az irányt, amerre a telefon mutat, az irányjelző hangot úgy módosíthatjuk, hogy úgy szóljon, mintha abból az irányból jönne. A hang térbeli elhelyezéséhez a Steam Audio könyvtárát használjuk, amely fejhez kötött átviteli függvényeket (HRTF) alkalmaz, hogy az elhelyezés a lehető legjobban szóljon. Ezen kívül csak annyit teszünk, hogy az irányjelző hangot a felhasználó iránya és az irányjelző hang helye közötti szög szerint másik hangra cseréljük. A szögek a választott stílustól függnek (*Kopogó*, *Fáklya*, *Csippanás* stb.), és némelyiknek több hangja van, mint a többinek. Ennyi az irányjelző hang a legegyszerűbb szinten.

Az egyetlen további bonyodalom a telefon helyére és arra az irányra vonatkozó feltevés, amerre a felhasználó mutat. Nézzük ezeket sorban.

### Hely
A GPS által visszaadott hely elég nagy hibával terhelt lehet. Ez attól függ, mekkora darab látszik az égből a telefon GPS-e számára, és hány fa és magas épület veri vissza a GPS-jelet a telefonig vezető úton.

A hely szűrésére az úgynevezett térképhez illesztést (map matching) használjuk. Ez feltételezi, hogy a felhasználó a legvalószínűbben egy térképen szereplő ösvényen vagy úton halad; minden útra, földútra és ösvényre az „útszakasz” kifejezést használjuk (angolul „Way”). A térképhez illesztés figyeli, merre járt a felhasználó, és a mozgás iránya meg a helyi térképadatok alapján kiválasztja a legvalószínűbb helyet egy útszakaszon. Ez a megközelítés nemcsak a GPS hibáit egyenlíti ki, hanem a térképadatok hibáit is. Nem minden útszakasz van pontosan feltérképezve, így ezeknek is vannak hibáik. Hogy eldöntse, melyik útszakaszon van a legvalószínűbben a felhasználó, az algoritmus figyelembe veszi:
* Milyen közel van egy útszakasz a GPS-helyhez és a korábbi GPS-helyekhez
* A haladás irányát: ugyanabba az irányba halad-e a felhasználó, mint az útszakasz
* Eljuthat-e a felhasználó az utolsó illesztett helyről az új helyre az útszakaszok hálózatán. Ez azért kell, hogy kizárjuk az átugrást olyan útszakaszok között, amelyek valójában nem kapcsolódnak, például az egyik hídon halad át a másik fölött, vagy alagútban alatta.
A térképhez illesztés arra is juthat, hogy nincs a közelben útszakasz, vagy nem biztos benne, melyiken van a felhasználó. Ilyenkor egyszerűen megvárja a következő GPS-helyet, és újra próbálkozik, amíg biztos nem lesz.

### Irány
A szoftverben több irányt is követünk:

1. Az irányt, amerre a telefon mutat. Ezt használjuk, ha a telefon fel van oldva, és az alkalmazás használatban van, de akkor is, ha a telefon zárolva van, feltéve, hogy vízszintesen, képernyővel az ég felé tartják. Érdemes erre gondolnia, amikor a telefont a táskájába teszi. Ha vízszintesen egy függőlegesen tartott táska aljára kerül, az alkalmazás a táska véletlenszerű irányát használná.
1. Az irányt, amerre a telefon halad.
1. A fejkövetéses fejhallgató irányát. Ezt jelenleg nem használjuk, bár az iOS-alkalmazás támogatta. A technológia megvan ahhoz, hogy a jövőben hozzáadjuk.

Ha a telefon zárolva van, és táskában van, az alkalmazás a haladás irányát használja. Ha viszont a felhasználó nem mozog, nincs elérhető irány. Ilyenkor az irányjelző hangok halkabbak lesznek, jelezve, hogy nem lehet tudni, éppen merre van az irányjelző hang: a felhasználó helyben is megfordulhat.

Az irány néhány felhasználásánál az alkalmazás az irányt az illesztett útszakasz irányához „igazítja”. Ha tehát a felhasználó nagyjából az útszakasz irányában halad, akkor az útszakasz tényleges irányát tekintjük helyesnek, és ezt használjuk ezekben a számításokban.

### Összegzés
Bár első ránézésre az irányjelző hangok egyszerűek, a térképhez illesztés, amellyel a hely- és irányhibákat próbáljuk kiszűrni, elég sok bonyolultságot hoz.

## Térképadatok
Az alkalmazás által használt térképadatok szinte mind az OpenStreetMap projektből származnak. Egy szervert üzemeltetünk, amelyen az egész világ térképe több nagyítási szinten megtalálható. Minden nagyítási szint csempékre van osztva. A 0. szint 1 csempéből áll, az 1. szint 4 csempéből, a 2. szint 16 csempéből, és így tovább egészen a 14. szintig, amely nagyjából 268 millió csempével fedi le a bolygót. Minden csempének több rétege van, és minden rétegben pontok, vonalak és sokszögek vannak, amelyekből grafikus térkép rajzolható. Ezt a grafikus térképet látja a felhasználó az alkalmazás képernyőjén. Minden pontnak, vonalnak és sokszögnek vannak metaadatai, amelyek leírják, mi az. Ezek többnyire közvetlenül az OpenStreetMap adataiból származnak, így egy vonal lehet egy `footway`, amely `sidewalk`, vagy egy `road`, amely `minor`.

Az adatokból egy „stílus” készít grafikus térképet, amely szabályokat tartalmaz arról, hogyan kell megrajzolni az egyes rétegek különböző pontjait, vonalait és sokszögeit, például egy ösvényt, egy erdőt vagy egy buszmegállót. A szabályok nagyítási szintenként eltérhetnek, ezért nagyításkor egyre több olyan pont és vonal jelenik meg, amely kicsinyítve nem látszik, például buszmegállók és ösvények.

A stílus módosításával megváltoztathatjuk, hogyan néz ki a térkép a képernyőn, és innen ered az „akadálymentes térkép” is, amelyet most kipróbálunk. Célja a nagyobb kontraszt és a vastagabb vonalak és szöveg. A stílus be van építve az alkalmazásba, így a megjelenés módosításához nem kell a szerveren lévő térképet megváltoztatnunk.

De hogyan használjuk a térképadatokat a hanghoz?

### A térképadatok használata a hanghoz
A hangalapú felület létrehozásához jelenleg a térképadatoknak viszonylag kis részét használjuk. Szinte az egész hangalapú felület csak a legnagyobb nagyítási szint csempéit használja. Az alkalmazás a felhasználó helye körül összeilleszt egy 2 × 2 csempéből álló rácsot, és csak néhány réteget vizsgál:

* `transportation` - mindenféle útszakaszhoz, beleértve az utakat, ösvényeket, vasutakat és villamosvonalakat.
* `poi` - érdekes helyek, például üzletek, sportközpontok, padok, postaládák, buszmegállók stb.
* `building` - azokhoz a `poi`-khoz, amelyek többként vannak feltérképezve, mint egy pont, például nagy szupermarketek vagy városházák.

Összekapcsolja a vonalakat és sokszögeket a csempék határain át, és az összes útszakaszt összefüggő szakaszokká és kereszteződésekké alakítja. Ez azért fontos, mert így végig tudunk keresni egy útszakaszon, hogy megtudjuk, hová juthatunk.

Az összes feldolgozott adat könnyen kereshető formátumba is kerül, hogy az alkalmazás könnyen megtalálja, mely térképelemek vannak a közelben. Ezen a ponton az adatok kategóriákba sorolódnak. A jelenlegi kategóriák:

* Utak
* Utak és ösvények (minden útszakasz)
* Kereszteződések: azok a pontok, ahol az útszakaszok keresztezik egymást
* Bejáratok: egy épület bejáratként megjelölt pontjai.
* Átkelők: gyalogátkelők az úttesten
* Érdekes helyek: minden érdekes hely
* Tömegközlekedési megállók: buszmegállók, vasútállomások, villamosmegállók és így tovább.
* Az érdekes helyek alkategóriái:
  * Információ
  * Tárgy
  * Hely
  * Nevezetesség
  * Mobilitás
  * Biztonság
* Települések és alkategóriák (lásd a következő részt)
  * Nagyvárosok
  * Városok
  * Falvak
  * Tanyák

Ennek köszönhetően az alkalmazás bármely helyhez könnyen megtalálja

* „Az összes tömegközlekedési megállót 50 m-en belül” vagy
 * „A legközelebbi kereszteződést előttem” vagy
 * „A legközelebbi tanyát, falut, várost vagy nagyvárost”

Ennek köszönhetően a bemondások létrehozása csak annyi, hogy a jelenlegi hely és irány alapján lekérdezzük az adatokat. Ahogy a felhasználó halad a csemperácson, az alkalmazás frissíti azt, hogy mindig a jelenlegi hely köré igazodjon.

### Több adat
Nagyon helyi térképadat-rácsunk egyik gondja, hogy bármely irányban legfeljebb nagyjából 1 km-re „látunk”. Ez elég ahhoz, hogy leírjuk, mi van előttünk, de néha több összefüggést szeretnénk adni. A fő példa erre, amikor az alkalmazást használják, de a felhasználó nem gyalogol.

Ha az alkalmazás azt észleli, hogy a felhasználó másodpercenként 5 méternél gyorsabban halad, megváltoztatja, ahogyan a világot leírja. Ahelyett, hogy minden kereszteződést és érdekes helyet bemondana, ritkábban mond be, és csak a közeli utakat. A gond ezzel az, hogy egy út neve nem sokat ér, ha nem tudja, melyik településen van.

Hogy ezt megoldjuk, a térképadatokat most egy kisebb nagyítási szinten is feldolgozzuk, és kinyerjük a `place` réteg adatait. Ez városok, nagyvárosok, városrészek, falvak és hasonlók nevét tartalmazza. A térképezés egyik gondja, hogy ezek között a helyek között nem mindig van egyértelmű határ. Az OpenStreetMap adatbázisában néha szerepelnek városhatárok, de még ilyenkor is gyakran elvész ez az információ, mire a csempés térképünkbe kerül. Amivel rendelkezünk, az a hely, ahol a településnevek a térképre kerülnek. Ezeket kategóriákba soroljuk, majd az alkalmazás megkeresi a felhasználóhoz legközelebbi tanyát, falut, várost vagy nagyvárost, és bemondja.

Sok nagyvárosnál magát a város nevét sosem mondja be, mert a legtöbb nagyváros kisebb részekre, például városrészekre oszlik, de ezek további összefüggést adnak, és nagyon hasznosak. Csak tartsa szem előtt: ha az alkalmazás azt mondja, hogy egy adott városrész egyik utcája közelében van, az csak annyit jelent, hogy annak a városrésznek a felirata a legközelebbi pont, és ez lehet pontatlan, vagy akár egy folyó túlpartján is lehet.

### Több összefüggés
Minél több összefüggést adhatunk a leírásokhoz, annál jobb, ha tömörek és kiszámíthatók maradnak. A kereszteződések leírásánál az egyik gond az volt, hogy gyakran szerepeltek bennük „névtelen” útszakaszok. Ezek olyan útszakaszok, amelyeknek nincs nevük. A térképadatokban ezek lehetnek csak egy földút, egy ösvény vagy egy szervizút, de további összefüggés nélkül nem sokat érnek a szöveges leírásokban. Szerencsére jobbat is tudunk: valahányszor az alkalmazás névtelen útszakaszt készül bemondani, megnézi, ki tud-e deríteni hozzá több összefüggést.

* **Járda?**
Az OpenStreetMap sok területén ma már a járdák az utaktól külön vannak feltérképezve. Ezeket általában `sidewalk` jelöléssel látják el, de többnyire nem derül ki belőlük, melyik út járdái.

Ha az alkalmazás névtelen járdára bukkan, olyan utat keres, amelyről úgy gondolja, hogy mellette halad, és arról nevezi el a járdát. Ez a bemondásaink szempontjából nagyon fontosnak bizonyult. Ahelyett, hogy a járda minden kereszteződését bemondaná, a járdán haladva a bemondások úgy szólnak, mintha a hozzá tartozó úton haladnánk. Ahelyett, hogy *„Nyugat felé halad az ösvényen”*, azt halljuk, hogy *„Nyugat felé halad a Moor Roadon”*. A felhasználó a feltérképezett járdán van, de a leírás értelmesebb.

* **Elnevezett útszakasznál ér véget?**
Nagyon gyakran vannak gyalogösvények, amelyek két utat kötnek össze. Az ösvény mindkét végét megnézve könnyen hozzáadhatjuk ezt az összefüggést: az egyik irányban lehet *„Ösvény a Moor Road felé”*, a másik végéről közelítve pedig *„Ösvény a Roselea Drive felé”*. Ezt csak akkor tesszük, ha az ösvény nem ágazik el; ha két névtelen ösvényre ágazik, nem próbáljuk hozzáadni ezt az összefüggést.

* **Jelölő közelében ér véget?**
Ha egy névtelen útszakasz jelölő közelében kezdődik vagy ér véget, a jelölőt használjuk a leírásához, például *„Ösvény a nagy fánál lévő elágazás felé”*. A felhasználó bárhová tehet jelölőket, és ha ösvényhálózatok mentén helyez el jelölőket, egy egész útvonalhoz adhat összefüggést.

* **Érdekes helybe vezet be vagy onnan ki?**
Ha egy névtelen útszakasz egy érdekes helyen kívül kezdődik, és belül ér véget (vagy fordítva), hozzáadhatjuk ezt az összefüggést, például *„Földút a Lennox Park felé”*.

* **Bejárat közelében ér véget?**
Ha egy névtelen útszakasz egy bejárathoz közelebb kezdődik vagy ér véget, hozzáadhatjuk ezt az összefüggést, például *„Szervizút a Best Buy felé”*.

* **Nevezetesség vagy hely közelében ér véget?**
Ha egy névtelen útszakasz egy nevezetességhez közelebb kezdődik vagy ér véget, ezt az összefüggést is hozzáadhatjuk, például *„Szervizút a Szent Egyed-székesegyház felé”*.

* **Zsákutca?**
Az alkalmazás zsákutcának jelöl minden olyan névtelen útszakaszt, amely nem vezet sehová.

* **Hídon, alagúton vagy lépcsőn halad át?**
Ha a névtelen útszakasz hídon vagy alagúton halad át, illetve lépcsőn megy fel vagy le, ezt feljegyezzük, és hozzáadjuk az összefüggéshez. Ez független a célállomás megjelölésétől, így olyan összefüggés is lehetséges, mint *„Ösvény hídon át a Lennox Park felé”*.

Ezeket az összefüggéseket sorrendben adjuk hozzá, így az egyik irányban lehet *„Ösvény a Park Lane felé lépcsőn át”*, a másikban pedig *„Ösvény a Lennox Park felé lépcsőn át”*. A parkból kifelé az elnevezett utca élvez elsőbbséget, befelé viszont a parkot használjuk.

#### Jövőbeli összefüggések
A jövőben több további összefüggést is szeretnénk hozzáadni, többek között:
* Összefüggés a vízfolyásokat követő útszakaszokhoz, például *„Ösvény a Dee folyó mellett”*
* Összefüggés az állóvizek partját követő útszakaszokhoz, például *„Ösvény a milngavie-i víztározó mellett”*
* Összefüggés a vasutat követő útszakaszokhoz, például *„Ösvény a vasút mellett”*. Ez akár a vasútvonal nevét is tartalmazhatná
* Több információ a hidakról és alagutakról, vagyis hogy mi fölött vagy alatt vezetnek, például *„Ösvény hídon át a vasút fölött a Moor Road felé”*

## Bemondások
Most, hogy a térképadatok könnyen használható formában vannak, a bemondások létrehozása tényleg elég egyszerű.

### Bemondások gyalogláskor
Gyalogláskor a következő bemondások hangozhatnak el (fontossági sorrendben):

1. Milyen messze van a jelenlegi célállomás
1. A közeledő kereszteződés leírása
1. Az 5 legközelebbi érdekes hely leírása

Minden bemondás gyakorisága korlátozott, hogy ne ismétlődjenek túl sűrűn. Ha a felhasználó megáll, a bemondások abbamaradnak, és mozgás közben sem ismétlődik egy bemondás minden új GPS-helynél. A gyakoriság az iOS-alkalmazáséval azonos:

* 60 másodpercenként a jelenlegi célállomáshoz
* 30 másodpercenként a közeledő kereszteződéshez
* 60 másodpercenként egy érdekes helyhez

A bemondások a *Beállítások* menüben szűrhetők, és ezt a viselkedést biztosan lehetne bővíteni.

### Bemondások gyorsabb haladáskor
Ha másodpercenként 5 méternél gyorsabban halad, a jelenlegi célállomás bemondása megmarad, de a kereszteződések és az érdekes helyek bemondását egy olyan bemondás váltja fel, amely nagyjából leírja, hol van a felhasználó. Ez megnevez egy közeli tömegközlekedési megállót, egy érdekes helyet, amelyen belül vagyunk (például egy nagy parkban), vagy egy közeli utat és települést. Ezek a korábban leírt adatokat használják, és a jövőben nyilván lehetőség van arra, hogy testre szabhatók legyenek.

## Jelölők és útvonalak
A jelölők és az útvonalak nagyrészt csak a felhasználói felület funkciói, amelyek sem a GPS-től, sem igazán a térképadatoktól nem függenek. A jelölők elnevezett helyek, amelyeket a felhasználó el szeretne menteni, az útvonalak pedig ezeknek a jelölőknek a sorba rendezett listái. Mindkettő létrehozásának felülete közvetlenül az iOS-verzióból származik.

### Útvonal lejátszása
Lejátszáskor kelnek életre az útvonalak. Amikor egy útvonal lejátszása elindul, az irányjelző hang az útvonal első jelölőjére kerül. Amint a felhasználó a jelölő közelébe ér, az útvonal automatikusan a következő jelölőre helyezi át az irányjelző hangot. Ha nincs több jelölő, az útvonal lejátszása véget ér.

## Összegzés
Reméljük, hogy ez betekintést adott az alkalmazás működésébe. Az alkalmazás a felhasználók visszajelzései alapján folyamatosan fejlődik, ezért kérjük, vegye fel velünk a kapcsolatot, ha úgy gondolja, hogy valamivel bővíteni lehetne.
