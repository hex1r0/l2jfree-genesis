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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.l2jfree.util.concurrent.L2ThreadPool;

/**
 * @author NB4L1
 */
public final class FloodManager
{
	public static enum ErrorMode
	{
		EMPTY_PACKET,
		INVALID_OPCODE,
		INVALID_STATE,
		BUFFER_UNDER_FLOW,
		BUFFER_OVER_FLOW,
		FAILED_READING,
		FAILED_RUNNING;
	}
	
	public static enum Result
	{
		ACCEPTED,
		WARNED,
		REJECTED;
		
		public static Result max(Result r1, Result r2)
		{
			if (r1.ordinal() > r2.ordinal())
				return r1;
			
			return r2;
		}
	}
	
	public static final FloodManager EMPTY_FLOOD_MANAGER = new FloodManager(1000);
	
	private static final long ZERO = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
	
	private final Map<String, LogEntry> _entries = new HashMap<String, LogEntry>();
	
	private final ReentrantLock _lock = new ReentrantLock();
	
	private final int _tickLength;
	private final int _tickAmount;
	
	private final FloodFilter[] _filters;
	
	public FloodManager(int msecPerTick, FloodFilter... filters)
	{
		_tickLength = msecPerTick;
		_filters = filters;
		
		int max = 1;
		
		for (FloodFilter filter : _filters)
			max = Math.max(filter.getTickLimit() + 1, max);
		
		_tickAmount = max;
		
		L2ThreadPool.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run()
			{
				flush();
			}
		}, 60000, 60000);
	}
	
	public static final class FloodFilter
	{
		private final int _warnLimit;
		private final int _rejectLimit;
		private final int _tickLimit;
		
		public FloodFilter(int warnLimit, int rejectLimit, int tickLimit)
		{
			_warnLimit = warnLimit;
			_rejectLimit = rejectLimit;
			_tickLimit = tickLimit;
		}
		
		public int getWarnLimit()
		{
			return _warnLimit;
		}
		
		public int getRejectLimit()
		{
			return _rejectLimit;
		}
		
		public int getTickLimit()
		{
			return _tickLimit;
		}
	}
	
	private void flush()
	{
		_lock.lock();
		try
		{
			for (Iterator<LogEntry> it = _entries.values().iterator(); it.hasNext();)
			{
				if (it.next().isActive())
					continue;
				
				it.remove();
			}
		}
		finally
		{
			_lock.unlock();
		}
	}
	
	public Result isFlooding(String key, boolean increment)
	{
		if (key == null || key.isEmpty())
			return Result.REJECTED;
		
		_lock.lock();
		try
		{
			LogEntry entry = _entries.get(key);
			
			if (entry == null)
			{
				entry = new LogEntry();
				
				_entries.put(key, entry);
			}
			
			return entry.isFlooding(increment);
		}
		finally
		{
			_lock.unlock();
		}
	}
	
	private final class LogEntry
	{
		private final short[] _ticks = new short[_tickAmount];
		
		private int _lastTick = getCurrentTick();
		
		public int getCurrentTick()
		{
			return (int)((System.currentTimeMillis() - ZERO) / _tickLength);
		}
		
		public boolean isActive()
		{
			return getCurrentTick() - _lastTick < _tickAmount * 10;
		}
		
		public Result isFlooding(boolean increment)
		{
			final int currentTick = getCurrentTick();
			
			if (currentTick - _lastTick >= _ticks.length)
			{
				_lastTick = currentTick;
				
				Arrays.fill(_ticks, (short)0);
			}
			else if (_lastTick > currentTick)
			{
				MMOController._log.warn("The current tick (" + currentTick + ") is smaller than the last (" + _lastTick
						+ ")!", new IllegalStateException());
				
				_lastTick = currentTick;
			}
			else
			{
				while (currentTick != _lastTick)
				{
					_lastTick++;
					
					_ticks[_lastTick % _ticks.length] = 0;
				}
			}
			
			if (increment)
				_ticks[_lastTick % _ticks.length]++;
			
			for (FloodFilter filter : _filters)
			{
				int previousSum = 0;
				int currentSum = 0;
				
				for (int i = 0; i <= filter.getTickLimit(); i++)
				{
					int value = _ticks[(_lastTick - i) % _ticks.length];
					
					if (i != 0)
						previousSum += value;
					
					if (i != filter.getTickLimit())
						currentSum += value;
				}
				
				if (previousSum > filter.getRejectLimit() || currentSum > filter.getRejectLimit())
					return Result.REJECTED;
				
				if (previousSum > filter.getWarnLimit() || currentSum > filter.getWarnLimit())
					return Result.WARNED;
			}
			
			return Result.ACCEPTED;
		}
	}
}
