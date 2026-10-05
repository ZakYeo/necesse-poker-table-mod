package poker;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import necesse.engine.GlobalData;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.network.client.Client;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.components.*;
import necesse.gfx.forms.presets.containerComponent.ContainerForm;
import necesse.gfx.gameFont.*;
import necesse.gfx.gameTexture.GameTexture;

public class PokerForm extends ContainerForm<PokerContainer> {
    private final FormTextButton join, deal, invite, dismiss, leave, fold, call, raise, allin;
    private final FormTextInput amount;
    private final FormLabel status, notice, history;
    private final FormContentBox details;
    private final PokerUiSettings ui;
    private final int detailsWidth;
    private final int[] handCards;
    private PokerView last;
    private int previousActor = -2, previousMin = -1, previousHand = -1;
    private GameTexture felt, cardBack;
    private final GameTexture[] cardFaces = new GameTexture[52];
    public PokerForm(Client client, PokerContainer container) {
        this(client,container,PokerUiSettings.load(Path.of(GlobalData.appDataPath(),"cfg","poker-ui")));
    }
    private PokerForm(Client client,PokerContainer container,PokerUiSettings ui) {
        super(client,ui.width,ui.height,container);
        this.ui=ui;
        handCards=ui.handCards();
        addComponent(label("title","POKER TABLE"));
        addComponent(label("subtitle","No-limit Hold'em  |  Blinds 1 / 2  |  Buy-in 200 coins"));
        addComponent(new TableArt());
        addComponent(label("hand.title","Your hand"));
        addComponent(new HandArt());
        Rectangle d=ui.panel("details");
        details=addComponent(new FormContentBox(d.x,d.y,d.width,d.height));
        detailsWidth=d.width-22;
        status=details.addComponent(label("Connecting to table...",ui.font("status"),0,0,detailsWidth));
        notice=details.addComponent(label("",ui.font("notice"),0,24,detailsWidth));
        history=details.addComponent(label("",ui.font("history"),0,48,detailsWidth));
        addComponent(label("poker.title","Poker actions"));
        fold=button("fold","Fold",()->send(PokerGame.FOLD));
        call=button("call","Check",()->send(PokerGame.CALL));
        Rectangle a=ui.button("amount");
        amount=addComponent(new FormTextInput(a.x,a.y,FormInputSize.SIZE_32,a.width,8));
        amount.setRegexMatchFull("[0-9]*"); amount.setText("4");
        raise=button("raise","Raise to",()->{
            try { int target=Integer.parseInt(amount.getText()); if(target>0) send(target); }
            catch(NumberFormatException ignored) { notice.setText("Enter a whole number of coins.",detailsWidth); details.setScrollY(0); }
        });
        allin=button("allin","All-in",()->send(PokerGame.ALL_IN));
        deal=button("deal","Deal hand",()->send(PokerContainer.DEAL));
        addComponent(label("table.title","Table & settlers"));
        join=button("join","Buy in: 200",()->send(PokerContainer.JOIN));
        invite=button("invite","Invite settlers",()->send(PokerContainer.INVITE));
        dismiss=button("dismiss","Dismiss NPCs",()->send(PokerContainer.DISMISS));
        leave=button("leave","Cash out",()->send(PokerContainer.CASH_OUT));
        button("close","Close",()->client.closeContainer(true));
        addComponent(label("footer.leave","Closing folds unless all-in. Unclaimed winnings stay at this table."));
        addComponent(label("footer.help","NPCs refill to at least 200 each hand. Scroll table messages for history."));
    }
    private FormLabel label(String key,String text) {
        Rectangle r=ui.label(key);
        FormLabel result=label(text,r.height,r.x,r.y,r.width);
        result.setY(Math.min(r.y,Math.max(0,ui.height-result.getHeight())));
        return result;
    }
    private FormLabel label(String text,int size,int x,int y,int width) {
        FormLabel label=new FormLabel(text,new FontOptions(size),FormLabel.ALIGN_LEFT,x,y,width);
        label.setColor(ui.color("text"));
        return label;
    }
    private FormTextButton button(String key,String text,Runnable callback) {
        Rectangle r=ui.button(key);
        FormTextButton b=addComponent(new FormTextButton(text,r.x,r.y,r.width)); b.onClicked(e->callback.run()); return b;
    }
    private void send(int action) { container.action.runAndSend(action); }
    @Override public boolean shouldOpenInventory() { return false; }
    @Override public void draw(TickManager tick,PlayerMob player,Rectangle clip) {
        PokerView v=container.view;
        if(v!=null && v!=last) {
            boolean newAction=last==null || v.actor!=last.actor || v.hand!=last.hand || !v.notice.equals(last.notice) || !v.result.equals(last.result);
            last=v; boolean turn=v.running && v.mine>=0 && v.actor==v.mine;
            fold.setActive(turn); call.setActive(turn); raise.setActive(turn&&v.raise); amount.setActive(turn&&v.raise);
            allin.setActive(turn && (v.raise || v.maximum<=v.call+(v.mine>=0?v.bets[v.mine]:0)));
            call.setText(v.call>0?"Call "+v.call:"Check");
            join.setActive(!v.running && (v.mine<0 || v.stacks[v.mine]==0));
            deal.setActive(!v.running && v.mine>=0 && v.mine==v.host);
            invite.setActive(!v.running && v.mine>=0 && v.mine==v.host);
            dismiss.setActive(!v.running && v.mine>=0 && v.mine==v.host);
            leave.setActive(v.mine>=0);
            if(v.actor!=previousActor || v.minimum!=previousMin || v.hand!=previousHand) amount.setText(Integer.toString(Math.min(v.minimum,v.maximum)));
            previousActor=v.actor; previousMin=v.minimum; previousHand=v.hand;
            String line=v.street+"  |  Pot: "+v.pot+" coins";
            if(v.running && v.actor>=0) line+="  |  "+(turn?"Your turn":v.names[v.actor]+" to act")+" ("+v.seconds+"s)";
            status.setText(line,detailsWidth);
            String message=!v.notice.isEmpty()?v.notice:v.running?(turn?(v.raise?"Raise-to total: "+v.minimum+" minimum, "+v.maximum+" maximum (or all-in).":"Check, call or fold."):"Waiting for the next action."):v.result;
            notice.setText(message,detailsWidth); history.setText("Recent actions:\n"+v.history,detailsWidth);
            if(newAction) details.setScrollY(0);
        }
        if(v==null) { join.setActive(false); deal.setActive(false); invite.setActive(false); dismiss.setActive(false); leave.setActive(false); fold.setActive(false); call.setActive(false); raise.setActive(false); allin.setActive(false); }
        notice.setY(status.getHeight()+6);
        history.setY(notice.getY()+notice.getHeight()+8);
        details.setContentBox(new Rectangle(0,0,detailsWidth,history.getY()+history.getHeight()));
        super.draw(tick,player,clip);
    }
    private void makeArt() {
        if(felt!=null) return;
        BufferedImage bg=new BufferedImage(652,206,BufferedImage.TYPE_INT_ARGB); Graphics2D g=bg.createGraphics();
        g.setColor(ui.color("table.background"));g.fillRoundRect(0,0,652,206,16,16);
        g.setColor(ui.color("table.rail"));g.fillRoundRect(184,49,284,112,54,54);
        g.setColor(ui.color("table.trim"));g.drawRoundRect(188,53,276,104,50,50);
        g.setColor(ui.color("table.felt"));g.fillRoundRect(195,58,262,94,44,44);
        g.setColor(ui.color("table.line"));g.drawRoundRect(202,63,248,84,38,38);g.dispose();
        felt=new GameTexture("poker_felt",bg).makeFinal();
        cardBack=new GameTexture("poker_back",ui.cardImage(-1)).makeFinal();
        for(int i=0;i<52;i++) cardFaces[i]=new GameTexture("poker_card_"+i,ui.cardImage(i)).makeFinal();
    }
    @Override public void dispose() {
        super.dispose();
        if(felt!=null) { felt.delete(); felt=null; }
        if(cardBack!=null) { cardBack.delete(); cardBack=null; }
        for(int i=0;i<cardFaces.length;i++) if(cardFaces[i]!=null) { cardFaces[i].delete(); cardFaces[i]=null; }
    }
    private void drawCard(int id,int x,int y,int width,int height) {
        if(id==-2) return;
        (id<0?cardBack:cardFaces[id]).initDraw().size(width,height).draw(x,y);
    }
    private class HandArt extends FormCustomDraw {
        HandArt(){this(ui.panel("hand"));}
        HandArt(Rectangle r){super(r.x,r.y,r.width,r.height);}
        @Override public void draw(TickManager tick,PlayerMob player,Rectangle clip) {
            makeArt(); PokerView v=container.view;
            if(v==null || v.mine<0 || v.cards[v.mine][0]==-2) {
                FontManager.bit.drawString(getX(),getY()+16,"No cards dealt",new FontOptions(12).color(ui.color("text")));
                return;
            }
            drawCard(v.cards[v.mine][0],getX(),getY(),handCards[0],handCards[1]);
            drawCard(v.cards[v.mine][1],getX()+handCards[0]+handCards[2],getY(),handCards[0],handCards[1]);
        }
    }
    private class TableArt extends FormCustomDraw {
        TableArt(){this(ui.table());}
        TableArt(Rectangle r){super(r.x,r.y,r.width,r.height);}
        private void text(int x,int y,String text,int size,Color color) {FontManager.bit.drawString(getX()+x,getY()+y,text,new FontOptions(size).color(color));}
        private String fit(String value,int size,int width){
            FontOptions font=new FontOptions(size);
            if(FontManager.bit.getWidthCeil(value,font)<=width) return value;
            while(!value.isEmpty() && FontManager.bit.getWidthCeil(value+"...",font)>width) value=value.substring(0,value.length()-1);
            return value+"...";
        }
        private void tableText(int y,String value,int size,Color color) {
            value=fit(value,size,248);
            text(326-FontManager.bit.getWidthCeil(value,new FontOptions(size))/2,y,value,size,color);
        }
        private void card(int id,int x,int y,boolean small) {
            if(id==-2) return;
            if(small) {text(x,y,id<0?"[??]":"["+PokerGame.card(id)+"]",16,ui.color("stack"));return;}
            drawCard(id,getX()+x,getY()+y,36,48);
        }
        @Override public void draw(TickManager tick,PlayerMob player,Rectangle clip) {
            makeArt();felt.initDraw().draw(getX(),getY());PokerView v=container.view;if(v==null)return;
            int[][] places={{238,163},{16,137},{16,10},{238,5},{480,10},{480,137}};
            for(int i=0;i<6;i++) {
                int x=places[i][0],y=places[i][1];Color color=ui.color(i==v.actor?"actor":"seat");
                int width=i==0||i==3?150:156;
                text(x,y,fit((i==v.actor?"> ":"")+v.names[i],16,width),16,color);
                if(!v.states[i].isEmpty()) {
                    text(x,y+18,fit(v.stacks[i]+" coins"+(v.bets[i]>0?" / bet "+v.bets[i]:""),12,width),12,ui.color("stack"));
                    text(x,y+33,fit(v.states[i],10,width),10,ui.color("state"));
                    // Top/bottom middle seats keep hole cards alongside their label.
                    if(i==0||i==3){card(v.cards[i][0],x+156,y,true);card(v.cards[i][1],x+156,y+18,true);}
                    else {card(v.cards[i][0],x,y+48,true);card(v.cards[i][1],x+56,y+48,true);}
                }
            }
            for(int i=0;i<5;i++) card(i<v.board.length?v.board[i]:-1,225+i*41,78,false);
            tableText(131,"Pot: "+v.pot+" coins",16,ui.color("pot"));
            tableText(60,v.street,12,ui.color("street"));
        }
    }
}
