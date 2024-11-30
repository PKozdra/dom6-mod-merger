# Notatki do Mod Mergera

## Fajne pomysły

### **1. Generacja adresów ID od preferowanych ID**
**Problem:**
Obecnie id są generowane od MODDING_START do MODDING_END z naszego ModRanges.kt (wszystkie dostepne ID dla modowania) co oczywiście może powodować problemy bo chyba łatwiej deweloperom modów będzie trafić na ID z początku listy MODDDING_START...

**Rozwiązanie:**
Możemy zrobić generacje ID od preferowanych ID np. od 1000 do 2000, 2000 do 3000 itd. i wtedy deweloperzy modów będą mieli większe szanse na uniknięcie konfliktów z ID. Najlepiej będzie jak sprawdzę które ID są najmniej używane i zrobię generacje od tych ID.

