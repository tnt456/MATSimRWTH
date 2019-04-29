/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.run;

import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.network.Network;


import java.util.HashMap;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author zmeng
 *
 */
class SpecificTripTime2ModeEventHandler implements PersonDepartureEventHandler, PersonArrivalEventHandler {

    private final Network network;
    List<String> personList;
    Map<String,  Map<String, TripTime>> allTrips2Person = new HashMap<>();

    List<String> helpMode = new LinkedList<>();


    SpecificTripTime2ModeEventHandler(Network network, List<String> personList) {

        this.network = network;
        this.personList = personList;
        helpMode.add("transit_walk");
        helpMode.add("access_walk");
        helpMode.add("egress_walk");

        for(int i=0;i<this.personList.size();i++){
            this.allTrips2Person.put(personList.get(i), new HashMap<String, TripTime>());
        }

    }

    @Override
    public void handleEvent(PersonDepartureEvent event){

        if (allTrips2Person.containsKey(event.getPersonId().toString()) && (!helpMode.contains(event.getLegMode()))){
            if (!allTrips2Person.get(event.getPersonId().toString()).containsKey(event.getLegMode())) {
                allTrips2Person.get(event.getPersonId().toString()).put(event.getLegMode(), new TripTime());
                allTrips2Person.get(event.getPersonId().toString()).get(event.getLegMode()).beginTime = event.getTime();
            }
        }
    }


    @Override
    public void handleEvent(PersonArrivalEvent event) {
        if(allTrips2Person.containsKey(event.getPersonId().toString()) && (!helpMode.contains(event.getLegMode()))){
            if(allTrips2Person.get(event.getPersonId().toString()).get(event.getLegMode()).endTime == 0){
                allTrips2Person.get(event.getPersonId().toString()).get(event.getLegMode()).endTime = event.getTime();
                allTrips2Person.get(event.getPersonId().toString()).get(event.getLegMode()).tripTime = event.getTime()-allTrips2Person.get(event.getPersonId().toString()).get(event.getLegMode()).beginTime;
            }
        }
    }

}
