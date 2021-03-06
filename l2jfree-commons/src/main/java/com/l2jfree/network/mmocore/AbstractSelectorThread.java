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
package com.l2jfree.network.mmocore;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.l2jfree.util.concurrent.RunnableStatsManager;

/**
 * Baseclass of {@link MMOController} associated threads executing periodic NIO selections.
 * 
 * @see Selector#selectNow()
 * @author NB4L1
 * @param <T>
 * @param <RP>
 * @param <SP>
 */
abstract class AbstractSelectorThread<T extends MMOConnection<T, RP, SP>, RP extends ReceivablePacket<T, RP, SP>, SP extends SendablePacket<T, RP, SP>>
		extends WorkerThread<T, RP, SP>
{
	private final Selector _selector;
	
	private final long _sleepTime;
	
	protected AbstractSelectorThread(MMOController<T, RP, SP> mmoController, MMOConfig config) throws IOException
	{
		super(mmoController);
		
		_sleepTime = config.getSelectorSleepTime();
		
		_selector = Selector.open();
	}
	
	protected final Selector getSelector()
	{
		return _selector;
	}
	
	@Override
	public final void run()
	{
		// main loop
		for (;;)
		{
			final long begin = System.nanoTime();
			
			try
			{
				cleanup();
				
				// check for shutdown
				if (isShuttingDown())
				{
					close();
					return;
				}
				
				try
				{
					if (getSelector().selectNow() > 0)
					{
						Set<SelectionKey> keys = getSelector().selectedKeys();
						
						for (SelectionKey key : keys)
						{
							handle(key);
						}
						
						keys.clear();
					}
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				catch (RuntimeException e)
				{
					e.printStackTrace();
				}
				
				cleanup();
			}
			finally
			{
				RunnableStatsManager.handleStats(getClass(), "selectNow()", System.nanoTime() - begin);
			}
			
			try
			{
				Thread.sleep(getSleepTime());
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	protected abstract void handle(SelectionKey key) throws IOException;
	
	protected void cleanup()
	{
		// to be overridden
	}
	
	private void close()
	{
		for (SelectionKey key : getSelector().keys())
		{
			IOUtils.closeQuietly(key.channel());
		}
		
		try
		{
			getSelector().close();
		}
		catch (IOException e)
		{
			// ignore
		}
	}
	
	private long getSleepTime()
	{
		return _sleepTime;
	}
}
