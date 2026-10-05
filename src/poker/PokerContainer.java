package poker;

import necesse.engine.network.NetworkClient;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.Container;
import necesse.inventory.container.customAction.IntCustomAction;

public class PokerContainer extends Container {
    public static final int JOIN = -10, DEAL = -11, INVITE = -12, CASH_OUT = -13, DISMISS = -14;
    public final PokerTableEntity table;
    public final IntCustomAction action;
    public PokerView view;
    public String notice = "";
    public PokerContainer(NetworkClient client, int seed, PokerTableEntity table) {
        super(client, seed); this.table = table;
        action = registerAction(new IntCustomAction() {
            @Override protected void run(int value) {
                if (client.isServer() && isValid(client.getServerClient())) table.action(PokerContainer.this, value);
            }
        });
        onEvent(PokerView.class, event -> { if (event.seed == uniqueSeed) view = event; });
    }
    @Override public void init() {
        super.init();
        if (client.isServer()) { table.viewers.add(this); table.claim(client.getServerClient()); table.send(this); }
    }
    @Override public boolean isValid(ServerClient client) {
        return super.isValid(client) && table != null && !table.removed() && client.isSamePlace(table.getLevel())
            && table.getObject().isInInteractRange(table.getLevel(), table.tileX, table.tileY, client.playerMob);
    }
    @Override public void onClose() {
        super.onClose();
        if (client.isServer() && table != null) {
            table.viewers.remove(this); table.leave(client.getServerClient());
        }
    }
}
