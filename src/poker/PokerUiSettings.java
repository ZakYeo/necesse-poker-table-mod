package poker;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** A fresh, client-only settings snapshot for each opened poker window. */
final class PokerUiSettings {
    static final String DEFAULTS = """
        # Poker UI: save this file, then reopen the table BETWEEN HANDS.
        # Closing the table leaves your seat and folds unless you are all-in.
        # Coordinates are in game UI pixels; overlapping controls are not auto-arranged.
        # window: width,height (minimum 680,518)
        window=680,518
        # Labels: x,y,wrap-width,font-size
        title=18,10,644,24
        subtitle=18,40,644,14
        hand.title=18,280,156,14
        poker.title=18,368,644,12
        table.title=18,426,644,12
        footer.leave=18,484,644,12
        footer.help=18,500,644,12
        # Table origin (the six-seat diagram stays 652 x 206)
        table=14,68
        # Panels: x,y,width,height
        hand=18,300,156,64
        details=190,280,472,84
        # Card dimensions: width,height,gap (automatically fit inside hand panel)
        hand.cards=48,64,8
        # Buttons and raise input: x,y,width (height is the game's standard 32)
        fold=18,386,76
        call=100,386,102
        amount=208,386,88
        raise=302,386,98
        allin=406,386,90
        deal=514,386,148
        join=18,444,126
        invite=150,444,136
        dismiss=292,444,128
        leave=426,444,116
        close=548,444,114
        font.status=16
        font.notice=14
        font.history=12
        # Colours: #RRGGBB (the native button/window skin is unchanged)
        color.text=#29251D
        color.table.background=#1C2522
        color.table.rail=#6E4B2B
        color.table.trim=#C3954A
        color.table.felt=#125B3F
        color.table.line=#26764F
        color.seat=#E1E6D9
        color.actor=#FFD86A
        color.stack=#FFFFFF
        color.state=#B1C6B6
        color.pot=#FFDD8B
        color.street=#C5E0C7
        color.card.face=#FAF6E7
        color.card.red=#AF222A
        color.card.black=#181F24
        color.card.back=#46548B
        color.card.pattern=#7486BA
        # Optional PNG overrides: cards/7d.png, cards/Ac.png, cards/back.png, etc.
        # Ranks: 2 3 4 5 6 7 8 9 T J Q K A. Suits: c d h s.
        # Missing/invalid artwork uses generated cards with the palette above.
        """;
    private static final Properties DEFAULT_VALUES = defaults();
    private final Properties values;
    private final java.util.Map<String,Color> colors=new java.util.HashMap<>();
    final Path directory;
    final int width, height;

    private PokerUiSettings(Path directory, Properties values) {
        this.directory=directory;
        this.values=values;
        int[] window=numbers("window",2,1,1600);
        width=Math.max(680,window[0]); height=Math.max(518,Math.min(1200,window[1]));
    }

    private static Properties defaults() {
        Properties result=new Properties();
        try { result.load(new StringReader(DEFAULTS)); }
        catch(IOException impossible) { throw new AssertionError(impossible); }
        return result;
    }

    static PokerUiSettings load(Path configDirectory) {
        Path directory=configDirectory;
        Path source=configDirectory.resolve("source.txt");
        if(Files.exists(source)) {
            try {
                Path linked=Path.of(Files.readString(source,StandardCharsets.UTF_8).trim());
                if(!linked.isAbsolute() || !Files.isDirectory(linked)) throw new IOException("UI source directory is unavailable");
                directory=linked;
            } catch(IOException | RuntimeException e) { warn("Cannot use "+source+": "+e.getMessage()+"; using local defaults/settings"); }
        }
        Properties values=new Properties();
        Path file=directory.resolve("poker-ui.properties");
        try {
            writeDefaults(directory);
            try(Reader reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) { values.load(reader); }
        } catch(IOException | IllegalArgumentException | SecurityException e) {
            values.clear(); warn("Cannot read "+file+": "+e.getMessage()+"; using defaults");
        }
        return new PokerUiSettings(directory,values);
    }

    static void writeDefaults(Path directory) throws IOException {
        Files.createDirectories(directory);
        try { Files.writeString(directory.resolve("poker-ui.properties"),DEFAULTS,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); }
        catch(FileAlreadyExistsException ignored) { /* Never replace the user's edits. */ }
    }

    private int[] numbers(String key,int count,int min,int max) {
        String fallback=DEFAULT_VALUES.getProperty(key);
        String value=values.getProperty(key,fallback);
        try {
            String[] parts=value.split(",",-1);
            if(parts.length!=count) throw new IllegalArgumentException();
            int[] result=new int[count];
            for(int i=0;i<count;i++) {
                result[i]=Integer.parseInt(parts[i].trim());
                if(result[i]<min || result[i]>max) throw new IllegalArgumentException();
            }
            return result;
        } catch(IllegalArgumentException e) {
            warn("Invalid setting "+key+"="+value+"; using "+fallback);
            return java.util.Arrays.stream(fallback.split(",")).mapToInt(Integer::parseInt).toArray();
        }
    }

    Rectangle panel(String key) {
        int[] n=numbers(key,4,0,4096);
        int w=Math.max(key.equals("details")?120:64,Math.min(width,n[2]));
        int h=Math.max(32,Math.min(height,n[3]));
        return new Rectangle(Math.min(n[0],width-w),Math.min(n[1],height-h),w,h);
    }

    Rectangle label(String key) {
        int[] n=numbers(key,4,0,4096);
        int size=Math.max(10,Math.min(32,n[3]));
        int w=Math.max(40,Math.min(width,n[2]));
        return new Rectangle(Math.min(n[0],width-w),Math.min(n[1],height-size),w,size);
    }

    Rectangle button(String key) {
        int[] n=numbers(key,3,0,4096);
        int w=Math.max(64,Math.min(width,n[2]));
        return new Rectangle(Math.min(n[0],width-w),Math.min(n[1],height-32),w,32);
    }

    Rectangle table() {
        int[] n=numbers("table",2,0,4096);
        return new Rectangle(Math.min(n[0],width-652),Math.min(n[1],height-206),652,206);
    }

    int[] handCards() {
        int[] n=numbers("hand.cards",3,0,512);
        Rectangle panel=panel("hand");
        int gap=Math.min(n[2],panel.width-48);
        return new int[]{Math.max(24,Math.min(n[0],(panel.width-gap)/2)),Math.max(32,Math.min(n[1],panel.height)),gap};
    }

    int font(String key) { return numbers("font."+key,1,10,32)[0]; }

    Color color(String key) {
        return colors.computeIfAbsent(key,this::readColor);
    }

    private Color readColor(String key) {
        key="color."+key;
        String fallback=DEFAULT_VALUES.getProperty(key);
        String value=values.getProperty(key,fallback).trim();
        if(!value.matches("#[0-9a-fA-F]{6}")) {
            warn("Invalid colour "+key+"="+value+"; using "+fallback); value=fallback;
        }
        return Color.decode(value);
    }

    BufferedImage cardImage(int id) {
        String name=id<0?"back":("23456789TJQKA".charAt(id%13)+new String[]{"c","d","h","s"}[id/13]);
        Path file=directory.resolve("cards").resolve(name+".png");
        if(Files.exists(file)) {
            try(ImageInputStream input=ImageIO.createImageInputStream(file.toFile())) {
                if(input==null) throw new IOException("Cannot open image");
                java.util.Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext()) throw new IOException("Unrecognised image");
                ImageReader reader=readers.next();
                try {
                    reader.setInput(input);
                    if(!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0)>512 || reader.getHeight(0)>512)
                        throw new IOException("Use a PNG no larger than 512 x 512");
                    return reader.read(0);
                } finally { reader.dispose(); }
            } catch(IOException | RuntimeException e) { warn("Cannot read "+file+": "+e.getMessage()+"; using generated card"); }
        }
        return PokerCardArt.image(id,color("card.face"),color("card.red"),color("card.black"),color("card.back"),color("card.pattern"));
    }

    private static void warn(String message) { System.err.println("[Poker UI] "+message); }
}
