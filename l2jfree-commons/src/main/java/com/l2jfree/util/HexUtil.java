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
package com.l2jfree.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import org.apache.commons.lang3.ArrayUtils;

/**
 * This class provides functions to manipulate byte arrays in the programmer-friendly hexadecimal
 * octet format.
 */
public final class HexUtil
{
	private HexUtil()
	{
		// utility class
	}
	
	/**
	 * Randomly generates <TT>size</TT> bytes.
	 * 
	 * @param size bytes to generate
	 * @return randomly generated byte array
	 */
	public static byte[] generateHex(int size)
	{
		byte[] array = new byte[size];
		for (int i = 0; i < size; i++)
			array[i] = (byte)Rnd.nextInt(256);
		return array;
	}
	
	/**
	 * Transforms a legacy HexID into a byte array.
	 * 
	 * @param string HexID
	 * @return decoded byte array
	 */
	public static byte[] stringToHex(String string)
	{
		return new BigInteger(string, 16).toByteArray();
	}
	
	/**
	 * Converts a byte array to a legacy HexID.
	 * 
	 * @param hex a byte array
	 * @return HexID
	 */
	public static String hexToString(byte[] hex)
	{
		if (hex == null)
			return "null";
		
		return new BigInteger(hex).toString(16);
	}
	
	/**
	 * Encodes a byte array to a string of hex octets.
	 * 
	 * @param hex a byte array
	 * @return encoded string
	 */
	public static String bytesToHexString(byte[] hex)
	{
		return bytesToHexString(hex, null);
	}
	
	/**
	 * Encodes a byte array to a string of hex octets.
	 * 
	 * @param hex a byte array
	 * @param delimiter byte delimiter
	 * @return encoded string
	 */
	public static String bytesToHexString(byte[] hex, CharSequence delimiter)
	{
		if (ArrayUtils.isEmpty(hex))
			return "";
		
		StringBuilder bytes = new StringBuilder(fillHex(hex[0] & 0xFF, 2));
		for (int i = 1; i < hex.length; i++)
		{
			if (delimiter != null)
				bytes.append(delimiter);
			bytes.append(fillHex(hex[i] & 0xFF, 2));
		}
		return bytes.toString().toUpperCase();
	}
	
	/**
	 * Decodes a string of hex octets to a byte array.
	 * 
	 * @param hex a byte array
	 * @return decoded byte array
	 */
	public static byte[] HexStringToBytes(String hex)
	{
		if (hex == null)
			return null;
		hex = hex.replace(" ", "");
		if (hex.length() % 2 == 1)
			return null;
		
		byte[] bytes = new byte[hex.length() / 2];
		for (int i = 0; i < bytes.length; i++)
		{
			String byte_ = hex.substring(i * 2, (i + 1) * 2);
			bytes[i] = (byte)(Integer.parseInt(byte_, 16) & 0xFF);
		}
		return bytes;
	}
	
	public static String printData(ByteBuffer buf, int offset, int len)
	{
		byte[] tmp = new byte[len];
		int pos = buf.position();
		buf.position(offset);
		buf.get(tmp);
		buf.position(pos);
		return printData(tmp, len);
	}
	
	/**
	 * This method is equivalent to <TT>printData(raw, raw.length)</TT>.
	 * 
	 * @param raw a byte array
	 * @return converted byte array
	 * @see #printData(byte[], int)
	 */
	public static String printData(byte[] raw)
	{
		return printData(raw, raw.length);
	}
	
	/**
	 * Converts a byte array to string in a special form. <BR>
	 * <BR>
	 * On the left side of the generated string, each byte is printed as a hex octet with a trailing
	 * space.<BR>
	 * On the right side of the generated string, each byte is printed as an ASCII char, unless it's
	 * a non-printing character (the same is done to extended ASCII characters): then a period is
	 * printed instead.
	 * 
	 * @param data a byte array
	 * @param len number of bytes to print
	 * @return converted byte array
	 * @see #printData(byte[], int)
	 */
	public static String printData(byte[] data, int len)
	{
		String eol = System.getProperty("line.separator", "\r\n");
		final StringBuilder result = new StringBuilder(eol);
		
		int counter = 0;
		
		for (int i = 0; i < len; i++)
		{
			if (counter % 16 == 0)
			{
				result.append(fillHex(i, 4));
				result.append(": ");
			}
			
			result.append(fillHex(data[i] & 0xff, 2));
			result.append(' ');
			counter++;
			if (counter == 16)
			{
				result.append("   ");
				
				int charpoint = i - 15;
				for (int a = 0; a < 16; a++)
				{
					int t1 = data[charpoint++];
					if (t1 > 0x1f && t1 < 0x80)
					{
						result.append((char)t1);
					}
					else
					{
						result.append('.');
					}
				}
				
				result.append(eol);
				counter = 0;
			}
		}
		
		int rest = data.length % 16;
		if (rest > 0)
		{
			for (int i = 0; i < 17 - rest; i++)
			{
				result.append("   ");
			}
			
			int charpoint = data.length - rest;
			for (int a = 0; a < rest; a++)
			{
				int t1 = data[charpoint++];
				if (t1 > 0x1f && t1 < 0x80)
				{
					result.append((char)t1);
				}
				else
				{
					result.append('.');
				}
			}
			
			result.append(eol);
		}
		
		return result.toString();
	}
	
	/**
	 * Converts a number to hexadecimal format and adds leading zeros if necessary.
	 * 
	 * @param data a number
	 * @param digits minimum hexadecimal digit count
	 * @return given number in hexadecimal format
	 */
	public static String fillHex(int data, int digits)
	{
		String hex = Integer.toHexString(data);
		
		StringBuilder number = new StringBuilder(Math.max(hex.length(), digits));
		for (int i = hex.length(); i < digits; i++)
			number.append(0);
		number.append(hex);
		
		return number.toString();
	}
}
