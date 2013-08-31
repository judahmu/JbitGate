package org.judahmu.jbitgate;

import static org.junit.Assert.assertEquals;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerAddress;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.crypto.ChildNumber;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.discovery.DnsDiscovery;
import com.google.bitcoin.params.TestNet3Params;
import com.google.bitcoin.store.BlockStoreException;
import com.google.common.collect.ImmutableList;

/**Create the Deterministic Hierarchies (private key and public key).
 * If running as a daemon, PublicTree.get().next() will return a 
 * PublicNode that has a listener attached for incoming coins. 
 * @author Jeff Masty (judah_mu@yahoo.com) Aug 27, 2013 */
public class Main implements ServletContextListener {
    /** let's use TestNet3 */
    public static final NetworkParameters NET = TestNet3Params.get();
    public static final String TestNetCoinsDump = "mrPNzgAqjpHoGetHCqKGPnQXX9mPDkeYTS";
    
    static Logger log = LoggerFactory.getLogger(Main.class);
    

    /**Starts JbitGate in J2EE mode */
    @Override
    public void contextInitialized(ServletContextEvent arg0) {
        PrivateTree priv = new PrivateTree();
        DeterministicKey pubRoot = priv.getPubRoot();
        PublicTree pub = null;
        try {
            // Serialize/Deserialize the pub root key and create Public Hierarchy. 
            // TODO BitcoinJ does not implement BIP 32 Base58 standard format yet
            pub = new PublicTree(pubRoot.getPubKeyBytes(), pubRoot.getChainCode());
            assertEquals(pubRoot.toECKey(), pub.getRootKey().toECKey());
            pub.downloadBlockChain();
        } catch (BlockStoreException e) {
            log.error(e.getMessage(), e);
        }
    }
    
    public void runTests() {
        PrivateTree priv = PrivateTree.get();
        PublicTree pub = PublicTree.get();
        DeterministicKey fromPub;
        ImmutableList<ChildNumber> home;
        for (int i = 0; i < 40; i++) {
            home = pub.getRootKey().getChildNumberPath();
            fromPub = pub.deriveChild(home, false, false, new ChildNumber(i, false));
            DeterministicKey sync = priv.getSyncKeyForPub(fromPub);
            Assert.assertTrue(Arrays.equals(sync.getPubKeyBytes(), fromPub.getPubKeyBytes()));
        }
    }
    
    /** shutdown in J2EE mode */
    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        PublicTree.shutdown();
        SpendsWallet.shutdown();
    }

    /**pre-load a couple of *good* testnet3 peers
     * @param peerGroup */
    public static void addPeers(PeerGroup peerGroup) {
        
        if (Main.NET.equals(TestNet3Params.get()) == false) {
            try {
                peerGroup.addAddress(InetAddress.getLocalHost());
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
        else {
            try {
                // really nice to run a local bitcoind testnet3, if possible
                peerGroup.addAddress(new PeerAddress(InetAddress.getLocalHost(), 
                        Main.NET.getPort()));
            } catch (UnknownHostException e) {
                log.warn("lol wtf localhost");
            }
            
    
            // Some known peers for testnet 
            String[] hosts = {"testnet-seed.bitcoin.petertodd.org", 
                    "testnet-seed.bluematt.me", "bitcashier.org"};
            for (String host : hosts) {
                try {
                    peerGroup.addAddress(new PeerAddress(
                            InetAddress.getByName(host), Main.NET.getPort()));
                } catch (UnknownHostException e) {
                    log.warn(host + " testnet seed not up?");
                }
            }
        }    
        
        peerGroup.setUserAgent("bitCashier", "0.001");
        peerGroup.addPeerDiscovery(new DnsDiscovery(Main.NET));
    }


}
