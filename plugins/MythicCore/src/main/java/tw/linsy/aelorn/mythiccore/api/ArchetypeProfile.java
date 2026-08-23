package tw.linsy.aelorn.mythiccore.api;

public record ArchetypeProfile(String id, String displayName, String role, String description) {
   public ArchetypeProfile(String id, String displayName, String role, String description) {
      id = StatSnapshot.normalize(id);
      displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : id;
      role = role == null ? "" : role.trim();
      description = description == null ? "" : description.trim();
      this.id = id;
      this.displayName = displayName;
      this.role = role;
      this.description = description;
   }
}
