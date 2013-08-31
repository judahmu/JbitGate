package org.judahmu.jbitgate;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TooManyListenersException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.crypto.ChildNumber;
import com.google.bitcoin.crypto.DeterministicHierarchy;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.crypto.HDKeyDerivation;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.SPVBlockStore;

/**Singleton of encapsulating the Public Hierarchy
 * @author Jeff Masty (judah_mu@yahoo.com) Aug 27, 2013 */
public class PublicTree extends DeterministicHierarchy {
    private static Logger log = LoggerFactory.getLogger(PublicTree.class);
    private static String TRY_AGAIN = "<br/>Please try again in 15 or 20 minutes.";
    /** */
    private static final long serialVersionUID = 1L;
    private static PublicTree pub;
    
    /** child number of wallet to create */
    private int childNum = 0;
    final Set<PublicNode> openWallets = Collections.synchronizedSet(new HashSet<PublicNode>());
    private final List<ChildNumber> rootPath;

    final WatchWallet wallet;
    
    final BlockStore store; // SPV or MemoryBlockStore
    final BlockChain chain; // created when download starts
    final PeerGroup peerGroup; // created when download starts
    
    /**@return this Singleton */
    public static PublicTree get() { return pub; }
    
    PublicTree(byte[] pubKeyBytes, byte[] chainCode) throws BlockStoreException {
        super(HDKeyDerivation.createMasterPubKeyFromBytes(pubKeyBytes, chainCode));
        pub = this;
        rootPath = getRootKey().getChildNumberPath();
        
        wallet = new WatchWallet(this);
        
        File spvFile = new File(PublicTree.class.getSimpleName() + ".spv");
        store = new SPVBlockStore(Main.NET, spvFile);
        // store = new MemoryBlockStore(Main.NET);
        chain = new BlockChain(Main.NET, store);
        // assume brand new addresses, sync wallet to top of chain
        wallet.setLastBlockSeenHeight(chain.getBestChainHeight());
        chain.addWallet(wallet);
        peerGroup = new PeerGroup(Main.NET, chain);
        peerGroup.setFastCatchupTimeSecs(System.currentTimeMillis());
        Main.addPeers(peerGroup); 
    }

    /**@return a new Public Node (contains the Public-only key/address) 
     * @throws Throttled if <i>too many</i> keys already being watched */
    public synchronized PublicNode next(String ip) throws TooManyListenersException {

        // Check if *too many* keys already being watched
        if (openWallets.size() > 40) { // max open wallets
            throw new TooManyListenersException(
                    "I have become too popular. Throttled for sanity." + TRY_AGAIN);
        }
        int count = 0;
        for (PublicNode node : openWallets) {
            if (ip.equals(node.getIp())) {
                count++;
                if (count > 6) { // max open wallets per IP address
                    throw new TooManyListenersException(
                            "Whoa, easy there tiger." + TRY_AGAIN);
                }
            }
        }
        
        // Create key at childNum counter and increment childNum
        DeterministicKey key = deriveChild(
                rootPath, true, false, new ChildNumber(childNum++, false));
        PublicNode node = new PublicNode(key, ip);
        
        // Connect the new address to the peer network
        wallet.addKey(key.toECKey());
        openWallets.add(node);
        log.info("watching " + node.getAddress());
        return node;
    }

    /**Stop peer-to-peer */
    public static void shutdown() {
        if (pub == null) { // aborted startup
            return;
        }
        PeerGroup peerGroup = pub.peerGroup;
        if (peerGroup != null && peerGroup.isRunning()) {
            peerGroup.stopAndWait();
            log.info("peering stopped");
        }
        BlockStore store = pub.store;
        if (store != null) {
            try {
                store.close();
            } catch (BlockStoreException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    /**start peer-to-peer in new thread (release current thread) */
    public void downloadBlockChain() {
        new Thread() {
            public void run() {
                peerGroup.startAndWait();
                peerGroup.downloadBlockChain();
                // blockchain download complete, start listening for tx
                wallet.addEventListener(wallet);
            }}.start();
    }

    WatchWallet getWallet() {
        return wallet;
    }

    /**@param key
     * @return PubNode from the list of open pubNodes for a given key (address)*/
    public PublicNode getOpenNodeFromKey(ECKey key) {
        for (PublicNode node : new HashSet<PublicNode>(openWallets)) {
            if (node.getECKey().equals(key)) {
                return node;
            }
        }
        return null; // error
    }
}
