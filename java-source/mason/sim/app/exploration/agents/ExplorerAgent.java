package sim.app.exploration.agents;

import sim.app.exploration.core.Simulator;
import sim.app.exploration.env.Bid;
import sim.app.exploration.env.Network;
import sim.app.exploration.env.SimEnvironment;
import sim.app.exploration.objects.Prototype;
import sim.app.exploration.objects.SimObject;
import sim.app.exploration.utils.Utils;
import sim.engine.SimState;
import sim.field.grid.SparseGrid2D;
import sim.util.Bag;
import sim.util.Double2D;
import sim.util.Int2D;

import java.awt.*;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ExplorerAgent implements sim.portrayal.Oriented2D {

    private static final long serialVersionUID = 1L;
    private static final int TIME_WAIT_REPLY_BIDDING = 15;
    private static final int IDENTIFY_TIME = 15;

    private float INTEREST_THRESHOLD = 65;
    private final double STEP = Math.sqrt(2);
    private final int viewRange = 40;

    private int identifyClock;
    private int biddingClock;

    private Int2D loc;
    private Int2D target;
    private PointOfInterest potentialNextTarget;
    private double orientation;

    public SimEnvironment env;

    /**
     * Vector of prototypes used for making predictions
     */
    private Vector<Prototype> knownObjects;
    /**
     * Array containing the world known to the agents. It contains the position of each agent and of each object seen
     * so far (classified or not, it contains also object of which the respective class is not known)
     */
    private SparseGrid2D knownWorld;
    /**
     * Array containing the class of the objects got so far (through prediction or through classification on location)
     */
    private Class[][] identifiedObjects;
    // Broker fields
    private ArrayList<PointOfInterest> pointsOfInterest;
    private ArrayList<PointOfInterest> removedPoIs;
    /**
     * Network used for broadcasting information between explorers
     */
    private Network net;
    private int id;

    private List<Bid> receivedBids;
    private boolean waitingForBidReply;

    static Handler fileHandler = null;
    private static final Logger logger = Logger.getLogger(ExplorerAgent.class.getClass().getName());

    static {
        setupLogger();
    }

    private static void setupLogger() {
        try {
            fileHandler = new FileHandler("logfile.log");
            System.setProperty("java.util.logging.SimpleFormatter.format", "(%1$tc) [%4$s] ~%2$s~%nMessage: \"%5$s\"%n");
            SimpleFormatter simple = new SimpleFormatter();
            fileHandler.setFormatter(simple);

            logger.addHandler(fileHandler);
            logger.setUseParentHandlers(false);
        } catch (IOException e) {
            // TODO Auto-generated catch block
        }
    }

    public ExplorerAgent(int id, Int2D loc, int width, int height) {
        this.id = id;
        this.loc = loc;
        this.orientation = 0;
        this.target = null;
        this.potentialNextTarget = null;
        this.knownObjects = new Vector<>();
        this.identifyClock = 0;
        this.biddingClock = 0;
        this.waitingForBidReply = false;

        net = Network.getInstance();
        net.register(this);

        receivedBids = new ArrayList<>();
        knownWorld = new SparseGrid2D(width, height);
        identifiedObjects = new Class[width][height];
        this.knownObjects = new Vector<>();
        // Update the known world with the agent starting position
        net.broadcastLocation(this, this.getLoc());

        this.pointsOfInterest = new ArrayList<>();
        this.removedPoIs = new ArrayList<>();
    }

    public void step(SimState state) {
        if (identifyClock == 0) {
            Bag visible = env.getVisibleObejcts(loc.x, loc.y, viewRange);

            // -------------------------------------------------------------
            for (int i = 1; i < visible.size(); i++) {
                SimObject obj = (SimObject) visible.get(i);

                if (!this.isIdentified(obj.loc)) {
                    Hashtable<Class, Double> probs = getProbabilityDist(obj);

                    float interest = getObjectInterest(probs);

                    // If not interesting enough, classify it to the highest prob
                    if (interest < INTEREST_THRESHOLD) {
                        Class highest = Utils.getHighestProb(probs);
                        this.identify(obj, highest);
                        net.broadcastClassifiedObject(this, obj, highest);
                        this.removePointOfInterest(obj.loc);
                        net.broadcastRemovePointOfInterest(this, obj.loc);
                    } else {
                        this.addObject(obj);
                        // Let the other explorers know that there is an object in that location
                        net.broadcastObject(this, obj);
                        // Create a new point of interest and add it to the explorer list
                        this.addPointOfInterest(new PointOfInterest(obj.loc, interest));
                        //net.broadcastPointOfInterest(new PointOfInterest(obj.loc,interest));
                    }
                }
            }
            // --------------------------------------------------------------

            // Check to see if the explorer has reached its target: in case identify the object on location (this require
            // time). After the object is identified, add it to the prototypes list
            if (target != null && loc.distance(target) == 0) {
                target = null;

                SimObject obj = env.identifyObject(loc);

                if (obj != null) {
                    // this.removePointOfInterest(obj.loc);
                    // this.identify(obj, obj.getClass());
                    this.removePointOfInterest(loc);
                    this.identify(obj, obj.getClass());
                    this.addPrototype(obj, obj.getClass());
                    net.broadcastClassifiedObject(this, obj, obj.getClass());
                    net.broadcastPrototype(this, obj, obj.getClass());
                    net.broadcastRemovePointOfInterest(this, loc);

                    identifyClock = IDENTIFY_TIME;
                }
                // Debug
                logger.info(String.format("[Explorer %d] reached target (%d,%d)", this.id, loc.x, loc.y));
            }

            if (target == null)
                runBiddingAlgorithm();


            // Move agent toward target
            if (target != null) {
                // Agent movement
                Double2D step = new Double2D(target.x - loc.x, target.y - loc.y);
                step.limit(STEP);

                loc.x += Math.round(step.x);
                loc.y += Math.round(step.y);

                env.updateLocation(this, loc);
                this.updateLocation(this, loc);
                net.broadcastLocation(this, loc);

                orientation = Math.atan2(Math.round(step.y), Math.round(step.x));
            }
        } else if (target == null)
            runBiddingAlgorithm();

        if (identifyClock > 0)
            identifyClock--;
        if (biddingClock > 0)
            biddingClock--;
    }

    private void runBiddingAlgorithm() {
        // Check if explorer is not waiting for a bid reply
        if (biddingClock == 0) {
            // Check if the explorer had already started a bid and was waiting for the respective reply
            if (waitingForBidReply) {
                // The explorer won the bid: the bid target is now it's new target
                target = potentialNextTarget.loc;
                potentialNextTarget = null;
                waitingForBidReply = false;
                // Debug
                logger.info(String.format("[Explorer %d] won bid for target (%d,%d)", this.id, target.x, target.y));
            } else {
                // Get the PoI with the highest interest
                PointOfInterest PoI = getHighestPointOfInterest();
                if (PoI != null && !removedPoIs.contains(PoI)) {
                    this.removePointOfInterest(PoI.loc);
                    net.broadcastRemovePointOfInterest(this, PoI.loc);
                    startBiddingForNewTarget(PoI);
                } else {
                    target = getLimitedRandomTarget(this.getLoc());

                    // Debug
                    logger.info(String.format("[Explorer %d] received as RANDOM TARGET location (%d,%d)",
                            this.id,
                            target.x,
                            target.y));
                }
            }
        } else {
            // Explorer has already made a bid and he is waiting for a reply
            // Check if the explorer has received a bid with an highest relevance (shorter distance) for its potential new target
            for (Bid bid : receivedBids) {
                if (bid.getPointOfInterest().equals(potentialNextTarget)) {
                    if (bid.getDistance() < potentialNextTarget.loc.distance(this.getLoc())) {
                        // Debug
                        logger.info(String.format("[Explorer %d] lost bid for target (%d,%d)", this.id, potentialNextTarget.loc.x, potentialNextTarget.loc.y));
                        // Explorer has lost the bid, stop waiting for a reply and quit the bid
                        waitingForBidReply = false;
                        // Stop waiting
                        biddingClock = 0;
                        // Delete the potential next target and quit the loop
                        potentialNextTarget = null;
                        break;
                    }
                }
            }
            receivedBids.clear();
        }
    }

    private void startBiddingForNewTarget(PointOfInterest PoI) {
        double distance = PoI.loc.distance(this.getLoc());

        // Debug
        logger.info(String.format("[Explorer %d] started bidding on target (%d,%d) [Distance %f]",
                this.id,
                PoI.loc.x,
                PoI.loc.y,
                distance));

        net.broadcastNextTargetBid(this, PoI, distance);
        potentialNextTarget = PoI;
        biddingClock = TIME_WAIT_REPLY_BIDDING;
        waitingForBidReply = true;
    }


    /**
     *
     * @param PoI
     * @param distance Distance of the agent who made the bid from the PoI
     */
    public void receiveBid(PointOfInterest PoI, double distance) {
        boolean replyToBid = false;
        receivedBids.add(new Bid(PoI, distance));
        double myDistance = PoI.loc.distance(this.getLoc());

        // Debug
        logger.info(String.format("[Explorer %d] received a bid for target (%d,%d). [Bid dist: %.2f] [His dist: %.2f] [Pot. target dist: %.2f] ",
                this.id,
                PoI.loc.x,
                PoI.loc.y,
                distance,
                myDistance,
                potentialNextTarget != null ? potentialNextTarget.loc.distance(this.getLoc()) : 0));

        // If my distance from the received PoI is lower then the received bid, then check if it is worth to make
        // a new bid for that location (reply to the bid)
        if (myDistance < distance) {
            if (potentialNextTarget == null) {
                // The explorer wasn't bidding on any other location, so it worth to reply to the received bid
                replyToBid = true;
            } else {
                // It is worth replying to the received bid only if the distance from the location on which the explorer
                // was previously bidding is greater than the explorer distance from the location of which he received
                // the bid
                if (myDistance < potentialNextTarget.loc.distance(this.getLoc())) {
                    replyToBid = true;
                    logger.info(String.format("[Explorer %d] has distance from target (%d,%d) lower than the one from its old potential target (%d,%d) [distance = %.2f] [old distance = %.2f",
                            this.id,
                            PoI.loc.x,
                            PoI.loc.y,
                            potentialNextTarget.loc.x,
                            potentialNextTarget.loc.y,
                            myDistance,
                            potentialNextTarget.loc.distance(this.getLoc())));
                }
            }
        }

        if (replyToBid) {
            // If the explorer was previously bidding on a location and quit the bid, then make again the
            // respective location a PoI
            if (potentialNextTarget != null) {
                removedPoIs.remove(potentialNextTarget);
                addPointOfInterest(potentialNextTarget);
            }

            startBiddingForNewTarget(PoI);
        }
    }


    @Override
    public double orientation2D() {
        return orientation;
    }

    public Int2D getLoc() {
        return loc;
    }

    public double getOrientation() {
        return orientation;
    }

    /**
     * Identify the object provided as parameter classifying it to the highest probability class, which is provided as
     * parameter as well.
     *
     * @param obj     Object which class has been predicted (object to identify)
     * @param highest Predicted class of the object provided as parameter
     */
    public void identify(SimObject obj, Class highest) {
        Int2D loc = obj.loc;

        // Store the predicted class
        identifiedObjects[loc.x][loc.y] = highest;

        Class[] params = {Int2D.class, Color.class, double.class};
        Object[] args = {obj.loc, obj.color, obj.size};

        if (highest.isInstance(obj)) {
            this.addObject(obj);
        } else {
            // If the object is not an instance of the predicted class provided as parameter ("highest"), then create a
            // new instance of the class "highest" and add it to the known world. In this way we are sure that for each
            // predicted class stored in identifiedObjects we have a corresponding object instance in the array knownWorld.
            try {
                Constructor c = highest.getConstructor(params);
                SimObject newObj = (SimObject) c.newInstance(args);
                this.addObject(newObj);
            } catch (Exception e) {
                System.err.println("No such constructor, please give up on life.");
            }
        }
    }

    /**
     * Store in the explorer known world the agent and the respective location provided as parameters
     *
     * @param agent
     * @param loc
     */
    public void updateLocation(ExplorerAgent agent, Int2D loc) {
        knownWorld.setObjectLocation(agent, loc);
    }


    public SparseGrid2D getKnownWorld() {
        return knownWorld;
    }

    /**
     * Return the class of the identified object at location (x,y)
     *
     * @param x
     * @param y
     * @return
     */
    public Class getIdentifiedObject(int x, int y) {
        return identifiedObjects[x][y];
    }

    /**
     * @param obj
     */
    public void addObject(SimObject obj) {
        Int2D loc = obj.loc;
        Bag temp = knownWorld.getObjectsAtLocation(loc.x, loc.y);

        if (temp != null) {
            Bag here = new Bag(temp);

            for (Object o : here) {
                if (!(o instanceof ExplorerAgent)) {
                    knownWorld.remove(o);
                }
            }
        }

        knownWorld.setObjectLocation(obj, loc);
    }

    private boolean isIdentified(Int2D loc) {
        return identifiedObjects[loc.x][loc.y] != null;
    }

    private int getObjectInterest(Hashtable<Class, Double> probs) {
        double unknownInterest = 0;
        double entropyInterest;
        Vector<Double> prob = new Vector<Double>();

        for (Class c : probs.keySet()) {
            if (c == SimObject.class)
                unknownInterest = Utils.interestFunction(probs.get(c));

            prob.add(probs.get(c));
        }

        entropyInterest = Utils.entropy(prob);

        double interest = (entropyInterest > unknownInterest ? entropyInterest : unknownInterest) * 100;

        return (int) Math.round(interest);
    }

    /**
     * Add a prototype to the explorer knownObjects
     *
     * @param obj    Object from which it is possible to get the info about the prototype (e.g. colour, shape, etc.)
     * @param class1 Class of the prototype to add
     */
    public void addPrototype(SimObject obj, Class class1) {
        for (Prototype p : this.knownObjects) {
            if (class1 == p.thisClass) {
                p.addOccurrence(obj.size, obj.color);
                return;
            }
        }

        this.knownObjects.add(new Prototype(class1, obj.size, obj.color));
    }

    private Hashtable<Class, Double> getProbabilityDist(SimObject obj) {

        Hashtable<Class, Double> probs = new Hashtable<Class, Double>();

        Vector<Prototype> prototypes;
        prototypes = this.knownObjects;

        int nClasses = prototypes.size();
        double unknownCorr = 0;
        double corrSum = 0;

        for (Prototype prot : prototypes) {
            double corr;
            double colorDist = Utils.colorDistance(obj.color, prot.color);
            double sizeDist = Math.abs(obj.size - prot.size) / Utils.MAX_SIZE;

            // Correlation
            corr = 1 - (0.5 * colorDist + 0.5 * sizeDist);
            // Saturation
            corr = Utils.saturate(corr, prot.nOccurrs);

            probs.put(prot.thisClass, corr * corr * corr);
            corrSum += corr * corr * corr;

            unknownCorr += (1 - corr) / nClasses;
        }

        if (nClasses == 0)
            unknownCorr = 1.0;
        probs.put(SimObject.class, unknownCorr * unknownCorr * unknownCorr);
        corrSum += unknownCorr * unknownCorr * unknownCorr;

        for (Class c : probs.keySet()) {

            probs.put(c, probs.get(c) / corrSum);
            //System.out.println(c.getSimpleName() + " : " + probs.get(c));
        }

        return probs;
    }


    public void removePointOfInterest(Int2D loc) {
        PointOfInterest tmp = new PointOfInterest(loc, 1);
        if (pointsOfInterest.contains(tmp)) {
            pointsOfInterest.remove(tmp);
            logger.info(String.format("[Explorer %d] removed the PoI (%d,%d). Now it has %d PoIs",
                    this.id,
                    loc.x,
                    loc.y,
                    pointsOfInterest.size()));
        }
        removedPoIs.add(tmp);
    }

    private void addPointOfInterest(PointOfInterest PoI) {
        if (!pointsOfInterest.contains(PoI) && !removedPoIs.contains(PoI)) {
            pointsOfInterest.add(PoI);
            logger.info(String.format("[Explorer %d] Added (%d,%d) as a new PoI. Now it has %d PoIs",
                    this.id,
                    PoI.loc.x,
                    PoI.loc.y,
                    pointsOfInterest.size()));
        }
    }


    private Int2D getLimitedRandomTarget(Int2D agentPos) {
        Int2D target = null;

        do {
            target = getRandomTarget();
        } while (!(agentPos.distance(target) <= Simulator.limitRadius));

        return target;
    }

    private Int2D getRandomTarget() {
        return new Int2D((int) (Math.random() * Simulator.WIDTH), (int) (Math.random() * Simulator.HEIGHT));
    }

    /**
     * Return, within all the Points of Interest of the explorer, the one with the highest interest. If the explorer
     * doesn't have any point of interest, then return null.
     *
     * @return
     */
    private PointOfInterest getHighestPointOfInterest() {
        PointOfInterest result = null;
        if (pointsOfInterest.size() > 0) {
            PointOfInterest maxInterestPoint = pointsOfInterest.get(0);
            for (PointOfInterest PoI : pointsOfInterest) {
                if (PoI.interestMeasure > maxInterestPoint.interestMeasure)
                    maxInterestPoint = PoI;
            }
            return maxInterestPoint;
        }
        return result;
    }
}
