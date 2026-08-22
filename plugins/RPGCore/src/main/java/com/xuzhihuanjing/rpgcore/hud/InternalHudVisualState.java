package com.xuzhihuanjing.rpgcore.hud;

record InternalHudVisualState(int frame, int healthStep, int manaStep, int movementStep) {
   InternalHudVisualState(int frame, int healthStep, int manaStep, int movementStep) {
      frame = Math.max(0, Math.min(3, frame));
      healthStep = Math.max(1, Math.min(32, healthStep));
      manaStep = Math.max(1, Math.min(32, manaStep));
      movementStep = Math.max(1, Math.min(25, movementStep));
      this.frame = frame;
      this.healthStep = healthStep;
      this.manaStep = manaStep;
      this.movementStep = movementStep;
   }
}
