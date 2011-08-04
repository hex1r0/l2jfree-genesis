/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.loginserver.network.legacy.packets.receivable;

import java.nio.BufferUnderflowException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;

import com.l2jfree.loginserver.config.ServiceConfig;
import com.l2jfree.loginserver.network.gameserver.L2GameServerCache;
import com.l2jfree.loginserver.network.legacy.L2GameServer;
import com.l2jfree.loginserver.network.legacy.L2LegacyConnections;
import com.l2jfree.loginserver.network.legacy.L2LegacyState;
import com.l2jfree.loginserver.network.legacy.L2NoServiceReason;
import com.l2jfree.loginserver.network.legacy.packets.L2GameServerPacket;
import com.l2jfree.loginserver.network.legacy.packets.sendable.AuthResponse;
import com.l2jfree.loginserver.network.legacy.packets.sendable.LoginServerFail;
import com.l2jfree.network.mmocore.InvalidPacketException;
import com.l2jfree.network.mmocore.MMOBuffer;
import com.l2jfree.sql.L2Database;
import com.l2jfree.util.HexUtil;
import com.l2jfree.util.logging.L2Logger;

/**
 * @author savormix
 *
 */
public final class GameServerAuth extends L2GameServerPacket
{
	/** Packet's identifier */
	public static final int OPCODE = 0x01;
	
	private static final L2Logger _log = L2Logger.getLogger(GameServerAuth.class);
	
	private int _desiredId;
	private boolean _acceptAlternateId;
	//private boolean _reservedHost;
	private int _port;
	private int _maxPlayers;
	private byte[] _hexId;
	private String[] _hosts;
	
	@Override
	protected int getMinimumLength()
	{
		return 17;
	}
	
	@Override
	protected void read(MMOBuffer buf) throws BufferUnderflowException,
			RuntimeException
	{
		_desiredId = buf.readC();
		_acceptAlternateId = (buf.readC() == 0 ? false : true);
		/*_reservedHost = (*/buf.readC()/* == 0 ? false : true)*/;
		_port = buf.readH();
		_maxPlayers = buf.readD();
		int size = buf.readD();
		_hexId = buf.readB(new byte[size]);
		size = 2 * buf.readD();
		_hosts = new String[size];
		for (int i = 0; i < size; i++)
			_hosts[i] = buf.readS();
	}
	
	@Override
	protected void runImpl() throws InvalidPacketException, RuntimeException
	{
		L2GameServer lgs = getClient();
		L2LegacyConnections llc = L2LegacyConnections.getInstance();
		synchronized (L2GameServerCache.getInstance().getAuthorizationLock())
		{
			if (llc.getById(_desiredId) != null)
			{
				if (!_acceptAlternateId || ServiceConfig.SVC_STRICT_AUTHORIZATION)
					// desired ID is not available
					lgs.close(new LoginServerFail(L2NoServiceReason.ALREADY_LOGGED_IN));
				else // game server can take any ID and login server may assign them
					tryAssignAvailableId(lgs);
			}
			else // no on-line game server on the desired ID
			{
				boolean reservedId = false;
				String auth = null;
				boolean bans = false;
				
				Connection con = null;
				try
				{
					con = L2Database.getConnection();
					PreparedStatement ps = con.prepareStatement("SELECT authData, allowBans FROM gameserver WHERE id = ?");
					ps.setInt(1, _desiredId);
					ResultSet rs = ps.executeQuery();
					
					reservedId = rs.next();
					if (reservedId)
					{
						auth = rs.getString("authData");
						bans = rs.getBoolean("allowBans");
					}
					
					rs.close();
					ps.close();
				}
				catch (SQLException e)
				{
					_log.error("Could not obtain game server data!", e);
					lgs.close(new LoginServerFail(L2NoServiceReason.NO_FREE_ID));
					return;
				}
				finally
				{
					L2Database.close(con);
				}
				
				if (reservedId) // this ID is registered
				{
					String hexId = HexUtil.bytesToHexString(_hexId);
					if (!hexId.equals(auth)) // invalid authorization
					{
						if (!_acceptAlternateId || ServiceConfig.SVC_STRICT_AUTHORIZATION)
							// desired ID is not available
							lgs.close(new LoginServerFail(L2NoServiceReason.WRONG_HEXID));
						else // game server can take any ID and login server may assign them
							tryAssignAvailableId(lgs);
					}
					else // valid authorization
						finishAuthorization(_desiredId, hexId, bans, lgs);
				}
				else if (ServiceConfig.SVC_STRICT_AUTHORIZATION) // ID is free, but not available
				{
					lgs.close(new LoginServerFail(L2NoServiceReason.WRONG_HEXID));
				}
				else // ID is available for persistent use
				{
					String hexId = HexUtil.bytesToHexString(_hexId);
					if (ServiceConfig.SVC_SAVE_REQUESTS)
					{
						try
						{
							con = L2Database.getConnection();
							PreparedStatement ps = con.prepareStatement("INSERT INTO gameserver (id, authData, allowBans) VALUES (?, ?, ?)");
							ps.setInt(1, _desiredId);
							ps.setString(2, hexId);
							ps.setBoolean(3, false);
							ps.executeUpdate();
							ps.close();
						}
						catch (SQLException e)
						{
							_log.error("Could not save game server data!", e);
							// but we still can authorize temporarily
						}
						finally
						{
							L2Database.close(con);
						}
					}
					
					finishAuthorization(_desiredId, hexId, false, lgs);
				}
			}
		}
	}
	
	private void finishAuthorization(int id, String auth, boolean trusted, L2GameServer lgs)
	{
		lgs.setId(id);
		lgs.setAuth(auth);
		lgs.setAllowedToBan(trusted);
		
		// TODO subnet-based hosts
		// REGRESSION -->
		lgs.setHost(null);
		for (int i = 0; i < _hosts.length; i++)
			if (_hosts[i++].startsWith("0"))
				lgs.setHost(_hosts[i]);
		
		if (lgs.getHost() == null)
		{
			lgs.setHost("127.0.0.1");
			_log.info("Game server on ID " + _desiredId + " did not specify a default IP!");
		}
		else
			_log.info("Authorized legacy/compatible game server on ID " + _desiredId +
					", advertised IP: " + lgs.getHost());
		// <-- REGRESSION
		lgs.setPort(_port);
		lgs.setMaxPlayers(_maxPlayers);
		
		L2LegacyConnections.getInstance().addGameServer(_desiredId, lgs);
		lgs.setState(L2LegacyState.AUTHED);
		lgs.sendPacket(new AuthResponse(lgs));
	}
	
	private void tryAssignAvailableId(L2GameServer lgs)
	{
		Set<Integer> reserved;
		
		Connection con = null;
		try
		{
			con = L2Database.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT id FROM gameserver");
			ResultSet rs = ps.executeQuery();
			
			reserved = new TreeSet<Integer>();
			while (rs.next())
				reserved.add(rs.getInt("id"));
			
			rs.close();
			ps.close();
		}
		catch (SQLException e)
		{
			_log.error("Could not obtain game server data!", e);
			lgs.close(new LoginServerFail(L2NoServiceReason.NO_FREE_ID));
			return;
		}
		finally
		{
			L2Database.close(con);
		}
		
		int newId;
		boolean available = false;
		for (newId = 1; newId < Byte.MAX_VALUE; newId++)
		{
			if (reserved.remove(newId) || // Cannot use a registered ID
					L2LegacyConnections.getInstance().getById(newId) != null)
					// Cannot use an ID in use
				continue;
			else // can use this ID
			{
				available = true;
				break;
			}
		}
		
		if (available)
			finishAuthorization(newId, null, false, lgs);
		else // all IDs registered or in use
			lgs.close(new LoginServerFail(L2NoServiceReason.NO_FREE_ID));
	}
}
