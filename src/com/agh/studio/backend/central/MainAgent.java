package com.agh.studio.backend.central;

import com.agh.studio.backend.headquarter.HeadQuarter;
import com.agh.studio.backend.navigation.Location;
import com.agh.studio.backend.signalstorage.SignalDatabase;
import com.agh.studio.backend.smartwatch.PatrolStatus;
import com.agh.studio.backend.smartwatch.Smartwatch;
import com.agh.studio.backend.smartwatch.SmartwatchReport;

import java.util.*;
import java.util.stream.Collectors;

public class MainAgent {

    private HeadQuarter headQuarter;

    private SignalDatabase signalDatabase;

    private List<Smartwatch> smartwatchList;

    public MainAgent(HeadQuarter headQuarter, SignalDatabase signalDatabase, List<Smartwatch> smartwatchList) {
        this.headQuarter = headQuarter;
        this.signalDatabase = signalDatabase;
        this.smartwatchList = smartwatchList;
    }

    // wywołanie z pozoimu Main, częstość zależna od parametrów w HQ
    public void collectSignals() {
        signalDatabase.receiveDataFromSmartwatches(smartwatchList);
    }

    public void receiveAndProcessSignals() {
        List<SmartwatchReport> smartwatchReportList = signalDatabase.sendSignals();

        Smartwatch smartwatchForHelp = null;
        List<Smartwatch> availableSmartwatches = new ArrayList<>();

        int helpers = 0;

        for (SmartwatchReport smartwatchReport : smartwatchReportList) {
            Smartwatch sm = smartwatchReport.getSmartwatch();

            if ( (sm.getStatus() == PatrolStatus.FIRE_INTERVENTION) || (sm.getStatus() == PatrolStatus.PURSUIT) ) {
                smartwatchForHelp = sm;

                helpers = headQuarter.howManyHelp(smartwatchReport);

            } else if ( (sm.getStatus() == PatrolStatus.OBSERVER) ||
                        (sm.getStatus() == PatrolStatus.ROUTINE_INTERVENTION) ){
                availableSmartwatches.add(sm);
            }
        }

        if (smartwatchForHelp != null) {

            Location destination = smartwatchForHelp.getNavigation().getCurrentLocation();

            Set<Smartwatch> patrolsToBeSent =
                    chooseTheClosestPatrols(availableSmartwatches, destination, helpers).keySet();

            for (Smartwatch smartwatch : patrolsToBeSent) {
                setGoingToIntervention(smartwatch, destination);
            }

            if (smartwatchForHelp.getStatus() == PatrolStatus.FIRE_INTERVENTION)
                smartwatchForHelp.updateParameters(PatrolStatus.FIRE_INTERVENTION_WITH_SUPPORT, null);
            else if (smartwatchForHelp.getStatus() == PatrolStatus.PURSUIT)
                smartwatchForHelp.updateParameters(PatrolStatus.PURSUIT_WITH_SUPPORT, null);

        }

    }

    private void setGoingToIntervention(Smartwatch smartwatch, Location interventionLocation) {
        smartwatch.updateParameters(PatrolStatus.GOING_TO_INTERVENTION, interventionLocation);
    }

    private Map<Smartwatch, Double> chooseTheClosestPatrols(List<Smartwatch> smartwatches, Location patrolInNeed, int numberOfHelpers) {

        Map<Smartwatch, Double> patrolHelpers = new HashMap<>();

        for (Smartwatch smartwatch : smartwatches) {
            double distance = headQuarter.calculateDistanceBetween(patrolInNeed, smartwatch.getNavigation().getCurrentLocation());
            patrolHelpers.put(smartwatch, distance);
        }

        return patrolHelpers
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(numberOfHelpers)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, HashMap::new));
    }

    // ta fukncja może być wgl nie potrzebna, ponieważ po interwencji patrol sam powinien zmienić status na OBSERVER
    // lub funkcja może zostać ale wtedy Patrol musiałby przesłać jakieś info o zakończeniu interwencji więc najlepiej zostawić zmianę statusu w samym Patrolu(SW)
    public void sendEndOfInterventionToSmartwatch(Smartwatch smartwatch) {
        smartwatch.updateParameters(PatrolStatus.OBSERVER, null);
        smartwatch.getGun().setFired(false);
        // inne parametry, które będziemy chcieli przekazać
    }

}
