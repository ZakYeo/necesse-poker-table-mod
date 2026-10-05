package poker;

import java.awt.Point;
import java.util.*;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.save.*;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;

public class PokerTableEntity extends ObjectEntity {
    public final PokerGame game = new PokerGame();
    public final Set<PokerContainer> viewers = new LinkedHashSet<>();
    private final Map<Long,Integer> credits = new LinkedHashMap<>();
    private final Map<Integer,Point> invited = new LinkedHashMap<>();
    private boolean releaseLoadedNpcs;
    private int ticks, turnTicks;
    public PokerTableEntity(Level level, int x, int y) { super(level,"pokertable",x,y); shouldSave = true; }
    public int secondsLeft() { return Math.max(0, 45 - turnTicks / 20); }
    private ServerClient online(long id) {
        return getLevel().getServer().streamClients().filter(c -> c.authentication == id && !c.isDisposed()).findFirst().orElse(null);
    }
    private HumanMob human(long id) {
        return getLevel().entityManager.mobs.stream().filter(m -> m instanceof HumanMob && m.getUniqueID() == id).map(m -> (HumanMob)m).findFirst().orElse(null);
    }
    public int host() { for (int i = 0; i < PokerGame.SEATS; i++) if (game.seats[i] != null && !game.seats[i].npc && !game.seats[i].leaving) return i; return -1; }
    private void credit(long id, int amount) { if (amount > 0) credits.merge(id,amount,Math::addExact); }
    public void claim(ServerClient client) {
        Integer amount = credits.remove(client.authentication);
        if (amount != null && amount > 0) client.playerMob.getInv().addItemsDropRemaining(new InventoryItem("coin",amount), "poker", client.playerMob, true, false);
    }
    public void action(PokerContainer container, int action) {
        ServerClient client = container.client.getServerClient();
        int index = game.find(client.authentication,false); container.notice = "";
        if (action == PokerContainer.JOIN) {
            if (game.running) { container.notice = "Wait until the current hand ends."; send(container); return; }
            claim(client);
            if (index < 0 && game.occupied() >= PokerGame.SEATS) { container.notice = "The table is full."; send(container); return; }
            if (index >= 0 && game.seats[index].stack > 0) { container.notice = "Cash out first to change your buy-in."; send(container); return; }
            if (client.playerMob.getInv().main.getAmount(getLevel(),client.playerMob,ItemRegistry.getItem("coin"),"poker") < PokerGame.BUY_IN) {
                container.notice = "You need 200 coins in your inventory."; send(container); return;
            }
            int removed = client.playerMob.getInv().main.removeItems(getLevel(),client.playerMob,ItemRegistry.getItem("coin"),PokerGame.BUY_IN,"poker");
            if (removed != PokerGame.BUY_IN) { credit(client.authentication,removed); claim(client); return; }
            if (index >= 0) { game.seats[index].stack = removed; game.seats[index].leaving = false; }
            else index = game.add(new PokerGame.Seat(client.authentication,client.getName(),false,removed));
            if (index < 0) { credit(client.authentication,removed); claim(client); }
            else { game.log(client.getName() + " buys in for 200 coins."); if (game.occupied() == 1) invite(client,2); }
        } else if (action == PokerContainer.CASH_OUT) {
            if (index >= 0) leave(client); else claim(client);
            if (game.running) container.notice = "Your remaining stack is returned; all-in winnings can be collected here after the hand.";
        } else if (action == PokerContainer.INVITE || action == PokerContainer.DISMISS || action == PokerContainer.DEAL) {
            if (index < 0 || index != host()) container.notice = "Only the first seated player can manage the table.";
            else if (game.running) container.notice = "Wait until the hand ends.";
            else if (action == PokerContainer.INVITE) {
                int count = invite(client, PokerGame.SEATS);
                if (count == 0) container.notice = "No available settlers nearby. Put the table in your settlement and leave space around it.";
            } else if (action == PokerContainer.DISMISS) dismiss();
            else {
                removeUnavailableNpcs();
                boolean arriving = false;
                for (PokerGame.Seat seat : game.seats) if (seat != null && seat.npc) { HumanMob mob = human(seat.id); if (mob != null && distance(mob) > 112) arriving = true; }
                if (arriving) container.notice = "Waiting for invited settlers to reach the table. Dismiss them if the path is blocked.";
                else { game.start(); turnTicks = 0; }
            }
        } else if (index >= 0) {
            String error = game.act(index,action);
            if (error != null) container.notice = error; else turnTicks = 0;
        }
        cleanSeats(); broadcast();
    }
    private double distance(HumanMob mob) { return Point.distance(mob.getX(),mob.getY(),tileX*32+16,tileY*32+16); }
    private int invite(ServerClient client, int limit) {
        int added = 0;
        List<HumanMob> candidates = getLevel().entityManager.mobs.stream().filter(m -> m instanceof HumanMob).map(m -> (HumanMob)m)
            .filter(m -> m.isSettler() && !m.removed() && !m.isDowned() && !m.hasCommandOrders() && m.canBeCommanded(client,false)
                && distance(m) < 800 && m.getSettlerSettlementNetworkData() != null
                && m.getSettlerSettlementNetworkData().isTileWithinBounds(tileX,tileY))
            .sorted(Comparator.comparingDouble(this::distance)).collect(java.util.stream.Collectors.toList());
        for (HumanMob mob : candidates) {
            if (added >= limit || game.occupied() >= PokerGame.SEATS) break;
            if (game.find(mob.getUniqueID(),true) >= 0) continue;
            Point place = freeSpot(); if (place == null) break;
            int index = game.add(new PokerGame.Seat(mob.getUniqueID(),mob.getSettlerName(),true,PokerGame.BUY_IN));
            if (index < 0) break;
            invited.put(mob.getUniqueID(),place); mob.commandGuard(client,place.x,place.y);
            mob.setActivity("poker",100,new StaticMessage("Playing poker"));
            added++; game.log(mob.getSettlerName() + " joins the table.");
        }
        return added;
    }
    private Point freeSpot() {
        int[][] offsets = {{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,1},{1,-1},{-1,1}};
        for (int[] p : offsets) {
            Point spot = new Point((tileX+p[0])*32+16,(tileY+p[1])*32+16);
            if (!getLevel().isSolidTile(tileX+p[0],tileY+p[1]) && !invited.containsValue(spot)) return spot;
        }
        return null;
    }
    private void release(int id) {
        Point point = invited.remove(id); HumanMob mob = human(id);
        if (mob != null) {
            if (point != null && point.equals(mob.commandGuardPoint)) mob.clearCommandsOrders(null);
            mob.clearActivity("poker");
        }
    }
    private void dismiss() {
        for (int i = 0; i < PokerGame.SEATS; i++) if (game.seats[i] != null && game.seats[i].npc) {
            release((int)game.seats[i].id); game.seats[i] = null;
        }
    }
    private void removeUnavailableNpcs() {
        int oldActor = game.actor;
        for (int i = 0; i < PokerGame.SEATS; i++) {
            PokerGame.Seat seat = game.seats[i]; if (seat == null || !seat.npc) continue;
            HumanMob mob = human(seat.id); Point point = invited.get((int)seat.id);
            if (mob == null || mob.removed() || mob.isDowned() || !Objects.equals(point,mob.commandGuardPoint)) game.leave(i);
        }
        cleanSeats();
        if (oldActor != game.actor) turnTicks = 0;
    }
    public void leave(ServerClient client) {
        int index = game.find(client.authentication,false);
        if (index >= 0) {
            PokerGame.Seat seat = game.seats[index];
            int oldActor = game.actor; game.leave(index); if (oldActor != game.actor) turnTicks = 0;
            // Uncommitted coins can be returned immediately, including when folding.
            credit(seat.id,seat.stack); seat.stack = 0;
            if (!game.running) cleanSeats();
        }
        if (!client.isDisposed()) claim(client);
        broadcast();
    }
    private void cleanSeats() {
        if (game.running) return;
        for (int i = 0; i < PokerGame.SEATS; i++) {
            PokerGame.Seat seat = game.seats[i]; if (seat == null || !seat.leaving) continue;
            if (seat.npc) release((int)seat.id);
            else {
                credit(seat.id,seat.stack);
                ServerClient client = online(seat.id);
                if (client != null && client.isSamePlace(getLevel())) claim(client);
            }
            game.seats[i] = null;
        }
        if (host() < 0) dismiss();
    }
    @Override public void serverTick() {
        super.serverTick(); ticks++;
        if (releaseLoadedNpcs) {
            for (int id : new ArrayList<>(invited.keySet())) release(id);
            releaseLoadedNpcs = false;
        }
        if (ticks % 20 == 0) {
            removeUnavailableNpcs();
            for (PokerGame.Seat seat : game.seats) if (seat != null && seat.npc) {
                HumanMob mob = human(seat.id);
                if (mob != null) mob.setActivity("poker",100,new StaticMessage("Playing poker"));
            }
        }
        if (game.running && game.actor >= 0) {
            turnTicks++;
            PokerGame.Seat acting = game.seats[game.actor];
            if ((acting.npc && turnTicks >= 28) || (!acting.npc && turnTicks >= 900)) {
                int choice = acting.npc ? game.chooseNpcAction() : game.toCall(game.actor) == 0 ? PokerGame.CALL : PokerGame.FOLD;
                game.act(game.actor,choice); turnTicks = 0; cleanSeats(); broadcast();
            }
        }
        if (ticks % 10 == 0) broadcast();
    }
    public void send(PokerContainer viewer) {
        if (!viewer.isClosed()) new PokerView(this,viewer).applyAndSendToClient(viewer.client.getServerClient());
    }
    private void broadcast() { for (PokerContainer viewer : new ArrayList<>(viewers)) send(viewer); }
    @Override public void addSaveData(SaveData save) {
        super.addSaveData(save);
        // Store a cancellation/refund snapshot. Never persist a partly dealt deck.
        Map<Long,Integer> refunds = new LinkedHashMap<>(credits);
        for (PokerGame.Seat seat : game.seats) if (seat != null && !seat.npc) refunds.merge(seat.id,seat.stack+seat.contributed,Math::addExact);
        for (Map.Entry<Long,Integer> entry : refunds.entrySet()) {
            SaveData balance = new SaveData("pokerBalance"); balance.addLong("player",entry.getKey()); balance.addInt("coins",entry.getValue()); save.addSaveData(balance);
        }
        for (Map.Entry<Integer,Point> entry : invited.entrySet()) {
            SaveData npc = new SaveData("pokerNpc"); npc.addInt("id",entry.getKey()); npc.addInt("x",entry.getValue().x); npc.addInt("y",entry.getValue().y); save.addSaveData(npc);
        }
    }
    @Override public void applyLoadData(LoadData save) {
        super.applyLoadData(save); credits.clear(); invited.clear();
        for (LoadData balance : save.getLoadDataByName("pokerBalance")) credit(balance.getLong("player"), Math.max(0,balance.getInt("coins")));
        for (LoadData npc : save.getLoadDataByName("pokerNpc")) invited.put(npc.getInt("id"),new Point(npc.getInt("x"),npc.getInt("y")));
        releaseLoadedNpcs = true;
        game.result = "Welcome back. Any interrupted bets are refunded when you open this table.";
    }
    @Override public ArrayList<InventoryItem> getDroppedItems() {
        ArrayList<InventoryItem> drops = super.getDroppedItems();
        long total = credits.values().stream().mapToLong(Integer::longValue).sum();
        for (PokerGame.Seat seat : game.seats) if (seat != null && !seat.npc) total += seat.stack+seat.contributed;
        while (total > 0) { int amount = (int)Math.min(total,10000); drops.add(new InventoryItem("coin",amount)); total -= amount; }
        return drops;
    }
    @Override public void remove() {
        if (getLevel().isServer()) {
            // Destruction drops escrow through getDroppedItems; do not also cash it out.
            for (int id : new ArrayList<>(invited.keySet())) release(id);
            Arrays.fill(game.seats,null); credits.clear(); game.running = false;
        }
        super.remove();
    }
}
