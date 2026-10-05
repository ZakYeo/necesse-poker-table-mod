package poker;

import java.util.*;
import necesse.engine.network.*;
import necesse.inventory.container.events.ContainerEvent;

/** A recipient-specific snapshot. Opponents' private cards never enter the packet. */
public class PokerView extends ContainerEvent {
    public int seed, mine, host, actor, dealer, pot, seconds, call, minimum, maximum, hand;
    public boolean running, raise;
    public String street, result, notice, history;
    public int[] board;
    public final String[] names = new String[6], states = new String[6];
    public final int[] stacks = new int[6], bets = new int[6];
    public final int[][] cards = new int[6][2];
    public PokerView(PokerTableEntity table, PokerContainer container) {
        PokerGame g = table.game;
        seed = container.uniqueSeed; mine = g.find(container.client.authentication,false); host = table.host();
        actor = g.actor; dealer = g.dealer; pot = g.pot(); seconds = table.secondsLeft(); hand = g.handNumber;
        running = g.running; street = g.street(); result = g.result; notice = container.notice;
        history = String.join("\n",g.history); board = g.board.stream().mapToInt(Integer::intValue).toArray();
        if (mine >= 0) { call = Math.min(g.seats[mine].stack,g.toCall(mine)); maximum = g.seats[mine].bet+g.seats[mine].stack; raise = g.canRaise(mine); }
        minimum = g.minimumTarget();
        for (int i = 0; i < 6; i++) {
            PokerGame.Seat s = g.seats[i]; cards[i][0] = cards[i][1] = -2;
            if (s == null) { names[i] = "Empty seat"; states[i] = ""; continue; }
            names[i] = s.name; stacks[i] = s.stack; bets[i] = s.bet;
            states[i] = (s.npc ? "Settler" : i == mine ? "You" : "Player") + (i == dealer ? " / Dealer" : "")
                + (s.leaving ? " / Leaving" : s.inHand && s.folded ? " / Folded" : running && s.stack == 0 ? " / All-in" : "");
            for (int c = 0; c < 2; c++) cards[i][c] = s.cards[c] < 0 ? -2 : i == mine || (g.showdown && s.inHand && !s.folded) ? s.cards[c] : -1;
        }
    }
    public PokerView(PacketReader r) {
        seed=r.getNextInt(); mine=r.getNextInt(); host=r.getNextInt(); actor=r.getNextInt(); dealer=r.getNextInt();
        pot=r.getNextInt(); seconds=r.getNextInt(); call=r.getNextInt(); minimum=r.getNextInt(); maximum=r.getNextInt(); hand=r.getNextInt();
        running=r.getNextBoolean(); raise=r.getNextBoolean(); street=r.getNextString(); result=r.getNextString(); notice=r.getNextString(); history=r.getNextString();
        int length=r.getNextByteUnsigned(); if (length > 5) throw new IllegalArgumentException("Invalid poker board");
        board=new int[length]; for(int i=0;i<length;i++) board[i]=r.getNextInt();
        for(int i=0;i<6;i++) { names[i]=r.getNextString(); states[i]=r.getNextString(); stacks[i]=r.getNextInt(); bets[i]=r.getNextInt(); cards[i][0]=r.getNextInt(); cards[i][1]=r.getNextInt(); }
    }
    @Override public void write(PacketWriter w) {
        w.putNextInt(seed); w.putNextInt(mine); w.putNextInt(host); w.putNextInt(actor); w.putNextInt(dealer);
        w.putNextInt(pot); w.putNextInt(seconds); w.putNextInt(call); w.putNextInt(minimum); w.putNextInt(maximum); w.putNextInt(hand);
        w.putNextBoolean(running); w.putNextBoolean(raise); w.putNextString(street); w.putNextString(result); w.putNextString(notice); w.putNextString(history);
        w.putNextByteUnsigned(board.length); for(int card:board) w.putNextInt(card);
        for(int i=0;i<6;i++) { w.putNextString(names[i]); w.putNextString(states[i]); w.putNextInt(stacks[i]); w.putNextInt(bets[i]); w.putNextInt(cards[i][0]); w.putNextInt(cards[i][1]); }
    }
}
