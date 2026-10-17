---
title: Tehnologija v ozadju aplikacije Soundscape
layout: page
parent: "Uporaba Soundscape"
has_toc: false
nav_order: 200
lang: sl
permalink: /users/how-it-works.html
machine-translated: true
translated-from: adca9a12417b9862100cb0f837baedb969eded43
---

# Tehnologija v ozadju aplikacije Soundscape
Namen te strani je dati splošno predstavo o tem, kako aplikacija Soundscape deluje v ozadju. Za uporabo aplikacije je ni treba prebrati, vendar je bila napisana iz več razlogov:

1. Da zainteresiranim novim uporabnikom pomaga razumeti, kje so omejitve aplikacije
1. Da uporabnikom da idejo, kaj vse bi bilo mogoče z novimi funkcijami
1. Da razvijalcem da pregled delovanja aplikacije

Aplikacijo omogočata dve tehnologiji: GPS in podatki OpenStreetMap. GPS nam da dobro predstavo o tem, kje je telefon in kje je bil. S podatki OpenStreetMap nato poiščemo, kaj je v bližini, in to opišemo uporabniku.

## Zvočni svetilniki
V večini pogledov so s tehničnega vidika najpreprostejši za izvedbo. Če poznamo lokacijo telefona in smer, v katero telefon kaže, lahko zvok svetilnika spremenimo tako, da se sliši, kot da prihaja iz te smeri. Za umestitev zvoka v prostor uporabljamo knjižnico Steam Audio, ki uporablja prenosne funkcije glave (HRTF), da umestitev zveni čim bolje. Edino, kar še naredimo, je, da zvok svetilnika spreminjamo tako, da se predvaja drugačen zvok glede na kot med smerjo uporabnika in lokacijo svetilnika. Koti se razlikujejo glede na izbrani svetilnik (*Tolkalni*, *Blisk*, *Pisk* itd.), nekateri pa imajo več zvokov kot drugi. In to so zvočni svetilniki na najpreprostejši ravni.

Edina dodatna zapletenost so predpostavke o lokaciji telefona in o smeri, v katero kaže uporabnik. Poglejmo si ju po vrsti.

### Lokacija
Lokacija, ki jo vrne GPS, ima lahko precej veliko napako. Ta je odvisna od tega, koliko neba vidi GPS v telefonu ter koliko dreves in visokih stavb odbija signal GPS na poti do telefona.

Lokacijo filtriramo s tako imenovanim prilagajanjem zemljevidu (map matching). Pri tem predpostavljamo, da se uporabnik najverjetneje premika po vrisani poti ali cesti; za vse ceste, kolovoze in poti uporabljamo izraz »pot« (angleško »Way«). Prilagajanje zemljevidu upošteva, kod je uporabnik hodil, in na podlagi smeri gibanja ter lokalnih podatkov zemljevida izbere najverjetnejšo lokacijo na neki poti. Ta pristop ne odpravlja le napak GPS, temveč tudi napake v podatkih zemljevida. Vse poti niso vrisane natančno, zato imajo tudi same napake. Da algoritem ugotovi, na kateri poti je uporabnik najverjetneje, upošteva:
* Kako blizu je pot lokaciji GPS in prejšnjim lokacijam GPS
* Smer gibanja: ali se uporabnik premika v isti smeri kot pot
* Ali je mogoče priti od zadnje prilagojene lokacije do nove lokacije po omrežju poti. To je potrebno, da se izključi preskakovanje med potmi, ki v resnici niso povezane, npr. ena vodi čez drugo po mostu ali pod njo skozi predor.
Prilagajanje zemljevidu lahko ugotovi, da v bližini ni nobene poti ali da ni prepričano, na kateri je uporabnik. V tem primeru preprosto počaka na naslednjo lokacijo GPS in poskusi znova, dokler ni prepričano.

### Smer
V programski opremi spremljamo več smeri:

1. Smer, v katero kaže telefon. Uporabljamo jo, ko je telefon odklenjen in se aplikacija uporablja, pa tudi ko je telefon zaklenjen, če ga držite vodoravno z zaslonom proti nebu. To je dobro imeti v mislih, ko telefon pospravite v torbo. Če ga položite vodoravno na dno torbe, ki jo nosite pokonci, bi aplikacija uporabila naključno smer, v katero kaže torba.
1. Smer, v katero se telefon premika.
1. Smer iz slušalk s sledenjem glavi. Te zdaj ne uporabljamo, čeprav jo je aplikacija za iOS podpirala. Tehnologijo za njeno dodajanje v prihodnosti imamo pripravljeno.

Ko je telefon zaklenjen in v torbi, aplikacija uporabi smer gibanja. Če pa se uporabnik ne premika, smer ni na voljo. Ko se to zgodi, zvočni svetilniki postanejo tišji, kar pove, da trenutne smeri svetilnika ni mogoče vedeti: uporabnik se lahko obrača, ne da bi spremenil lokacijo.

Pri nekaterih uporabah smeri v aplikaciji se smer »pripne« na smer poti, na katero je bila lokacija prilagojena. Če torej uporabnik hodi približno v smeri poti, se predpostavi, da je pravilna dejanska smer poti, in ta se uporabi v teh izračunih.

### Sklep
Čeprav so zvočni svetilniki na prvi pogled preprosti, prilagajanje zemljevidu, s katerim skušamo odpraviti napake lokacije in smeri, prinese precej zapletenosti.

## Podatki zemljevida
Podatki zemljevida, ki jih uporablja aplikacija, skoraj vsi izvirajo iz projekta OpenStreetMap. Imamo strežnik z zemljevidom vsega sveta na več ravneh povečave. Vsaka raven povečave je razdeljena na ploščice. Raven 0 vsebuje 1 ploščico, raven 1 vsebuje 4 ploščice, raven 2 vsebuje 16 ploščic in tako naprej do ravni 14, ki vsebuje približno 268 milijonov ploščic, ki pokrivajo ves planet. Vsaka ploščica ima več plasti, vsaka plast pa ima točke, črte in poligone, iz katerih je mogoče narisati grafični zemljevid. Ta grafični zemljevid uporabnik vidi na zaslonu aplikacije. Vsaka točka, črta in poligon ima metapodatke, ki opisujejo, kaj je. Ti večinoma prihajajo neposredno iz podatkov OpenStreetMap, zato je črta lahko `footway`, ki je `sidewalk`, ali `road`, ki je `minor`.

Podatki se pretvorijo v grafični zemljevid s »slogom«, ki ima pravila, kako narisati različne točke, črte in poligone v vsaki plasti, npr. kako narisati pot, gozd ali avtobusno postajališče. Pravila se lahko razlikujejo glede na raven povečave, zato se ob povečevanju pojavlja vedno več točk in črt, ki jih pri pomanjšanem prikazu ni videti, npr. avtobusna postajališča in poti.

S spreminjanjem sloga lahko spremenimo videz zemljevida na zaslonu, od tod pa izvira tudi »dostopni zemljevid«, ki ga preizkušamo. Njegov cilj je večji kontrast ter debelejše črte in besedilo. Slog je vgrajen v aplikacijo, zato nam za spremembo videza zemljevida na strežniku ni treba spreminjati.

Kako pa podatke zemljevida uporabimo za zvok?

### Uporaba podatkov zemljevida za zvok
Za ustvarjanje zvočnega vmesnika zdaj uporabljamo razmeroma majhen del podatkov zemljevida. Skoraj ves zvočni vmesnik uporablja le ploščice na največji ravni povečave. Aplikacija sestavi mrežo 2 × 2 ploščic okoli mesta, kjer je uporabnik, in pogleda le nekaj plasti:

* `transportation` - za vse vrste poti, vključno s cestami, potmi, železniškimi in tramvajskimi progami.
* `poi` - točke zanimanja, npr. trgovine, športni centri, klopi, poštni nabiralniki, avtobusna postajališča itd.
* `building` - za `poi`, ki so vrisani kot nekaj več kot točka, npr. veliki supermarketi ali mestne hiše.

Poveže črte in poligone čez meje ploščic ter vse poti pretvori v povezane odseke poti in križišča. To je pomembno, ker lahko tako iščemo vzdolž poti in ugotovimo, kam lahko pridemo.

Vsi obdelani podatki se shranijo tudi v obliko, ki jo je lahko preiskovati, da aplikacija zlahka najde, kateri elementi zemljevida so v bližini. Na tej točki se podatki razvrstijo v kategorije. Trenutne kategorije so:

* Ceste
* Ceste in poti (vse poti)
* Križišča: točke, kjer se poti sekajo
* Vhodi: točke na stavbi, označene kot vhod.
* Prehodi: prehodi čez cesto
* Točke zanimanja: vse točke zanimanja
* Postajališča javnega prevoza: avtobusna postajališča, železniške postaje, tramvajska postajališča in podobno.
* Podkategorije točk zanimanja:
  * Informacije
  * Predmet
  * Kraj
  * Orientacijska točka
  * Mobilnost
  * Varnost
* Naselja in podkategorije (glejte naslednji razdelek)
  * Velika mesta
  * Mesta
  * Vasi
  * Zaselki

S tem lahko aplikacija za katero koli lokacijo zlahka najde

* »Vsa postajališča javnega prevoza v krogu 50 m« ali
 * »Najbližje križišče pred mano« ali
 * »Najbližji zaselek, vas, mesto ali veliko mesto«

S tem je ustvarjanje zvočnih obvestil le vprašanje poizvedbe po podatkih glede na trenutno lokacijo in smer. Ko se uporabnik premika čez mrežo ploščic, jo aplikacija posodobi, tako da je vedno osredinjena okoli trenutne lokacije.

### Več podatkov
Ena od težav naše zelo lokalne mreže podatkov je, da lahko »vidimo« največ približno 1 km v katero koli smer. To je v redu, ko opisujemo, kar je pred nami, včasih pa bi radi dali več konteksta. Glavni primer je, ko se aplikacija uporablja, uporabnik pa ne hodi.

Ko aplikacija zazna, da se uporabnik premika hitreje kot 5 metrov na sekundo, spremeni način opisovanja sveta. Namesto da bi oznanila vsako križišče in vsako točko zanimanja, obvešča redkeje in le o bližnjih cestah. Težava je, da ime ceste ni kaj dosti v pomoč, če ne veste, v katerem kraju je.

Da bi to rešili, zdaj podatke zemljevida obdelamo tudi na nižji ravni povečave in iz njih izluščimo podatke plasti `place`. Ta vsebuje imena mest, velikih mest, sosesk, vasi in podobno. Ena od težav pri kartiranju je, da med temi kraji ni vedno jasne meje. OpenStreetMap ima včasih v svoji zbirki podatkov meje mest, a tudi takrat se ta informacija pogosto izgubi, preden pride do našega zemljevida iz ploščic. Imamo pa lokacijo, kjer so imena krajev narisana na zemljevidu. Ta se razvrstijo v kategorije, aplikacija pa nato poišče zaselek, vas, mesto ali veliko mesto, ki je uporabniku najbližje, in ga sporoči.

Pri mnogih velikih mestih ime samega mesta ne bo nikoli sporočeno, ker je večina velikih mest razdeljena na manjše dele, kot so soseske, vendar te dajo dodaten kontekst in so zelo koristne. Le zapomnite si: ko aplikacija sporoči, da ste blizu ulice v določeni soseski, to pomeni le, da je oznaka te soseske najbližja točka. Ta je lahko napačna ali celo na drugi strani reke.

### Več konteksta
Več konteksta, kot ga lahko dodamo opisom, tem bolje, če ostanejo jedrnati in predvidljivi. Ena od težav, ki smo jih opazili pri opisovanju križišč, je bila, da so bile v njih pogosto poti »brez imena«. To so poti, ki nimajo imena. V podatkih zemljevida je to lahko le kolovoz, pot ali dovozna cesta, a brez več konteksta v opisih ni kaj dosti uporabno. Na srečo lahko naredimo bolje: kadar koli aplikacija namerava sporočiti pot brez imena, preveri, ali lahko zanjo ugotovi več konteksta.

* **Ali je to pločnik?**
Mnoga območja v OpenStreetMap imajo danes pločnike vrisane ločeno od cest. Običajno so označeni kot `sidewalk`, vendar navadno ne povedo, ob kateri cesti so.

Ko aplikacija naleti na pločnik brez imena, poišče cesto, za katero misli, da poteka ob njem, in po njej poimenuje pločnik. Izkazalo se je, da je to za naša zvočna obvestila zelo pomembno. Namesto da bi sporočala vsako križišče pločnika, se med premikanjem po pločniku obvestila izgovarjajo, kot da bi se premikali po pripadajoči cesti. Namesto *»Potovanje proti zahodu po poti«* slišimo *»Potovanje proti zahodu po Moor Road«*. Uporabnik je na vrisanem pločniku, a opis je bolj smiseln.

* **Ali se konča pri poti z imenom?**
Zelo pogosto obstajajo pešpoti, ki povezujejo dve cesti. Če pogledamo oba konca poti, lahko ta kontekst zlahka dodamo: v eni smeri je to lahko *»Pot do Moor Road«*, ob prihodu z drugega konca pa *»Pot do Roselea Drive«*. To naredimo le, če se pot ne razcepi; če se razcepi na dve poti brez imena, tega konteksta ne poskušamo dodati.

* **Ali se konča blizu oznake?**
Če se pot brez imena začne ali konča blizu oznake, se ta uporabi za njen opis, npr. *»Pot do križišča pri velikem drevesu«*. Uporabnik lahko oznake doda, kamor želi, in z dodajanjem oznak vzdolž omrežja poti lahko da kontekst celotni poti.

* **Ali vodi v točko zanimanja ali iz nje?**
Če se pot brez imena začne zunaj točke zanimanja in konča v njej (ali obratno), lahko dodamo ta kontekst, npr. *»Kolovoz do Lennox Parka«*.

* **Ali se konča blizu vhoda?**
Če se pot brez imena začne ali konča bliže vhodu, lahko dodamo ta kontekst, npr. *»Dovozna cesta do trgovine Best Buy«*.

* **Ali se konča blizu orientacijske točke ali kraja?**
Če se pot brez imena začne ali konča bliže orientacijski točki, lahko dodamo tudi ta kontekst, npr. *»Dovozna cesta do katedrale sv. Egidija«*.

* **Ali je to slepa ulica?**
Aplikacija kot slepo ulico označi vsako pot brez imena, ki nikamor ne vodi.

* **Ali gre čez most, skozi predor ali po stopnicah?**
Če pot brez imena gre čez most, skozi predor ali po stopnicah gor ali dol, se to zabeleži in doda kontekstu. To je ločeno od cilja, zato je mogoč tudi kontekst, kot je *»Pot čez most do Lennox Parka«*.

Ti konteksti se dodajajo po vrsti, zato je mogoče v eni smeri imeti *»Pot do Park Lane po stopnicah«*, v drugi pa *»Pot do Lennox Parka po stopnicah«*. Pri izhodu iz parka ima prednost ulica z imenom, pri vstopu vanj pa se uporabi park.

#### Prihodnji kontekst
V prihodnosti upamo dodati še več vrst konteksta, med drugim:
* Kontekst za poti, ki sledijo vodotokom, npr. *»Pot ob reki Dee«*
* Kontekst za poti, ki sledijo robu vodnih površin, npr. *»Pot ob zbiralniku Milngavie«*
* Kontekst za poti, ki sledijo železnici, npr. *»Pot ob železnici«*. To bi lahko vključevalo celo ime železniške proge
* Več podatkov o mostovih in predorih, torej čez kaj ali pod čim vodijo, npr. *»Pot čez most nad železnico do Moor Road«*

## Zvočna obvestila
Zdaj ko imamo podatke zemljevida v obliki, ki jo lahko zlahka uporabimo, je ustvarjanje zvočnih obvestil res precej preprosto.

### Zvočna obvestila med hojo
Med hojo se lahko slišijo ta zvočna obvestila (po prednostnem vrstnem redu):

1. Kako daleč je trenutni cilj
1. Opis križišča, ki se bliža
1. Opis 5 najbližjih točk zanimanja

Vsa zvočna obvestila imajo omejeno pogostost, da se ne ponavljajo prepogosto. Če se uporabnik neha premikati, obvestila prenehajo, in tudi med premikanjem se obvestilo ne ponovi ob vsaki novi lokaciji GPS. Pogostost je enaka kot v aplikaciji za iOS:

* Vsakih 60 sekund za trenutni cilj
* Vsakih 30 sekund za križišče, ki se bliža
* Vsakih 60 sekund za točko zanimanja

Zvočna obvestila lahko filtrirate v meniju *Nastavitve*, to vedenje pa bi vsekakor lahko razširili.

### Zvočna obvestila pri hitrejšem premikanju
Pri premikanju hitreje kot 5 metrov na sekundo se obvestilo o trenutnem cilju še vedno izgovarja, obvestila o križiščih in točkah zanimanja pa nadomesti obvestilo, ki približno opiše, kje je uporabnik. Navede bližnje postajališče javnega prevoza, točko zanimanja, v kateri smo (npr. v velikem parku), ali bližnjo cesto in naselje. Uporabljajo podatke, opisane prej, v prihodnosti pa bi očitno lahko omogočili njihovo prilagajanje.

## Oznake in poti
Oznake in poti so večinoma le funkcija uporabniškega vmesnika, ki ni odvisna niti od GPS niti pravzaprav od podatkov zemljevida. Oznake so poimenovana mesta, ki jih uporabnik želi shraniti, poti pa so urejeni seznami teh oznak. Uporabniški vmesnik za ustvarjanje obojega je prevzet neposredno iz različice za iOS.

### Predvajanje poti
S predvajanjem poti poti oživijo. Ko se pot predvaja, se na prvi oznaki poti ustvari zvočni svetilnik. Ko se uporabnik tej oznaki približa, pot samodejno premakne zvočni svetilnik na naslednjo oznako. Če ni več oznak, se predvajanje poti konča.

## Sklep
Upamo, da vam je to dalo vpogled v to, kako aplikacija deluje. Aplikacija se nenehno razvija na podlagi povratnih informacij uporabnikov, zato se nam oglasite, če menite, da bi lahko kaj dodali.
