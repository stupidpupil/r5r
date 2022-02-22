package org.ipea.r5r;

import com.conveyal.r5.OneOriginResult;
import com.conveyal.r5.analyst.PointSet;
import com.conveyal.r5.analyst.TravelTimeComputer;
import com.conveyal.r5.analyst.cluster.RegionalTask;
import com.conveyal.r5.transit.TransportNetwork;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

public class ParetoFrontierCalculator  extends R5Process {

    public ParetoFrontierCalculator(ForkJoinPool threadPool, TransportNetwork transportNetwork, RoutingProperties routingProperties) {
        super(threadPool, transportNetwork, routingProperties);
    }

    @Override
    protected RDataFrame runProcess(int index) throws ParseException {
        RegionalTask request = buildRequest(index);
        TravelTimeComputer computer = new TravelTimeComputer(request, transportNetwork);

        Map<Float, OneOriginResult> travelTimeResults = new HashMap<>();

        for (float fareCutoff : this.routingProperties.fareCutoffs) {
            request.maxFare = Math.round(fareCutoff * 100.0f);
            OneOriginResult results = computer.computeTravelTimes();

            travelTimeResults.put(fareCutoff, results);
        }

        RDataFrame travelTimesTable = buildDataFrameStructure(fromIds[index], 10);
        populateDataFrame(travelTimeResults, travelTimesTable);

        if (travelTimesTable.nRow() > 0) {
            return travelTimesTable;
        } else {
            return null;
        }
    }

    @Override
    protected RDataFrame buildDataFrameStructure(String fromId, int nRows) {
        // Build return table
        RDataFrame travelTimesTable = new RDataFrame(nRows);
        travelTimesTable.addStringColumn("from_id", fromId);
        travelTimesTable.addStringColumn("to_id", "");
        travelTimesTable.addIntegerColumn("percentile", 0);
        travelTimesTable.addIntegerColumn("travel_time", 0);
        travelTimesTable.addDoubleColumn("monetary_cost", 0.0);
        travelTimesTable.addDoubleColumn("monetary_cost_upper", 0.0);

        return travelTimesTable;
    }

    private void populateDataFrame(Map<Float, OneOriginResult> travelTimeResults, RDataFrame travelTimesTable) {
        for (int destination = 0; destination < destinationPoints.featureCount(); destination++) {
            for (int percentileIndex = 0; percentileIndex < this.routingProperties.percentiles.length; percentileIndex++) {
                // add new row to data frame
                travelTimesTable.append();

                // set destination id
                travelTimesTable.set("to_id", toIds[destination]);
                travelTimesTable.set("percentile", this.routingProperties.percentiles[percentileIndex]);

                boolean first = true;
                int previousTT = -1;
                for (float fare : this.routingProperties.fareCutoffs) {
                    OneOriginResult travelTimesByFare = travelTimeResults.get(fare);
                    int tt = travelTimesByFare.travelTimes.getValues()[percentileIndex][destination];

                    if (tt != previousTT) {
                        if (!first) travelTimesTable.appendRepeat();
                        travelTimesTable.set("monetary_cost", (double) fare);
                        travelTimesTable.set("monetary_cost_upper", (double) fare);
                        travelTimesTable.set("travel_time", tt);

                        previousTT = tt;
                        first = false;
                    } else {
                        travelTimesTable.set("monetary_cost_upper", (double) fare);
                    }
                }
            }
        }

    }

    @Override
    protected RegionalTask buildRequest(int index) throws ParseException {
        RegionalTask request = super.buildRequest(index);

        request.destinationPointSets = new PointSet[1];
        request.destinationPointSets[0] = destinationPoints;

        return request;
    }

}
