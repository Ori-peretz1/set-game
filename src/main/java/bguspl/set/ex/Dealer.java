package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    public ArrayBlockingQueue<Player> allPlayers;
    public final Object dLock = new Object(); // as learned it will be object

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;
    private List<Integer> currentCards; //maybe unneseccary

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList()); //magic number

        this.allPlayers = new ArrayBlockingQueue<>(players.length);
        reshuffleTime = System.currentTimeMillis();
        currentCards = new LinkedList<Integer>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player p : players) {
            Thread pThread = new Thread(p, "player" + p.getId());
            pThread.start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis(); //magic number
        while ((!terminate) && (System.currentTimeMillis() < reshuffleTime)) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * env.ui.testset
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        for (int i = players.length - 1; i >= 0; i--) {
            players[i].terminate();
        }
        env.ui.removeTokens();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        while (!allPlayers.isEmpty()) {
            boolean toCheck = true;
            Player pRemove = allPlayers.remove();
            int[] pSlotArray = pRemove.getCardsThatChosen();
            int[] pCardArray = new int[pSlotArray.length];
            for (int i = 0; i < pCardArray.length && toCheck; i++) {
                if (table.slotToCard[pSlotArray[i]] != null) {
                    pCardArray[i] = table.slotToCard[pSlotArray[i]];
                } else {
                    toCheck = false;
                }
            }
            if (toCheck) {
                int playerId = pRemove.getId();

                if (env.util.testSet(pCardArray)) {
                    players[playerId].setPoint(true);
                    players[playerId].setDealerState(true);
                    for (int i = 0; i < pCardArray.length; i++) {
                        try {
                            int currslot = table.cardToSlot[pCardArray[i]];
                            table.removeCard(currslot);
                        } catch (NullPointerException ignore) {
                        }

                    }
                    table.playerGotsetToRemove(playerId);
                    players[playerId].resetTokens();
                    for (int i = 0; i < table.getPlayersChoices().length; i++) {
                        int currNumOfTok = 0;
                        for (int j = 0; j < table.getPlayersChoices()[i].length; j++) {
                            if (table.getPlayersChoices()[i][j]) {
                                currNumOfTok++;

                            }
                        }
                        players[i].setNumOfTok(currNumOfTok);

                    }
                    updateTimerDisplay(true);

                } else {
                    players[playerId].setPenallize(true);
                    players[playerId].setDealerState(true);
                }
            }
        }
        for (int i = 0; i < players.length; i++) {
            synchronized (players[i].pLock) {
                players[i].pLock.notify();
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        playersBlock();
        while ((table.countCards() != (env.config.rows) * (env.config.columns)) && (!deck.isEmpty())) { //magic number
            int cardIndex = shfflingCards();
            int slotIndex = getNextEmptySlot();
            if (slotIndex != -1) {
                table.placeCard(deck.get(cardIndex), slotIndex);
                deck.remove(cardIndex);
                if (env.config.hints) {
                    table.hints();
                }
            }
        }
        playersUnBlock();
        LinkedList<Integer> newCards = new LinkedList<>();
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] != null) {
                newCards.add(table.slotToCard[i]);
            }
        }
        currentCards = newCards;
        for (int i = 0; i < players.length; i++) {
            synchronized (players[i].compLock) {
                players[i].compLock.notifyAll(); // maybe notify
            }
        }

    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        synchronized (dLock) {
            try {
                dLock.wait(10);

            } catch (InterruptedException ignore) {
            }

        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (!reset) {
            if (reshuffleTime - System.currentTimeMillis() <= 5000) {
                env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
            } else {
                env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
            }
        } else {
            reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis();
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        playersBlock();
        env.ui.removeTokens();
        for (int i = 0; i < env.config.rows * env.config.columns; i++) { //magic number
            if (table.slotToCard[i] != null) {
                deck.add(table.slotToCard[i]);
                table.removeCard(i);
            }
        }
        table.removeAllTokens();
        for (int i = 0; i < players.length; i++) {
            players[i].resetTokens();
        }

        playersUnBlock();

    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        env.ui.removeTokens();
        int maxScore = 0;
        int numOfWinners = 0;
        for (int i = 0; i < players.length; i++) {
            int pScore = players[i].score();
            if (pScore > maxScore) {
                maxScore = pScore;
            }

        }
        for (int i = 0; i < players.length; i++) {
            int pScore = players[i].score();
            if (pScore == maxScore) {
                numOfWinners += 1;
            }
        }
        int[] theWinners = new int[numOfWinners];
        for (int i = 0; i < players.length; i++) {
            int pScore = players[i].score();
            if (pScore == maxScore) {
                theWinners[numOfWinners - 1] = players[i].getId();
                numOfWinners--;
            }
        }
        env.ui.announceWinner(theWinners);
        terminate();
    }

    private void playersBlock() {
        for (int i = 0; i < players.length; i++) {
            players[i].setPutCard(false);
        }
    }

    private void playersUnBlock() {
        for (int i = 0; i < players.length; i++) {
            players[i].setPutCard(true);
        }
    }

    private int shfflingCards() {
        return getRandomNum(0, deck.size()); //
    }

    private int getRandomNum(int min, int max) { // added func
        return (int) ((Math.random() * (max - min)) + min);
    }

    private int getNextEmptySlot() {
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] == null) {
                return i;
            }
        }
        return -1; // there is no slot available
    }
}
