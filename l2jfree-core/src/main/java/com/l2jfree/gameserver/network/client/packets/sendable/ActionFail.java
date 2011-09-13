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
package com.l2jfree.gameserver.network.client.packets.sendable;

/**
 * @author savormix (generated)
 */
public abstract class ActionFail extends StaticPacket
{
	/**
	 * A nicer name for {@link ActionFail}.
	 * 
	 * @author savormix (generated)
	 * @see ActionFail
	 */
	public static final class InteractionFinished extends ActionFail
	{
		/** This packet. */
		public static final InteractionFinished PACKET = new InteractionFinished();
		
		/**
		 * Constructs this packet.
		 * 
		 * @see ActionFail#ActionFail()
		 */
		private InteractionFinished()
		{
		}
	}
	
	/** Constructs this packet. */
	public ActionFail()
	{
	}
	
	@Override
	protected int getOpcode()
	{
		return 0x1f;
	}
}
