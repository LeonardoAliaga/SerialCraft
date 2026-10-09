package com.serialcraft.client.ui.io;

import com.serialcraft.block.IOSide;
import com.serialcraft.block.entity.HardwareIOBlockEntity;
import com.serialcraft.board.IoMode;
import com.serialcraft.board.LogicMode;
import com.serialcraft.board.SignalType;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.ConfigPayload;
import com.serialcraft.network.ConfigResultPayload;
import com.serialcraft.network.SignalProtocol;
import com.serialcraft.network.guard.NetGuard;

/** Presentation state only: rebuilding widgets never replaces the user's draft. */
public final class IoEditorDraft {
    public enum Section { CONFIGURATION, DIAGNOSTICS }
    public enum SaveState { IDLE, SAVING, ACCEPTED, REJECTED, TIMED_OUT }
    public record Values(String name, String channel, IoMode mode, SignalType signal,
                         LogicMode logic, boolean enabled, int sides) {}
    public static final long SAVE_TIMEOUT_NANOS = 5_000_000_000L;

    private final BoardInfo target;
    private final String dimension;
    private Values baseline;
    private Values values;
    private Values submitted;
    private Section section = Section.CONFIGURATION;
    private SaveState saveState = SaveState.IDLE;
    private String message = "";
    private int requestId;
    private long startedAt;

    public IoEditorDraft(BoardInfo target, String dimension) {
        this.target = target;
        this.dimension = dimension;
        baseline = values = new Values(target.id(), target.data(), target.mode(), target.signalType(),
                target.logicMode(), target.enabled(), target.snapshot().sides());
    }

    public BoardInfo target() { return target; }
    public String dimension() { return dimension; }
    public Values values() { return values; }
    public Section section() { return section; }
    public void section(Section value) { section = value; }
    public boolean dirty() { return !values.equals(baseline); }
    public boolean saving() { return saveState == SaveState.SAVING; }
    public SaveState saveState() { return saveState; }
    public String message() { return message; }
    public boolean valid() {
        return values.name().length() <= BoardInfo.MAX_ID_LENGTH
                && values.channel().length() <= BoardInfo.MAX_DATA_LENGTH
                && SignalProtocol.isValidChannel(values.channel().trim()) && IOSide.isValidPacked(values.sides());
    }

    public void change(Values next) {
        if (saving() || next.equals(values)) return;
        values = next;
        saveState = SaveState.IDLE;
        message = "";
    }
    public void name(String name) { change(new Values(name, values.channel(), values.mode(), values.signal(), values.logic(), values.enabled(), values.sides())); }
    public void channel(String channel) { change(new Values(values.name(), channel, values.mode(), values.signal(), values.logic(), values.enabled(), values.sides())); }
    public void mode(IoMode mode) { change(new Values(values.name(), values.channel(), mode, values.signal(), values.logic(), values.enabled(), values.sides())); }
    public void signal(SignalType signal) { change(new Values(values.name(), values.channel(), values.mode(), signal, values.logic(), values.enabled(), values.sides())); }
    public void logic(LogicMode logic) { change(new Values(values.name(), values.channel(), values.mode(), values.signal(), logic, values.enabled(), values.sides())); }
    public void enabled(boolean enabled) { change(new Values(values.name(), values.channel(), values.mode(), values.signal(), values.logic(), enabled, values.sides())); }

    public void localError(String key) {
        if (!saving()) { saveState = SaveState.REJECTED; message = key; }
    }
    public boolean beginSave(int id, long now) {
        if (saving()) return false;
        if (!valid()) { localError("gui.serialcraft.editor.invalid_channel"); return false; }
        submitted = new Values(NetGuard.sanitize(values.name(), BoardInfo.MAX_ID_LENGTH, HardwareIOBlockEntity.DEFAULT_BOARD_ID),
                values.channel().trim(), values.mode(), values.signal(), values.logic(), values.enabled(), values.sides());
        requestId = id;
        startedAt = now;
        saveState = SaveState.SAVING;
        message = "gui.serialcraft.editor.saving";
        return true;
    }
    public ConfigPayload payload() {
        if (!saving()) throw new IllegalStateException("no pending configuration");
        return new ConfigPayload(target.pos(), submitted.mode(), submitted.channel().trim(), submitted.signal(),
                submitted.enabled(), submitted.name().trim(), submitted.logic(), submitted.sides(), dimension, requestId);
    }
    public boolean accept(ConfigResultPayload result) {
        if (!saving() || requestId != result.requestId() || !target.pos().equals(result.pos())) return false;
        saveState = result.accepted() ? SaveState.ACCEPTED : SaveState.REJECTED;
        message = result.accepted() ? "gui.serialcraft.editor.saved" : result.reason();
        if (result.accepted()) baseline = values = submitted;
        return true;
    }
    public boolean tick(long now) {
        if (!saving() || now - startedAt <= SAVE_TIMEOUT_NANOS) return false;
        saveState = SaveState.TIMED_OUT;
        message = "gui.serialcraft.editor.timeout";
        return true;
    }
}
