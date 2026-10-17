---
title: Teknolojia Inayowezesha Soundscape
layout: page
parent: "Kutumia Soundscape"
has_toc: false
nav_order: 200
lang: sw
permalink: /users/how-it-works.html
machine-translated: true
translated-from: adca9a12417b9862100cb0f837baedb969eded43
---

# Teknolojia Inayowezesha Soundscape
Lengo la ukurasa huu ni kukupa uelewa wa jumla wa jinsi programu ya Soundscape inavyofanya kazi ndani yake. Huhitaji kuusoma ili kutumia programu, lakini umeandikwa kwa sababu kadhaa:

1. Kuwasaidia wageni wanaovutiwa na programu kuelewa mipaka yake iko wapi
1. Kuwapa watumiaji wazo la mambo mengine yanayowezekana kwa vipengele vipya
1. Kuwapa wasanidi programu muhtasari wa jinsi programu inavyofanya kazi

Kuna teknolojia mbili zinazowezesha programu hii: GPS na data ya OpenStreetMap. GPS inatupa wazo zuri la mahali simu ilipo, na ilipokuwa. Kisha data ya OpenStreetMap inatumika kutafuta kilicho karibu, na tunaweza kuitumia kumweleza mtumiaji.

## Beacon za sauti
Kwa njia nyingi hizi ndizo rahisi zaidi kutekeleza kiteknolojia. Tukijua mahali simu ilipo, na upande ambao simu inaelekea, tunaweza kubadilisha sauti ya beacon ili isikike kana kwamba inatoka upande huo. Tunatumia maktaba ya Steam Audio kuweka sauti mahali pake, ambayo hutumia vipengele vya uhamishaji vinavyohusiana na kichwa (HRTF) ili sauti isikike vizuri iwezekanavyo mahali ilipo. Jambo jingine pekee tunalofanya ni kubadilisha sauti ya beacon ili sauti tofauti ichezwe kulingana na pembe kati ya upande anaoelekea mtumiaji na mahali beacon ilipo. Pembe hutofautiana kulingana na beacon iliyochaguliwa (*Mgusano*, *Mwako*, *Ping* n.k.) na baadhi zina sauti nyingi kuliko nyingine. Hizo ndizo beacon za sauti katika kiwango rahisi kabisa.

Ugumu wa ziada pekee ni dhana kuhusu mahali simu ilipo na upande anaoelekea mtumiaji. Hebu tuziangalie moja baada ya nyingine.

### Mahali
Mahali panapotolewa na GPS panaweza kuwa na kosa kubwa kiasi. Hilo hutegemea ni kiasi gani cha anga kinachoonekana na GPS ya simu, na ni miti mingapi na majengo marefu mangapi yanayoakisi ishara ya GPS ikiwa njiani kuelekea kwenye simu.

Ili kuchuja mahali, tunatumia mbinu inayojulikana kama kulinganisha na ramani (map matching). Mbinu hii inadhani kwamba mtumiaji kuna uwezekano mkubwa anasafiri kwenye kijia au barabara iliyo kwenye ramani; tunatumia neno "njia" (kwa Kiingereza "Way") kujumuisha barabara, vijia vya magari na vijia vyote. Kulinganisha na ramani huangalia mtumiaji amepita wapi, na kwa kutumia upande wa mwendo pamoja na data ya ramani ya eneo hilo huchagua mahali panapowezekana zaidi kwenye njia fulani. Mbinu hii hairekebishi tu makosa ya GPS, bali pia makosa katika data ya ramani. Si njia zote zimechorwa kwa usahihi, kwa hiyo nazo zina makosa. Ili kubaini njia ambayo mtumiaji ana uwezekano mkubwa wa kuwa juu yake, mfumo huzingatia:
* Njia iko karibu kiasi gani na mahali pa GPS pa sasa na pa awali
* Upande wa safari: je, mtumiaji anaenda upande uleule wa njia
* Je, inawezekana kufika kutoka mahali pa mwisho palipolinganishwa na ramani hadi mahali papya kupitia mtandao wa njia. Hili linahitajika ili kuzuia kuruka kati ya njia ambazo kwa kweli hazijaunganishwa, k.m. moja inapita juu ya nyingine kwa daraja, au chini yake kwa handaki.
Kulinganisha na ramani kunaweza kuamua kwamba hakuna njia karibu, au hakina uhakika mtumiaji yuko kwenye njia ipi. Hapo husubiri tu mahali pa GPS panapofuata na kujaribu tena hadi kiwe na uhakika.

### Upande
Kuna pande kadhaa ambazo programu inafuatilia:

1. Upande ambao simu inaelekea. Tunautumia simu ikiwa haijafungwa na programu inatumika, lakini pia simu ikiwa imefungwa, mradi imeshikwa bapa huku skrini ikiangalia angani. Ni vizuri kukumbuka hili unapoweka simu kwenye mkoba. Ukiiweka bapa chini ya mkoba ulioshikwa wima, programu itatumia upande wowote ambao mkoba unaelekea kwa bahati.
1. Upande ambao simu inasafiri.
1. Upande unaotoka kwenye vipokea sauti vinavyofuatilia kichwa. Kwa sasa hatuutumii, ingawa programu ya iOS iliuwezesha. Tuna teknolojia tayari ya kuuongeza siku zijazo.

Simu ikiwa imefungwa na iko kwenye mkoba, programu itatumia upande wa safari. Hata hivyo, mtumiaji asipokuwa anatembea basi hakuna upande unaopatikana. Hili likitokea, beacon za sauti hupunguza sauti kuonyesha kwamba haiwezekani kujua upande wa sasa wa beacon: mtumiaji anaweza kuwa anageuka bila kubadilisha mahali.

Kwa baadhi ya matumizi ya upande katika programu, upande "hunyooshwa" kufuata upande wa njia iliyolinganishwa na ramani. Kwa hiyo mtumiaji akitembea takriban upande wa njia, upande halisi wa njia huchukuliwa kuwa sahihi na kutumika katika hesabu hizo.

### Hitimisho
Ingawa kwa juu juu beacon za sauti zinaonekana rahisi, kutumia kulinganisha na ramani ili kujaribu kuondoa makosa ya mahali na upande kunaleta ugumu mwingi kiasi.

## Data ya ramani
Data ya ramani inayotumiwa na programu karibu yote inatoka kwenye mradi wa OpenStreetMap. Tunaendesha seva yenye ramani ya dunia nzima katika viwango kadhaa vya ukuzaji. Kila kiwango cha ukuzaji kimegawanywa katika vigae. Kiwango 0 kina kigae 1, kiwango 1 kina vigae 4, kiwango 2 kina vigae 16 na kuendelea hivyo hadi kiwango 14 ambacho kina takriban vigae milioni 268 kufunika sayari nzima. Kila kigae kina tabaka kadhaa na kila tabaka lina nukta, mistari na poligoni zinazoweza kuchorwa kutengeneza ramani ya picha. Ramani hiyo ya picha ndiyo anayoonyeshwa mtumiaji kwenye skrini ya programu. Kila nukta, mstari na poligoni ina metadata inayoeleza ni nini. Kwa kiasi kikubwa hii inatoka moja kwa moja kwenye data ya OpenStreetMap, kwa hiyo mstari unaweza kuwa `footway` ambayo ni `sidewalk`, au `road` ambayo ni `minor`.

Data hubadilishwa kuwa ramani ya picha kupitia "mtindo" wenye kanuni za jinsi ya kuchora nukta, mistari na poligoni mbalimbali katika kila tabaka, k.m. jinsi ya kuchora kijia, msitu au kituo cha basi. Kanuni zinaweza kutofautiana kulingana na kiwango cha ukuzaji, ndiyo sababu unapokuza, nukta na mistari mingi zaidi huonekana ambazo hazionekani ramani ikiwa imepunguzwa, k.m. vituo vya basi na vijia.

Kwa kubadilisha mtindo tunaweza kubadilisha jinsi ramani inavyoonekana kwenye skrini, na hapo ndipo "ramani inayofikika" tunayoijaribu inapotoka. Lengo lake ni kuwa na utofautishaji mkubwa zaidi na mistari na maandishi mazito zaidi. Mtindo umejengwa ndani ya programu, kwa hiyo hatuhitaji kubadilisha ramani kwenye seva ili kubadilisha mwonekano wake.

Lakini tunatumiaje data ya ramani kwa sauti?

### Kutumia data ya ramani kwa sauti
Kwa sasa tunatumia sehemu ndogo kiasi ya data ya ramani kutengeneza kiolesura cha sauti. Karibu kiolesura chote cha sauti kinatumia tu vigae vya kiwango cha juu kabisa cha ukuzaji. Programu inaunganisha gridi ya vigae 2 kwa 2 kuzunguka mahali alipo mtumiaji kisha inaangalia tabaka chache tu:

* `transportation` - kwa aina zote za njia ikiwemo barabara, vijia, reli na njia za tramu.
* `poi` - maeneo ya kuvutia k.m. maduka, vituo vya michezo, mabenchi, masanduku ya posta, vituo vya basi n.k.
* `building` - kwa `poi` ambazo zimechorwa kama zaidi ya nukta tu, k.m. maduka makubwa au kumbi za mji.

Inaunganisha mistari na poligoni kuvuka mipaka ya vigae na kugeuza njia zote kuwa vipande vya njia na makutano vilivyounganishwa. Hili ni muhimu kwa sababu linatuwezesha kutafuta kwenye njia ili kujua tunaweza kufika wapi.

Data yote iliyochakatwa pia huwekwa katika muundo rahisi kutafuta ili programu iweze kupata kwa urahisi vitu vya ramani vilivyo karibu. Katika hatua hii data huwekwa katika makundi. Makundi ya sasa ni:

* Barabara
* Barabara na vijia (njia zote)
* Makutano: sehemu ambapo njia zinakatana
* Milango ya kuingilia: sehemu kwenye jengo zilizowekwa alama kama mlango wa kuingilia.
* Vivuko: sehemu za kuvuka barabara
* Maeneo ya kuvutia: maeneo yote ya kuvutia
* Vituo vya usafiri wa umma: vituo vya basi, stesheni za treni, vituo vya tramu na kadhalika.
* Makundi madogo ya maeneo ya kuvutia:
  * Taarifa
  * Kitu
  * Mahali
  * Kivutio
  * Uhamaji
  * Usalama
* Makazi na makundi madogo (tazama sehemu inayofuata)
  * Majiji
  * Miji
  * Vijiji
  * Vitongoji

Kwa hili, kwa mahali popote programu inaweza kupata kwa urahisi

* "Vituo vyote vya usafiri wa umma ndani ya mita 50" au
 * "Makutano yaliyo karibu zaidi mbele yangu" au
 * "Kitongoji, kijiji, mji au jiji lililo karibu zaidi"

Kwa hili, kutengeneza matangazo ya sauti ni suala la kuuliza data kulingana na mahali na upande wa sasa. Mtumiaji anapopita kwenye gridi ya vigae, programu huisasisha ili iwe katikati kuzunguka mahali pa sasa.

### Data zaidi
Mojawapo ya matatizo ya gridi yetu ya data ya eneo dogo sana ni kwamba tunaweza "kuona" hadi takriban kilomita 1 tu kwa upande wowote. Hiyo ni sawa tunapoeleza kilicho mbele yetu, lakini wakati mwingine tungependa kutoa muktadha zaidi. Mfano mkuu ni programu inapotumika na mtumiaji hatembei kwa miguu.

Programu inapogundua kwamba mtumiaji anasafiri kwa kasi zaidi ya mita 5 kwa sekunde, hubadilisha jinsi inavyoueleza ulimwengu. Badala ya kutangaza kila makutano na eneo la kuvutia, hutangaza mara chache zaidi na barabara za karibu tu. Tatizo ni kwamba kujua jina la barabara si msaada mkubwa kama hujui iko katika mji gani.

Ili kushughulikia hili, sasa pia tunachakata data ya ramani katika kiwango cha chini cha ukuzaji na kutoa data kutoka kwenye tabaka la `place`. Tabaka hili lina majina ya miji, majiji, mitaa, vijiji na kadhalika. Tatizo moja la kuchora ramani ni kwamba si mara zote kuna mpaka ulio wazi kati ya maeneo haya. Wakati mwingine OpenStreetMap ina mipaka ya majiji katika hifadhidata yake, lakini hata hivyo kufikia wakati inapofika kwenye ramani yetu ya vigae taarifa hiyo mara nyingi hupotea. Tulicho nacho ni mahali ambapo majina ya maeneo yanachorwa kwenye ramani. Haya huwekwa katika makundi kisha programu hupata kitongoji, kijiji, mji au jiji lililo karibu zaidi na mtumiaji na kulitangaza.

Kwa majiji mengi jina la jiji lenyewe halitatangazwa kamwe, kwa sababu majiji mengi yamegawanywa katika sehemu ndogo kama mitaa, lakini hizo hutoa muktadha wa ziada na ni muhimu sana. Kumbuka tu kwamba programu inaposema uko karibu na barabara katika mtaa fulani, hiyo inamaanisha tu kwamba lebo ya mtaa huo ndiyo nukta iliyo karibu zaidi, na inaweza kuwa si sahihi au hata iko ng'ambo ya mto.

### Muktadha zaidi
Kadiri muktadha zaidi unavyoweza kuongezwa katika maelezo ndivyo bora, mradi yabaki mafupi na yanayotabirika. Mojawapo ya matatizo tuliyoona tulipoeleza makutano ni kwamba mara nyingi kulikuwa na njia "zisizo na jina". Hizi ni njia ambazo hazina jina. Katika data ya ramani zinaweza kuwa tu barabara ya vumbi, kijia au barabara ya huduma, lakini bila muktadha zaidi hazisaidii sana katika maelezo. Kwa bahati nzuri, tunaweza kufanya vizuri zaidi: kila programu inapokaribia kutangaza njia isiyo na jina, huangalia kama inaweza kupata muktadha zaidi kwa ajili yake.

* **Je, ni njia ya watembea kwa miguu kando ya barabara?**
Maeneo mengi ya OpenStreetMap sasa yana njia za kando ya barabara zilizochorwa tofauti na barabara. Kwa kawaida hizi huwekwa lebo `sidewalk` lakini kwa kawaida hazisemi ni za kando ya barabara ipi.

Programu inapokutana na njia ya kando isiyo na jina, hutafuta barabara inayodhani inapita kando yake na kuitumia kuipa jina njia hiyo ya kando. Hili limeonekana kuwa muhimu sana kwa matangazo yetu ya sauti. Badala ya kutangaza kila makutano ya njia ya kando, tunapotembea kando ya barabara matangazo hutolewa kana kwamba tunatembea kwenye barabara husika. Badala ya *"Unasafiri magharibi kwenye kijia"* tunasikia *"Unasafiri magharibi kwenye Moor Road"*. Mtumiaji yuko kwenye njia ya kando iliyochorwa, lakini maelezo yanaeleweka zaidi.

* **Je, inaishia kwenye njia yenye jina?**
Mara nyingi sana kuna vijia vya watembea kwa miguu vinavyounganisha barabara mbili. Kwa kuangalia ncha zote mbili za kijia tunaweza kuongeza muktadha huo kwa urahisi, kwa hiyo upande mmoja inaweza kuwa *"Kijia kuelekea Moor Road"* na ukikaribia kutoka ncha nyingine inaweza kuwa *"Kijia kuelekea Roselea Drive"*. Hili hufanywa tu pale ambapo kijia hakigawanyiki; kikigawanyika kuwa vijia viwili visivyo na jina basi hatujaribu kuongeza muktadha huu.

* **Je, inaishia karibu na alama?**
Njia isiyo na jina ikianzia au ikiishia karibu na alama, alama hiyo hutumika kuieleza, k.m. *"Kijia kuelekea makutano ya mti mkubwa"*. Mtumiaji anaweza kuongeza alama popote anapotaka, na kwa kuongeza alama kando ya mitandao ya vijia anaweza kuongeza muktadha kwa safari nzima.

* **Je, inaingia au kutoka kwenye eneo la kuvutia?**
Njia isiyo na jina ikianzia nje ya eneo la kuvutia na kuishia ndani yake (au kinyume chake) tunaweza kuongeza muktadha huo, k.m. *"Barabara ya vumbi kuelekea Lennox Park"*.

* **Je, inaishia karibu na mlango wa kuingilia?**
Njia isiyo na jina ikianzia au ikiishia karibu zaidi na mlango wa kuingilia tunaweza kuongeza muktadha huo, k.m. *"Barabara ya huduma kuelekea Best Buy"*.

* **Je, inaishia karibu na kivutio au mahali?**
Njia isiyo na jina ikianzia au ikiishia karibu zaidi na kivutio tunaweza pia kuongeza muktadha huo, k.m. *"Barabara ya huduma kuelekea Kanisa Kuu la St Giles"*.

* **Je, ni mwisho wa barabara?**
Programu huweka alama ya mwisho wa barabara kwenye njia zote zisizo na jina ambazo haziendi popote.

* **Je, inapita juu ya daraja, ndani ya handaki au kwenye ngazi?**
Njia isiyo na jina ikipita juu ya daraja, ndani ya handaki au kupanda/kushuka ngazi basi hili hurekodiwa na kuongezwa kwenye muktadha. Hili ni tofauti na kutaja mahali inapoelekea, kwa hiyo muktadha kama *"Kijia juu ya daraja kuelekea Lennox Park"* unawezekana.

Miktadha hii huongezwa kwa mpangilio, kwa hiyo inawezekana kuwa na *"Kijia kuelekea Park Lane kupitia ngazi"* upande mmoja na *"Kijia kuelekea Lennox Park kupitia ngazi"* upande mwingine. Barabara yenye jina hupewa kipaumbele unapotoka kwenye bustani, lakini bustani hutumika unapoingia.

#### Muktadha wa siku zijazo
Kuna miktadha mbalimbali ya ziada tunayotumaini kuongeza siku zijazo ikiwemo:
* Muktadha kwa njia zinazofuata vijito na mito, k.m. *"Kijia kando ya Mto Dee"*
* Muktadha kwa njia zinazofuata ukingo wa maziwa na mabwawa, k.m. *"Kijia kando ya bwawa la Milngavie"*
* Muktadha kwa njia zinazofuata reli, k.m. *"Kijia kando ya reli"*. Hili linaweza hata kujumuisha jina la njia ya reli
* Kuongeza maelezo zaidi kuhusu madaraja na mahandaki, yanapita juu au chini ya nini, k.m. *"Kijia kupitia daraja juu ya reli kuelekea Moor Road"*

## Matangazo ya sauti
Sasa kwa kuwa tuna data ya ramani katika muundo tunaoweza kutumia kwa urahisi, kutengeneza matangazo ya sauti ni rahisi kiasi.

### Matangazo unapotembea
Unapotembea, matangazo yanayoweza kutokea ni (kwa mpangilio wa kipaumbele):

1. Kueleza mahali unapoelekea sasa pako umbali gani
1. Kueleza makutano yanayokaribia
1. Kueleza maeneo 5 ya kuvutia yaliyo karibu zaidi

Matangazo yote yamewekewa kikomo ili yasirudiwe mara nyingi mno. Mtumiaji akiacha kusonga basi matangazo yatasimama, na hata akiwa anasonga tangazo halitarudiwa kwa kila mahali papya pa GPS. Marudio, kama ilivyo kwenye programu ya iOS, ni:

* Kila sekunde 60 kwa mahali unapoelekea sasa
* Kila sekunde 30 kwa makutano yanayokaribia
* Kila sekunde 60 kwa eneo la kuvutia

Matangazo yanaweza kuchujwa kupitia menyu ya *Mipangilio*, na hakika kuna nafasi ya kupanua tabia hii.

### Matangazo unaposafiri kwa kasi zaidi
Unaposafiri kwa kasi zaidi ya mita 5 kwa sekunde, tangazo la mahali unapoelekea sasa bado hutolewa, lakini matangazo ya makutano na maeneo ya kuvutia hubadilishwa na tangazo linaloeleza takriban mahali mtumiaji alipo. Linataja kituo cha usafiri wa umma kilicho karibu, eneo la kuvutia tulilomo ndani yake (k.m. ndani ya bustani kubwa), au barabara na makazi yaliyo karibu. Haya yanatumia data iliyoelezwa awali, na ni wazi kuna nafasi ya kuruhusu yabadilishwe kulingana na mapendeleo siku zijazo.

## Alama na Njia
Kwa sehemu kubwa alama na Njia ni kipengele cha kiolesura tu ambacho hakitegemei GPS wala kwa kweli data ya ramani. Alama ni mahali palipopewa jina ambapo mtumiaji anataka kuhifadhi, na Njia ni orodha zilizopangwa za alama hizo. Kiolesura cha kutengeneza vyote viwili kimechukuliwa moja kwa moja kutoka kwenye toleo la iOS.

### Kucheza Njia
Njia huwa hai zinapochezwa. Njia inapochezwa, beacon ya sauti huwekwa kwenye alama ya kwanza ya Njia. Mtumiaji anapokaribia alama hiyo, Njia husogeza beacon ya sauti kiotomatiki hadi alama inayofuata. Kama hakuna alama zaidi, uchezaji wa Njia unaisha.

## Hitimisho
Tunatumaini kwamba hili limekupa ufahamu wa jinsi programu inavyofanya kazi. Programu inaendelea kuboreshwa kulingana na maoni ya watumiaji, kwa hiyo tafadhali wasiliana nasi kama unadhani kuna kitu kinachoweza kuongezwa.
