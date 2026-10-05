package poker;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

public class PokerUiSettingsTest {
    private static int checks;
    private static void check(boolean ok,String message) {
        checks++;
        if(!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory(Path.of("build"),"ui-settings-test-");
        try {
            Path config=root.resolve("config");
            PokerUiSettings initial=PokerUiSettings.load(config);
            check(Files.isRegularFile(config.resolve("poker-ui.properties")),"First open creates editable defaults");
            check(initial.width==680 && initial.height==518,"Default window preserves existing layout");
            Path file=config.resolve("poker-ui.properties");
            Files.writeString(file,"fold=24,380,100\nfont.status=20\ncolor.table.felt=#123456\n");
            PokerUiSettings changed=PokerUiSettings.load(config);
            check(changed.button("fold").equals(new Rectangle(24,380,100,32)),"Reopen reads changed layout");
            check(initial.button("fold").x==18,"Already open window keeps its settings snapshot");
            check(changed.font("status")==20,"Font setting reloads");
            check(changed.color("table.felt").equals(new Color(0x123456)),"Colour setting reloads");
            check(changed.button("close").width==114,"Omitted values use defaults");
            check(Files.readString(file).startsWith("fold=24"),"Loading preserves user's file");
            Files.writeString(file,"window=bad\nfold=-9,broken,999\nfont.status=9999\ncolor.text=oops\n");
            PokerUiSettings invalid=PokerUiSettings.load(config);
            check(invalid.width==680,"Invalid window falls back");
            check(invalid.button("fold").x==18,"Invalid coordinates fall back");
            check(invalid.font("status")==16,"Invalid font falls back");
            check(invalid.color("text").equals(new Color(0x29251D)),"Invalid colour falls back");
            Files.writeString(file,"details=4000,4000,1,1\nclose=4000,4000,4000\nhand=4000,4000,64,32\nhand.cards=512,512,512\n");
            PokerUiSettings bounded=PokerUiSettings.load(config);
            Rectangle bounds=new Rectangle(0,0,bounded.width,bounded.height);
            check(bounds.contains(bounded.panel("details")),"Details remain inside form with room for scrollbar");
            check(bounds.contains(bounded.button("close")),"Close button remains reachable");
            int[] cards=bounded.handCards();
            check(cards[0]*2+cards[2]<=bounded.panel("hand").width && cards[1]<=bounded.panel("hand").height,"Cards fit their panel");
            Files.writeString(file,"font.status=22\nbad=\\uZZZZ\n");
            check(PokerUiSettings.load(config).font("status")==16,"Malformed properties discard partial reads");

            Path linked=root.resolve("linked").toAbsolutePath(); Files.createDirectories(linked);
            Files.writeString(linked.resolve("poker-ui.properties"),"font.status=21\n");
            Files.writeString(config.resolve("source.txt"),linked.toString());
            check(PokerUiSettings.load(config).font("status")==21,"Development source link is honoured");
            Files.writeString(linked.resolve("poker-ui.properties"),"font.status=23\ncolor.card.face=#123456\n");
            PokerUiSettings linkedSettings=PokerUiSettings.load(config);
            check(linkedSettings.font("status")==23,"Linked workspace edits reload");
            check(linkedSettings.cardImage(0).getRGB(5,50)==new Color(0x123456).getRGB(),"Generated cards use palette");
            Files.createDirectories(linked.resolve("cards"));
            Path art=linked.resolve("cards/7d.png");
            BufferedImage custom=new BufferedImage(48,64,BufferedImage.TYPE_INT_ARGB);
            custom.setRGB(10,10,Color.MAGENTA.getRGB()); ImageIO.write(custom,"png",art.toFile());
            check(linkedSettings.cardImage(18).getRGB(10,10)==Color.MAGENTA.getRGB(),"Rank/suit filename selects correct card");
            custom.setRGB(10,10,Color.BLUE.getRGB()); ImageIO.write(custom,"png",art.toFile());
            check(PokerUiSettings.load(config).cardImage(18).getRGB(10,10)==Color.BLUE.getRGB(),"Edited artwork is read again");
            Files.writeString(art,"broken image");
            check(PokerUiSettings.load(config).cardImage(18).getWidth()==48,"Broken art falls back");
            ImageIO.write(new BufferedImage(513,1,BufferedImage.TYPE_INT_ARGB),"png",art.toFile());
            check(linkedSettings.cardImage(18).getWidth()==48,"Oversized images fall back before decoding");
            Files.delete(art);
            check(linkedSettings.cardImage(18).getHeight()==64,"Removed override restores generated art");
            check(linkedSettings.cardImage(-1).getHeight()==64,"Face-down cards have artwork");
            Files.writeString(config.resolve("source.txt"),root.resolve("missing").toAbsolutePath().toString());
            Files.writeString(file,"font.status=19\n");
            check(PokerUiSettings.load(config).font("status")==19,"Missing development folder falls back to local settings");
            Files.delete(config.resolve("source.txt")); Files.delete(file);
            check(PokerUiSettings.load(config).font("status")==16,"Deleting file restores defaults");
            System.out.println("PASS: "+checks+" UI settings checks (reloads, bounds, fallbacks, artwork and source link).");
        } finally {
            // Only remove this run's freshly created scratch directory under build.
            Path target=root.toAbsolutePath().normalize();
            if(!target.startsWith(Path.of("build").toAbsolutePath().normalize())) throw new IllegalStateException("Unexpected test path");
            try(var paths=Files.walk(target)) {
                for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
