package com.FIRNI.superheromod.core.entity;

/**
 * Bir oyuncunun BILEREK cagirdigi varlik.
 *
 * Dunya korumalari (hub'da mob dogmasini engelleme gibi) dogal olarak dogan
 * varliklar icin yazilmistir; cagrilan muttefikler onlara takilmamali.
 * Sand Soldier'lar tam olarak buna takilip sessizce yok ediliyordu.
 *
 * Isaretleyici arayuz olarak tutuluyor ki ileride eklenecek her cagirma
 * (Colossus yardimcilari, baska kahramanlarin summonlari) tek satirla muaf
 * olsun; her koruma icin ayri ayri tur kontrolu yazmak gerekmesin.
 */
public interface PlayerSummoned {
}
