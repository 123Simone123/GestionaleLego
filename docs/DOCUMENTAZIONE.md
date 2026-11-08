# Brick Archivio: documentazione di progetto

App Android personale per catalogare la propria collezione LEGO® fotografandola.
Questo documento è stato scritto durante la progettazione e va aggiornato a ogni modifica.

---

## 1. Requisiti

Requisiti espressi dall'utente:

| # | Requisito | Come è soddisfatto |
|---|-----------|--------------------|
| R1 | App installabile come APK sul telefono | Progetto Android nativo; APK compilato da GitHub Actions (sez. 8) |
| R2 | Fotografare uno o più oggetti per volta; l'app li riconosce e li aggiunge al catalogo | Rilevamento multi-oggetto sul telefono + riconoscimento online (sez. 6) |
| R3 | Struttura a oggetti: il set contiene l'immagine originale e tutte le sue informazioni | Classe `LegoItem` con sottoclassi `LegoSet`, `Minifigure`, `Part` (sez. 4) |
| R4 | Tutto salvato sul telefono | Database Room e file nella memoria privata dell'app (sez. 4.3) |
| R5 | Usare API/siti per le informazioni | Brickognize (riconoscimento) e Rebrickable (dati ufficiali) (sez. 5) |
| R6 | Le foto scattate restano nell'app | Le foto vengono copiate nella memoria privata dell'app, mai nella galleria |
| R7 | Sicurezza non prioritaria (uso locale) | Nessuna cifratura né login; chiave di firma APK inclusa nel repository |
| R8 | Documentazione scritta durante il progetto | Questo file |

### 1.1 Decisioni prese con l'utente

Domande poste durante l'analisi e risposte ricevute (8 novembre 2026):

| Domanda | Risposta |
|---------|----------|
| Come ottenere l'APK (qui non c'è l'Android SDK)? | GitHub Actions, su un repository dell'utente |
| Cosa verrà fotografato? | Scatole, set montati, minifigure e pezzi sciolti (tutti e quattro) |
| Fonte dei dati dei set? | Rebrickable con chiave API personale gratuita |
| Dati personali da salvare? | Prezzi (prezzo pagato, data e luogo d'acquisto) e Note + tag |
| Più oggetti in una foto? | Rilevamento automatico più correzione manuale dei riquadri |
| Si possono inviare i ritagli delle foto a Brickognize? | Sì. Le foto restano comunque salvate solo nell'app |

Non richiesti, quindi **non** implementati: stato/completezza del set, posizione fisica,
valore di mercato.

---

## 2. Scelte tecniche

| Ambito | Scelta | Motivo |
|--------|--------|--------|
| Linguaggio / UI | Kotlin + Jetpack Compose (Material 3) | Standard attuale Android |
| Database | Room (SQLite) | Relazioni oggetto-foto-tag, query, migrazioni |
| Rete | OkHttp + `org.json` | Poche dipendenze; parsing tollerante |
| Immagini | Coil | Mostra sia file locali sia URL |
| Rilevamento oggetti | ML Kit Object Detection (modello incluso) | Offline, fino a 5 oggetti per foto |
| Lettura numeri | ML Kit Text Recognition (modello incluso) | Offline: legge il numero del set dalla scatola |
| Riconoscimento visivo | Brickognize API | Gratuita, senza chiave, riconosce set, minifigure e pezzi |
| Dati ufficiali | Rebrickable API v3 | Database completo di set, temi, pezzi |
| Fotocamera | App Fotocamera di sistema (`TakePicture`) | Nessun permesso CAMERA da chiedere |
| SDK | minSdk 26 (Android 8), target 35 | Copre praticamente tutti i telefoni attuali |

---

## 3. Architettura

```
ui/            Schermate Compose + ViewModel
  catalog/     Elenco, filtri, totali
  scan/        Flusso di scansione (foto → riquadri → conferma)
  detail/      Scheda oggetto (dati, foto, prezzi, note, tag)
  search/      Ricerca manuale su Rebrickable
  settings/    Chiave API, backup
domain/        Modello a oggetti (LegoItem e sottoclassi)
data/
  db/          Room: entità, DAO, database
  remote/      Client Brickognize e Rebrickable
  CatalogRepository   Unico punto di accesso ai dati per la UI
  PhotoStorage        Gestione file (foto, ritagli, immagini ufficiali)
  Backup              Esporta/importa zip
recognition/   Pipeline di riconoscimento (ML Kit + Brickognize + Rebrickable)
BrickApp       Crea e collega le dipendenze (iniezione manuale)
```

Flusso dei dati: **UI → ViewModel → CatalogRepository → (Room | file | API)**.
La UI osserva il database tramite `Flow`: ogni modifica si riflette subito nelle schermate.

---

## 4. Modello dati

### 4.1 Modello a oggetti (`domain/LegoItem.kt`)

```
LegoItem (sealed, astratta)
├─ id, code, name
├─ officialImage : OfficialImage   (url + copia locale)
├─ links         : ExternalLinks   (Rebrickable, BrickLink)
├─ personal      : PersonalInfo    (quantità, prezzo, data, luogo, note, tag)
├─ photos        : List<UserPhoto> (foto scattate dall'utente)
│
├─ LegoSet     + year, theme, numParts
├─ Minifigure  + year, theme, numParts
└─ Part        + category
```

Ogni oggetto è autosufficiente: contiene l'immagine ufficiale (scaricata sul telefono),
i dati ufficiali, le tue foto e i tuoi dati.

### 4.2 Database (`data/db`)

Database `brickarchivio.db`, versione schema 1.

**items**: un'unica tabella per i tre tipi, distinti da `type`.

| Colonna | Tipo | Note |
|---------|------|------|
| id | INTEGER PK | |
| type | TEXT | `SET`, `MINIFIG`, `PART` |
| code | TEXT | es. `75192-1`, `sw0001`, `3001` |
| name | TEXT | |
| year, numParts | INTEGER? | set e minifigure |
| theme | TEXT? | tema completo, es. "Star Wars › UCS" |
| category | TEXT? | categoria del pezzo |
| officialImageUrl | TEXT? | URL d'origine |
| officialImagePath | TEXT? | copia locale (usata per prima, funziona offline) |
| rebrickableUrl, bricklinkUrl | TEXT? | link esterni |
| quantity | INTEGER | quanti esemplari possiedi |
| pricePaid | REAL? | prezzo **totale** pagato, in euro |
| purchaseDate | TEXT? | formato ISO `AAAA-MM-GG` |
| purchasePlace, notes | TEXT? | |
| createdAt, updatedAt | INTEGER | millisecondi |

Indice univoco su `(type, code)`: **se fotografi di nuovo un oggetto già in catalogo
non viene duplicato**; la quantità aumenta di 1 e la nuova foto viene aggiunta.

**photos**: `id`, `itemId` (FK → items, cancellazione a cascata), `path` (ritaglio),
`sourcePath` (foto intera d'origine, condivisa tra gli oggetti della stessa foto), `createdAt`.

**tags** (`id`, `name` univoco) e **item_tags** (`itemId`, `tagId`): relazione molti-a-molti.
I tag non più usati vengono eliminati automaticamente.

### 4.3 File sul telefono

Tutto nella memoria privata dell'app (`/data/data/com.brickarchivio.app/`):

```
files/photos/originals/   foto intere scattate o importate
files/photos/crops/       ritagli dei singoli oggetti
files/official/           immagini ufficiali scaricate
databases/brickarchivio.db
```

Nessun file finisce nella galleria. Gli scatti passano per un file temporaneo in
`cache/captures/` che viene cancellato subito dopo la copia.

Le foto vengono raddrizzate secondo l'EXIF e ridotte a 2048 px sul lato lungo.
Quando elimini un oggetto vengono cancellati anche i suoi ritagli, l'immagine ufficiale
e, se nessun altro oggetto la usa, la foto intera.

---

## 5. Servizi esterni

### 5.1 Brickognize (riconoscimento)

- Sito: https://brickognize.com. Gratuito, senza chiave.
- Chiamata usata: `POST https://api.brickognize.com/predict/`, multipart, campo
  `query_image` con il JPEG. **Verificato** (esempio pubblicato dall'autore del servizio).
- Viene inviato **solo il ritaglio** dell'oggetto, ridotto a 1024 px.
- Campi letti dalla risposta: `items[]` con `id`, `name`, `img_url`, `category`, `type`,
  `score`, `external_sites[]` (`name`, `url`).
  ⚠ **Da verificare al primo test**: durante la progettazione la documentazione ufficiale
  (`api.brickognize.com/docs`) non era raggiungibile, quindi questi nomi non sono confermati.
  Il parsing è tollerante (un campo mancante non blocca nulla) e nella schermata di conferma
  il pulsante **"Dettagli tecnici"** mostra la risposta grezza, per correggere il codice se serve.
- Per i set, un codice senza suffisso (es. `75192`) viene convertito in `75192-1` (formato Rebrickable).

### 5.2 Rebrickable (dati ufficiali)

- Documentazione: https://rebrickable.com/api/v3/docs/
- Autenticazione: header `Authorization: key <CHIAVE>` (verificato).
- **Limite: in media 1 richiesta al secondo** (verificato). Oltre si riceve HTTP 429 e,
  insistendo, l'IP può essere bloccato per 1-2 giorni. Il client distanzia ogni chiamata
  di almeno 1,1 s e su un 429 aspetta 3 s e riprova una sola volta.
- Endpoint usati:

| Endpoint | Uso | Campi letti |
|----------|-----|-------------|
| `GET /lego/sets/{set_num}/` | dati del set | `set_num, name, year, theme_id, num_parts, set_img_url, set_url` (verificati) |
| `GET /lego/sets/?search=` | ricerca manuale | `results[]`; `search` cerca su nome e numero (verificato) |
| `GET /lego/themes/{id}/` | nome del tema (e del tema padre) | `name, parent_id` |
| `GET /lego/minifigs/{fig_num}/` e `?search=` | minifigure | `set_num, name, num_parts, set_img_url, set_url` ⚠ da verificare |
| `GET /lego/parts/{part_num}/` e `?search=` | pezzi | `part_num, name, part_cat_id, part_img_url, part_url` ⚠ da verificare |
| `GET /lego/part_categories/{id}/` | nome categoria pezzo | `name` |
| `GET /lego/colors/?page_size=1` | verifica della chiave | — |

### 5.3 Note sui codici

- **Set**: Brickognize e Rebrickable usano lo stesso formato (`75192-1`), quindi i dati
  vengono completati da Rebrickable.
- **Minifigure**: Brickognize restituisce codici BrickLink (es. `sw0001`), Rebrickable usa
  `fig-000001`. Le due numerazioni non coincidono: le minifigure riconosciute da foto
  vengono salvate con i dati di Brickognize (nome, immagine, link BrickLink). Quelle
  aggiunte con la ricerca manuale hanno i dati Rebrickable.
- **Pezzi**: per i pezzi comuni il numero è uguale nei due siti; se Rebrickable non lo
  trova restano i dati di Brickognize.
- Il colore dei pezzi non viene rilevato: puoi indicarlo nelle note o con un tag.

---

## 6. Flusso di scansione

```
[Scatta foto / Galleria]  (puoi accumulare più foto)
          │
          ▼
per ogni foto:
  1. Copia nell'app (raddrizzata, max 2048 px)
  2. ML Kit Object Detection → riquadri proposti (max 5; se nessuno: foto intera)
  3. Tu correggi i riquadri: sposta, ridimensiona, aggiungi, elimina
  4. Per ogni riquadro:
       a. salva il ritaglio
       b. ML Kit legge i numeri di 4-7 cifre (offline), ordinati per grandezza del testo
       c. invio del ritaglio a Brickognize
       d. i numeri letti (max 3) vengono verificati su Rebrickable come set
       e. unione dei risultati: prima i numeri trovati sulla scatola, poi Brickognize
  5. Schermata di conferma: per ogni oggetto scegli il risultato giusto,
     "Non aggiungere" o "Cerca a mano…"
  6. Salvataggio: dati completati da Rebrickable, immagine ufficiale scaricata,
     ritaglio collegato all'oggetto (o quantità +1 se già presente)
          │
          ▼
[Riepilogo] → apri un oggetto o fai una nuova scansione
```

Se abbandoni la scansione a metà, le foto e i ritagli non salvati vengono cancellati.

Consigli per un buon riconoscimento: sfondo uniforme, oggetti separati, numero
della scatola ben visibile e a fuoco.

---

## 7. Schermate

| Schermata | Funzioni |
|-----------|----------|
| Catalogo | Elenco con immagine, nome, codice, tema, anno, pezzi, quantità; filtro testuale (nome, codice, tema, note, tag); filtri per tipo e per tag; totale esemplari e totale speso |
| Scansione | Flusso della sezione 6 |
| Cerca e aggiungi | Aggiunta senza foto: ricerca per numero o nome su Rebrickable |
| Dettaglio | Immagine ufficiale, dati, link a Rebrickable/BrickLink, le tue foto (aggiungi/elimina), quantità, prezzo, data e luogo d'acquisto, note, tag; aggiorna dati online; elimina |
| Impostazioni | Chiave Rebrickable (con verifica), esporta/importa backup, informazioni |

---

## 8. Compilazione e installazione

### 8.1 Compilazione (GitHub Actions)

Il workflow `.github/workflows/build-apk.yml` si avvia a ogni push su `main`
(o a mano da *Actions → Build APK → Run workflow*):

1. installa Java 17 e Gradle;
2. esegue `./gradlew assembleRelease`;
3. pubblica l'APK sia come *artifact* sia in una **Release** chiamata `build-N`.

Versioni: Gradle 8.14.3, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compileSdk 35.

### 8.2 Firma

L'APK è firmato con una chiave fissa inclusa nel repository (`app/keystore/brickarchivio.jks`,
password `brickarchivio`). È una scelta voluta: senza una chiave fissa ogni build
avrebbe una firma diversa, Android rifiuterebbe l'aggiornamento e bisognerebbe
disinstallare l'app, **perdendo il catalogo**. Va bene per un'app personale; non
usare questa chiave per app pubblicate.

`versionCode` = numero della build su GitHub, così ogni APK si installa sopra il precedente.

### 8.3 Installazione sul telefono

1. Dal telefono apri il repository su GitHub → **Releases** → ultima `build-N`.
2. Tocca `BrickArchivio-N.apk` per scaricarlo.
3. Apri il file; Android chiederà di consentire l'installazione da quella app (browser o File).
4. Al primo avvio: **Impostazioni → Chiave API Rebrickable → Salva e verifica**.

Per aggiornare basta installare il nuovo APK sopra il precedente: i dati restano.

### 8.4 Backup

Impostazioni → **Esporta backup** crea un `.zip` con database, foto e immagini ufficiali.
**Importa backup** sostituisce il catalogo attuale e riavvia l'app.
I dati esistono solo sul telefono: disinstallare l'app li cancella.

---

## 9. Limiti noti e aspetti da verificare

1. **Schema della risposta Brickognize** da confermare al primo test (sez. 5.1).
2. **Campi Rebrickable per minifigure e pezzi** da confermare (sez. 5.2).
3. ML Kit rileva al massimo **5 oggetti** per foto; per averne di più si aggiungono riquadri a mano.
4. Il rilevamento automatico funziona meglio con oggetti ben separati su sfondo uniforme.
5. Il riconoscimento visivo richiede internet; senza rete funziona solo la lettura dei numeri
   (che però ha bisogno di Rebrickable per la verifica, quindi anch'essa di internet).
   Il catalogo invece è sempre consultabile offline.
6. Le minifigure riconosciute da foto non hanno anno e tema (sez. 5.3).
7. Il colore dei pezzi non viene rilevato.
8. Il progetto è stato scritto senza poterlo compilare in locale: la prima build su
   GitHub Actions serve anche da verifica del codice.

---

## 10. Registro delle modifiche

| Data | Versione | Modifica |
|------|----------|----------|
| 2026-11-08 | 1.0 | Prima versione: catalogo, scansione multi-oggetto, ricerca manuale, dettaglio, backup |
