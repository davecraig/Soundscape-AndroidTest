---
title: Precisão do GPS
layout: page
parent: "Utilizar o Soundscape"
has_toc: false
nav_order: 150
lang: pt
permalink: /users/help-gps-accuracy.html
translated-from: cf9d1bc3ba5aaac2a0ffceb928eff90a1878e910
---

# Precisão do GPS

## Precisão do GPS

O Soundscape só consegue descrever o que está à sua volta com a precisão com que o telemóvel sabe onde está. Uma precisão de 10 metros ou melhor é boa. Até 20 metros é aceitável. Acima disso, os avisos podem indicar o lado errado da rua ou chegar atrasados.

A precisão costuma ser pior dentro de edifícios, entre prédios altos e no primeiro minuto depois de sair para a rua. Se o valor abaixo continuar fraco quando está ao ar livre, desative e volte a ativar os serviços de localização do telemóvel.

{% if app %}
Precisão atual: {{ accuracy }} ({{ quality }})
{% endif %}
