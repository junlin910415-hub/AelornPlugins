package tw.linsy.aelorn.rpgcore.api.module;

/** A small lifecycle unit. Implementations must put every owned registration into the context. */
public interface RpgModule {
    ModuleDescriptor descriptor();

    void start(ModuleContext context) throws Exception;

    default void stop() throws Exception {
        // Most modules only own registrations, which the host closes automatically.
    }
}
