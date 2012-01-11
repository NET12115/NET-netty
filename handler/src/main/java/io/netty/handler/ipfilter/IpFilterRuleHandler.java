/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ipfilter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;

/**
 * Implementation of Filter of IP based on ALLOW and DENY rules.<br>
 * <br><br>
 * This implementation could be changed by implementing a new {@link IpFilterRule} than default
 * {@link IpV4SubnetFilterRule} (IPV4 support only), {@link IpSubnetFilterRule} (IPV4 and IPV6 support) or {@link IpFilterRule} (IP and host name string pattern support) .<br>
 * <br>
 * The check is done by going from step to step in the underlying array of IpFilterRule.<br>
 * Each {@link IpFilterRule} answers to the method accept if the {@link InetAddress} is accepted or not,
 * according to its implementation. If an InetAddress arrives at the end of the list, as in Firewall
 * usual rules, the InetAddress is therefore accepted by default.<br>
 * <ul>
 * <li>If it was constructed with True as first argument,
 * the IpFilterRule is an ALLOW rule (every InetAddress that fits in the rule will be accepted).</li>
 * <li>If it was constructed with False as first argument,
 * the IpFilterRule is a DENY rule (every InetAddress that fits in the rule will be refused).</li>
 * </ul><br>
 * <br>
 * An empty list means allow all (no limitation).<br><br>
 * <b>For efficiency reason, you should not add/remove too frequently IpFilterRules to/from this handler.
 * You should prefer to replace an entry (<tt>set</tt> method) with an ALLOW/DENY ALL IpFilterRule
 * if possible.</b><br><br><br>
 * <b>This handler should be created only once and reused on every pipeline since it handles
 * a global status of what is allowed or blocked.</b><br><br>
 * <p/>
 * Note that {@link IpSubnetFilterRule} which supports IPV4 and IPV6 should be used with as much as
 * possible no mixed IP protocol. Both IPV4 and IPV6 are supported but a mix (IpFilter in IPV6 notation
 * and the address from the channel in IPV4, or the reverse) can lead to wrong result.
 */
@Sharable
public class IpFilterRuleHandler extends IpFilteringHandlerImpl {
    /** List of {@link IpFilterRule} */
    private final CopyOnWriteArrayList<IpFilterRule> ipFilterRuleList = new CopyOnWriteArrayList<IpFilterRule>();

    /** Constructor from a new list of IpFilterRule */
    public IpFilterRuleHandler(List<IpFilterRule> newList) {
        if (newList != null) {
            ipFilterRuleList.addAll(newList);
        }
    }

    /**
     * Empty constructor (no IpFilterRule in the List at construction). In such a situation,
     * empty list implies allow all.
     */
    public IpFilterRuleHandler() {
    }

    // Below are methods directly inspired from CopyOnWriteArrayList methods

    /** Add an ipFilterRule in the list at the end */
    public void add(IpFilterRule ipFilterRule) {
        if (ipFilterRule == null) {
            throw new NullPointerException("IpFilterRule can not be null");
        }
        ipFilterRuleList.add(ipFilterRule);
    }

    /** Add an ipFilterRule in the list at the specified position (shifting to the right other elements) */
    public void add(int index, IpFilterRule ipFilterRule) {
        if (ipFilterRule == null) {
            throw new NullPointerException("IpFilterRule can not be null");
        }
        ipFilterRuleList.add(index, ipFilterRule);
    }

    /**
     * Appends all of the elements in the specified collection to the end of this list,
     * in the order that they are returned by the specified collection's iterator.
     */
    public void addAll(Collection<IpFilterRule> c) {
        if (c == null) {
            throw new NullPointerException("Collection can not be null");
        }
        ipFilterRuleList.addAll(c);
    }

    /** Inserts all of the elements in the specified collection into this list, starting at the specified position. */
    public void addAll(int index, Collection<IpFilterRule> c) {
        if (c == null) {
            throw new NullPointerException("Collection can not be null");
        }
        ipFilterRuleList.addAll(index, c);
    }

    /**
     * Append the element if not present.
     *
     * @return the number of elements added
     */
    public int addAllAbsent(Collection<IpFilterRule> c) {
        if (c == null) {
            throw new NullPointerException("Collection can not be null");
        }
        return ipFilterRuleList.addAllAbsent(c);
    }

    /**
     * Append the element if not present.
     *
     * @return true if the element was added
     */
    public boolean addIfAbsent(IpFilterRule ipFilterRule) {
        if (ipFilterRule == null) {
            throw new NullPointerException("IpFilterRule can not be null");
        }
        return ipFilterRuleList.addIfAbsent(ipFilterRule);
    }

    /** Clear the list */
    public void clear() {
        ipFilterRuleList.clear();
    }

    /**
     * Returns true if this list contains the specified element
     *
     * @return true if this list contains the specified element
     */
    public boolean contains(IpFilterRule ipFilterRule) {
        if (ipFilterRule == null) {
            throw new NullPointerException("IpFilterRule can not be null");
        }
        return ipFilterRuleList.contains(ipFilterRule);
    }

    /**
     * Returns true if this list contains all of the elements of the specified collection
     *
     * @return true if this list contains all of the elements of the specified collection
     */
    public boolean containsAll(Collection<IpFilterRule> c) {
        if (c == null) {
            throw new NullPointerException("Collection can not be null");
        }
        return ipFilterRuleList.containsAll(c);
    }

    /**
     * Returns the element at the specified position in this list
     *
     * @return the element at the specified position in this list
     */
    public IpFilterRule get(int index) {
        return ipFilterRuleList.get(index);
    }

    /**
     * Returns true if this list contains no elements
     *
     * @return true if this list contains no elements
     */
    public boolean isEmpty() {
        return ipFilterRuleList.isEmpty();
    }

    /** Remove the ipFilterRule from the list */
    public void remove(IpFilterRule ipFilterRule) {
        if (ipFilterRule == null) {
            throw new NullPointerException("IpFilterRule can not be null");
        }
        ipFilterRuleList.remove(ipFilterRule);
    }

    /**
     * Removes the element at the specified position in this list
     *
     * @return the element previously at the specified position
     */
    public IpFilterRule remove(int index) {
        return ipFilterRuleList.remove(index);
    }

    /** Removes from this list all of its elements that are contained in the specified collection */
    public void removeAll(Collection<IpFilterRule> c) {
        if (c == null) {
            throw new NullPointerException("Collection can not be null");
        }
        ipFilterRuleList.removeAll(c);
    }

    /** Retains only the elements in this list that are contained in the specified collection */
    public void retainAll(Collection<IpFilterRule> c) {
        if (c == null) {
            throw new NullPointerException("Collection can not be null");
        }
        ipFilterRuleList.retainAll(c);
    }

    /**
     * Replaces the element at the specified position in this list with the specified element
     *
     * @return the element previously at the specified position
     */
    public IpFilterRule set(int index, IpFilterRule ipFilterRule) {
        if (ipFilterRule == null) {
            throw new NullPointerException("IpFilterRule can not be null");
        }
        return ipFilterRuleList.set(index, ipFilterRule);
    }

    /**
     * Returns the number of elements in this list.
     *
     * @return the number of elements in this list.
     */
    public int size() {
        return ipFilterRuleList.size();
    }

    /* (non-Javadoc)
    * @see io.netty.handler.ipfilter.IpFilteringHandler#accept(io.netty.channel.ChannelHandlerContext, io.netty.channel.ChannelEvent, java.net.InetSocketAddress)
    */
    @Override
    protected boolean accept(ChannelHandlerContext ctx, ChannelEvent e, InetSocketAddress inetSocketAddress)
            throws Exception {
        if (ipFilterRuleList.isEmpty()) {
            // No limitation neither in deny or allow, so accept
            return true;
        }
        InetAddress inetAddress = inetSocketAddress.getAddress();
        Iterator<IpFilterRule> iterator = ipFilterRuleList.iterator();
        IpFilterRule ipFilterRule = null;
        while (iterator.hasNext()) {
            ipFilterRule = iterator.next();
            if (ipFilterRule.contains(inetAddress)) {
                // Match founds, is it a ALLOW or DENY rule
                return ipFilterRule.isAllowRule();
            }
        }
        // No limitation founds and no allow either, but as it is like Firewall rules, it is therefore accepted
        return true;
    }

}
