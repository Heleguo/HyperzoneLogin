/*
 * Copyright (C) 2021 - 2025 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fun.iiii.hyperzone.login.limbo.handler;

import com.velocitypowered.api.proxy.Player;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;

public class LimboAuthSessionHandler implements LimboSessionHandler {
    private final Player proxyPlayer;
    private LimboPlayer player;

    public LimboAuthSessionHandler(Player proxyPlayer) {
        this.proxyPlayer = proxyPlayer;
    }

    @Override
    public void onSpawn(Limbo server, LimboPlayer player) {
        this.player = player;
    }

    @Override
    public void onChat(String message) {

        this.proxyPlayer.sendPlainMessage("SUCCESS MES");
        finishLogin();
    }

    @Override
    public void onGeneric(Object packet) {
//        有个对应的mod
    }

    @Override
    public void onDisconnect() {

    }

    public void finishLogin() {
        this.proxyPlayer.sendPlainMessage("SUCCESS LOGIN");
        finishAuth();
    }

    private void finishAuth() {
        this.proxyPlayer.clearTitle();
        this.player.disconnect();
    }
}
