/***************************************************************************
 *                    Copyright © 2024 - Faiumoni e. V.                    *
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Affero General Public License as        *
 *   published by the Free Software Foundation; either version 3 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 ***************************************************************************/

import { ButtonBase } from "./ButtonBase";
import { ChatOptionsDialog } from "../dialog/ChatOptionsDialog";


/**
 * Button to bring up chat options dialog.
 */
export class ChatOptsButton extends ButtonBase {

	constructor() {
		super("chatopts");
	}

	protected override onClick(e: Event) {
		ChatOptionsDialog.createOptions();
	}
}
