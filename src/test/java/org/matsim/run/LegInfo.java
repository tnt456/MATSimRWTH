/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
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

/**
* @author ikaddoura
*/

public class LegInfo {

	private final String legMode;
	private final double departureTime;
	private double arrivalTime;
	
	public LegInfo(String legMode, double departureTime) {
		this.legMode = legMode;
		this.departureTime = departureTime;
	}

	public String getLegMode() {
		return legMode;
	}

	public double getDepartureTime() {
		return departureTime;
	}

	public double getArrivalTime() {
		return arrivalTime;
	}

	public void setArrivalTime(double time) {
		this.arrivalTime = time;
	}

	public double getTravelTime() {
		return arrivalTime - departureTime;
	}

}

