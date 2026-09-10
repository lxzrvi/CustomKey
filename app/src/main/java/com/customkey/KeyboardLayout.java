package com.customkey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the key grid for a page.
 *
 * <p>The saved order in {@link Config#order} is the single source of truth:
 * the first time a page is rendered its default rows (minus whatever the user
 * has hidden) are written there, and every later edit — reorder, drag, hiding
 * a row — mutates that saved list instead of the defaults.</p>
 */
public final class KeyboardLayout {

    public static final int PAGE_ABC = 0;
    public static final int PAGE_NUM = 1;
    public static final int PAGE_SYM = 2;
    public static final int PAGE_EMOJI = 3;

    public static final String ID_SHIFT = "shift";
    public static final String ID_BACK = "backspace";
    public static final String ID_SPACE = "space";
    public static final String ID_ENTER = "enter";
    public static final String ID_SYM = "sym";
    public static final String ID_SYM2 = "sym2";
    public static final String ID_ABC = "abc";
    public static final String ID_EMOJI = "emoji";
    public static final String ID_GLOBE = "globe";
    public static final String ID_COPY = "copy";
    public static final String ID_CUT = "cut";
    public static final String ID_PASTE = "paste";
    public static final String ID_ALL = "select_all";
    public static final String ID_LEFT = "left";
    public static final String ID_RIGHT = "right";

    /** Row roles, so a hidden row can always be found and put back. */
    public static final int ROLE_TOOLS = 0;
    public static final int ROLE_NUMBER = 1;
    public static final int ROLE_BOTTOM = 2;

    public static final String[] TOOLS = {
            ID_ALL, ID_CUT, ID_COPY, ID_PASTE, ID_LEFT, ID_RIGHT, ID_GLOBE
    };

    public static final String[] NUMBERS = {
            "k_1", "k_2", "k_3", "k_4", "k_5",
            "k_6", "k_7", "k_8", "k_9", "k_0"
    };

    private static final String[] EMOJI = {
            "\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83E\uDD23",
            "\uD83D\uDE09", "\uD83D\uDE0D", "\uD83D\uDE18", "\uD83D\uDE0E",
            "\uD83E\uDD14", "\uD83D\uDE10", "\uD83D\uDE44", "\uD83D\uDE2C",
            "\uD83D\uDE2D", "\uD83D\uDE21", "\uD83D\uDE31", "\uD83D\uDE34",
            "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4F", "\uD83D\uDE4F",
            "\uD83D\uDCAA", "\uD83D\uDC4C", "\u2764", "\uD83D\uDD25"
    };

    private KeyboardLayout() {
    }

    /* ==================================================================
     * Building
     * ================================================================== */

    public static List<List<KeyModel>> build(int page) {
        Config cfg = Prefs.get();
        List<List<String>> ids = orderFor(page);

        List<List<KeyModel>> rows = new ArrayList<>();
        for (List<String> rowIds : ids) {
            List<KeyModel> row = new ArrayList<>();
            for (String id : rowIds) {
                KeyModel key = create(id, page);
                if (key == null) {
                    continue;
                }
                String custom = cfg.labels.get(id);
                if (custom != null && custom.length() > 0) {
                    key.label = custom;
                    if (key.type == KeyModel.TEXT) {
                        key.output = custom;
                    }
                }
                row.add(key);
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    /** Saved order for a page, seeded from the defaults on first use. */
    public static List<List<String>> orderFor(int page) {
        Config cfg = Prefs.get();
        List<List<String>> rows = cfg.order.get(page);
        if (rows == null || rows.isEmpty()) {
            rows = defaultOrder(page, cfg);
            cfg.order.put(page, rows);
        }
        return rows;
    }

    private static List<List<String>> defaultOrder(int page, Config cfg) {
        List<List<String>> rows = new ArrayList<>();

        if (cfg.toolsRow) {
            rows.add(new ArrayList<>(Arrays.asList(TOOLS)));
        }
        if (cfg.numberRow && page == PAGE_ABC) {
            rows.add(new ArrayList<>(Arrays.asList(NUMBERS)));
        }

        switch (page) {
            case PAGE_NUM:
                rows.add(split("1 2 3 4 5 6 7 8 9 0"));
                rows.add(split("- / : ; ( ) $ & @ \""));
                rows.add(new ArrayList<>(Arrays.asList(
                        ID_SYM2, "k_,", "k_.", "k_?", "k_!", "k_'", ID_BACK)));
                break;

            case PAGE_SYM:
                rows.add(split("[ ] { } # % ^ * + ="));
                rows.add(split("_ \\ | ~ < > € £ ¥ •"));
                rows.add(new ArrayList<>(Arrays.asList(
                        ID_SYM, "k_\"", "k_'", "k_,", "k_.", "k_?", "k_!", ID_BACK)));
                break;

            case PAGE_EMOJI:
                for (int i = 0; i < EMOJI.length; i += 8) {
                    List<String> row = new ArrayList<>();
                    for (int j = i; j < i + 8 && j < EMOJI.length; j++) {
                        row.add("e_" + j);
                    }
                    rows.add(row);
                }
                break;

            case PAGE_ABC:
            default:
                rows.add(split("q w e r t y u i o p"));
                rows.add(split("a s d f g h j k l"));
                List<String> third = new ArrayList<>();
                third.add(ID_SHIFT);
                third.addAll(split("z x c v b n m"));
                third.add(ID_BACK);
                rows.add(third);
                break;
        }

        if (cfg.bottomBar) {
            rows.add(defaultBottom(page, cfg));
        }
        return rows;
    }

    private static List<String> defaultBottom(int page, Config cfg) {
        List<String> row = new ArrayList<>();

        if (page == PAGE_EMOJI) {
            row.add(ID_ABC);
            row.add(ID_BACK);
            row.add(ID_SPACE);
            row.add(ID_ENTER);
            return row;
        }

        row.add(page == PAGE_ABC ? ID_SYM : ID_ABC);
        if (cfg.emojiKey) {
            row.add(ID_EMOJI);
        }
        row.add(ID_SPACE);
        row.add("k_.");
        row.add(ID_ENTER);
        return row;
    }

    private static List<String> split(String chars) {
        List<String> out = new ArrayList<>();
        for (String part : chars.split(" ")) {
            if (part.length() > 0) {
                out.add("k_" + part);
            }
        }
        return out;
    }

    /* ==================================================================
     * Key factory — every id a page can legitimately contain
     * ================================================================== */

    private static KeyModel create(String id, int page) {
        if (id == null || id.length() < 2) {
            return null;
        }
        if (id.startsWith("k_")) {
            String ch = id.substring(2);
            return new KeyModel(id, ch, ch, KeyModel.TEXT, 1f, -1);
        }
        if (id.startsWith("e_")) {
            try {
                int index = Integer.parseInt(id.substring(2));
                if (index < 0 || index >= EMOJI.length) {
                    return null;
                }
                return new KeyModel(id, EMOJI[index], EMOJI[index],
                        KeyModel.TEXT, 1f, -1);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        switch (id) {
            case ID_SHIFT:
                return act(ID_SHIFT, "⇧", KeyModel.SHIFT, 1.4f);
            case ID_BACK:
                return act(ID_BACK, "⌫", KeyModel.BACKSPACE, 1.4f);
            case ID_SPACE:
                return act(ID_SPACE, "space", KeyModel.SPACE, 4f);
            case ID_ENTER:
                return act(ID_ENTER, "↵", KeyModel.ENTER, 1.4f);
            case ID_COPY:
                return act(ID_COPY, "COPY", KeyModel.COPY, 1.3f);
            case ID_CUT:
                return act(ID_CUT, "CUT", KeyModel.CUT, 1.2f);
            case ID_PASTE:
                return act(ID_PASTE, "PASTE", KeyModel.PASTE, 1.4f);
            case ID_ALL:
                return act(ID_ALL, "ALL", KeyModel.SELECT_ALL, 1.2f);
            case ID_LEFT:
                return act(ID_LEFT, "←", KeyModel.CURSOR_LEFT, 1f);
            case ID_RIGHT:
                return act(ID_RIGHT, "→", KeyModel.CURSOR_RIGHT, 1f);
            case ID_GLOBE:
                return act(ID_GLOBE, "\uD83C\uDF10", KeyModel.IME_SWITCH, 1.1f);
            case ID_SYM:
                return page(ID_SYM, page == PAGE_ABC ? "?123" : "123",
                        PAGE_NUM, 1.5f);
            case ID_SYM2:
                return page(ID_SYM2, "#+=", PAGE_SYM, 1.5f);
            case ID_ABC:
                return page(ID_ABC, "ABC", PAGE_ABC, 1.5f);
            case ID_EMOJI:
                return page(ID_EMOJI, "☺", PAGE_EMOJI, 1.2f);
            default:
                return null;
        }
    }

    private static KeyModel act(String id, String label, int type, float width) {
        return new KeyModel(id, label, "", type, width, -1);
    }

    private static KeyModel page(String id, String label, int target, float width) {
        return new KeyModel(id, label, "", KeyModel.PAGE, width, target);
    }

    /* ==================================================================
     * Mutations (used by the editor)
     * ================================================================== */

    public static void setRowEnabled(int page, int role, boolean on) {
        Config cfg = Prefs.get();
        List<List<String>> rows = orderFor(page);
        int index = findRole(rows, role);

        if (!on) {
            if (index >= 0) {
                rows.remove(index);
            }
        } else if (index < 0) {
            int at;
            if (role == ROLE_BOTTOM) {
                at = rows.size();
            } else if (role == ROLE_NUMBER) {
                int tools = findRole(rows, ROLE_TOOLS);
                at = tools >= 0 ? tools + 1 : 0;
            } else {
                at = 0;
            }
            rows.add(Math.min(at, rows.size()), roleRow(role, page, cfg));
        }

        cfg.order.put(page, rows);
        syncToggleFlags(cfg);
    }

    public static void setEmojiKey(int page, boolean on) {
        Config cfg = Prefs.get();
        List<List<String>> rows = orderFor(page);
        int index = findRole(rows, ROLE_BOTTOM);

        if (index >= 0) {
            List<String> row = rows.get(index);
            if (on && !row.contains(ID_EMOJI)) {
                int at = row.indexOf(ID_SPACE);
                row.add(at < 0 ? row.size() : at, ID_EMOJI);
            } else if (!on) {
                row.remove(ID_EMOJI);
            }
            cfg.order.put(page, rows);
            syncToggleFlags(cfg);
        }
    }

    private static int findRole(List<List<String>> rows, int role) {
        for (int i = 0; i < rows.size(); i++) {
            if (isRole(rows.get(i), role)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isRole(List<String> row, int role) {
        switch (role) {
            case ROLE_TOOLS:
                return row.contains(ID_COPY) && row.contains(ID_PASTE);
            case ROLE_NUMBER:
                return row.contains("k_1") && row.contains("k_0")
                        && !row.contains(ID_SPACE);
            case ROLE_BOTTOM:
                return row.contains(ID_SPACE);
            default:
                return false;
        }
    }

    private static List<String> roleRow(int role, int page, Config cfg) {
        if (role == ROLE_TOOLS) {
            return new ArrayList<>(Arrays.asList(TOOLS));
        }
        if (role == ROLE_NUMBER) {
            return new ArrayList<>(Arrays.asList(NUMBERS));
        }
        return defaultBottom(page, cfg);
    }

    /**
     * Keeps the stored booleans in step with the rows. Always derived from the
     * ABC page so that toggling a row on another page cannot flip a flag.
     */
    private static void syncToggleFlags(Config cfg) {
        List<List<String>> rows = orderFor(PAGE_ABC);
        cfg.toolsRow = findRole(rows, ROLE_TOOLS) >= 0;
        cfg.numberRow = findRole(rows, ROLE_NUMBER) >= 0;
        cfg.emojiKey = anyRowContains(rows, ID_EMOJI);
        cfg.bottomBar = findRole(rows, ROLE_BOTTOM) >= 0;
    }

    /**
     * Moves a key inside the saved order.
     */
    public static void move(int page, int fromRow, int fromIdx, int toRow, int toIdx) {
        Config cfg = Prefs.get();
        List<List<String>> rows = orderFor(page);

        if (fromRow < 0 || fromRow >= rows.size()) {
            return;
        }
        List<String> src = rows.get(fromRow);
        if (fromIdx < 0 || fromIdx >= src.size()) {
            return;
        }
        String id = src.remove(fromIdx);

        int tr = Math.max(0, Math.min(toRow, rows.size() - 1));
        List<String> dst = rows.get(tr);

        // removing from the same row shifts every later index one left
        int ti = toIdx;
        if (tr == fromRow && fromIdx < toIdx) {
            ti--;
        }
        ti = Math.max(0, Math.min(ti, dst.size()));
        dst.add(ti, id);

        cfg.order.put(page, rows);
    }

    public static void resetPage(int page) {
        Config cfg = Prefs.get();
        cfg.order.remove(page);
    }

    public static void resetAll() {
        Config cfg = Prefs.get();
        cfg.order.clear();
        cfg.styles.clear();
        cfg.longPress.clear();
        cfg.labels.clear();
    }

    private static boolean anyRowContains(List<List<String>> rows, String id) {
        for (List<String> r : rows) {
            if (r.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public static String pageName(int page) {
        switch (page) {
            case PAGE_NUM:
                return "123";
            case PAGE_SYM:
                return "#+=";
            case PAGE_EMOJI:
                return "☺";
            case PAGE_ABC:
            default:
                return "ABC";
        }
    }
}
