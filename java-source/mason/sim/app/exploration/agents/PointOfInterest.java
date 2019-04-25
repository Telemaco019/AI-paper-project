package sim.app.exploration.agents;

import sim.util.Int2D;

public class PointOfInterest {
    public Int2D loc;
    public double interestMeasure;	// I expect this to be in [0, 100]

    PointOfInterest(Int2D loc, double interestMeasure) {
        this.loc = loc;
        this.interestMeasure = interestMeasure;
    }

    @Override
    public boolean equals(Object o_PoI) {
        PointOfInterest PoI = (PointOfInterest) o_PoI;
        return this.loc.equals(PoI.loc);
    }

    public String toString() {
        return "[" + loc.x + ", " + loc.y + " - " + interestMeasure + "]";
    }
}