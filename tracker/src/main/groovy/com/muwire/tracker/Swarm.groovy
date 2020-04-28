package com.muwire.tracker

import java.util.function.Function

import com.muwire.core.InfoHash
import com.muwire.core.Persona

import groovy.util.logging.Log

/**
 * A swarm for a given file
 */
@Log
class Swarm {
    private final InfoHash infoHash
    
    /** 
     * Invariant: these four collections are mutually exclusive.
     * A given host can be only in one of them at the same time.
     */
    private final Map<Persona,Host> seeds = new HashMap<>()
    private final Map<Persona,Host> leeches = new HashMap<>()
    private final Map<Persona,Host> unknown = new HashMap<>()
    private final Set<Persona> negative = new HashSet<>()
    
    /**
     * hosts which are currently being pinged.  Hosts can be in here
     * and in the collections above, except for negative.
     */
    private final Map<Persona, Host> inFlight = new HashMap<>()
    
    Swarm(InfoHash infoHash) {
        this.infoHash = infoHash
    }
    
    /**
     * @param cutoff expire hosts older than this
     */
    synchronized void expire(long cutoff) {
        doExpire(cutoff, seeds)
        doExpire(cutoff, leeches)
        doExpire(cutoff, unknown)
    }
    
    private static void doExpire(long cutoff, Map<Persona,Host> map) {
        for (Iterator<Persona> iter = map.keySet().iterator(); iter.hasNext();) {
            Persona p = iter.next()
            Host h = map.get(p)
            if (h.isExpired(cutoff))
                iter.remove()
        }
    }
    
    synchronized boolean needsQuery() {
        seeds.isEmpty() &&
            leeches.isEmpty() &&
            inFlight.isEmpty() &&
            unknown.isEmpty()
    }
    
    synchronized boolean isHealthy() {
        !seeds.isEmpty()
        // TODO add xHave accumulation of leeches 
    }
    
    synchronized void add(Persona p) {
        if (!(seeds.containsKey(p) || leeches.containsKey(p) || 
            negative.contains(p) || inFlight.containsKey(p)))
            unknown.computeIfAbsent(p, {new Host(it)} as Function)
    }
    
    synchronized void handleResponse(Host responder, int code) {
        Host h = inFlight.remove(responder.persona)
        if (responder != h)
            log.warning("received a response mismatch from host $responder vs $h")
            
        responder.lastResponded = System.currentTimeMillis()    
        switch(code) {
            case 200: addSeed(responder); break
            case 206 : addLeech(responder); break;
            default :
            addNegative(responder)
        }
    }
    
    synchronized void fail(Host failed) {
        Host h = inFlight.remove(failed.persona)
        if (h != failed)
            log.warning("failed a host that wasn't in flight $failed vs $h")
    }
    
    private void addSeed(Host h) {
        leeches.remove(h.persona)
        unknown.remove(h.persona)
        seeds.put(h.persona, h)
    }
    
    private void addLeech(Host h) {
        unknown.remove(h.persona)
        seeds.remove(h.persona)
        leeches.put(h.persona, h)
    }
    
    private void addNegative(Host h) {
        unknown.remove(h.persona)
        seeds.remove(h.persona)
        leeches.remove(h.persona)
        negative.add(h.persona)
    }
    
    synchronized List<Host> getBatchToPing(int max) {
        List<Host> rv = new ArrayList<>()
        rv.addAll(unknown.values())
        rv.addAll(seeds.values())
        rv.addAll(leeches.values())
        rv.removeAll(inFlight.values())
        
        Collections.sort(rv, {l, r ->
            Long.compare(l.lastPinged, r.lastPinged)
        } as Comparator<Host>)
        
        if (rv.size() > max)
            rv = rv[0..(max-1)]
        
        final long now = System.currentTimeMillis()
        rv.each {
            it.lastPinged = now
            inFlight.put(it.persona, it)
        }
        
        rv
    }
}
