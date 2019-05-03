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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.population.Person;

/**
 * @author zmeng
 *
 */
class PersonsFirstLegTTAnalyzer implements PersonDepartureEventHandler, PersonArrivalEventHandler {

    private final List<Id<Person>> personList;
    private final List<String> helpModes;
    private final Map<Id<Person>,  Map<String, TripTime>> person2mode2legTime = new HashMap<>();

    PersonsFirstLegTTAnalyzer(List<Id<Person>> personList, List<String> legModesToIgnore) {

        this.personList = personList;
        this.helpModes = legModesToIgnore;

        for(int i=0; i<this.personList.size(); i++){
            this.person2mode2legTime.put(personList.get(i), new HashMap<String, TripTime>());
        }
    }

    @Override
	public void reset(int iteration) {
    	this.getPerson2Mode2LegTime().clear();
    }

	@Override
    public void handleEvent(PersonDepartureEvent event){

        if (person2mode2legTime.containsKey(event.getPersonId()) && (!helpModes.contains(event.getLegMode()))){
            if (!person2mode2legTime.get(event.getPersonId()).containsKey(event.getLegMode())) {
                person2mode2legTime.get(event.getPersonId()).put(event.getLegMode(), new TripTime());
                person2mode2legTime.get(event.getPersonId()).get(event.getLegMode()).setBeginTime(event.getTime());
            }
        }
    }


    @Override
    public void handleEvent(PersonArrivalEvent event) {
        if(person2mode2legTime.containsKey(event.getPersonId()) && (!helpModes.contains(event.getLegMode()))){
            if(person2mode2legTime.get(event.getPersonId()).get(event.getLegMode()).getEndTime() == 0.){
                person2mode2legTime.get(event.getPersonId()).get(event.getLegMode()).setEndTime(event.getTime());
                person2mode2legTime.get(event.getPersonId()).get(event.getLegMode()).setTripTime(event.getTime() - person2mode2legTime.get(event.getPersonId()).get(event.getLegMode()).getBeginTime());
            }
        }
    }

	public Map<Id<Person>, Map<String, TripTime>> getPerson2Mode2LegTime() {
		return person2mode2legTime;
	}

}
