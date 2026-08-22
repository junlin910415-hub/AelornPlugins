package com.xuzhihuanjing.rpgcore.domain.ability;

public enum InputToken {
   LEFT("L"),
   RIGHT("R");

   private final String symbol;

   private InputToken(String symbol) {
      this.symbol = symbol;
   }

   public String symbol() {
      return this.symbol;
   }
}
