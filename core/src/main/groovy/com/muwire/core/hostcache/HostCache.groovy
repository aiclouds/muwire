package com.muwire.core.hostcache

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

import com.muwire.core.MuWireSettings
import com.muwire.core.Service
import com.muwire.core.connection.ConnectionAttemptStatus
import com.muwire.core.connection.ConnectionEvent
import com.muwire.core.trust.TrustLevel
import com.muwire.core.trust.TrustService

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import net.i2p.data.Destination

import java.util.Collections

class HostCache extends Service {

    final TrustService trustService
    final File storage
    final int interval
    final Timer timer
    final MuWireSettings settings
    final Destination myself
    final Map<Destination, Host> hosts = Collections.synchronizedMap(new HashMap<>())
    
    HostCache(){}

    public HostCache(TrustService trustService, File storage, int interval,
        MuWireSettings settings, Destination myself) {
        this.trustService = trustService
        this.storage = storage
        this.interval = interval
        this.settings = settings
        this.myself = myself
        this.timer = new Timer("host-persister",true)
    }

    void start() {
        timer.schedule({load()} as TimerTask, 1)
    }

    void stop() {
        timer.cancel()
    }

    void onHostDiscoveredEvent(HostDiscoveredEvent e) {
        if (myself == e.destination)
            return
        synchronized(hosts) {
            if (hosts.containsKey(e.destination)) {
                if (!e.fromHostcache)
                    return
                hosts.get(e.destination).clearFailures()
                return
            }
        }
        Host host = new Host(e.destination, settings.hostClearInterval, settings.hostHopelessInterval, 
            settings.hostRejectInterval, settings.hostHopelessPurgeInterval)
        if (allowHost(host)) {
            hosts.put(e.destination, host)
        }
    }

    void onConnectionEvent(ConnectionEvent e) {
        if (e.leaf)
            return
        Destination dest = e.endpoint.destination
        Host host
        synchronized(hosts) {
            host = hosts.get(dest)
            if (host == null) {
                host = new Host(dest, settings.hostClearInterval, settings.hostHopelessInterval,
                        settings.hostRejectInterval, settings.hostHopelessPurgeInterval)
                hosts.put(dest, host)
            }
        }

        switch(e.status) {
            case ConnectionAttemptStatus.SUCCESSFUL:
                host.onConnect()
                break
            case ConnectionAttemptStatus.REJECTED:
                host.onReject()
                break
            case ConnectionAttemptStatus.FAILED:
                host.onFailure()
                break
        }
    }

    List<Destination> getHosts(int n, Predicate<Destination> filter) {
        List<Destination> rv
        
        synchronized(hosts) {
            rv = new ArrayList<>(hosts.keySet())
            rv.retainAll {allowHost(hosts[it]) && filter.test(it)}
            final long now = System.currentTimeMillis()
            rv.removeAll {
                def h = hosts[it];
                (h.isFailed() && !h.canTryAgain(now)) || h.isRecentlyRejected(now) || h.isHopeless(now)
            }
        }
        if (rv.size() <= n)
            return rv
        Collections.shuffle(rv)
        rv[0..n-1]
    }

    List<Destination> getGoodHosts(int n) {
        List<Destination> rv
        synchronized(hosts) {
            rv = new ArrayList<>(hosts.keySet())
            rv.retainAll {
                Host host = hosts[it]
                allowHost(host) && host.hasSucceeded()
            }
        }
        if (rv.size() <= n)
            return rv
        Collections.shuffle(rv)
        rv[0..n-1]
    }
    
    int countFailingHosts() {
        List<Destination> rv
        synchronized(hosts) {
            rv = new ArrayList<>(hosts.keySet())
            rv.retainAll {
                hosts[it].isFailed()
            }
        }
        rv.size()
    }
    
    int countHopelessHosts() {
        List<Destination> rv
        synchronized(hosts) {
            rv = new ArrayList<>(hosts.keySet())
            final long now = System.currentTimeMillis()
            rv.retainAll {
                hosts[it].isHopeless(now)
            }
        }
        rv.size()
    }

    void load() {
        if (storage.exists()) {
            JsonSlurper slurper = new JsonSlurper()
            storage.eachLine {
                def entry = slurper.parseText(it)
                Destination dest = new Destination(entry.destination)
                Host host = new Host(dest, settings.hostClearInterval, settings.hostHopelessInterval, 
                    settings.hostRejectInterval, settings.hostHopelessPurgeInterval)
                host.failures = Integer.valueOf(String.valueOf(entry.failures))
                host.successes = Integer.valueOf(String.valueOf(entry.successes))
                if (entry.lastAttempt != null)
                    host.lastAttempt = entry.lastAttempt
                if (entry.lastSuccessfulAttempt != null)
                    host.lastSuccessfulAttempt = entry.lastSuccessfulAttempt
                if (entry.lastRejection != null)
                    host.lastRejection = entry.lastRejection
                if (allowHost(host)) 
                    hosts.put(dest, host)
            }
        }
        timer.schedule({save()} as TimerTask, interval, interval)
        loaded = true
    }

    private boolean allowHost(Host host) {
        if (host.destination == myself)
            return false
        TrustLevel trust = trustService.getLevel(host.destination)
        switch(trust) {
            case TrustLevel.DISTRUSTED :
                return false
            case TrustLevel.TRUSTED :
                return true
            case TrustLevel.NEUTRAL :
                return settings.allowUntrusted()
        }
        false
    }

    private void save() {
        final long now = System.currentTimeMillis()
        Map<Destination, Host> copy
        synchronized(hosts) {
            hosts.keySet().removeAll { hosts[it].shouldBeForgotten(now) }
            copy = new HashMap<>(hosts)
        }
        storage.delete()
        storage.withPrintWriter { writer ->
            copy.each { dest, host ->
                if (allowHost(host) && !host.isHopeless(now)) {
                    def map = [:]
                    map.destination = dest.toBase64()
                    map.failures = host.failures
                    map.successes = host.successes
                    map.lastAttempt = host.lastAttempt
                    map.lastSuccessfulAttempt = host.lastSuccessfulAttempt
                    map.lastRejection = host.lastRejection
                    def json = JsonOutput.toJson(map)
                    writer.println json
                }
            }
        }
    }
}
