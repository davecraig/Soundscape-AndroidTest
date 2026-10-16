---
title: Tehnologija iza aplikacije Soundscape
layout: page
parent: "Korištenje Soundscape"
has_toc: false
nav_order: 200
lang: hr
permalink: /users/how-it-works.html
machine-translated: true
translated-from: 1d7f81a05233463dbc2eb8bf62ea26979f5114d0
---

# Tehnologija iza aplikacije Soundscape
Cilj ove stranice je dati opću predodžbu o tome kako aplikacija Soundscape radi iznutra. Ne morate je pročitati da biste koristili aplikaciju, ali napisana je iz nekoliko razloga:

1. Da zainteresiranim novim korisnicima pomogne razumjeti gdje su ograničenja aplikacije
1. Da korisnicima da ideju što bi još bilo moguće s novim značajkama
1. Da programerima da pregled rada aplikacije

Aplikaciju omogućuju dvije tehnologije: GPS i podaci OpenStreetMapa. GPS nam daje dobru predodžbu o tome gdje je telefon i gdje je bio. Podatke OpenStreetMapa zatim koristimo da bismo pronašli što je u blizini i to opisali korisniku.

## Zvučni svjetionici
U većini pogleda to je tehnički najjednostavnije za izvesti. Ako znamo lokaciju telefona i smjer u kojem telefon pokazuje, možemo promijeniti zvuk svjetionika tako da zvuči kao da dolazi iz tog smjera. Za smještanje zvuka u prostor koristimo biblioteku Steam Audio, koja koristi prijenosne funkcije vezane uz glavu (HRTF) kako bi smještanje zvučalo što bolje. Jedino što još radimo jest da mijenjamo zvuk svjetionika tako da se reproducira drugačiji zvuk ovisno o kutu između smjera korisnika i lokacije svjetionika. Kutovi se razlikuju ovisno o odabranom svjetioniku (*Taktilni*, *Bljesak*, *Ping* itd.), a neki imaju više zvukova od drugih. I to su zvučni svjetionici na najjednostavnijoj razini.

Jedina dodatna složenost su pretpostavke o lokaciji telefona i smjeru u kojem korisnik pokazuje. Pogledajmo ih redom.

### Lokacija
Lokacija koju vraća GPS može imati prilično veliku pogrešku, a ona ovisi o tome koliko neba GPS u telefonu vidi te koliko drveća i visokih zgrada odbija GPS signal na putu do telefona.

Lokaciju filtriramo pomoću takozvanog prilagođavanja karti (map matching). Ono pretpostavlja da se korisnik najvjerojatnije kreće ucrtanim putem ili cestom; za sve ceste, poljske putove i staze koristimo pojam „put” (engleski „Way”). Prilagođavanje karti gleda gdje je korisnik bio te, koristeći smjer kretanja i lokalne podatke karte, bira najvjerojatniju lokaciju na nekom putu. Ovaj pristup ne ispravlja samo pogreške GPS-a, nego i pogreške u podacima karte. Nisu svi putovi ucrtani točno, pa i oni imaju pogreške. Kako bi odredio na kojem je putu korisnik najvjerojatnije, algoritam uzima u obzir:
* Koliko je put blizu GPS lokacije i prethodnih GPS lokacija
* Smjer kretanja: kreće li se korisnik u istom smjeru kao put
* Je li moguće doći od posljednje prilagođene lokacije do nove lokacije mrežom putova. To je potrebno kako bi se isključilo preskakanje između putova koji zapravo nisu povezani, npr. jedan prelazi preko drugog mostom ili ispod njega tunelom.
Prilagođavanje karti može zaključiti da u blizini nema putova ili da nije sigurno na kojem je korisnik. U tom slučaju jednostavno čeka sljedeću GPS lokaciju i pokušava ponovno dok ne bude sigurno.

### Smjer
U softveru pratimo nekoliko smjerova:

1. Smjer u kojem telefon pokazuje. Koristimo ga kada je telefon otključan i aplikacija se koristi, ali i kada je telefon zaključan, sve dok ga držite vodoravno sa zaslonom okrenutim prema nebu. To je dobro imati na umu kada stavljate telefon u torbu. Ako ga stavite vodoravno na dno torbe koju nosite uspravno, aplikacija bi koristila nasumičan smjer u kojem torba pokazuje.
1. Smjer u kojem se telefon kreće.
1. Smjer iz slušalica s praćenjem glave. Trenutačno ga ne koristimo, iako ga je aplikacija za iOS podržavala. Tehnologiju za njegovo dodavanje u budućnosti imamo spremnu.

Kada je telefon zaključan i u torbi, aplikacija koristi smjer kretanja. No ako se korisnik ne kreće, smjer nije dostupan. Kada se to dogodi, zvučni svjetionici postaju tiši kako bi pokazali da nije moguće znati trenutačni smjer svjetionika: korisnik se može okretati bez promjene lokacije.

Za neke primjene smjera u aplikaciji smjer se „poravnava” sa smjerom puta na koji je lokacija prilagođena. Ako korisnik hoda otprilike u smjeru puta, pretpostavlja se da je stvarni smjer puta točan i koristi se u tim izračunima.

### Zaključak
Iako su zvučni svjetionici na prvi pogled jednostavni, prilagođavanje karti kojim pokušavamo ukloniti pogreške lokacije i smjera unosi priličnu složenost.

## Podaci karte
Podaci karte koje aplikacija koristi gotovo svi potječu iz projekta OpenStreetMap. Imamo poslužitelj s kartom cijelog svijeta na više razina zumiranja. Svaka razina zumiranja podijeljena je na pločice. Razina 0 sadrži 1 pločicu, razina 1 sadrži 4 pločice, razina 2 sadrži 16 pločica i tako dalje do razine 14, koja sadrži oko 268 milijuna pločica koje pokrivaju cijeli planet. Svaka pločica ima više slojeva, a svaki sloj ima točke, linije i poligone od kojih se može nacrtati grafička karta. Tu grafičku kartu korisnik vidi na zaslonu aplikacije. Svaka točka, linija i poligon imaju metapodatke koji opisuju što su. Oni većinom dolaze izravno iz podataka OpenStreetMapa, pa linija može biti `footway` koja je `sidewalk` ili `road` koja je `minor`.

Podaci se pretvaraju u grafičku kartu pomoću „stila” koji ima pravila o tome kako crtati različite točke, linije i poligone u svakom sloju, npr. kako nacrtati stazu, šumu ili autobusnu stanicu. Pravila se mogu razlikovati ovisno o razini zumiranja, zbog čega se pri zumiranju pojavljuje sve više točaka i linija koje nisu vidljive kada je karta udaljena, npr. autobusne stanice i staze.

Mijenjanjem stila možemo promijeniti izgled karte na zaslonu, a odatle dolazi i „pristupačna karta” koju isprobavamo. Cilj joj je veći kontrast te deblje linije i tekst. Stil je ugrađen u aplikaciju, pa ne moramo mijenjati kartu na poslužitelju da bismo promijenili njezin izgled.

Ali kako koristimo podatke karte za zvuk?

### Korištenje podataka karte za zvuk
Za stvaranje zvučnog sučelja trenutačno koristimo razmjerno malen dio podataka karte. Gotovo cijelo zvučno sučelje koristi samo pločice na najvećoj razini zumiranja. Aplikacija spaja mrežu od 2 × 2 pločice oko mjesta gdje je korisnik i gleda samo nekoliko slojeva:

* `transportation` - za sve vrste putova, uključujući ceste, staze, željezničke i tramvajske pruge.
* `poi` - točke interesa, npr. trgovine, sportski centri, klupe, poštanski sandučići, autobusne stanice itd.
* `building` - za `poi` koje su ucrtane kao nešto više od točke, npr. veliki supermarketi ili gradske vijećnice.

Spaja linije i poligone preko granica pločica i pretvara sve putove u povezane dionice putova i raskrižja. To je važno jer nam omogućuje da pretražujemo duž puta i saznamo kamo možemo stići.

Svi obrađeni podaci također se spremaju u format koji se lako pretražuje, kako bi aplikacija lako pronašla koji su elementi karte u blizini. U ovom se koraku podaci razvrstavaju u kategorije. Trenutačne kategorije su:

* Ceste
* Ceste i staze (svi putovi)
* Raskrižja: točke u kojima se putovi sijeku
* Ulazi: točke na zgradi označene kao ulaz.
* Prijelazi: prijelazi preko ceste
* Točke interesa: sve točke interesa
* Stajališta javnog prijevoza: autobusne stanice, željezničke stanice, tramvajska stajališta i slično.
* Podkategorije točaka interesa:
  * Informacije
  * Predmet
  * Mjesto
  * Orijentir
  * Mobilnost
  * Sigurnost
* Naselja i podkategorije (vidi sljedeći odjeljak)
  * Veliki gradovi
  * Gradovi
  * Sela
  * Zaseoci

Uz to aplikacija za bilo koju lokaciju može lako pronaći

* „Sva stajališta javnog prijevoza unutar 50 m” ili
 * „Najbliže raskrižje ispred mene” ili
 * „Najbliži zaselak, selo, grad ili veliki grad”

Uz to je stvaranje najava samo pitanje pretraživanja podataka prema trenutačnoj lokaciji i smjeru. Kako se korisnik kreće preko mreže pločica, aplikacija je ažurira tako da bude centrirana oko trenutačne lokacije.

### Više podataka
Jedan od problema naše vrlo lokalne mreže podataka karte je to što možemo „vidjeti” najviše oko 1 km u bilo kojem smjeru. To je u redu kada opisujemo ono što je ispred nas, ali ponekad bismo željeli dati više konteksta. Glavni je primjer kada se aplikacija koristi, a korisnik ne hoda.

Kada aplikacija otkrije da se korisnik kreće brže od 5 metara u sekundi, mijenja način na koji opisuje svijet. Umjesto da najavljuje svako raskrižje i svaku točku interesa, najavljuje rjeđe i samo obližnje ceste. Problem je u tome što poznavanje naziva ceste ne pomaže mnogo ako ne znate u kojem je mjestu.

Kako bismo to riješili, sada obrađujemo podatke karte i na nižoj razini zumiranja i izdvajamo podatke iz sloja `place`. On sadrži nazive gradova, velikih gradova, četvrti, sela i slično. Jedan od problema pri kartiranju jest to što između tih mjesta nema uvijek jasne granice. OpenStreetMap ponekad u svojoj bazi ima granice gradova, ali čak i tada se ta informacija često izgubi dok stigne do naše karte od pločica. Ono što imamo jest lokacija na kojoj se nazivi mjesta crtaju na karti. Oni se razvrstavaju u kategorije, a aplikacija zatim pronalazi zaselak, selo, grad ili veliki grad najbliži korisniku i najavljuje ga.

Za mnoge velike gradove sam naziv grada nikada neće biti najavljen, jer je većina velikih gradova podijeljena na manje dijelove poput četvrti, ali one daju dodatni kontekst i vrlo su korisne. Samo imajte na umu da, kada aplikacija najavi da ste blizu ulice u određenoj četvrti, to znači samo da je oznaka te četvrti najbliža točka. Ona može biti netočna ili čak s druge strane rijeke.

### Više konteksta
Što se više konteksta može dodati opisima, to bolje, sve dok su sažeti i predvidljivi. Jedan od problema koje smo uočili pri opisivanju raskrižja jest to što su u njima često bili putovi „bez naziva”. To su putovi koji nemaju ime. U podacima karte to može biti samo poljski put, staza ili servisna cesta, ali bez više konteksta to nije osobito korisno u opisima. Srećom, možemo bolje: kad god aplikacija treba najaviti put bez naziva, provjerava može li za njega pronaći više konteksta.

* **Je li to nogostup?**
Mnoga područja OpenStreetMapa danas imaju nogostupe ucrtane odvojeno od cesta. Obično su označeni kao `sidewalk`, ali obično ne navode uz koju cestu idu.

Kada aplikacija naiđe na nogostup bez naziva, traži cestu za koju misli da ide uz njega i prema njoj imenuje nogostup. Pokazalo se da je to vrlo važno za naše najave. Umjesto da se najavljuje svako raskrižje nogostupa, dok se krećemo nogostupom najave se izgovaraju kao da se krećemo pripadajućom cestom. Umjesto *„Kretanje prema zapadu stazom”* čujemo *„Kretanje prema zapadu ulicom Moor Road”*. Korisnik je na ucrtanom nogostupu, ali opis ima više smisla.

* **Završava li kod puta s nazivom?**
Vrlo često postoje pješačke staze koje spajaju dvije ceste. Gledajući oba kraja staze, lako možemo dodati taj kontekst: u jednom smjeru to može biti *„Staza do Moor Road”*, a pri dolasku s drugog kraja *„Staza do Roselea Drive”*. To radimo samo ako se staza ne račva; ako se račva na dvije staze bez naziva, taj kontekst ne pokušavamo dodati.

* **Završava li blizu oznake?**
Ako put bez naziva počinje ili završava blizu oznake, ona se koristi za njegov opis, npr. *„Staza do raskrižja kod velikog stabla”*. Korisnik može dodati oznake gdje god želi, a dodavanjem oznaka duž mreže staza može dati kontekst cijeloj ruti.

* **Ulazi li u točku interesa ili izlazi iz nje?**
Ako put bez naziva počinje izvan točke interesa i završava unutar nje (ili obrnuto), možemo dodati taj kontekst, npr. *„Poljski put do Lennox Parka”*.

* **Završava li blizu ulaza?**
Ako put bez naziva počinje ili završava bliže ulazu, možemo dodati taj kontekst, npr. *„Servisna cesta do Best Buyja”*.

* **Završava li blizu orijentira ili mjesta?**
Ako put bez naziva počinje ili završava bliže orijentiru, možemo dodati i taj kontekst, npr. *„Servisna cesta do katedrale sv. Egidija”*.

* **Je li to slijepa ulica?**
Aplikacija označava kao slijepu ulicu svaki put bez naziva koji nikamo ne vodi.

* **Prolazi li preko mosta, kroz tunel ili stubama?**
Ako put bez naziva prelazi preko mosta, prolazi kroz tunel ili ide stubama gore ili dolje, to se bilježi i dodaje kontekstu. To je odvojeno od odredišta, pa je moguć i kontekst poput *„Staza preko mosta do Lennox Parka”*.

Ti se konteksti dodaju redom, pa je moguće u jednom smjeru imati *„Staza do Park Lanea preko stuba”*, a u drugom *„Staza do Lennox Parka preko stuba”*. Pri izlasku iz parka prednost ima ulica s nazivom, a pri ulasku u njega koristi se park.

#### Budući kontekst
U budućnosti se nadamo dodati još nekoliko vrsta konteksta, uključujući:
* Kontekst za putove koji prate vodene tokove, npr. *„Staza uz rijeku Dee”*
* Kontekst za putove koji prate rub vodenih površina, npr. *„Staza uz akumulaciju Milngavie”*
* Kontekst za putove koji prate željezničke pruge, npr. *„Staza uz prugu”*. To bi moglo uključivati čak i naziv željezničke linije
* Više podataka o mostovima i tunelima, odnosno preko čega ili ispod čega prolaze, npr. *„Staza preko mosta iznad pruge do Moor Road”*

## Najave
Sada kada imamo podatke karte u formatu koji lako koristimo, stvaranje najava zaista je prilično jednostavno.

### Najave tijekom hodanja
Tijekom hodanja mogu se čuti sljedeće najave (redom prioriteta):

1. Koliko je udaljeno trenutačno odredište
1. Opis raskrižja koje slijedi
1. Opis 5 najbližih točaka interesa

Sve najave imaju ograničenu učestalost kako se ne bi prečesto ponavljale. Ako se korisnik prestane kretati, najave prestaju, a ni tijekom kretanja najava se ne ponavlja pri svakoj novoj GPS lokaciji. Učestalost je ista kao u aplikaciji za iOS:

* Svakih 60 sekundi za trenutačno odredište
* Svakih 30 sekundi za raskrižje koje slijedi
* Svakih 60 sekundi za točku interesa

Najave se mogu filtrirati u izborniku *Postavke*, a to bi se ponašanje svakako moglo proširiti.

### Najave pri bržem kretanju
Pri kretanju brže od 5 metara u sekundi najava trenutačnog odredišta i dalje se izgovara, ali najave raskrižja i točaka interesa zamjenjuje najava koja otprilike opisuje gdje je korisnik. Ona navodi obližnje stajalište javnog prijevoza, točku interesa u kojoj se nalazimo (npr. unutar velikog parka) ili obližnju cestu i naselje. Koriste podatke opisane ranije, a u budućnosti je očito moguće omogućiti njihovu prilagodbu.

## Oznake i rute
Oznake i rute uglavnom su samo značajka korisničkog sučelja koja ne ovisi ni o GPS-u ni zapravo o podacima karte. Oznake su imenovana mjesta koja korisnik želi spremiti, a rute su poredani popisi tih oznaka. Korisničko sučelje za stvaranje i jednog i drugog preuzeto je izravno iz verzije za iOS.

### Reprodukcija rute
Reprodukcijom rute rute oživljavaju. Kada se ruta reproducira, na prvoj oznaci rute stvara se zvučni svjetionik. Kada se korisnik približi toj oznaci, ruta automatski premješta zvučni svjetionik na sljedeću oznaku. Ako više nema oznaka, reprodukcija rute završava.

## Zaključak
Nadamo se da vam je ovo dalo uvid u to kako aplikacija radi. Aplikacija se stalno razvija na temelju povratnih informacija korisnika, pa nam se javite ako mislite da bi se nešto moglo dodati.
