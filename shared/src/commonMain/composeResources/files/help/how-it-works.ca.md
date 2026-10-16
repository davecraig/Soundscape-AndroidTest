---
title: La tecnologia darrere de Soundscape
layout: page
parent: "Utilitzar Soundscape"
has_toc: false
nav_order: 200
lang: ca
permalink: /users/how-it-works.html
machine-translated: true
translated-from: 1d7f81a05233463dbc2eb8bf62ea26979f5114d0
---

# La tecnologia darrere de Soundscape
L'objectiu d'aquesta pàgina és donar una idea general de com funciona l'aplicació Soundscape per dins. No cal que la llegeixis per fer servir l'aplicació, però s'ha escrit per diversos motius:

1. Per ajudar els nouvinguts interessats a entendre on són els límits de l'aplicació
1. Per donar als usuaris una idea de què més es podria fer amb funcions noves
1. Per donar als desenvolupadors una visió general del funcionament de l'aplicació

Hi ha dues tecnologies que fan possible l'aplicació: el GPS i les dades d'OpenStreetMap. El GPS ens dona una bona idea d'on és el telèfon i d'on ha estat. Després, les dades d'OpenStreetMap serveixen per trobar què hi ha a prop i per descriure-ho a l'usuari.

## Balises sonores
En molts sentits, són el més senzill d'implementar des del punt de vista tecnològic. Si tenim la ubicació del telèfon i la direcció cap on apunta, podem modificar l'àudio de la balisa perquè soni com si vingués d'aquella direcció. Fem servir una biblioteca de Steam Audio per posicionar l'àudio, que utilitza funcions de transferència relatives al cap (HRTF) per aconseguir el posicionament que soni millor. L'única cosa més que fem és canviar l'àudio de la balisa perquè soni un so diferent segons l'angle entre la direcció de l'usuari i la ubicació de la balisa. Els angles varien segons la balisa triada (*Tàctil*, *Bengala*, *Ping*, etc.) i algunes tenen més sons que d'altres. I això són les balises sonores al nivell més senzill.

L'única complexitat addicional són les suposicions sobre la ubicació del telèfon i la direcció cap on apunta l'usuari. Vegem-les una per una.

### Ubicació
La ubicació que dona el GPS pot tenir un error força gran, i depèn de quanta part del cel veu el GPS del telèfon i de quants arbres i edificis alts reflecteixen el senyal GPS abans d'arribar al telèfon.

Per filtrar la ubicació fem servir el que es coneix com a ajust al mapa (map matching). Aquest mètode suposa que el més probable és que l'usuari es desplaci per un camí o una carretera del mapa; fem servir el terme «via» (en anglès, «Way») per a totes les carreteres, pistes i camins. L'ajust al mapa té en compte per on ha passat l'usuari i, amb la direcció del moviment i les dades del mapa de la zona, tria la ubicació més probable sobre una via. Aquest mètode no només compensa els errors del GPS, sinó també els errors de les dades del mapa. No totes les vies estan cartografiades amb precisió, i per tant també tenen errors. Per decidir per quina via és més probable que vagi l'usuari, l'algorisme té en compte:
* Com de prop és una via de la ubicació GPS i de les ubicacions GPS anteriors
* La direcció del desplaçament: si es mou en la mateixa direcció que la via
* Si és possible arribar des de l'última ubicació ajustada al mapa fins a la nova a través de la xarxa de vies. Això és necessari per descartar salts entre vies que en realitat no estan connectades, per exemple quan una passa per sobre d'una altra per un pont o per sota per un túnel.
L'ajust al mapa pot decidir que no hi ha cap via a prop, o que no està segur de per quina va l'usuari. En aquest cas, simplement espera la següent ubicació GPS i torna a provar-ho fins que n'està segur.

### Direcció
El programari segueix diverses direccions:

1. La direcció cap on apunta el telèfon. La fem servir quan el telèfon està desbloquejat i l'aplicació s'està fent servir, però també quan el telèfon està bloquejat, sempre que estigui pla amb la pantalla cap al cel. Val la pena tenir-ho en compte quan poses el telèfon a la bossa. Si el poses pla al fons d'una bossa que va dreta, l'aplicació faria servir la direcció a l'atzar cap on apunta la bossa.
1. La direcció cap on es desplaça el telèfon.
1. La direcció dels auriculars amb seguiment del cap. Ara mateix no la fem servir, tot i que l'aplicació d'iOS sí que la feia servir. Tenim la tecnologia a punt per afegir-la en el futur.

Quan el telèfon està bloquejat i dins d'una bossa, l'aplicació fa servir la direcció del desplaçament. Però si l'usuari no es mou, no hi ha cap direcció disponible. Quan passa això, les balises sonores baixen de volum per indicar que no es pot saber la direcció actual de la balisa: l'usuari podria estar girant-se sense canviar de lloc.

En alguns usos de la direcció, l'aplicació «enganxa» la direcció a la de la via ajustada al mapa. Així, si l'usuari camina més o menys en la direcció de la via, se suposa que la direcció real de la via és la correcta i és la que es fa servir en aquests càlculs.

### Conclusió
Tot i que a primera vista les balises sonores són senzilles, l'ús de l'ajust al mapa per intentar eliminar els errors d'ubicació i de direcció hi afegeix força complexitat.

## Dades del mapa
Gairebé totes les dades del mapa que fa servir l'aplicació provenen del projecte OpenStreetMap. Tenim un servidor amb un mapa de tot el món a diversos nivells de zoom. Cada nivell de zoom es divideix en tessel·les. El nivell 0 té 1 tessel·la, el nivell 1 en té 4, el nivell 2 en té 16, i així successivament fins al nivell 14, que en té uns 268 milions per cobrir tot el planeta. Cada tessel·la té diverses capes, i cada capa té punts, línies i polígons que es poden dibuixar per fer un mapa gràfic. Aquest mapa gràfic és el que l'aplicació mostra a l'usuari a la pantalla. Cada punt, línia i polígon té metadades que descriuen què és. Gairebé tot ve directament de les dades d'OpenStreetMap, de manera que una línia pot ser un `footway` que és un `sidewalk`, o una `road` que és `minor`.

Les dades es converteixen en el mapa gràfic mitjançant un «estil», que té regles per dibuixar els diferents punts, línies i polígons de cada capa: per exemple, com dibuixar un camí, un bosc o una parada d'autobús. Les regles poden variar segons el nivell de zoom, i per això, quan amplies el mapa, apareixen cada vegada més punts i línies que no es veuen quan està allunyat, com ara parades d'autobús i camins.

Si modifiquem l'estil, podem canviar l'aspecte del mapa a la pantalla, i d'aquí ve el «mapa accessible» que estem provant. Vol tenir més contrast i línies i text més gruixuts. L'estil està integrat a l'aplicació, de manera que no cal canviar el mapa del servidor per canviar-ne l'aspecte.

Però com fem servir les dades del mapa per a l'àudio?

### Ús de les dades del mapa per a l'àudio
Ara com ara fem servir una part relativament petita de les dades del mapa per generar la interfície d'àudio. Gairebé tota la interfície d'àudio fa servir només les tessel·les del nivell de zoom màxim. L'aplicació uneix una quadrícula de 2 per 2 tessel·les al voltant d'on és l'usuari i només mira unes quantes capes:

* `transportation` - per a tota mena de vies, incloses carreteres, camins, vies de tren i de tramvia.
* `poi` - punts d'interès, per exemple botigues, centres esportius, bancs, bústies, parades d'autobús, etc.
* `building` - per als `poi` que estan cartografiats com alguna cosa més que un punt, per exemple supermercats grans o ajuntaments.

Uneix les línies i els polígons a través dels límits de les tessel·les i converteix totes les vies en trams de via i cruïlles connectats. Això és important perquè ens permet buscar al llarg d'una via per saber on podem arribar.

Totes les dades processades també es desen en un format fàcil de consultar, perquè l'aplicació pugui trobar fàcilment quins elements del mapa són a prop. En aquest punt les dades es classifiquen en categories. Les categories actuals són:

* Carreteres
* Carreteres i camins (totes les vies)
* Cruïlles: els punts on es creuen les vies
* Entrades: punts d'un edifici marcats com a entrada.
* Passos: passos per creuar la carretera
* Punts d'interès: tots els punts d'interès
* Parades de transport públic: parades d'autobús, estacions de tren, parades de tramvia, etc.
* Subcategories dels punts d'interès:
  * Informació
  * Objecte
  * Lloc
  * Punt de referència
  * Mobilitat
  * Seguretat
* Nuclis de població i subcategories (vegeu la secció següent)
  * Ciutats
  * Viles
  * Pobles
  * Llogarets

Amb tot això, per a qualsevol ubicació l'aplicació pot trobar fàcilment

* «Totes les parades de transport públic a menys de 50 m» o
 * «La cruïlla més propera davant meu» o
 * «El llogaret, poble, vila o ciutat més propers»

Amb això, crear els avisos de veu només és qüestió de consultar les dades segons la ubicació i la direcció actuals. A mesura que l'usuari es mou per la quadrícula de tessel·les, l'aplicació l'actualitza perquè quedi centrada al voltant de la ubicació actual.

### Més dades
Un dels problemes de la nostra quadrícula de dades tan local és que només podem «veure» com a màxim uns 1 km en qualsevol direcció. Això està bé per descriure el que tenim al davant, però de vegades voldríem donar més context. L'exemple principal és quan es fa servir l'aplicació i l'usuari no camina.

Quan l'aplicació detecta que l'usuari es desplaça a més de 5 metres per segon, canvia la manera de descriure el món. En lloc d'anunciar cada cruïlla i cada punt d'interès, fa avisos menys sovint i només de les carreteres properes. El problema és que saber el nom d'una carretera no serveix de gaire si no saps en quina població és.

Per intentar resoldre-ho, ara també processem les dades del mapa a un nivell de zoom més baix i n'extraiem les dades de la capa `place`. Aquesta capa conté els noms de viles, ciutats, barris, pobles, etc. Un problema a l'hora de cartografiar és que no sempre hi ha un límit clar entre aquests llocs. De vegades OpenStreetMap té els límits de les ciutats a la base de dades, però fins i tot llavors, quan arriben al nostre mapa en tessel·les, sovint aquesta informació s'ha perdut. El que sí que tenim és la ubicació on es dibuixen els noms dels llocs al mapa. Es classifiquen i després l'aplicació troba el llogaret, poble, vila o ciutat més proper a l'usuari i l'anuncia.

En moltes ciutats el nom de la ciutat no s'anunciarà mai, perquè la majoria de ciutats es divideixen en parts més petites, com ara barris, però aquests sí que donen context addicional i són molt útils. Només cal recordar que, si l'aplicació diu que ets a prop d'un carrer d'un barri concret, això només vol dir que l'etiqueta d'aquell barri és el punt més proper, i pot ser incorrecta o fins i tot ser a l'altra banda d'un riu.

### Més context
Com més context es pugui afegir a les descripcions, millor, sempre que siguin breus i previsibles. Un dels problemes que vam veure en descriure les cruïlles és que sovint hi havia vies «sense nom». Són vies que no tenen nom. A les dades del mapa poden ser només una pista, un camí o un carrer de servei, però sense més context no són gaire útils a les descripcions. Per sort, podem fer-ho millor: cada vegada que l'aplicació està a punt d'anunciar una via sense nom, mira si pot esbrinar-ne més context.

* **És una vorera?**
Moltes zones d'OpenStreetMap ara tenen les voreres cartografiades per separat de les carreteres. Normalment estan etiquetades com a `sidewalk`, però no solen dir de quina carretera són la vorera.

Quan l'aplicació troba una vorera sense nom, busca una carretera que cregui que hi va al costat i en fa servir el nom per anomenar la vorera. Això és molt important per als nostres avisos de veu. En lloc d'anunciar cada cruïlla de la vorera, mentre avancem per una vorera els avisos es fan com si avancéssim per la carretera associada. En lloc de *«Avançant cap a l'oest per un camí»* tenim *«Avançant cap a l'oest per Moor Road»*. L'usuari és a la vorera cartografiada, però la descripció té més sentit.

* **Acaba en una via amb nom?**
Molt sovint hi ha camins per a vianants que uneixen dues carreteres. Si mirem els dos extrems del camí, podem afegir fàcilment aquest context: en una direcció pot ser *«Camí cap a Moor Road»* i, arribant per l'altre extrem, *«Camí cap a Roselea Drive»*. Només ho fem quan el camí no es divideix; si es divideix en dos camins sense nom, no intentem afegir aquest context.

* **Acaba a prop d'un marcador?**
Si una via sense nom comença o acaba a prop d'un marcador, es fa servir per descriure-la, per exemple *«Camí cap a la cruïlla de l'arbre gran»*. L'usuari pot afegir marcadors on vulgui, i si n'afegeix al llarg d'una xarxa de camins pot donar context a tot un recorregut.

* **Entra o surt d'un punt d'interès?**
Si una via sense nom comença fora d'un punt d'interès i acaba a dins (o a l'inrevés), podem afegir aquest context, per exemple *«Pista cap a Lennox Park»*.

* **Acaba a prop d'una entrada?**
Si una via sense nom comença o acaba a prop d'una entrada, podem afegir aquest context, per exemple *«Carrer de servei cap a Best Buy»*.

* **Acaba a prop d'un punt de referència o d'un lloc?**
Si una via sense nom comença o acaba a prop d'un punt de referència, també podem afegir aquest context, per exemple *«Carrer de servei cap a la catedral de St Giles»*.

* **És un carrer sense sortida?**
L'aplicació marca com a carrer sense sortida qualsevol via sense nom que no porti enlloc.

* **Passa per un pont, un túnel o unes escales?**
Si la via sense nom passa per un pont, per un túnel o puja o baixa unes escales, es té en compte i s'afegeix al context. Això és independent de la destinació, de manera que és possible un context com *«Camí per un pont cap a Lennox Park»*.

Aquests contextos s'afegeixen per ordre, i per tant és possible tenir *«Camí cap a Park Lane per unes escales»* en una direcció i *«Camí cap a Lennox Park per unes escales»* en l'altra. El carrer amb nom té prioritat en sortir del parc, però s'utilitza el parc en entrar-hi.

#### Context futur
Hi ha diversos contextos més que esperem afegir en el futur, com ara:
* Context per a vies que segueixen elements d'aigua lineals, per exemple *«Camí al costat del riu Dee»*
* Context per a vies que segueixen la vora de masses d'aigua, per exemple *«Camí al costat de l'embassament de Milngavie»*
* Context per a vies que segueixen vies de tren, per exemple *«Camí al costat de la via del tren»*. Fins i tot podria incloure el nom de la línia de tren
* Afegir més contingut als ponts i túnels, per sobre o per sota de què passen, per exemple *«Camí per un pont sobre la via del tren cap a Moor Road»*

## Avisos de veu
Ara que tenim les dades del mapa en un format fàcil de fer servir, generar els avisos de veu és realment força senzill.

### Avisos de veu quan camines
Quan camines, els avisos de veu que es poden fer són (per ordre de prioritat):

1. Dir a quina distància és la destinació actual
1. Descriure una cruïlla que s'acosta
1. Descriure els 5 punts d'interès més propers

Tots els avisos de veu tenen un límit de freqüència perquè no es repeteixin massa sovint. Si l'usuari deixa de moure's, els avisos s'aturen, i fins i tot en moviment un avís no es repeteix amb cada nova ubicació GPS. La freqüència, com a l'aplicació d'iOS, és:

* Cada 60 segons per a la destinació actual
* Cada 30 segons per a una cruïlla que s'acosta
* Cada 60 segons per a un punt d'interès

Els avisos de veu es poden filtrar des del menú *Configuració*, i hi ha marge per ampliar aquest comportament.

### Avisos de veu quan et desplaces més de pressa
Quan et desplaces a més de 5 metres per segon, l'avís de la destinació actual continua, però els avisos de cruïlles i de punts d'interès se substitueixen per un avís que descriu més o menys on és l'usuari. Indica una parada de transport públic propera, un punt d'interès que ens conté (per exemple, si som dins d'un parc gran) o una carretera i una població properes. Fan servir les dades descrites abans, i és clar que en el futur es podria permetre personalitzar-ho.

## Marcadors i rutes
En general, els marcadors i les rutes són només una funció de la interfície d'usuari que no depèn ni del GPS ni realment de les dades del mapa. Els marcadors són llocs amb nom que l'usuari vol desar, i les rutes són una llista ordenada d'aquests marcadors. La interfície per crear-los tots dos està presa directament de la versió d'iOS.

### Reproducció de rutes
En reproduir una ruta és quan les rutes cobren vida. Quan es reprodueix una ruta, es crea una balisa sonora al primer marcador de la ruta. Quan l'usuari s'acosta a aquell marcador, la ruta mou automàticament la balisa sonora al marcador següent. Si no hi ha més marcadors, la reproducció de la ruta s'acaba.

## Conclusió
Esperem que això t'hagi donat una idea de com funciona l'aplicació. L'aplicació evoluciona contínuament a partir dels comentaris dels usuaris, així que posa't en contacte amb nosaltres si creus que es podria afegir alguna cosa.
