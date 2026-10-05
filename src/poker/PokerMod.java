package poker;

import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.registries.*;
import necesse.inventory.container.events.ContainerEventRegistry;
import necesse.inventory.recipe.*;

@ModEntry
public class PokerMod {
    public static int CONTAINER;
    public void init() {
        ContainerEventRegistry.registerUpdate(PokerView.class);
        CONTAINER = ContainerRegistry.registerOEContainer(
            (client, seed, entity, packet) -> new PokerForm(client, new PokerContainer(client.getClient(), seed, (PokerTableEntity)entity)),
            (client, seed, entity, packet, extra) -> new PokerContainer(client, seed, (PokerTableEntity)entity));
        ObjectRegistry.registerObject("pokertable", new PokerTableObject(), 50, true);
    }
    public void postInit() {
        Recipes.registerModRecipe(new Recipe("pokertable", 1, RecipeTechRegistry.WORKSTATION,
            new Ingredient[]{new Ingredient("anylog", 20), new Ingredient("ironbar", 4)}));
    }
}
