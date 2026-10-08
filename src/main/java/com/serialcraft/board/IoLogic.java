package com.serialcraft.board;

/** Redstone input combination. No inputs produce zero; a hardware gate bypasses them. */
public final class IoLogic {
    private IoLogic() {}

    public static int combine(LogicMode mode, int... levels) {
        if (levels.length == 0) return 0;
        int min = 15, max = 0, active = 0;
        for (int raw : levels) {
            int value = Math.clamp(raw, 0, 15);
            min = Math.min(min, value);
            max = Math.max(max, value);
            if (value > 0) active++;
        }
        return switch (mode) {
            case OR -> max;
            case AND -> min;
            case XOR -> (active & 1) == 1 ? max : 0;
        };
    }
}
