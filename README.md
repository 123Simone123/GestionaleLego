# FreeCard

App Android per tenere sul telefono tutte le tessere fedeltà dei negozi (in stile Stocard).

- Aggiungi una carta scegliendo il negozio dalla lista (o con un nome a piacere) e inquadrando il codice a barre con la fotocamera, leggendolo da una foto o scrivendo il numero a mano.
- Alla cassa la carta si apre con il codice grande, luminosità al massimo e schermo sempre acceso.
- Preferite, ricerca, ordinamento (più usate, recenti, A-Z), colori, foto fronte/retro, note.
- Backup ed import in un file JSON (foto comprese).
- Tutto resta sul telefono: nessun account, nessun server.

## Scaricare l'APK

Ogni push sul ramo `carte-fedelta` compila l'APK con GitHub Actions e lo pubblica
nelle **Releases** del repository come `FreeCard-N.apk` (release "FreeCard build N").

## Webapp (iPhone e qualsiasi browser)

La cartella `web/` contiene la stessa app come webapp installabile (PWA): funziona offline,
salva le carte nel telefono e usa gli stessi QR di condivisione e lo stesso file di backup dell'app Android.
A ogni push viene pubblicata nel ramo `gh-pages`, servito da GitHub Pages:
https://123simone123.github.io/GestionaleLego/

Su iPhone: aprila con Safari → Condividi → «Aggiungi alla schermata Home».

## Condividere le carte tra telefoni (offline)

Apri una carta → Condividi → «QR per un altro telefono», oppure menu ⋮ → «Condividi tutte (QR)».
Sull'altro telefono: menu ⋮ → «Ricevi carte (QR)». Con molte carte i QR sono più di uno e scorrono da soli.
