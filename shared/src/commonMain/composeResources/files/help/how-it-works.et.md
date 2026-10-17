---
title: "Soundscape'i taga olev tehnoloogia"
layout: page
parent: "Soundscape'i kasutamine"
has_toc: false
nav_order: 200
lang: et
permalink: /users/how-it-works.html
machine-translated: true
translated-from: adca9a12417b9862100cb0f837baedb969eded43
---

# Soundscape'i taga olev tehnoloogia
Selle lehe eesmärk on anda üldine ülevaade sellest, kuidas Soundscape'i rakendus seest töötab. Rakenduse kasutamiseks ei pea sa seda lugema, kuid see on kirjutatud mitmel põhjusel:

1. Et aidata huvilistel uutel kasutajatel mõista, kus on rakenduse piirangud
1. Et anda kasutajatele aimu, mida veel oleks uute funktsioonidega võimalik teha
1. Et anda arendajatele ülevaade rakenduse toimimisest

Rakenduse teevad võimalikuks kaks tehnoloogiat: GPS ja OpenStreetMapi andmed. GPS annab meile hea ettekujutuse, kus telefon on ja kus see on olnud. Seejärel saab OpenStreetMapi andmete abil leida, mis on läheduses, ja seda kasutajale kirjeldada.

## Helimajakad
Tehnoloogia seisukohast on need enamjaolt kõige lihtsamad teostada. Kui teame telefoni asukohta ja suunda, kuhu telefon osutab, saame helimajaka heli muuta nii, et see kõlaks, nagu tuleks see sellest suunast. Heli paigutamiseks ruumis kasutame Steam Audio teeki, mis kasutab peaga seotud ülekandefunktsioone (HRTF), et paigutus kõlaks võimalikult hästi. Ainus, mida veel teeme, on helimajaka heli muutmine nii, et sõltuvalt kasutaja suuna ja helimajaka asukoha vahelisest nurgast mängitakse erinevat heli. Nurgad sõltuvad valitud helimajakast (*Kombatav*, *Erk lamp*, *Kõll* jne) ja mõnel on rohkem helisid kui teistel. Ja ongi kõik, mis helimajakate kõige lihtsamal tasemel on.

Ainus lisakeerukus on oletused telefoni asukoha ja selle suuna kohta, kuhu kasutaja osutab. Vaatame neid kordamööda.

### Asukoht
GPS-i antud asukohal võib olla üsna suur viga. See sõltub sellest, kui suurt osa taevast telefoni GPS näeb ning kui palju puid ja kõrgeid hooneid GPS-signaali teel telefonini peegeldab.

Asukoha filtreerimiseks kasutame nn kaardile sobitamist (map matching). See eeldab, et kasutaja liigub kõige tõenäolisemalt mööda kaardistatud rada või teed; kõigi teede, pinnasteede ja radade kohta kasutame mõistet „teelõik“ (inglise keeles „Way“). Kaardile sobitamine vaatab, kus kasutaja on olnud, ning liikumissuuna ja kohalike kaardiandmete abil valib teelõigul kõige tõenäolisema asukoha. See lähenemine ei arvesta mitte ainult GPS-i vigu, vaid ka kaardiandmete vigu. Kõik teelõigud ei ole täpselt kaardistatud, nii et ka neil on vigu. Et otsustada, millisel teelõigul kasutaja kõige tõenäolisemalt on, arvestab algoritm järgmist:
* Kui lähedal on teelõik GPS-i asukohale ja varasematele GPS-i asukohtadele
* Liikumissuund: kas kasutaja liigub teelõiguga samas suunas
* Kas viimasest kaardile sobitatud asukohast on võimalik teelõikude võrgustiku kaudu uude asukohta jõuda. See on vajalik, et välistada hüppamine teelõikude vahel, mis tegelikult ühendatud ei ole, nt üks läheb teisest üle silla kaudu või alt tunneli kaudu.
Kaardile sobitamine võib otsustada, et läheduses ei ole ühtegi teelõiku või et see ei ole kindel, millisel neist kasutaja on. Sel juhul ootab see lihtsalt järgmist GPS-i asukohta ja proovib uuesti, kuni on kindel.

### Suund
Tarkvaras jälgime mitut suunda:

1. Suund, kuhu telefon osutab. Kasutame seda, kui telefon on lukust lahti ja rakendus on kasutusel, aga ka siis, kui telefon on lukus, kui seda hoitakse horisontaalselt, ekraan taeva poole. Seda tasub meeles pidada, kui paned telefoni kotti. Kui paned selle lapiti püsti hoitava koti põhja, kasutaks rakendus suunda, kuhu kott juhuslikult osutab.
1. Suund, kuhu telefon liigub.
1. Peaasendi jälgimisega kõrvaklappide suund. Praegu me seda ei kasuta, kuigi iOS-i rakendus seda toetas. Tehnoloogia selle hilisemaks lisamiseks on meil olemas.

Kui telefon on lukus ja kotis, kasutab rakendus liikumissuunda. Kui aga kasutaja ei liigu, ei ole suunda saada. Sel juhul muutuvad helimajakad vaiksemaks, et näidata, et helimajaka praegust suunda ei ole võimalik teada: kasutaja võib paigal seistes ümber pöörata.

Mõnel juhul „kinnitab“ rakendus suuna kaardile sobitatud teelõigu suunale. Kui kasutaja kõnnib ligikaudu teelõigu suunas, eeldatakse, et teelõigu tegelik suund on õige, ja seda kasutatakse neis arvutustes.

### Kokkuvõte
Kuigi esmapilgul on helimajakad lihtsad, lisab kaardile sobitamine, millega püüame asukoha- ja suunavigu kõrvaldada, üsna palju keerukust.

## Kaardiandmed
Rakenduse kasutatavad kaardiandmed pärinevad peaaegu täielikult OpenStreetMapi projektist. Meil on server, kus on kogu maailma kaart mitmel suumitasemel. Iga suumitase on jagatud paanideks. Tasemel 0 on 1 paan, tasemel 1 on 4 paani, tasemel 2 on 16 paani ja nii edasi kuni tasemeni 14, kus on kogu planeedi katmiseks umbes 268 miljonit paani. Igal paanil on mitu kihti ja igas kihis on punktid, jooned ja hulknurgad, millest saab joonistada graafilise kaardi. Seda graafilist kaarti näeb kasutaja rakenduse ekraanil. Igal punktil, joonel ja hulknurgal on metaandmed, mis kirjeldavad, mis see on. Need pärinevad enamasti otse OpenStreetMapi andmetest, nii et joon võib olla `footway`, mis on `sidewalk`, või `road`, mis on `minor`.

Andmed muudetakse graafiliseks kaardiks „stiili“ abil, millel on reeglid, kuidas igas kihis erinevaid punkte, jooni ja hulknurki joonistada, nt kuidas joonistada rada, metsa või bussipeatust. Reeglid võivad suumitasemeti erineda, mistõttu sisse suumides ilmub üha rohkem punkte ja jooni, mida välja suumides ei näe, nt bussipeatused ja rajad.

Stiili muutes saame muuta, kuidas kaart ekraanil välja näeb, ja sealt pärineb ka „ligipääsetav kaart“, mida katsetame. Selle eesmärk on suurem kontrast ning jämedamad jooned ja tekst. Stiil on rakendusse sisse ehitatud, nii et kaardi välimuse muutmiseks ei pea me serveris kaarti muutma.

Aga kuidas me kaardiandmeid heli jaoks kasutame?

### Kaardiandmete kasutamine heli jaoks
Heliliidese loomiseks kasutame praegu suhteliselt väikest osa kaardiandmetest. Peaaegu kogu heliliides kasutab ainult suurima suumitaseme paane. Rakendus ühendab kasutaja asukoha ümber 2 × 2 paanist koosneva ruudustiku ja vaatab ainult mõnda kihti:

* `transportation` - igat liiki teelõikude jaoks, sh teed, rajad, raudteed ja trammiteed.
* `poi` - huvipunktid, nt poed, spordikeskused, pingid, postkastid, bussipeatused jne.
* `building` - nende `poi` jaoks, mis on kaardistatud enama kui punktina, nt suured supermarketid või raekojad.

See ühendab jooned ja hulknurgad üle paanide piiride ning muudab kõik teelõigud ühendatud lõikudeks ja ristmikeks. See on oluline, sest nii saame otsida mööda teelõiku, kuhu me jõuda saame.

Kõik töödeldud andmed salvestatakse ka kergesti otsitavas vormingus, et rakendus leiaks hõlpsasti, millised kaardi objektid on läheduses. Selles etapis jagatakse andmed kategooriatesse. Praegused kategooriad on:

* Teed
* Teed ja rajad (kõik teelõigud)
* Ristmikud: punktid, kus teelõigud ristuvad
* Sissepääsud: hoone punktid, mis on märgitud sissepääsuks.
* Ülekäigud: teeületuskohad
* Huvipunktid: kõik huvipunktid
* Ühistranspordi peatused: bussipeatused, raudteejaamad, trammipeatused jms.
* Huvipunktide alamkategooriad:
  * Teave
  * Objekt
  * Koht
  * Vaatamisväärsus
  * Liikuvus
  * Ohutus
* Asulad ja alamkategooriad (vt järgmist jaotist)
  * Suurlinnad
  * Linnad
  * Külad
  * Asundused

Selle abil saab rakendus iga asukoha kohta hõlpsasti leida

* „Kõik ühistranspordi peatused 50 m raadiuses“ või
 * „Lähima ristmiku minu ees“ või
 * „Lähima asunduse, küla, linna või suurlinna“

Selle abil on häälteadete loomine vaid andmete päring praeguse asukoha ja suuna põhjal. Kui kasutaja liigub üle paaniruudustiku, uuendab rakendus seda nii, et see oleks praeguse asukoha ümber keskel.

### Rohkem andmeid
Meie väga kohaliku kaardiandmete ruudustiku üks probleem on see, et me „näeme“ igas suunas kõige rohkem umbes 1 km kaugusele. See sobib, kui kirjeldame, mis on meie ees, kuid mõnikord tahaksime anda rohkem konteksti. Peamine näide on see, kui rakendust kasutatakse ja kasutaja ei kõnni.

Kui rakendus tuvastab, et kasutaja liigub kiiremini kui 5 meetrit sekundis, muudab see maailma kirjeldamise viisi. Iga ristmiku ja huvipunkti teatamise asemel teatab see harvemini ja ainult lähedal olevatest teedest. Probleem on selles, et tee nimest pole suurt kasu, kui sa ei tea, millises asulas see on.

Selle lahendamiseks töötleme nüüd kaardiandmeid ka madalamal suumitasemel ja võtame andmed kihist `place`. See sisaldab linnade, suurlinnade, linnaosade, külade jms nimesid. Kaardistamise üks probleem on see, et nende kohtade vahel ei ole alati selget piiri. OpenStreetMapi andmebaasis on mõnikord linnapiirid, kuid isegi siis läheb see teave meie paanidest kaardini jõudes sageli kaduma. Meil on aga olemas asukoht, kuhu kohanimed kaardile joonistatakse. Need jagatakse kategooriatesse ja seejärel leiab rakendus kasutajale lähima asunduse, küla, linna või suurlinna ja teatab selle.

Paljude suurlinnade puhul ei teatata linna enda nime kunagi, sest enamik suurlinnu on jagatud väiksemateks osadeks, näiteks linnaosadeks, kuid need annavad lisakonteksti ja on väga kasulikud. Pea lihtsalt meeles: kui rakendus teatab, et oled teatud linnaosa tänava lähedal, tähendab see ainult seda, et selle linnaosa silt on lähim punkt. See võib olla ebatäpne või isegi teisel pool jõge.

### Rohkem konteksti
Mida rohkem konteksti saab kirjeldustesse lisada, seda parem, kui need jäävad lühikeseks ja etteaimatavaks. Üks probleem, mida ristmike kirjeldamisel nägime, oli see, et sageli olid seal „nimetud“ teelõigud. Need on teelõigud, millel pole nime. Kaardiandmetes võivad need olla lihtsalt pinnasetee, rada või teenindustee, kuid ilma lisakontekstita pole neist tekstikirjeldustes suurt kasu. Õnneks saame paremini: iga kord, kui rakendus hakkab nimetut teelõiku teatama, vaatab see, kas saab selle kohta rohkem konteksti välja selgitada.

* **Kas see on kõnnitee?**
Paljudes OpenStreetMapi piirkondades on kõnniteed nüüd teedest eraldi kaardistatud. Tavaliselt on need märgistatud kui `sidewalk`, kuid need ei ütle tavaliselt, millise tee kõnniteega on tegu.

Kui rakendus satub nimetule kõnniteele, otsib see tee, mis tema arvates selle kõrval kulgeb, ja kasutab seda kõnnitee nimetamiseks. See osutub meie häälteadete jaoks väga oluliseks. Iga kõnnitee ristmiku teatamise asemel tehakse kõnniteel liikudes häälteated nii, nagu liiguksime mööda sellega seotud teed. *„Liigud läände mööda rada“* asemel kuuleme *„Liigud läände mööda Moor Roadi“*. Kasutaja on kaardistatud kõnniteel, kuid kirjeldus on mõistlikum.

* **Kas see lõpeb nimega teelõigul?**
Väga sageli on jalgrajad, mis ühendavad kahte teed. Vaadates raja mõlemat otsa, saame selle konteksti hõlpsasti lisada: ühes suunas võib see olla *„Rada Moor Roadile“* ja teisest otsast lähenedes *„Rada Roselea Drive'ile“*. Seda tehakse ainult siis, kui rada ei hargne; kui see hargneb kaheks nimetuks rajaks, ei püüa me seda konteksti lisada.

* **Kas see lõpeb markeri lähedal?**
Kui nimetu teelõik algab või lõpeb markeri lähedal, kasutatakse seda selle kirjeldamiseks, nt *„Rada suure puu ristmikule“*. Kasutaja saab lisada markereid kuhu iganes ja radade võrgustiku äärde markereid lisades saab anda konteksti tervele teekonnale.

* **Kas see siseneb huvipunkti või väljub sellest?**
Kui nimetu teelõik algab huvipunktist väljaspool ja lõpeb selle sees (või vastupidi), saame selle konteksti lisada, nt *„Pinnasetee Lennox Parki“*.

* **Kas see lõpeb sissepääsu lähedal?**
Kui nimetu teelõik algab või lõpeb sissepääsule lähemal, saame selle konteksti lisada, nt *„Teenindustee Best Buy juurde“*.

* **Kas see lõpeb vaatamisväärsuse või koha lähedal?**
Kui nimetu teelõik algab või lõpeb vaatamisväärsusele lähemal, saame ka selle konteksti lisada, nt *„Teenindustee Püha Giles'i katedraali juurde“*.

* **Kas see on ummiktee?**
Rakendus märgib ummikteeks kõik nimetud teelõigud, mis kuhugi ei vii.

* **Kas see läheb üle silla, läbi tunneli või mööda treppe?**
Kui nimetu teelõik läheb üle silla, läbi tunneli või mööda treppe üles või alla, märgitakse see üles ja lisatakse konteksti. See on sihtkoha märkimisest eraldi, nii et võimalik on ka kontekst nagu *„Rada üle silla Lennox Parki“*.

Need kontekstid lisatakse järjekorras, nii et ühes suunas võib olla *„Rada Park Lane'ile mööda treppe“* ja teises *„Rada Lennox Parki mööda treppe“*. Pargist lahkudes on eelisõigus nimega tänaval, pargi sisenedes aga kasutatakse parki.

#### Tulevane kontekst
Tulevikus loodame lisada mitmesugust täiendavat konteksti, sealhulgas:
* Kontekst veekogude kallast järgivatele teelõikudele, nt *„Rada Dee jõe ääres“*
* Kontekst järvede ja veehoidlate serva järgivatele teelõikudele, nt *„Rada Milngavie veehoidla ääres“*
* Kontekst raudteed järgivatele teelõikudele, nt *„Rada raudtee ääres“*. See võiks sisaldada isegi raudteeliini nime
* Lisateave sildade ja tunnelite kohta, st mille kohal või all need on, nt *„Rada üle raudteesilla Moor Roadile“*

## Häälteated
Nüüd, kui kaardiandmed on meil kergesti kasutatavas vormingus, on häälteadete loomine tõesti üsna lihtne.

### Häälteated kõndides
Kõndides võivad kõlada järgmised häälteated (tähtsuse järjekorras):

1. Kui kaugel on praegune sihtkoht
1. Läheneva ristmiku kirjeldus
1. 5 lähima huvipunkti kirjeldus

Kõigi häälteadete sagedus on piiratud, et need liiga tihti ei korduks. Kui kasutaja peatub, häälteated lõpevad, ja ka liikudes ei korrata häälteadet iga uue GPS-i asukoha juures. Sagedus on sama mis iOS-i rakenduses:

* Iga 60 sekundi järel praeguse sihtkoha kohta
* Iga 30 sekundi järel läheneva ristmiku kohta
* Iga 60 sekundi järel huvipunkti kohta

Häälteateid saab filtreerida menüüs *Seadistused* ja seda käitumist saaks kindlasti laiendada.

### Häälteated kiiremini liikudes
Kiiremini kui 5 meetrit sekundis liikudes jätkub praeguse sihtkoha häälteade, kuid ristmike ja huvipunktide häälteated asendatakse häälteatega, mis kirjeldab ligikaudu, kus kasutaja on. See nimetab lähedal oleva ühistranspordi peatuse, huvipunkti, mille sees oleme (nt suur park), või lähedal oleva tee ja asula. Need kasutavad eespool kirjeldatud andmeid ja tulevikus on ilmselgelt võimalik lubada neid kohandada.

## Markerid ja marsruudid
Markerid ja marsruudid on enamasti lihtsalt kasutajaliidese funktsioon, mis ei sõltu ei GPS-ist ega tegelikult ka kaardiandmetest. Markerid on nimega kohad, mida kasutaja tahab salvestada, ja marsruudid on nende markerite järjestatud loendid. Mõlema loomise kasutajaliides on võetud otse iOS-i versioonist.

### Marsruudi esitamine
Marsruudi esitamisel ärkavad marsruudid ellu. Kui marsruuti esitatakse, luuakse marsruudi esimesele markerile helimajakas. Kui kasutaja jõuab selle markeri lähedale, liigutab marsruut helimajaka automaatselt järgmisele markerile. Kui rohkem markereid pole, lõpeb marsruudi esitamine.

## Kokkuvõte
Loodetavasti andis see ülevaate sellest, kuidas rakendus töötab. Rakendus areneb pidevalt kasutajate tagasiside põhjal, nii et võta meiega ühendust, kui arvad, et midagi võiks lisada.
