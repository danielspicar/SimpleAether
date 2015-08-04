package com.github.danielspicar.simpleaether;

import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.LightweightHttpWagon;
import org.apache.maven.wagon.providers.http.LightweightHttpsWagon;
import org.eclipse.aether.transport.wagon.WagonProvider;

/**
 * Simple HTTP and HTTPS Wagon Provider.
 *
 * @author daniel
 */
class SimpleWagonProvider implements WagonProvider {

    @Override
    public Wagon lookup(String roleHint) throws Exception {
        if (roleHint.equalsIgnoreCase("http")) {
            return new LightweightHttpWagon();
        } else if (roleHint.equalsIgnoreCase("https")) {
            return new LightweightHttpsWagon();
        }

        //TODO: support ssh/scp

        return null;
    }

    @Override
    public void release(Wagon wagon) {
        //nop
    }
}
