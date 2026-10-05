package poker;

import java.nio.file.Path;

/** Creates the editable development settings once, preserving subsequent edits. */
public class ExportPokerUi {
    public static void main(String[] args) throws Exception {
        PokerUiSettings.writeDefaults(Path.of(args[0]));
    }
}
