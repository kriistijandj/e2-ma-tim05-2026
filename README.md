# 🧩 Slagalica – Android Mobilna Aplikacija (2025/26)

Android mobilna aplikacija inspirisana popularnim TV kvizom **Slagalica**, razvijena u okviru predmeta **Mobilne aplikacije**  
(Smer: *Računarstvo i automatika*).

Projekat implementira kompletan tok igranja, sistem naloga, rangiranja, statistike i socijalne funkcije, uz fokus na čistu arhitekturu i jasnu podelu odgovornosti.

---

## 📌 Sadržaj

- [Opis projekta](#-opis-projekta)
- [Tehnologije](#-tehnologije)
- [Arhitektura sistema](#-arhitektura-sistema)
- [Implementirane igre](#-implementirane-igre)
- [Funkcionalnosti sistema](#-funkcionalnosti-sistema)
- [Instalacija i pokretanje](#-instalacija-i-pokretanje)
- [Struktura projekta](#-struktura-projekta)
- [Napomene](#-napomene)
- [Autori](#-autor)

---
## 🚀 Instalacija i pokretanje

### ✅ Preduslovi
- Android Studio
- Java JDK 17
- Android emulator ili fizički uređaj

### 📦 Kloniranje repozitorijuma
```bash
git clone <repository-url>
```
Otvorite projekat u Android Studio okruženju.
Sačekajte da se završi Gradle Sync i preuzimanje svih zavisnosti.
### Pokretanje
 - Izaberite virtualni uređaj (Emulator) ili povežite fizički Android telefon.
 - Pritisnite dugme Run 'app' (Shift + F10).
---

## 📱 Opis projekta

**Slagalica** je kviz aplikacija koja simulira format istoimenog TV kviza kroz niz logičkih i znalačkih igara.  
Aplikacija omogućava korisnicima da:

- igraju kompletan ciklus igara
- prate lični napredak i statistiku
- takmiče se u ligama i rang listama

---

## 🧪 Tehnologije

- **Programski jezik:** Java (JDK 17)
- **Razvojno okruženje:** Android Studio
- **UI:** XML Layout-i
- **Arhitektura:** Troslojna (Presentation – Business Logic – Data)
- **Platforma:** Android
---

## 🛠 Arhitektura sistema

Aplikacija je implementirana po **troslojnoj arhitekturi** radi jasne separacije odgovornosti.

### 🎨 Prezentacioni sloj
- XML layout fajlovi
- Fragmenti
- Aktivnosti
- Obrada korisničkih interakcija

### ⚙️ Sloj poslovne logike
- Pravila svake igre
- Bodovanje
- Upravljanje tokom partije
- Validacija korisničkih poteza

### 💾 Sloj podataka
- Upravljanje korisnicima
- Statistika
- Rang liste
- Regionalni podaci

---

## 🎮 Implementirane igre

Aplikacija podržava **kompletan ciklus od 6 igara**:

- **Ko zna zna**  
  Brzo odgovaranje na 5 pitanja sa vremenskim ograničenjem.

- **Spojnice**  
  Povezivanje pojmova na osnovu zadatog kriterijuma.

- **Asocijacije**  
  Otvaranje polja i pogađanje konačnog rešenja.

- **Skočko**  
  Logičko pogađanje kombinacije simbola.

- **Korak po korak**  
  Postepeno otkrivanje pojma.

- **Moj broj**  
  Formiranje matematičkog izraza za dobijanje ciljnog broja.  
---

## 📋 Funkcionalnosti sistema

### 👤 Autentifikacija i nalozi
- Registracija korisnika
- Potvrda naloga putem email-a
- Resetovanje lozinke

### 📊 Profil i statistika
- Pregled statistike po svakoj igri
- Ukupni rezultati i uspešnost
- 
### 🔔 Notifikacije
- Posebni notifikacioni kanali za:
  - čet poruke
  - nagrade
  - rang liste

---


