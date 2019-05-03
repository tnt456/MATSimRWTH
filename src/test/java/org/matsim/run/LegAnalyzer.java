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
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.population.Person;

/**
 * @author zmeng, ikaddoura
 *
 */
class LegAnalyzer implements PersonDepartureEventHandler, PersonArrivalEventHandler {

    private final Set<Id<Person>> personList;
    private final Map<Id<Person>, Map<Integer, LegInfo>> person2legInfo = new HashMap<>();
    private final Map<Id<Person>, Integer> person2legNr = new HashMap<>();

    LegAnalyzer(Set<Id<Person>> personList) {
        this.personList = personList;
    }

	@Override
	public void reset(int iteration) {
		this.person2legInfo.clear();
		this.person2legNr.clear();
    }

	@Override
    public void handleEvent(PersonDepartureEvent event){

		if (this.personList.contains(event.getPersonId())) {
			
			if (this.person2legNr.get(event.getPersonId()) == null) {
				
				this.person2legNr.put(event.getPersonId(), 0);
				
				Map<Integer, LegInfo> legNr2LegInfo = new HashMap<>();
				this.person2legInfo.put(event.getPersonId(), legNr2LegInfo);
				
			} else {
				int legNr = this.person2legNr.get(event.getPersonId()) + 1;
				this.person2legNr.put(event.getPersonId(), legNr);
			}
			
			this.person2legInfo.get(event.getPersonId()).put(person2legNr.get(event.getPersonId()), new LegInfo(event.getLegMode(), event.getTime()));
			
		} else {
			// skip person
		}
    }


    @Override
    public void handleEvent(PersonArrivalEvent event) {
		if (this.personList.contains(event.getPersonId())) {
			this.person2legInfo.get(event.getPersonId()).get(person2legNr.get(event.getPersonId())).setArrivalTime(event.getTime());
		} else {
			// skip person
		}
    }

	public Map<Id<Person>, Map<Integer, LegInfo>> getPerson2legInfo() {
		return person2legInfo;
	}

}
