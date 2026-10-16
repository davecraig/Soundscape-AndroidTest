---
title: GPS-pontosság
layout: page
parent: "A Soundscape használata"
has_toc: false
nav_order: 150
lang: hu
permalink: /users/help-gps-accuracy.html
translated-from: cf9d1bc3ba5aaac2a0ffceb928eff90a1878e910
---

# GPS-pontosság

## GPS-pontosság

A Soundscape csak olyan pontosan tudja leírni a környezetét, amilyen pontosan a telefonja tudja, hol van. A 10 méteres vagy annál jobb pontosság jó. 20 méterig elfogadható. Ennél rosszabb pontosságnál a bemondások az utca rossz oldalát nevezhetik meg, vagy későn érkezhetnek.

A pontosság általában épületben, magas épületek között és a szabadba lépés utáni első percben a legrosszabb. Ha az alábbi érték akkor is gyenge marad, amikor a szabadban, nyílt terepen van, kapcsolja ki, majd újra be a telefon helymeghatározási szolgáltatását.

{% if app %}
Jelenlegi pontosság: {{ accuracy }} ({{ quality }})
{% endif %}
