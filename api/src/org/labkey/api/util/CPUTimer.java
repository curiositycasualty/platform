/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.util;

import org.apache.log4j.Logger;

import java.util.*;


public class CPUTimer
{
    private static final Logger _log = Logger.getLogger(CPUTimer.class);


	//
	// cumulative timers
	//

	private static final WeakHashMap<CPUTimer, Object> timers = new WeakHashMap<>();

	private final String _name;
	private long _cumulative = 0;
	private long _start = 0;
	private int _calls = 0;


	public CPUTimer(String name)
    {
		synchronized(timers)
        {
			if (null == name)
				name = "timer " + timers.size();
			_name = name;
			timers.put(this, null);
        }
    }


	public boolean start()
    {
		_start = System.nanoTime();
		return true;
    }

    public long getStart()
    {
        return _start;
    }

    public boolean started()
    {
        return _start > 0;
    }

	public boolean stop()
    {
		long stop = System.nanoTime();
		if (stop > _start)
        {
			_cumulative += (stop - _start);
			_calls++;
        }
		_start = 0;
		return true;
    }


	public boolean clear()
    {
		_cumulative = 0;
        return true;
    }

    public String getName()
    {
        return _name;
    }

	public long getTotal()
    {
        return _cumulative;
    }


    public long getTotalMilliseconds()
    {
        return (long)(_cumulative * msFactor);
    }


    public String getDuration()
    {
        return DateUtil.formatDuration(getTotalMilliseconds());
    }
    

    public static String dumpAllTimers()
    {
		synchronized(timers)
        {
			Set<CPUTimer> set = timers.keySet();
			CPUTimer[] a = set.toArray(new CPUTimer[set.size()]);

			Arrays.sort(a, Comparator.comparing(o -> o._name));

            StringBuilder sb = new StringBuilder();
            sb.append("TIMER SUMMARY: ").append(new Date().toString()).append("\n");
			sb.append("  cumulative\t     average\t       calls\ttimer\n");
            for (CPUTimer cpuTimer : a)
            {
                appendString(cpuTimer, sb);
            }
            logDebug(sb);
            return sb.toString();
        }
    }


    private static final double msFactor = 1.0e-6;

    public static void appendString(CPUTimer cpuTimer, StringBuilder sb)
    {
        double ms = cpuTimer._cumulative * msFactor;
        sb.append(ms);
		sb.append("\t");
		format((cpuTimer._calls == 0 ? 0 : ms / cpuTimer._calls), 12, sb);
		sb.append("\t");
		format(cpuTimer._calls, 12, sb);
		sb.append("\t");
		sb.append(cpuTimer._name);
    }


	@Override
	public String toString()
    {
		StringBuilder sb = new StringBuilder();
		appendString(this, sb);
		return sb.toString();
    }


	private static void format(Object l, int width, StringBuilder sb)
    {
		String s = String.valueOf(l);
		for (int p=width-s.length() ; p>0 ; p--)
			sb.append(' ');
		sb.append(s);
    }


    private static void logDebug(CharSequence s)
    {
        _log.debug(s);
    }


    public static void main(String[] args)
    {
        CPUTimer timerA = new CPUTimer("timerA");
        double x = 0;
        timerA.start();
        for (int i=0 ;i<1000; i++)
            x = x * Math.sin(x*i);
        timerA.stop();
        CPUTimer.dumpAllTimers();

        CPUTimer calibrate = new CPUTimer("test");
        long a = System.currentTimeMillis();
        calibrate.start();
        try {Thread.sleep(10000);} catch(Exception ex){}
        long b = System.currentTimeMillis();
        calibrate.stop();
        double f = (double)calibrate.getTotal() / (double)(b-a);
        System.err.println(f);
        assert msFactor*.999 < f && f < msFactor * 1.001;
    }
}
