package pokersmoke;

import java.util.*;
import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.commands.*;
import necesse.engine.network.*;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.*;
import necesse.engine.network.networkInfo.InvalidNetworkInfo;
import necesse.engine.registries.*;
import necesse.engine.save.*;
import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.recipe.Recipes;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;
import poker.*;

/** Test-only companion mod. Never included in the distributed poker jar. */
@ModEntry
public class PokerSmokeMod {
    public void init() {
        CommandsManager.registerServerCommand(new ModularChatCommand("pokerworldfinalize","Finalize public Poker Playground access",PermissionLevel.SERVER,false) {
            @Override public void runModular(Client c,Server s,ServerClient sc,Object[] args,String[] errors,CommandLog log) {
                try { PokerWorldBuilder.finalizeWorld(s);log.add("Poker Playground finalized."); }
                catch(Throwable error){error.printStackTrace();log.add("WORLD FINALIZE FAIL: "+error);}
            }
        });
        CommandsManager.registerServerCommand(new ModularChatCommand("pokerworldbuild","Build separate Poker Playground",PermissionLevel.SERVER,false) {
            @Override public void runModular(Client c,Server s,ServerClient sc,Object[] args,String[] errors,CommandLog log) {
                try { PokerWorldBuilder.build(s); log.add("Poker Playground built."); }
                catch(Throwable error){error.printStackTrace();log.add("WORLD BUILD FAIL: "+error);}
            }
        });
        CommandsManager.registerServerCommand(new ModularChatCommand("pokerworldcheck","Verify Poker Playground",PermissionLevel.SERVER,false) {
            @Override public void runModular(Client c,Server s,ServerClient sc,Object[] args,String[] errors,CommandLog log) {
                try { PokerWorldBuilder.check(s);log.add("Poker Playground verified."); }
                catch(Throwable error){error.printStackTrace();log.add("WORLD CHECK FAIL: "+error);}
            }
        });
        CommandsManager.registerServerCommand(new ModularChatCommand("pokersmoke","Run isolated poker integration checks",PermissionLevel.SERVER,false) {
            @Override public void runModular(Client c,Server server,ServerClient sc,Object[] args,String[] errors,CommandLog log) {
                try { runChecks(server); log.add("POKER INTEGRATION PASS: placement, crafting, private packets, save refunds, NPC funds excluded, drops, real inventories, double-cashout protection."); }
                catch(Throwable error) { error.printStackTrace(); log.add("POKER INTEGRATION FAIL: "+error); }
            }
        });
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    private static int coins(PokerTableEntity table) { return table.getDroppedItems().stream().filter(i->i.item.getStringID().equals("coin")).mapToInt(i->i.getAmount()).sum(); }
    private static void runChecks(Server server) {
        Level level=server.world.getLevel(server.world.worldEntity.spawnLevelIdentifier);
        int x=server.world.worldEntity.spawnTile.x+10,y=server.world.worldEntity.spawnTile.y+10;
        int object=ObjectRegistry.getObjectID("pokertable");require(object>0,"Object registered");
        require(!Recipes.getRecipesFromResult(ItemRegistry.getItemID("pokertable")).isEmpty(),"Crafting recipe registered");
        ObjectRegistry.getObject(object).placeObject(level,x,y,0,true);
        require(level.entityManager.getObjectEntity(x,y) instanceof PokerTableEntity,"Placed object has poker entity");
        PokerTableEntity table=(PokerTableEntity)level.entityManager.getObjectEntity(x,y);
        table.game.add(new PokerGame.Seat(101,"Alice",false,200));
        table.game.add(new PokerGame.Seat(102,"Bob",false,200));
        table.game.add(new PokerGame.Seat(103,"NPC",true,200));
        table.game.start();
        FakeClient alice=new FakeClient(101,level),bob=new FakeClient(102,level),spectator=new FakeClient(104,level);
        for(FakeClient client:Arrays.asList(alice,bob,spectator)) {
            PokerContainer container=new PokerContainer(client,456,table);
            PokerView original=new PokerView(table,container);Packet packet=new Packet();original.write(new PacketWriter(packet));
            PokerView decoded=new PokerView(new PacketReader(packet));
            require(decoded.seed==456&&decoded.pot==3,"Packet snapshot roundtrip");
            for(int i=0;i<3;i++) {
                boolean owner=table.game.seats[i].id==client.authentication;
                require(decoded.cards[i][0]==(owner?table.game.seats[i].cards[0]:-1),"Private cards filtered for "+client.authentication);
            }
        }
        require(table.game.act(table.game.actor,30)==null,"Place actual bet");
        SaveData data=new SaveData("object");table.addSaveData(data);
        PokerTableEntity loaded=new PokerTableEntity(level,x,y);loaded.applyLoadData(data.toLoadData());
        require(!loaded.game.running&&loaded.game.occupied()==0,"Loaded hand cancelled");
        require(coins(loaded)==400,"Loaded refunds include player stacks and bets, exclude NPC funds");
        require(coins(table)==400,"Table destruction returns player escrow only");
        SaveData data2=new SaveData("object");loaded.addSaveData(data2);
        PokerTableEntity twice=new PokerTableEntity(level,x,y);twice.applyLoadData(data2.toLoadData());
        require(coins(twice)==400,"Repeated save/load does not duplicate refunds");
        table.remove();require(table.game.occupied()==0,"Removed table clears live escrow");
        level.setObject(x,y,0);
        inventoryChecks(server,level,x,y);
    }
    private static int wallet(ServerClient c,Level level) { return c.playerMob.getInv().main.getAmount(level,c.playerMob,ItemRegistry.getItem("coin"),"poker"); }
    private static void inventoryChecks(Server server,Level level,int x,int y) {
        ServerClient a=ServerClient.getNewPlayerClient(server,501,new InvalidNetworkInfo(),0,501);
        ServerClient b=ServerClient.getNewPlayerClient(server,502,new InvalidNetworkInfo(),1,502);
        a.playerMob.getInv().main.setItem(0,new InventoryItem("coin",500));
        b.playerMob.getInv().main.setItem(0,new InventoryItem("coin",500));
        PokerTableEntity table=new PokerTableEntity(level,x,y);
        PokerContainer ca=new PokerContainer(a,789,table),cb=new PokerContainer(b,790,table);
        table.action(ca,PokerContainer.JOIN);table.action(ca,PokerContainer.DISMISS);
        require(wallet(a,level)==300&&coins(table)==200,"Buy-in removes exactly 200 inventory coins");
        table.action(ca,PokerContainer.JOIN);require(wallet(a,level)==300,"Repeated join does not charge");
        table.action(cb,PokerContainer.JOIN);table.action(ca,PokerContainer.DEAL);
        require(table.game.running,"Two real inventory clients can deal");
        table.action(ca,PokerGame.ALL_IN);table.action(cb,PokerGame.CALL);
        require(!table.game.running,"All-in game settles");
        table.action(ca,PokerContainer.CASH_OUT);table.action(cb,PokerContainer.CASH_OUT);
        require(wallet(a,level)+wallet(b,level)==1000&&coins(table)==0,"Cash-out returns pot and stacks without minting player coins");
        table.action(ca,PokerContainer.CASH_OUT);table.action(cb,PokerContainer.CASH_OUT);
        require(wallet(a,level)+wallet(b,level)==1000,"Repeated cash-out cannot duplicate coins");
        // A reload creates a claim owned by the original authentication ID.
        a.playerMob.getInv().main.setItem(0,new InventoryItem("coin",500));
        table.action(ca,PokerContainer.JOIN);table.action(ca,PokerContainer.DISMISS);
        int beforeA=wallet(a,level),beforeB=wallet(b,level);
        SaveData saved=new SaveData("object");table.addSaveData(saved);
        PokerTableEntity restored=new PokerTableEntity(level,x,y);restored.applyLoadData(saved.toLoadData());
        restored.claim(b);require(wallet(b,level)==beforeB,"Another player cannot claim saved coins");
        restored.claim(a);require(wallet(a,level)==beforeA+200,"Original player receives saved refund");
        restored.claim(a);require(wallet(a,level)==beforeA+200&&coins(restored)==0,"Saved refund is paid once");
        table.remove();restored.remove();a.dispose();b.dispose();
    }
    private static class FakeClient extends NetworkClient {
        private final Level level;
        FakeClient(long auth,Level level) {super(0,auth);this.level=level;playerMob=new PlayerMob(auth,this);playerMob.setLevel(level);}
        @Override public boolean pvpEnabled(){return false;}
        @Override public String getName(){return "Test";}
        @Override public LevelIdentifier getLevelIdentifier(){return level.getIdentifier();}
    }
}
