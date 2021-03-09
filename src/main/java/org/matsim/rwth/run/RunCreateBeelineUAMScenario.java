package org.matsim.rwth.run;

import net.bhl.matsim.uam.scenario.RunCreateUAMBeelineScenario;

public class RunCreateBeelineUAMScenario {
    public static void main(String[] args) {
        System.out.println("ARGS: config.xml* uam-stations.csv* uam-link-freespeed* uam-link-capacity* uam-vehicles.csv");
        System.out.println("(* required)");
        RunCreateUAMBeelineScenario.main(args);

    }
}
