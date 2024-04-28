package bguspl.set.ex;

import java.util.concurrent.ArrayBlockingQueue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private int[] cardsThatChosen; // add field - represents the current placed tokens

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;
    private Dealer ourDealer;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;
    private volatile boolean dealerIsAvailable;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;
    private volatile boolean setFounded; // added field
    private volatile boolean penallize; // added field
    // private volatile boolean point; // added field
    private boolean isFreezed; // added field
    private boolean putCard; // added field
    private int currNumOfTokens; // addded field

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    protected ArrayBlockingQueue<Integer> playerTokensQ;
    public final Object pLock = new Object(); // added field, as learned it will be object
    public final Object compLock = new Object(); // added field, as learned it will be object

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.score = 0;

        this.playerTokensQ = new ArrayBlockingQueue<>(env.config.featureSize); // represents the 3 current tokens &
                                                                               // magic Number
        this.ourDealer = dealer;
        this.dealerIsAvailable = false;
        penallize = false;
        setFounded = false;
        // point = false;
        cardsThatChosen = new int[env.config.featureSize]; // magic number
        putCard = false;
        currNumOfTokens = 0;
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human)
            createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            if (!(playerTokensQ.isEmpty())) {
                Integer numOfSpecipicSlot = playerTokensQ.remove();
                if (table.slotToCard[numOfSpecipicSlot] != null) {
                    boolean isChosen = table.cardHasChosen(id, numOfSpecipicSlot);
                    if (isChosen) {
                        currNumOfTokens--;
                        table.removeToken(id, numOfSpecipicSlot);

                    } else {
                        if (currNumOfTokens < env.config.featureSize) {
                            currNumOfTokens++;
                            table.placeToken(id, numOfSpecipicSlot);
                            if (currNumOfTokens == env.config.featureSize) {
                                int[] currChosenCards = new int[env.config.featureSize];
                                int i = 0;
                                boolean[][] playersTokens = table.getPlayersChoices();
                                for (int j = 0; j < playersTokens[id].length; j++) {
                                    if (table.cardHasChosen(id, j)) {
                                        int currCard = j;
                                        // currCard = table.slotToCard[j];

                                        currChosenCards[i] = currCard; // currChosenSlots
                                        i++;
                                    }

                                }
                                cardsThatChosen = currChosenCards;
                                ourDealer.allPlayers.add(this);
                                synchronized (ourDealer.dLock) {
                                    ourDealer.dLock.notify();
                                }
                                synchronized (pLock) {
                                    try {
                                        pLock.wait();
                                    } catch (InterruptedException ignored) {

                                    }
                                }

                            }
                        }
                    }

                }
            }
            if (dealerIsAvailable) {
                if (setFounded) {
                    point();
                    setFounded = false;
                    dealerIsAvailable = false;
                } else if (penallize) {
                    penalty();
                    penallize = false;
                    dealerIsAvailable = false;
                }
            }
        }

        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                int slot = getRandomNum(0, env.config.tableSize); // slot will be random from 0-11 (include the edges)
                if (!dealerIsAvailable) { // magic number above
                    this.keyPressed(slot);
                }
                synchronized (compLock) {

                    try {
                        compLock.wait(2);

                    } catch (InterruptedException ignore) {
                    }
                }

            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() { // Bonus 2
        // TODO implement
        terminate = true;
        synchronized (pLock) {
            pLock.notifyAll();
        }
        playerThread.interrupt();
        synchronized (compLock) {
            compLock.notifyAll();
        }
        try {
            playerThread.join();
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if (putCard) {
            if (!isFreezed) {
                if (playerTokensQ.size() != 3) {
                    playerTokensQ.add(slot);
                }
            }
        }

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score); // add 1 point

        long afterFreeze = System.currentTimeMillis() + env.config.pointFreezeMillis; // magic number
        while (afterFreeze - System.currentTimeMillis() > 900) {// magic number
            env.ui.setFreeze(id, afterFreeze - System.currentTimeMillis());// magic number
            try {
                synchronized (this) {
                    System.out.println("went to sleep for a point!");
                    wait(900);
                }
            } catch (InterruptedException ignore) {
            }
        }
        env.ui.setFreeze(id, 0);
        isFreezed = false;

    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        long penaltyTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;// magic number
        isFreezed = true;
        while (penaltyTime - System.currentTimeMillis() > 900) {// magic number
            env.ui.setFreeze(id, penaltyTime - System.currentTimeMillis());
            try {
                System.out.println("went to sleep for a penalty :(");
                synchronized (this) {
                    wait(900);
                }
            } catch (InterruptedException ignore) {
            }
        }
        env.ui.setFreeze(id, 0);
        isFreezed = false;
    }

    public int score() {
        return score;
    }

    // additional methods :
    public int getId() {
        return id;
    }

    public int[] getCardsThatChosen() {
        return cardsThatChosen;
    }

    public void setPoint(boolean state) {
        this.setFounded = state;
    }

    public void setDealerState(boolean state) {
        this.dealerIsAvailable = state;
    }

    public void setPenallize(boolean state) {
        this.penallize = true;
    }

    public void setPutCard(boolean state) {
        this.putCard = state;
    }

    public void resetTokens() { // added func
        this.currNumOfTokens = 0;
    }

    public void setNumOfTok(int i) { // added func
        this.currNumOfTokens = i;
    }

    public int getCurrNumOfTok() { // added fucn
        return this.currNumOfTokens;
    }

    private int getRandomNum(int min, int max) { // added func
        return (int) ((Math.random() * (max - min)) + min);
    }

}
