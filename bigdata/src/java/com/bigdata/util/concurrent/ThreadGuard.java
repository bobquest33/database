/**

Copyright (C) SYSTAP, LLC 2006-2007.  All rights reserved.

Contact:
     SYSTAP, LLC
     4501 Tower Road
     Greensboro, NC 27410
     licenses@bigdata.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package com.bigdata.util.concurrent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;

/**
 * Pattern used to guard critical regions that await {@link Condition}s when a
 * concurrent event may cause the {@link Condition} to become unsatisfiable.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 */
public class ThreadGuard {

    public static abstract class Guard {

        abstract public void run() throws InterruptedException;
        
    }
    
    /**
     * Execute a critical region which needs to be interrupted if we have to
     * terminate the quorum.
     * 
     * @param r
     *            The lambda.
     *            
     * @return The result (if any).
     */
    public void guard(final Guard r) throws InterruptedException {
        incThread();
        try {
            r.run();
        } catch(InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            decThread();
        }
    }

    private final ConcurrentHashMap<Thread, AtomicInteger> threads = new ConcurrentHashMap<Thread, AtomicInteger>();

    /**
     * Increment thread in critical region awaiting a {@link Condition}.
     */
    private void incThread() {
        synchronized (threads) {
            final Thread t = Thread.currentThread();
            final AtomicInteger tmp = threads.get(t);
            if (tmp == null) {
                threads.put(t, new AtomicInteger(1));
            } else {
                tmp.incrementAndGet();
            }
        }
    }

    /**
     * Decrement thread in critical region awaiting a {@link Condition}.
     */
    public void decThread() {
        synchronized (threads) {
            final Thread t = Thread.currentThread();
            final AtomicInteger tmp = threads.get(t);
            if (tmp == null) {
                // code must be wrong with broken try/finally.
                throw new AssertionError();
            }
            if (tmp.decrementAndGet() == 0) {
                threads.remove(t);
            }
        }
    }

    /**
     * Interrupt any threads in critical regions awaiting a
     * {@link Condition}.
     */
    public void interruptAll() {
        synchronized (threads) {
            final Iterator<Map.Entry<Thread, AtomicInteger>> itr = threads
                    .entrySet().iterator();
            while(itr.hasNext()) {
                final Map.Entry<Thread,AtomicInteger> e = itr.next();
                final Thread t = e.getKey();
                t.interrupt();
            }
            // Note: Will be cleared when we leave the finally{}.
//            // clear everything!
//            threads.clear();
        }
    }

}
