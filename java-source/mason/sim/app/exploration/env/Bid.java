package sim.app.exploration.env;

import sim.app.exploration.agents.PointOfInterest;

/**
 * Represent a bid made by an explorer agent during the process of determining a new target location.
 * It consists of a point of interest and the distance of the PoI from the explorer who made the bid.
 */
public class Bid {
    PointOfInterest pointOfInterest;
    double distance;

    public Bid(PointOfInterest pointOfInterest, double distance) {
        this.pointOfInterest = pointOfInterest;
        this.distance = distance;
    }

    public PointOfInterest getPointOfInterest() {
        return pointOfInterest;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }
}
