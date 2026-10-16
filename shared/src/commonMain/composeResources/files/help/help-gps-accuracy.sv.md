---
title: GPS-noggrannhet
layout: page
parent: "Använda Soundscape"
has_toc: false
nav_order: 150
lang: sv
permalink: /users/help-gps-accuracy.html
translated-from: cf9d1bc3ba5aaac2a0ffceb928eff90a1878e910
---

# GPS-noggrannhet

## GPS-noggrannhet

Soundscape kan bara beskriva din omgivning så bra som telefonen vet var du är. En noggrannhet på 10 meter eller bättre är bra. Upp till 20 meter är okej. Över det kan informationsljud nämna fel sida av gatan eller komma för sent.

Noggrannheten är oftast sämst inomhus, mellan höga byggnader och under den första minuten efter att du har gått ut. Om värdet nedan fortsätter vara dåligt när du är ute i det fria stänger du av platstjänsterna på telefonen och sätter på dem igen.

{% if app %}
Aktuell noggrannhet: {{ accuracy }} ({{ quality }})
{% endif %}
