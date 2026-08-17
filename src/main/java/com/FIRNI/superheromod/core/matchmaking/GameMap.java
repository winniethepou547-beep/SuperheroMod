package com.FIRNI.superheromod.core.matchmaking;

import net.minecraft.core.BlockPos;

/**
 * Bir maçın oynanacağı harita. frameColor MapVoteScreen'de çerçeve/simge rengi
 * olarak kullanılır. spawn, maç başlayınca katılımcıların ışınlanacağı nokta -
 * gerçek harita sanatı gelene kadar haritanın merkezine denk geliyor.
 */
public record GameMap(String id, String displayName, int frameColor, BlockPos spawn) {
}
