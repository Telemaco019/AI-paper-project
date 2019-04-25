package sim.app.exploration.env;

import sim.app.exploration.agents.ExplorerAgent;
import sim.app.exploration.agents.PointOfInterest;
import sim.app.exploration.objects.SimObject;
import sim.util.Int2D;

import java.util.ArrayList;

/**
 * Singleton class meant for allowing the broadcast of information between the explorer
 */
public class Network {
    private ArrayList<ExplorerAgent> nodes;
    private static Network instance;
    private static long number_of_messages;

    private Network() {
       init();
    }

    /**
     * Insert into the network the explorer agent provided as parameter
     *
     * @param agent
     */
    public void register(ExplorerAgent agent) {
        nodes.add(agent);
    }

    public static Network getInstance() {
        if (instance == null)
            instance = new Network();
        return instance;
    }

    /**
     * Returns the class of the identified object at location (x,y), based on the common knowledge of the exploers
     * of the network
     *
     * @param i x coordinate of the location
     * @param j y coordinate of the location
     * @return
     */
    public Class getIdentifiedObject(int i, int j) {
        // TODO
        return nodes.get(0).getIdentifiedObject(i, j);
    }

    /**
     * Return world commonly known by the explorers of the network (based on the common knowledge of the explorers)
     *
     * @return
     */
    public Object getKnownWorld() {
        // TODO
        return nodes.get(0).getKnownWorld();
    }

    public long getNumberOfMessages() {
        return number_of_messages;
    }

    public void broadcastLocation(ExplorerAgent caller, Int2D location) {
        nodes.stream().filter(n -> !n.equals(caller)).forEach(n -> n.updateLocation(caller, location));
        number_of_messages += nodes.size() - 1;
    }

    /*
    public void broadcastPointOfInterest(PointOfInterest poI) {
        nodes.stream().forEach(n -> n.addPointOfInterest(poI));
        number_of_messages += nodes.size();
    }
    */

    public void broadcastPrototype(ExplorerAgent caller, SimObject obj, Class class1) {
        nodes.stream().filter(n -> !n.equals(caller)).forEach(n -> n.addPrototype(obj, class1));
        number_of_messages += nodes.size() - 1;
    }

    public void broadcastClassifiedObject(ExplorerAgent caller, SimObject obj, Class highest) {
        nodes.stream().filter(n -> !n.equals(caller)).forEach(n -> n.identify(obj, highest));
        number_of_messages += nodes.size() - 1;
    }


    public void broadcastRemovePointOfInterest(ExplorerAgent caller, Int2D loc) {
        nodes.stream().filter(n -> !n.equals(caller)).forEach(n -> n.removePointOfInterest(loc));
        number_of_messages += nodes.size() - 1;
    }


    public void broadcastObject(ExplorerAgent caller, SimObject obj) {
        nodes.stream().filter(n -> !n.equals(caller)).forEach(n -> n.addObject(obj));
        number_of_messages += nodes.size() - 1;
    }

    /**
     * Broadcast the point of interest and the respective distance provided as parameter (associated to a bid)
     * to the explorers which are waiting for a new target
     *
     * @param poI
     * @param relevance
     */
    public void broadcastNextTargetBid(ExplorerAgent caller, PointOfInterest poI, double relevance) {
        nodes.stream()
                .filter(n -> !n.equals(caller))
                .forEach(n -> n.receiveBid(poI, relevance));
        number_of_messages += nodes.size() - 1;
    }

    public void init() {
        nodes = new ArrayList<>();
        number_of_messages = 0l;
    }
}
