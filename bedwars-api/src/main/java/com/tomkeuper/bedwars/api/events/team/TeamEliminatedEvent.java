/*
 * BedWars2023 - A bed wars mini-game.
 * Copyright (C) 2024 Tomas Keuper
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Contact e-mail: contact@fyreblox.com
 */

package com.tomkeuper.bedwars.api.events.team;

import com.tomkeuper.bedwars.api.arena.IArena;
import com.tomkeuper.bedwars.api.arena.team.ITeam;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@SuppressWarnings("unused")
public class TeamEliminatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final IArena arena;
    private final ITeam team;

    /**
     * Called when all player on a team get killed and Bed is broken.
     * @param arena the arena.
     * @param team the eliminated team.
     */

    public TeamEliminatedEvent(IArena arena, ITeam team) {
        this.arena = arena;
        this.team = team;
    }

    public IArena getArena() {return arena;}

    public ITeam getTeam() {return team;}

    public HandlerList getHandlers() {return HANDLERS;}

    public static HandlerList getHandlerList() {return HANDLERS;}
}