package com.serialcraft.signal;

import com.serialcraft.signal.SignalRecorder.Direction;
import com.serialcraft.signal.SignalRecorder.SeriesId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Decide que series se dibujan en la linea de tiempo.
 *
 * Sin filtro (campo vacio): las que han tenido actividad, hasta el maximo que
 * cabe en pantalla. Se eligen las mas recientes pero se DIBUJAN en orden fijo
 * (primero RX, luego TX, alfabetico) para que las pistas no salten de sitio
 * cada vez que un sensor habla.
 *
 * Con filtro: una lista separada por comas o espacios, en el orden escrito.
 * Cada elemento es un nombre exacto ("pot_val") o un prefijo con asterisco
 * ("mc_*"), sin distinguir mayusculas.
 */
public final class LanePlanner {

    private LanePlanner() {}

    public record Plan(List<SeriesId> lanes, int hidden) {}

    public static Plan plan(List<SeriesId> activeByRecency, List<SeriesId> allKnown,
                            String filter, int maxLanes) {
        int max = Math.max(1, maxLanes);
        String f = filter == null ? "" : filter.trim();

        if (f.isEmpty()) {
            List<SeriesId> top = new ArrayList<>(activeByRecency.subList(0, Math.min(max, activeByRecency.size())));
            top.sort(FIXED_ORDER);
            return new Plan(top, Math.max(0, activeByRecency.size() - top.size()));
        }

        List<SeriesId> picked = new ArrayList<>();
        for (String raw : f.split("[,\\s]+")) {
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) continue;
            boolean prefix = token.endsWith("*");
            String base = prefix ? token.substring(0, token.length() - 1) : token;

            List<SeriesId> matches = new ArrayList<>();
            for (SeriesId id : allKnown) {
                String key = id.key().toLowerCase(Locale.ROOT);
                if (prefix ? key.startsWith(base) : key.equals(base)) matches.add(id);
            }
            matches.sort(FIXED_ORDER);
            for (SeriesId m : matches) if (!picked.contains(m)) picked.add(m);
        }
        int shown = Math.min(max, picked.size());
        return new Plan(new ArrayList<>(picked.subList(0, shown)), picked.size() - shown);
    }

    /** RX antes que TX; despues alfabetico. */
    public static final Comparator<SeriesId> FIXED_ORDER =
            Comparator.comparing((SeriesId id) -> id.dir() == Direction.RX ? 0 : 1)
                      .thenComparing(SeriesId::key, String.CASE_INSENSITIVE_ORDER);
}
