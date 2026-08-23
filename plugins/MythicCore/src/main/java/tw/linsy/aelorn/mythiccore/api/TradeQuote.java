package tw.linsy.aelorn.mythiccore.api;

public record TradeQuote(double buyPrice, double sellPrice, String currency, String formulaId) {
   public TradeQuote(double buyPrice, double sellPrice, String currency, String formulaId) {
      buyPrice = Math.max((double)0.0F, Double.isFinite(buyPrice) ? buyPrice : (double)0.0F);
      sellPrice = Math.max((double)0.0F, Double.isFinite(sellPrice) ? sellPrice : (double)0.0F);
      currency = currency != null && !currency.isBlank() ? currency.trim() : "coins";
      formulaId = formulaId != null && !formulaId.isBlank() ? formulaId.trim() : "default";
      this.buyPrice = buyPrice;
      this.sellPrice = sellPrice;
      this.currency = currency;
      this.formulaId = formulaId;
   }
}
