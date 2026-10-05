package poker;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.message.*;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.registries.ContainerRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.TextureDrawOptionsEnd;
import necesse.gfx.drawables.*;
import necesse.gfx.gameTexture.GameTexture;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.furniture.TableObject;
import necesse.level.maps.Level;

public class PokerTableObject extends TableObject {
    private GameTexture texture;
    public PokerTableObject() {
        super(new Rectangle(2, 5, 28, 24), new Color(27, 103, 69));
        toolType = ToolType.ALL; objectHealth = 100; isLightTransparent = true;
        hoverHitbox = new Rectangle(0, -16, 32, 48);
    }
    @Override public GameMessage getNewLocalization() { return new StaticMessage("Poker Table"); }
    @Override public void loadTextures() { super.loadTextures(); texture = createTexture(); }
    @Override public GameTexture generateItemTexture() { return createTexture(); }
    // Code-drawn pixel art: wooden rail, green felt, cards and coin stacks.
    private GameTexture createTexture() {
        BufferedImage image = new BufferedImage(32, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(48, 28, 24)); g.fillRect(5, 25, 5, 13); g.fillRect(23, 25, 4, 13);
        g.setColor(new Color(108, 64, 38)); g.fillRect(6, 25, 2, 11); g.fillRect(24, 25, 2, 11);
        g.setColor(new Color(45, 26, 22)); g.fillRoundRect(0, 4, 32, 29, 10, 10);
        g.setColor(new Color(151, 96, 52)); g.fillRoundRect(0, 1, 32, 28, 10, 10);
        g.setColor(new Color(202, 149, 81)); g.drawRoundRect(1, 2, 29, 25, 8, 8);
        g.setColor(new Color(19, 69, 50)); g.fillRoundRect(4, 5, 24, 20, 6, 6);
        g.setColor(new Color(34, 126, 80)); g.fillRoundRect(5, 5, 22, 17, 5, 5);
        g.setColor(new Color(58, 149, 91)); g.drawLine(8, 7, 22, 7);
        g.setColor(new Color(247, 238, 203)); g.fillRect(10, 12, 5, 7); g.fillRect(16, 11, 5, 7);
        g.setColor(new Color(183, 46, 45)); g.fillRect(11, 13, 2, 2);
        g.setColor(new Color(34, 39, 43)); g.fillRect(17, 12, 2, 2);
        g.setColor(new Color(144, 94, 24)); g.fillRect(22, 18, 4, 3); g.fillRect(6, 9, 3, 3);
        g.setColor(new Color(249, 204, 70)); g.fillRect(22, 17, 4, 2); g.fillRect(6, 8, 3, 2);
        g.dispose(); return new GameTexture("poker_table_generated", image).makeFinal();
    }
    @Override public void addDrawables(List<LevelSortedDrawable> list, OrderableDrawables tiles, Level level, int x, int y, TickManager tick, GameCamera camera, PlayerMob player) {
        final TextureDrawOptionsEnd draw = texture.initDraw().light(level.getLightLevel(x,y)).pos(camera.getTileDrawX(x), camera.getTileDrawY(y)-8);
        list.add(new LevelSortedDrawable(this,x,y) {
            @Override public int getSortY() { return 20; }
            @Override public void draw(TickManager tick) { draw.draw(); }
        });
    }
    @Override public void drawPreview(Level level, int x, int y, int rotation, float alpha, PlayerMob player, GameCamera camera) {
        texture.initDraw().alpha(alpha).draw(camera.getTileDrawX(x),camera.getTileDrawY(y)-8);
    }
    @Override public boolean canInteract(Level level, int x, int y, PlayerMob player) { return true; }
    @Override public void interact(Level level, int x, int y, PlayerMob player) {
        if (level.isServer()) ContainerRegistry.openAndSendContainer(player.getServerClient(),
            PacketOpenContainer.ObjectEntity(PokerMod.CONTAINER, level.entityManager.getObjectEntity(x,y)));
    }
    @Override public ObjectEntity getNewObjectEntity(Level level, int x, int y) { return new PokerTableEntity(level,x,y); }
}
