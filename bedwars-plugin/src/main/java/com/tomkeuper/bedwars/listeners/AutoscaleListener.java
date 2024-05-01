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

package com.tomkeuper.bedwars.listeners;

import com.tomkeuper.bedwars.api.arena.GameState;
import com.tomkeuper.bedwars.api.events.gameplay.GameStateChangeEvent;
import com.tomkeuper.bedwars.arena.Arena;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class AutoscaleListener implements Listener {

    @EventHandler
    public void onPlaying(GameStateChangeEvent e) {
        if (e.getNewState() == GameState.playing && Arena.canAutoScale(e.getArena().getArenaName())) {
            if (Arena.getGamesBeforeRestart() > 1){
                new Arena(e.getArena().getArenaName(), null);
            }
        }
    }
}
