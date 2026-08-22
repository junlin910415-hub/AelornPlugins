package com.xuzhihuanjing.rpgcore.domain.character;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public interface CharacterRepository {
   Optional<AccountProfile> find(UUID var1) throws IOException;

   void save(AccountProfile var1) throws IOException;
}
