package poker;

import java.util.*;

public class PokerGameTest {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    private static PokerGame table(int... stacks) {
        PokerGame g=new PokerGame(new Random(1234));
        for(int i=0;i<stacks.length;i++)g.add(new PokerGame.Seat(i,"P"+i,false,stacks[i]));
        return g;
    }
    private static void act(PokerGame g,int action){String error=g.act(g.actor,action);check(error==null,"Action "+action+": "+error);}
    private static int total(PokerGame g){return Arrays.stream(g.seats).filter(Objects::nonNull).mapToInt(s->s.stack+s.contributed).sum();}
    public static void main(String[] args) {
        headsUp(); shortRaises(); pots(); privacyAndLeaving(); randomHands(); handRanks();
        System.out.println("PASS: "+checks+" checks; 6,000 simulated hands and all 2,598,960 five-card combinations.");
    }
    private static void headsUp(){
        PokerGame g=table(200,200);check(g.start(),"start");check(g.actor==0,"Heads-up dealer acts first preflop");
        act(g,PokerGame.CALL);check(g.actor==1&&g.round==0,"Big blind has option");
        act(g,PokerGame.CALL);check(g.actor==1&&g.round==1,"Heads-up BB acts first after flop");
        check(g.act(0,100)!=null,"Reject out of turn");check(g.act(1,1)!=null,"Reject below minimum opening bet");
        check(g.act(1,Integer.MAX_VALUE)!=null,"Reject over stack");
        while(g.running) act(g,PokerGame.CALL);check(total(g)==400,"Checkdown conservation");
        g=table(1,200);g.start();check(!g.running,"Lone funded opponent does not bet into dry side pot");check(total(g)==201,"Short blind conservation");
    }
    private static void shortRaises(){
        PokerGame g=table(200,14,200,200);g.start();check(g.actor==3,"UTG");
        act(g,10);act(g,PokerGame.CALL);act(g,PokerGame.ALL_IN);act(g,PokerGame.CALL);
        check(g.actor==3&&!g.canRaise(3),"Single short raise does not reopen");
        check(g.act(3,22)!=null,"Reject closed raising");act(g,PokerGame.CALL);act(g,PokerGame.CALL);
        check(g.round==1,"Street progresses after short all-in calls");
        g=table(200,14,18,200);g.start();act(g,10);act(g,PokerGame.CALL);act(g,PokerGame.ALL_IN);act(g,PokerGame.ALL_IN);
        check(g.actor==3&&g.canRaise(3),"Cumulative short raises reopen");check(g.minimumTarget()==26,"Full-raise increment retained");
        act(g,26);check(g.canRaise(0),"Reopening applies to previous caller");
    }
    private static void pots(){
        PokerGame g=table(0,0,1);g.running=true;g.round=3;g.actor=2;g.dealer=0;g.currentBet=200;
        g.board.addAll(Arrays.asList(0,14,31,45,7));
        int[] invested={50,100,200};int[][] holes={{12,25},{11,24},{10,23}};
        for(int i=0;i<3;i++){PokerGame.Seat s=g.seats[i];s.inHand=true;s.bet=s.contributed=invested[i];s.pending=i==2;System.arraycopy(holes[i],0,s.cards,0,2);}
        act(g,PokerGame.CALL);
        check(g.seats[0].stack==150,"Short stack wins main pot");check(g.seats[1].stack==100,"Second stack wins side pot");check(g.seats[2].stack==101,"Uncalled overbet refunded");check(total(g)==351,"Side pot conservation");
        g=table(1,0,0);g.running=true;g.round=3;g.actor=0;g.dealer=0;g.currentBet=5;
        g.board.addAll(Arrays.asList(8,9,10,11,12));
        for(int i=0;i<3;i++){PokerGame.Seat s=g.seats[i];s.inHand=true;s.bet=s.contributed=i==2?1:5;s.cards[0]=13+i*3;s.cards[1]=14+i*3;s.pending=i==0;}
        g.seats[2].folded=true;act(g,PokerGame.CALL);
        check(g.seats[0].stack==6&&g.seats[1].stack==6,"Tied pot odd chip goes left of dealer");
    }
    private static void privacyAndLeaving(){
        PokerGame g=table(200,200);g.start();act(g,PokerGame.ALL_IN);g.leave(0);
        check(!g.seats[0].folded,"Leaving all-in stays eligible");act(g,PokerGame.CALL);check(total(g)==400,"All-in disconnect conservation");
        g=table(200,200,200);g.start();int leaver=g.actor;g.leave(leaver);check(g.seats[leaver].folded,"Leaving with chips folds");
        while(g.running)act(g,PokerGame.CALL);check(total(g)==600,"Fold-on-leave conservation");
        g=table(200,200);g.start();act(g,40);g.voidHand();check(g.seats[0].stack==200&&g.seats[1].stack==200,"Cancellation refunds bets");
    }
    private static void randomHands(){
        Random r=new Random(713);
        for(int n=0;n<6000;n++){
            int[] stacks=new int[2+r.nextInt(5)];for(int i=0;i<stacks.length;i++)stacks[i]=1+r.nextInt(500);
            PokerGame g=table(stacks);int starting=total(g);g.start();int actions=0;
            while(g.running){
                int actor=g.actor;check(actor>=0&&actor<6,"Valid actor");PokerGame.Seat s=g.seats[actor];
                check(s.stack>0&&!s.folded&&s.pending,"Only actionable player gets turn");
                int choice=PokerGame.CALL,roll=r.nextInt(10);
                if(roll<2)choice=PokerGame.FOLD;
                else if(roll<5&&g.canRaise(actor))choice=PokerGame.ALL_IN;
                else if(roll<8&&g.canRaise(actor))choice=Math.min(s.bet+s.stack,g.minimumTarget()+r.nextInt(30));
                act(g,choice);check(total(g)==starting,"Chip conservation in randomized hand "+n);
                for(PokerGame.Seat seat:g.seats)if(seat!=null)check(seat.stack>=0&&seat.contributed>=0,"Nonnegative bankrolls");
                check(++actions<150,"Hand finishes without stalled round");
            }
            Set<Integer> cards=new HashSet<>(g.board);
            for(PokerGame.Seat s:g.seats)if(s!=null&&s.inHand)for(int c:s.cards)check(cards.add(c),"No duplicate dealt cards");
        }
    }
    private static void handRanks(){
        long[] counts=new long[9];
        for(int a=0;a<48;a++)for(int b=a+1;b<49;b++)for(int c=b+1;c<50;c++)for(int d=c+1;d<51;d++)for(int e=d+1;e<52;e++)counts[(int)(PokerGame.rank5(a,b,c,d,e)/759375L)]++;
        long[] expected={1302540,1098240,123552,54912,10200,5108,3744,624,40};
        check(Arrays.equals(counts,expected),"Exhaustive rank distribution: "+Arrays.toString(counts));
        long wheel=PokerGame.rank5(12,13,27,41,3),six=PokerGame.rank5(0,14,28,42,4);
        check(PokerGame.category(wheel).equals("Straight")&&six>wheel,"Ace low wheel ranks below six-high straight");
        check(PokerGame.rank(new int[]{12,25},Arrays.asList(38,11,24,37,0))>PokerGame.rank(new int[]{11,24},Arrays.asList(37,10,23,36,0)),"Best full house from two trips");
    }
}
