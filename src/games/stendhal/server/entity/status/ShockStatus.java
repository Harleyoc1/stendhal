/***************************************************************************
 *                      (C) Copyright 2013 - Stendhal                      *
 ***************************************************************************
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 ***************************************************************************/
package games.stendhal.server.entity.status;

import games.stendhal.server.entity.RPEntity;

/**
 * A status effect that causes the entity to stop moving after a set amount of
 * steps
 * 
 * @author Jordan
 * 
 */
public class ShockStatus extends Status {

	/** Entity is "shocked" after taking 5 steps */
	private final int stepsDelay = 5;

	/**
	 * Create the status
	 */
	public ShockStatus() {
		// Give the status a name
		super("shock");
		super.setTimeout(200);
		super.setMaxOccurrences(6);
	}

	/**
	 * Called on each turn
	 * 
	 * @param entity
	 */
	@Override
	public void affect(final RPEntity entity) {
		super.affect(entity);

		int stepsTaken = entity.getStepsTaken();
		if (stepsTaken == stepsDelay) {

			// Stop the entity's movement after 5 steps
			entity.stop();
			entity.clearPath();
		}
	}

	/**
	 * returns the status type
	 * 
	 * @return StatusType
	 */
	@Override
	public StatusType getStatusType() {
		return StatusType.SHOCKED;
	}
}
