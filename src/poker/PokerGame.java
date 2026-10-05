package poker;

import java.security.SecureRandom;
import java.util.*;

/** Server-only no-limit Hold'em rules. No game or rendering dependencies. */
public final class PokerGame {
    public static final int SEATS = 6, BUY_IN = 200, SMALL_BLIND = 1, BIG_BLIND = 2;
    public static final int FOLD = -1, CALL = -2, ALL_IN = -3;
    public static final class Seat {
        public final long id;
        public final String name;
        public final boolean npc;
        public int stack, bet, contributed, lastActBet = -1;
        public boolean folded, pending, inHand, leaving;
        public final int[] cards = {-1, -1};
        public Seat(long id, String name, boolean npc, int stack) {
            this.id = id; this.name = name; this.npc = npc; this.stack = stack;
        }
    }
    public final Seat[] seats = new Seat[SEATS];
    public final List<Integer> board = new ArrayList<>();
    public final Deque<String> history = new ArrayDeque<>();
    private final Random random;
    private final List<Integer> deck = new ArrayList<>();
    public int dealer = -1, actor = -1, round = -1, currentBet, minRaise = BIG_BLIND, handNumber;
    public boolean running, showdown;
    public String result = "Take a seat, invite settlers or friends, then deal.";

    public PokerGame() { this(new SecureRandom()); }
    PokerGame(Random random) { this.random = random; }
    public int find(long id, boolean npc) {
        for (int i = 0; i < SEATS; i++) if (seats[i] != null && seats[i].id == id && seats[i].npc == npc) return i;
        return -1;
    }
    public int add(Seat seat) {
        if (running || seat.stack < 0 || find(seat.id, seat.npc) >= 0) return -1;
        for (int i = 0; i < SEATS; i++) if (seats[i] == null) { seats[i] = seat; return i; }
        return -1;
    }
    public int occupied() { return (int) Arrays.stream(seats).filter(Objects::nonNull).count(); }
    public int pot() { return Arrays.stream(seats).filter(Objects::nonNull).mapToInt(s -> s.contributed).sum(); }
    public int alive() { return (int) Arrays.stream(seats).filter(s -> s != null && s.inHand && !s.folded).count(); }
    private int actionable() { return (int) Arrays.stream(seats).filter(s -> s != null && s.inHand && !s.folded && s.stack > 0).count(); }
    private int next(int from, java.util.function.Predicate<Seat> predicate) {
        for (int n = 1; n <= SEATS; n++) { int i = (from + n + SEATS) % SEATS; if (seats[i] != null && predicate.test(seats[i])) return i; }
        return -1;
    }
    public void log(String message) { history.addLast(message); while (history.size() > 5) history.removeFirst(); }
    public boolean start() {
        if (running) return false;
        long funded = Arrays.stream(seats).filter(s -> s != null && !s.leaving && (s.npc || s.stack > 0)).count();
        if (funded < 2) { result = "At least two funded seats are needed."; return false; }
        for (Seat s : seats) if (s != null) {
            if (s.npc) s.stack = Math.max(BUY_IN, s.stack);
            s.bet = s.contributed = 0; s.lastActBet = -1;
            s.inHand = !s.leaving && s.stack > 0; s.folded = !s.inHand; s.pending = s.inHand;
            Arrays.fill(s.cards, -1);
        }
        running = true; showdown = false; round = 0; handNumber++;
        board.clear(); deck.clear(); for (int i = 0; i < 52; i++) deck.add(i);
        Collections.shuffle(deck, random);
        dealer = next(dealer, s -> s.inHand);
        for (int card = 0; card < 2; card++) {
            int i = dealer;
            for (int n = 0; n < funded; n++) { i = next(i, s -> s.inHand); seats[i].cards[card] = draw(); }
        }
        int small = funded == 2 ? dealer : next(dealer, s -> s.inHand);
        int big = next(small, s -> s.inHand);
        put(seats[small], Math.min(SMALL_BLIND, seats[small].stack));
        put(seats[big], Math.min(BIG_BLIND, seats[big].stack));
        currentBet = BIG_BLIND; minRaise = BIG_BLIND;
        actor = big; result = "Hand " + handNumber + " - no-limit Texas Hold'em";
        log("Hand " + handNumber + ": " + seats[dealer].name + " deals.");
        advance(); return true;
    }
    private int draw() { return deck.remove(deck.size() - 1); }
    private void put(Seat s, int amount) { s.stack -= amount; s.bet += amount; s.contributed += amount; }
    public int toCall(int index) { return Math.max(0, currentBet - seats[index].bet); }
    public int minimumTarget() { return currentBet + minRaise; }
    public boolean canRaise(int index) {
        Seat s = seats[index];
        return running && index == actor && actionable() > 1 && s.stack > toCall(index)
            && (s.lastActBet < 0 || currentBet - s.lastActBet >= minRaise);
    }
    /** Positive action is the total street bet, not the amount added. */
    public String act(int index, int action) {
        if (!running || actor != index || index < 0 || seats[index] == null) return "It is not your turn.";
        Seat s = seats[index];
        if (action == FOLD) { s.folded = true; s.pending = false; log(s.name + " folds."); }
        else {
            int maximum = s.bet + s.stack;
            int target = action == CALL ? Math.min(currentBet, maximum) : action == ALL_IN ? maximum : action;
            if (target < s.bet || target > maximum || (target < currentBet && target != maximum)) return "Invalid bet amount.";
            if (target > currentBet) {
                if (!canRaise(index)) return "Raising is not available; check or call.";
                int increase = target - currentBet;
                if (increase < minRaise && target != maximum) return "Minimum raise-to is " + minimumTarget() + " coins.";
                if (increase >= minRaise) {
                    minRaise = increase;
                    for (Seat other : seats) if (other != null && other.inHand && !other.folded && other.stack > 0) other.pending = true;
                } else {
                    for (Seat other : seats) if (other != null && other.inHand && !other.folded && other.stack > 0 && other.bet < target) other.pending = true;
                }
                currentBet = target;
                log(s.name + " raises to " + target + (target == maximum ? " (all-in)." : "."));
            } else log(s.name + (target == s.bet ? " checks." : " calls " + (target - s.bet) + (target == maximum ? " (all-in)." : ".")));
            put(s, target - s.bet); s.lastActBet = currentBet; s.pending = false;
        }
        advance(); return null;
    }
    public void leave(int index) {
        Seat s = seats[index]; if (s == null) return;
        s.leaving = true;
        // An all-in player remains eligible after disconnecting.
        if (running && s.inHand && !s.folded && s.stack > 0) {
            s.folded = true; s.pending = false; log(s.name + " leaves and folds.");
            if (index == actor || alive() <= 1) advance();
        }
    }
    private void advance() {
        while (running) {
            if (alive() == 1) {
                int winner = next(dealer, s -> s.inHand && !s.folded);
                int amount = pot(); seats[winner].stack += amount;
                finish(seats[winner].name + " wins " + amount + " coins.", false); return;
            }
            if (alive() == 0) { voidHand(); return; }
            if (actionable() == 1) {
                int only = next(actor, s -> s.inHand && !s.folded && s.stack > 0);
                if (seats[only].bet >= currentBet) seats[only].pending = false;
            }
            int next = next(actor, s -> s.inHand && !s.folded && s.stack > 0 && s.pending);
            if (next >= 0) { actor = next; return; }
            refundUncalled();
            if (round == 3) { settle(); return; }
            round++; draw(); // Burn one card before each street.
            int count = round == 1 ? 3 : 1;
            for (int i = 0; i < count; i++) board.add(draw());
            currentBet = 0; minRaise = BIG_BLIND;
            for (Seat s : seats) if (s != null) { s.bet = 0; s.lastActBet = -1; s.pending = s.inHand && !s.folded && s.stack > 0; }
            actor = dealer;
        }
    }
    private void refundUncalled() {
        Seat top = null; int highest = 0, second = 0;
        for (Seat s : seats) if (s != null && s.inHand) {
            if (s.bet > highest) { second = highest; highest = s.bet; top = s; }
            else second = Math.max(second, s.bet);
        }
        if (top != null && highest > second) { int refund = highest - second; top.bet -= refund; top.contributed -= refund; top.stack += refund; }
    }
    private void settle() {
        TreeSet<Integer> tiers = new TreeSet<>();
        for (Seat s : seats) if (s != null && s.contributed > 0) tiers.add(s.contributed);
        int previous = 0; int[] won = new int[SEATS];
        for (int tier : tiers) {
            int contributors = 0; long best = -1; List<Integer> winners = new ArrayList<>();
            for (int i = 0; i < SEATS; i++) {
                Seat s = seats[i]; if (s == null || s.contributed < tier) continue;
                contributors++;
                if (!s.folded && s.inHand) {
                    long score = rank(s.cards, board);
                    if (score > best) { best = score; winners.clear(); }
                    if (score == best) winners.add(i);
                }
            }
            int amount = (tier - previous) * contributors;
            if (winners.isEmpty()) {
                // No eligible player: return these unmatched contributions.
                for (int i = 0; i < SEATS; i++) if (seats[i] != null && seats[i].contributed >= tier) won[i] += tier - previous;
            } else {
                int share = amount / winners.size(), extra = amount % winners.size();
                for (int winner : winners) won[winner] += share;
                for (int n = 1; extra > 0 && n <= SEATS; n++) { int i = (dealer + n) % SEATS; if (winners.contains(i)) { won[i]++; extra--; } }
            }
            previous = tier;
        }
        StringJoiner summary = new StringJoiner("; ");
        for (int i = 0; i < SEATS; i++) if (won[i] > 0) {
            seats[i].stack += won[i]; summary.add(seats[i].name + " wins " + won[i]);
            log(seats[i].name + ": " + category(rank(seats[i].cards, board)) + ", +" + won[i]);
        }
        finish(summary.toString(), true);
    }
    private void finish(String message, boolean show) {
        running = false; showdown = show; actor = -1; result = message;
        for (Seat s : seats) if (s != null) { s.bet = 0; s.contributed = 0; s.pending = false; }
        if (!show) log(message);
    }
    public void voidHand() {
        for (Seat s : seats) if (s != null) s.stack += s.contributed;
        finish("Hand cancelled; all bets returned.", false);
    }
    public String street() { return !running ? (showdown ? "Showdown" : "Lobby") : new String[]{"Pre-flop", "Flop", "Turn", "River"}[round]; }
    public static String card(int card) { return card < 0 ? "??" : "23456789TJQKA".charAt(card % 13) + new String[]{"c", "d", "h", "s"}[card / 13]; }
    public static String category(long score) { return new String[]{"High card", "One pair", "Two pair", "Three of a kind", "Straight", "Flush", "Full house", "Four of a kind", "Straight flush"}[(int)(score / 759375L)]; }
    public static long rank(int[] hole, List<Integer> board) {
        int[] cards = new int[hole.length + board.size()]; System.arraycopy(hole, 0, cards, 0, hole.length);
        for (int i = 0; i < board.size(); i++) cards[hole.length + i] = board.get(i);
        if (cards.length < 5) return 0;
        long best = -1;
        for (int a = 0; a < cards.length - 4; a++) for (int b = a+1; b < cards.length - 3; b++)
        for (int c = b+1; c < cards.length - 2; c++) for (int d = c+1; d < cards.length - 1; d++)
        for (int e = d+1; e < cards.length; e++) best = Math.max(best, rank5(cards[a], cards[b], cards[c], cards[d], cards[e]));
        return best;
    }
    public static long rank5(int a, int b, int c, int d, int e) {
        int[] cards = {a,b,c,d,e}, counts = new int[15]; boolean flush = true;
        for (int card : cards) { counts[card%13 + 2]++; if (card/13 != a/13) flush = false; }
        int straight = 0;
        for (int high = 14; high >= 6; high--) { boolean yes = true; for (int r = high-4; r <= high; r++) if (counts[r] == 0) yes = false; if (yes) { straight = high; break; } }
        if (straight == 0 && counts[14] > 0 && counts[2] > 0 && counts[3] > 0 && counts[4] > 0 && counts[5] > 0) straight = 5;
        int quad = 0, trip = 0; List<Integer> pairs = new ArrayList<>(), singles = new ArrayList<>();
        for (int r = 14; r >= 2; r--) { if (counts[r] == 4) quad = r; else if (counts[r] == 3) trip = r; else if (counts[r] == 2) pairs.add(r); else if (counts[r] == 1) singles.add(r); }
        if (flush && straight > 0) return score(8, straight);
        if (quad > 0) return score(7, quad, singles.get(0));
        if (trip > 0 && !pairs.isEmpty()) return score(6, trip, pairs.get(0));
        if (flush) return score(5, singles.stream().mapToInt(Integer::intValue).toArray());
        if (straight > 0) return score(4, straight);
        if (trip > 0) return score(3, trip, singles.get(0), singles.get(1));
        if (pairs.size() == 2) return score(2, pairs.get(0), pairs.get(1), singles.get(0));
        if (pairs.size() == 1) return score(1, pairs.get(0), singles.get(0), singles.get(1), singles.get(2));
        return score(0, singles.stream().mapToInt(Integer::intValue).toArray());
    }
    private static long score(int category, int... ranks) { long value = category; for (int i = 0; i < 5; i++) value = value * 15 + (i < ranks.length ? ranks[i] : 0); return value; }

    /** Equity sampling only receives the NPC's cards and the public board. */
    public int chooseNpcAction() {
        Seat s = seats[actor]; int opponents = alive() - 1;
        List<Integer> unseen = new ArrayList<>();
        for (int c = 0; c < 52; c++) if (c != s.cards[0] && c != s.cards[1] && !board.contains(c)) unseen.add(c);
        double equity = 0;
        for (int trial = 0; trial < 70; trial++) {
            Collections.shuffle(unseen, random); int offset = 0;
            List<Integer> future = new ArrayList<>(board); while (future.size() < 5) future.add(unseen.get(offset++));
            long mine = rank(s.cards, future); int tied = 1; boolean beaten = false;
            for (int n = 0; n < opponents; n++) { long theirs = rank(new int[]{unseen.get(offset++), unseen.get(offset++)}, future); if (theirs > mine) beaten = true; if (theirs == mine) tied++; }
            if (!beaten) equity += 1.0 / tied;
        }
        equity /= 70;
        int call = Math.min(s.stack, toCall(actor));
        double odds = call / (double) Math.max(1, pot() + call);
        if (call > 0 && equity + 0.06 < odds && random.nextDouble() > 0.1) return FOLD;
        if (canRaise(actor) && (equity > 0.60 || random.nextDouble() < 0.08)) {
            int target = Math.min(s.bet + s.stack, Math.max(minimumTarget(), currentBet + Math.max(2, (pot() + call) / 2)));
            return target;
        }
        return CALL;
    }
}
