package tw.linsy.aelornstore.model;

/** Which balance a shop product is priced against. */
public enum PriceCurrency {
    /** The store's own top-up credit, held in this plugin's ledger. */
    CREDIT,
    /** The server economy, reached through Vault. */
    VAULT
}
