package sim.app.exploration.core;

import sim.app.exploration.env.SimEnvironment;
import sim.engine.SimState;

/**
 * @author Miguel Tavares, Pedro Gaspar
 */
public class Simulator extends SimState {

    private static final long serialVersionUID = 1L;

    private final static int N_EXPLORERS = 10;
    public final static int WIDTH = 400;
    public final static int HEIGHT = 300;
    public final static int limitRadius = (int) (Math.max(WIDTH, HEIGHT) * 0.25);    // The 0.25 should be RAIUS_RATIO or something
    private static final int NUMBER_OF_EXECUTIONS = 50;

    public SimEnvironment env;

    /**
     * Default constructor that creates a new instance of the simulator
     *
     * @param seed Seed for the random number generator
     */
    public Simulator(long seed) {
        super(seed);
    }

    /**
     * Start the simulation. Schedule the environment to iterate.
     */
    public void start() {
        super.start();
        env = new SimEnvironment(this, WIDTH, HEIGHT, N_EXPLORERS);
        schedule.scheduleRepeating(env);
        // Now, everything else is up to the environment
    }

    public static void main(String[] args) {
        for (int i = 1; i <= NUMBER_OF_EXECUTIONS; i++) {
            try {
                System.out.println("Execution number: " + i);
                doLoop(Simulator.class, args);
            } catch (Exception e) {
                System.out.println("An error occurred, rescheduling execution " + i);
                i = i - 1;
            }
        }
        System.exit(0);
    }
}


