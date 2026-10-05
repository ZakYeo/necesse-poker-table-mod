package poker;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** Card faces use drawn suit symbols, independent of the game's font glyphs. */
final class PokerCardArt {
    static BufferedImage image(int id) {
        return image(id,new Color(250,246,231),new Color(175,34,42),new Color(24,31,36),new Color(70,84,139),new Color(116,134,186));
    }

    static BufferedImage image(int id,Color face,Color red,Color black,Color back,Color pattern) {
        BufferedImage image=new BufferedImage(48,64,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(9,28,23)); g.fillRoundRect(2,2,46,62,6,6);
        g.setColor(face); g.fillRoundRect(0,0,46,62,6,6);
        if(id<0) {
            g.setColor(back); g.fillRect(4,4,38,54);
            g.setColor(pattern);
            for(int x=6;x<41;x+=5) for(int y=6;y<57;y+=5) g.fillRect(x,y,2,2);
        } else {
            int suit=id/13;
            g.setColor(suit==1||suit==2?red:black);
            String rank=id%13==8?"10":String.valueOf("23456789TJQKA".charAt(id%13));
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
            g.drawString(rank,4,21);
            suit(g,suit,24,35);
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));
            g.drawString(rank,42-g.getFontMetrics().stringWidth(rank),57);
        }
        g.dispose();
        return image;
    }

    private static void suit(Graphics2D graphics,int suit,int x,int y) {
        Graphics2D g=(Graphics2D)graphics.create(); g.translate(x,y);
        if(suit==1) {
            g.fillPolygon(new int[]{0,9,0,-9},new int[]{-10,0,10,0},4);
        } else if(suit==0) {
            g.fillOval(-5,-11,10,10); g.fillOval(-10,-4,11,11); g.fillOval(-1,-4,11,11);
            g.fillPolygon(new int[]{0,-4,4},new int[]{0,11,11},3);
        } else {
            Path2D heart=new Path2D.Double();
            heart.moveTo(0,9); heart.curveTo(-18,-2,-7,-15,0,-6);
            heart.curveTo(7,-15,18,-2,0,9); heart.closePath();
            if(suit==3) {
                g.rotate(Math.PI); g.fill(heart); g.rotate(-Math.PI);
                g.fillPolygon(new int[]{0,-4,4},new int[]{0,11,11},3);
            } else g.fill(heart);
        }
        g.dispose();
    }
}
