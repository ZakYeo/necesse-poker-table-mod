package pokersmoke;

import java.awt.Point;
import java.util.*;
import necesse.engine.GameRaidFrequency;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.networkInfo.InvalidNetworkInfo;
import necesse.engine.network.server.*;
import necesse.engine.registries.*;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.objectEntity.*;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.*;
import poker.*;

/** One-time builder, only for the separate Poker Playground world. */
public final class PokerWorldBuilder {
    private static final long OWNER = 76561198067658872L;
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static void guard(Server server){require(server.getSettings().creationSettings.worldFilePath.getName().equals("Poker Playground.zip"),"Builder only works in Poker Playground.zip");}
    private static void place(Level level,String id,int x,int y,int rotation){
        require(ObjectRegistry.getObjectID(id)>0,"Missing object: "+id);
        ObjectRegistry.getObject(id).placeObject(level,x,y,rotation,true);
    }
    private static InventoryObjectEntity chest(Level level,int x,int y,String name){
        place(level,"oakchest",x,y,0);
        InventoryObjectEntity chest=(InventoryObjectEntity)level.entityManager.getObjectEntity(x,y);
        chest.setInventoryName(name);return chest;
    }
    private static ServerClient owner(Server server){
        ServerClient c=ServerClient.getNewPlayerClient(server,OWNER,new InvalidNetworkInfo(),0,OWNER);
        c.playerMob.playerName="BIMBO BOY";return c;
    }
    public static void build(Server server){
        guard(server);
        Level level=server.world.getLevel(server.world.worldEntity.spawnLevelIdentifier);
        Point spawn=server.world.worldEntity.spawnTile;
        int x=spawn.x,y=spawn.y;
        require(!(level.entityManager.getObjectEntity(x,y-2) instanceof PokerTableEntity),"Playground already built; use pokerworldcheck");
        level.regionManager.ensureTilesAreLoaded(x-20,y-18,x+20,y+16);
        int grass=TileRegistry.getTileID("grasstile"),wood=TileRegistry.getTileID("woodfloor"),stone=TileRegistry.getTileID("stonefloor");
        // All terrain changes are in this new world's starting clearing.
        for(int dx=-18;dx<=18;dx++)for(int dy=-16;dy<=14;dy++){
            level.setObject(x+dx,y+dy,0);level.setTile(x+dx,y+dy,grass);
        }
        for(int dx=-15;dx<=15;dx++)for(int dy=-6;dy<=6;dy++)level.setTile(x+dx,y+dy,stone);
        for(int dx=-6;dx<=6;dx++)for(int dy=-4;dy<=4;dy++)level.setTile(x+dx,y+dy,wood);
        List<Point> beds=new ArrayList<>();
        for(int room=0;room<4;room++){
            int left=x-14+room*7,top=y-14;
            for(int dx=0;dx<=6;dx++)for(int dy=0;dy<=6;dy++){
                level.setTile(left+dx,top+dy,wood);
                if(dx==0||dx==6||dy==0||dy==6)place(level,"woodwall",left+dx,top+dy,0);
            }
            place(level,"wooddoor",left+3,top+6,0);
            place(level,"oakbed",left+2,top+3,0);beds.add(new Point(left+2,top+3));
            place(level,"oakdesk",left+4,top+2,0);
            place(level,"oakchair",left+4,top+3,0);
            place(level,"oakbookshelf",left+1,top+1,0);
            place(level,"torch",left+5,top+5,0);
        }
        place(level,"pokertable",x,y-2,0);
        place(level,"settlementflag",x-9,y,0);
        place(level,"carpentersbench",x+9,y+2,0);
        for(int dx:new int[]{-6,6})for(int dy:new int[]{-4,4})place(level,"torch",x+dx,y+dy,0);
        InventoryObjectEntity money=chest(level,x+3,y,"POKER MONEY - 10,000 coins");
        int remaining=10000,slot=0,stack=Math.min(1000,ItemRegistry.getItem("coin").getStackSize());
        while(remaining>0){require(slot<money.inventory.getSize(),"Coin chest too small");int count=Math.min(stack,remaining);money.inventory.setItem(slot++,new InventoryItem("coin",count));remaining-=count;}
        InventoryObjectEntity food=chest(level,x-5,y,"Settler pantry");
        for(int i=0;i<10;i++)food.inventory.setItem(i,new InventoryItem("bread",Math.min(50,ItemRegistry.getItem("bread").getStackSize())));
        place(level,"sign",x+3,y+3,0);
        ((SignObjectEntity)level.entityManager.getObjectEntity(x+3,y+3)).setText("POKER PLAYGROUND\nTake coins from the chest beside you.\nInteract with the green table, Buy in: 200, then Invite settlers and Deal hand.\nFour settlers live just north. NPC rebuys are unlimited.");
        ServerClient owner=owner(server);
        ServerSettlementData settlement=SettlementsWorldData.getSettlementsData(server).getOrCreateServerData(level,x-9,y);
        settlement.networkData.setFlagTile(x-9,y);settlement.worldData.updateSettlement(settlement);
        settlement.networkData.setOwner(null);settlement.networkData.setPrivate(false);settlement.networkData.markPreventDisbanding();
        settlement.networkData.setName(new StaticMessage("Poker Playground"));
        settlement.storageManager.assignStorage(x-5,y,true);
        String[] kinds={"farmerhuman","blacksmithhuman","anglerhuman","guardhuman"};
        String[] names={"Ace","Chip","River","Bluff"};
        for(int i=0;i<4;i++){
            HumanMob mob=(HumanMob)MobRegistry.getMob(kinds[i],level);
            mob.setPos((x-4+i*3)*32+16,(y-5)*32+16,true);
            level.entityManager.mobs.add(mob);
            LevelSettler settler=new LevelSettler(settlement,mob);
            mob.makeSettler(settlement,settler);
            SettlementBed bed=settlement.addOrValidateBed(beds.get(i).x,beds.get(i).y);
            require(bed!=null,"Bed exists for "+names[i]);
            require(settlement.moveSettler(settler,bed,null),"Assigned room for "+names[i]);
            settlement.renameSettler(mob.getUniqueID(),names[i]);
            mob.hungerLevel=1.0f;mob.resetHungerStrikeCooldownBuffer();mob.resetRecreationStrikeCooldownBuffer();
            mob.clearCommandsOrders(null);
        }
        server.world.settings.raidFrequency=GameRaidFrequency.NEVER;
        server.world.settings.disableMobSpawns=true;
        server.world.settings.playerHunger=false;
        server.world.settings.canSettlersDie=false;
        server.world.settings.allowOutsideCharacters=true;
        server.world.settings.unloadSettlements=false;
        server.world.settings.saveSettings();
        // Remove any generated hostile mobs in the immediate clearing.
        level.entityManager.mobs.stream().filter(m->m.isHostile&&Math.abs(m.getTileX()-x)<24&&Math.abs(m.getTileY()-y)<24).forEach(m->m.remove());
        owner.dispose();
        check(server);
        System.out.println("POKER WORLD BUILT at spawn "+x+", "+y+". 10,000 coins; 4 recruited settlers with beds.");
    }
    public static void check(Server server){
        guard(server);
        Level level=server.world.getLevel(server.world.worldEntity.spawnLevelIdentifier);
        Point spawn=server.world.worldEntity.spawnTile;int x=spawn.x,y=spawn.y;
        level.regionManager.ensureTilesAreLoaded(x-20,y-18,x+20,y+16);
        PokerTableEntity table=(PokerTableEntity)level.entityManager.getObjectEntity(x,y-2);
        require(table!=null,"Poker table saved");
        InventoryObjectEntity money=(InventoryObjectEntity)level.entityManager.getObjectEntity(x+3,y);
        require(money!=null&&money.inventory.getAmount(level,null,ItemRegistry.getItem("coin"),"poker")==10000,"Chest contains 10,000 coins");
        ServerSettlementData settlement=SettlementsWorldData.getSettlementsData(server).getOrLoadServerDataAtTile(level.getIdentifier(),x-9,y);
        if(settlement!=null)System.out.println("Playground settlers="+settlement.countTotalSettlers()+", beds assigned="+settlement.countSettlersWithBed());
        require(settlement!=null&&settlement.countTotalSettlers()>=4&&settlement.countSettlersWithBed()>=4,"Four recruited settlers and four assigned beds");
        require(!settlement.networkData.isPrivate()&&settlement.networkData.hasFlag(),"Public test settlement has a valid flag");
        ServerClient owner=owner(server);owner.playerMob.setPos(x*32+16,y*32+16,true);
        owner.playerMob.getInv().main.setItem(0,new InventoryItem("coin",200));
        PokerContainer container=new PokerContainer(owner,9102,table);
        table.action(container,PokerContainer.JOIN);table.action(container,PokerContainer.INVITE);
        Set<String> invitedNames=new HashSet<>();for(PokerGame.Seat seat:table.game.seats)if(seat!=null&&seat.npc)invitedNames.add(seat.name);
        System.out.println("Invited settlers: "+invitedNames);
        require(invitedNames.containsAll(Arrays.asList("Ace","Chip","River","Bluff")),"All four actual settlers accept poker invitations");
        table.leave(owner);owner.dispose();
        require(table.game.occupied()==0,"Test invitations released, ready for player");
        System.out.println("POKER WORLD CHECK PASS: table, 10,000 coins, public access, four beds, four inviteable settlers.");
    }
    public static void finalizeWorld(Server server){
        guard(server);Level level=server.world.getLevel(server.world.worldEntity.spawnLevelIdentifier);
        Point spawn=server.world.worldEntity.spawnTile;level.regionManager.ensureTilesAreLoaded(spawn.x-20,spawn.y-18,spawn.x+20,spawn.y+16);
        ServerSettlementData settlement=SettlementsWorldData.getSettlementsData(server).getOrLoadServerDataAtTile(level.getIdentifier(),spawn.x-9,spawn.y);
        require(settlement!=null,"Playground settlement exists");
        settlement.networkData.setOwner(null);settlement.networkData.setPrivate(false);settlement.networkData.markPreventDisbanding();
        settlement.networkData.setFlagTile(spawn.x-9,spawn.y);
        settlement.worldData.updateSettlement(settlement);
        settlement.networkData.serverTick();
        settlement.worldData.saveSettlements();
        check(server);
    }
}
