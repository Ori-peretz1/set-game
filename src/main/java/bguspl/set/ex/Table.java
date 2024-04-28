package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)
    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)
    private boolean[][] playersChoices; // added field - represent slots choosed by player

    // protected Integer numOfPlayers;

    /**
     * Constructor for testing.
     *
     * @param env        - the game envir onment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if
     *                   none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if
     *                   none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.playersChoices = new boolean[env.config.players][env.config.rows * env.config.columns]; // magic number
        // numOfPlayers = env.config.players; =----------------------- maybe need to
        

    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the
     * table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted()
                    .collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(
                    sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * 
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }
        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        // TODO implement
        env.ui.placeCard(card, slot); // update interface
    }

    /**
     * Removes a card from a grid slot on the table.
     * 
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        // TODO implement
        if (isValid(slot, "slot")) { // using added funct
            if (slotToCard[slot] != null) {
                int toRemove = slotToCard[slot]; // saving the card we remove
                slotToCard[slot] = null;
                cardToSlot[toRemove] = null; // using the saved card (int)
                env.ui.removeCard(slot);
            }
        }
    }

    /**
     * Places a player token on a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        // TODO implement
        playersChoices[player][slot] = true;
        env.ui.placeToken(player, slot);

    }

    /**
     * Removes a token of a player from a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        // TODO implement
        // if (playersChoices[player][slot]) {
        playersChoices[player][slot] = false;
        env.ui.removeToken(player, slot);
        return false;
    }

    public void removeAllTokens() {
        playersChoices = new boolean[env.config.players][(env.config.rows) * (env.config.columns)]; //magic number
    }

    public boolean isValid(int i, String str) { // added func
        if (str == "slot") {
            if (i >= 0 & i < slotToCard.length) {
                return true;
            }
            return false;
        } else {
            if (i < cardToSlot.length && i >= 0) {
                return true;
            }
            return false;
        }
    }

    public void resetAll() { // added func
        for (int i = 0; i < this.playersChoices.length; i++) {
            for (int j = 0; j < this.playersChoices[i].length; j++) {
                this.playersChoices[i][j] = false;
            }
        }
    }

    public void resetPlayerChoices(int playerId) { // addfunc when player placed three without figure a set
        for (int i = 0; i < this.playersChoices[playerId].length; i++) {
            this.playersChoices[playerId][i] = false;
        }
    }

    public void playerGotsetToRemove(int playerId) { // added func # removes token if set

        for (int i = 0; i < playersChoices[playerId].length; i++) {
            if (playersChoices[playerId][i]) {
                for (int j = 0; j < playersChoices.length; j++) { //////
                    // ----- in remove we make sure to remove each token
                    removeToken(j, i); // deleting from other players that has token on the same card
                }
            }
        }
    }

    public boolean[][] getPlayersChoices() { // added func
        return this.playersChoices;
    }

    public boolean cardHasChosen(int playerId, int slot) { // added func
        return playersChoices[playerId][slot];
    }

}
