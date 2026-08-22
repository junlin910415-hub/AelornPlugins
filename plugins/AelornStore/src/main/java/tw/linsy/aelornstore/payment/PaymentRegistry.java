package tw.linsy.aelornstore.payment;

import java.util.LinkedHashMap;
import java.util.Map;
import tw.linsy.aelornstore.config.StoreSettings;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a channel id from config.yml to the implementation that serves it.
 *
 * Channels are discovered from config rather than hard-coded, so adding a third
 * aggregator is a config block plus a backend adapter — no change here. Anything
 * that is not {@code manual} is assumed to settle through the web backend, which
 * is true of every Taiwanese aggregator worth integrating.
 */
public final class PaymentRegistry {

    private final Map<String, PaymentProvider> providers = new LinkedHashMap<>();

    /** Rebuilt on every reload so a newly configured channel becomes usable at once. */
    public void rebuild(StoreSettings settings) {
        providers.clear();
        providers.put(ManualProvider.ID, new ManualProvider());
        for (String id : settings.providers().keySet()) {
            if (!providers.containsKey(id)) {
                providers.put(id, new WebGatewayProvider(id));
            }
        }
    }

    public @Nullable PaymentProvider find(String id) {
        return providers.get(id);
    }

    /** Channel ids that are both configured-enabled and actually usable. */
    public String describeUsable(StoreSettings settings) {
        StringBuilder names = new StringBuilder();
        for (StoreSettings.ProviderSettings channel : settings.enabledProviders()) {
            PaymentProvider provider = providers.get(channel.id());
            if (provider != null && provider.available(channel)) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(channel.id());
            }
        }
        return names.length() == 0 ? "（無）" : names.toString();
    }

    public boolean usable(StoreSettings settings, String id) {
        StoreSettings.ProviderSettings channel = settings.providers().get(id);
        PaymentProvider provider = providers.get(id);
        return channel != null && provider != null && provider.available(channel);
    }
}
