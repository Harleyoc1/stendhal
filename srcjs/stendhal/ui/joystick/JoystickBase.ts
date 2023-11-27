/***************************************************************************
 *                       Copyright © 2023 - Stendhal                       *
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Affero General Public License as        *
 *   published by the Free Software Foundation; either version 3 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 ***************************************************************************/

declare var stendhal: any;


/**
 * Joystick implementation that does nothing. Used when joystick should not be drawn on-screen.
 */
export class JoystickBase {

	public reset() {
		// do nothing
	}

	public onRemoved() {
		// do nothing
	}

	protected getResource(res: string) {
		return stendhal.paths.gui + "/joystick/" + res + ".png";
	}

	protected getCenterX(): number {
		return stendhal.config.getInt("ui.joystick.center.x", 224);
	}

	protected getCenterY(): number {
		return stendhal.config.getInt("ui.joystick.center.y", 384);
	}
}
