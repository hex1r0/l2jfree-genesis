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
package com.l2jfree.gameserver.gameobjects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.l2jfree.gameserver.datatables.PlayerNameTable;
import com.l2jfree.gameserver.datatables.PlayerTemplateTable;
import com.l2jfree.gameserver.gameobjects.player.PlayerAppearance;
import com.l2jfree.gameserver.gameobjects.player.PlayerKnownList;
import com.l2jfree.gameserver.network.client.Disconnection;
import com.l2jfree.gameserver.network.client.EmptyClient;
import com.l2jfree.gameserver.network.client.IL2Client;
import com.l2jfree.gameserver.network.client.packets.L2ServerPacket;
import com.l2jfree.gameserver.templates.L2PlayerTemplate;
import com.l2jfree.gameserver.templates.player.ClassId;
import com.l2jfree.gameserver.templates.player.Gender;
import com.l2jfree.gameserver.util.IdFactory;
import com.l2jfree.gameserver.util.IdFactory.IdRange;
import com.l2jfree.gameserver.world.L2World;
import com.l2jfree.sql.L2Database;

/**
 * @author NB4L1
 */
public class L2Player extends L2Character implements IL2Playable, PlayerNameTable.IPlayerInfo
{
	static
	{
		ComponentFactory.KNOWNLIST.register(L2Player.class, PlayerKnownList.class);
	}
	
	public static L2Player create(String name, String accountName, ClassId classId)
	{
		return create(name, accountName, classId, Gender.Male, (byte)0, (byte)0, (byte)0); // TODO
	}
	
	public static L2Player create(String name, String accountName, ClassId classId, Gender gender, byte face,
			byte hairColor, byte hairStyle)
	{
		final int objectId = IdFactory.getInstance().getNextId(IdRange.PLAYERS);
		
		L2Player result = null;
		
		Connection con = null;
		try
		{
			con = L2Database.getConnection();
			
			final PreparedStatement ps =
					con.prepareStatement("INSERT INTO players (objectId, name, accountName) VALUES (?,?,?)");
			ps.setInt(1, objectId);
			ps.setString(2, name);
			ps.setString(3, accountName);
			ps.executeUpdate();
			ps.close();
			
			result = new L2Player(objectId, classId, accountName, gender, face, hairColor, hairStyle);
			result.setName(name);
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			L2Database.close(con);
		}
		
		return result;
	}
	
	public static L2Player load(int objectId)
	{
		L2Player.disconnectIfOnline(objectId);
		
		L2Player result = null;
		
		Connection con = null;
		try
		{
			con = L2Database.getConnection();
			
			final PreparedStatement ps = con.prepareStatement("SELECT * FROM players WHERE objectId = ?");
			ps.setInt(1, objectId);
			
			final ResultSet rs = ps.executeQuery();
			
			if (rs.next())
			{
				final String accountName = rs.getString("accountName");
				final String name = rs.getString("name");
				final ClassId classId = ClassId.HumanFighter; // TODO
				final Gender gender = Gender.Male; // TODO
				final byte face = 0; // TODO
				final byte hairColor = 0; // TODO
				final byte hairStyle = 0; // TODO
				
				result = new L2Player(objectId, classId, accountName, gender, face, hairColor, hairStyle);
				result.setName(name);
			}
			
			rs.close();
			ps.close();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			L2Database.close(con);
		}
		
		return result;
	}
	
	public static List<L2Player> loadAccountPlayers(String accountName)
	{
		final List<L2Player> players = new ArrayList<L2Player>();
		
		for (Integer objectId : PlayerNameTable.getInstance().getObjectIdsForAccount(accountName))
			players.add(load(objectId));
		
		return players;
	}
	
	public static void disconnectIfOnline(int objectId)
	{
		L2Player onlinePlayer = L2World.findPlayer(objectId);
		
		if (onlinePlayer == null)
			onlinePlayer = L2World.findPlayer(PlayerNameTable.getInstance().getNameByObjectId(objectId));
		
		if (onlinePlayer == null)
			return;
		
		//if (!onlinePlayer.isInOfflineMode())
		_log.warn("Avoiding duplicate character! Disconnecting online character (" + onlinePlayer.getName() + ")");
		
		// FIXME won't be sent because client.close() clears the packet queue
		//onlinePlayer.sendPacket(SystemMessageId.ANOTHER_LOGIN_WITH_ACCOUNT);
		
		new Disconnection(onlinePlayer).defaultSequence(true);
	}
	
	private final String _accountName;
	
	private String _name;
	private String _title;
	
	private final PlayerAppearance _appearance;
	private IL2Client _client = EmptyClient.getInstance();
	
	private L2Player(int objectId, ClassId classId, String accountName, Gender gender, byte face, byte hairColor,
			byte hairStyle)
	{
		super(objectId, PlayerTemplateTable.getInstance().getPlayerTemplate(classId));
		
		_accountName = accountName;
		_appearance = new PlayerAppearance(this, gender, face, hairColor, hairStyle);
	}
	
	@Override
	public L2PlayerTemplate getTemplate()
	{
		return (L2PlayerTemplate)super.getTemplate();
	}
	
	@Override
	public String getAccountName()
	{
		return _accountName;
	}
	
	@Override
	public String getName()
	{
		return _name;
	}
	
	@Override
	public void setName(String name)
	{
		final String oldName = _name;
		
		_name = name;
		
		L2World.updateOnlinePlayer(this, oldName, name);
		
		PlayerNameTable.getInstance().update(this);
	}
	
	public String getTitle()
	{
		return _title;
	}
	
	public void setTitle(String title)
	{
		_title = title;
	}
	
	@Override
	public int getAccessLevel()
	{
		return 0; // FIXME
	}
	
	@Override
	public boolean isGM()
	{
		return false; // FIXME
	}
	
	public PlayerAppearance getAppearance()
	{
		return _appearance;
	}
	
	public IL2Client getClient()
	{
		return _client;
	}
	
	public void setClient(IL2Client client)
	{
		_client.setActiveChar(null);
		_client = client != null ? client : EmptyClient.getInstance();
		_client.setActiveChar(this);
	}
	
	@Override
	public boolean sendPacket(L2ServerPacket sp)
	{
		return getClient().sendPacket(sp);
	}
	
	@Override
	public boolean addToWorld()
	{
		if (!super.addToWorld())
			return false;
		
		// TODO
		
		setOnlineStatus(true);
		return true;
	}
	
	@Override
	public boolean removeFromWorld()
	{
		synchronized (this)
		{
			if (getObjectState() == OBJECT_STATE_ALIVE)
			{
				new Disconnection(this).close(false).store();
			}
		}
		
		if (!super.removeFromWorld())
			return false;
		
		// TODO
		
		setOnlineStatus(false);
		return true;
	}
	
	public boolean isOnline()
	{
		return getObjectState() == OBJECT_STATE_ALIVE;
	}
	
	public void setOnlineStatus(boolean isOnline)
	{
		Connection con = null;
		try
		{
			con = L2Database.getConnection();
			
			final PreparedStatement ps = con.prepareStatement("UPDATE players SET online = ? WHERE objectId = ?");
			ps.setBoolean(1, isOnline);
			ps.setInt(2, getObjectId());
			ps.executeUpdate();
			ps.close();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			L2Database.close(con);
		}
	}
}
