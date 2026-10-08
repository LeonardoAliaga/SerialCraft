package com.serialcraft.board;

/** Session data only. Reading redstone never becomes a redstone output in OUTPUT mode. */
public final class IoSignalState {
    private int receivedWire = -1;
    private int redstoneInput;
    private int processed;
    private int emitted;
    private boolean gateSatisfied = true;

    public void receive(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("wire value outside 0..255");
        receivedWire = value;
    }

    public void invalidateHardware() { receivedWire = -1; }

    public void evaluate(IoMode mode, SignalType signal, LogicMode logic, boolean enabled, int... inputs) {
        redstoneInput = IoLogic.combine(logic, inputs);
        gateSatisfied = inputs.length == 0 || redstoneInput > 0;
        processed = !enabled ? 0 : mode.isOutput() ? redstoneInput
                : receivedWire >= 0 && gateSatisfied ? signal.wireToRedstone(receivedWire) : 0;
        if (signal == SignalType.DIGITAL && processed > 0) processed = 15;
        emitted = mode.isInput() ? processed : 0;
    }

    public int receivedWire() { return receivedWire; }
    public int redstoneInput() { return redstoneInput; }
    public int processed() { return processed; }
    public int emitted() { return emitted; }
    public boolean gateSatisfied() { return gateSatisfied; }
}
