package tw.linsy.aelorn.mythiccore.api.combat;

import java.util.Map;

public interface AttackCadenceApi {
   Map<String, AttackCadenceProfile> attackCadenceProfiles();

   AttackCadenceProfile attackCadenceProfile(String var1);

   AttackTimeline calculateAttackTimeline(String var1, int var2, double var3);
}
