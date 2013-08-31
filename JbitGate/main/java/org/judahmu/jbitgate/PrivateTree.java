package org.judahmu.jbitgate;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.crypto.ChildNumber;
import com.google.bitcoin.crypto.DeterministicHierarchy;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.crypto.HDKeyDerivation;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.SPVBlockStore;
import com.google.common.collect.ImmutableList;

/**Private keys Hierarchy. Creates the Public Tree off of M/0'/0'/0
 * @author Jeff Masty (judah_mu@yahoo.com) Aug 27, 2013 */
public class PrivateTree extends DeterministicHierarchy {
    private static final long serialVersionUID = 1L;
    
    private static final String SEED = "BitCashier demo - Don't try this at home!";
    //private static final String SEED = "Don't be an idiot on MainNet"; // maybe has some coins on it

    static Logger log = LoggerFactory.getLogger(PrivateTree.class);
    static final File QUICk_SPV = new File(PrivateTree.class.getSimpleName() + ".spv");
    
    /** Singleton */
    private static PrivateTree priv; 
    private final DeterministicKey pubRoot;
    private final ImmutableList<ChildNumber> branchPath;
    
    PrivateTree() {
        super(HDKeyDerivation.createMasterPrivateKey(SEED.getBytes()));
        priv = this;
        ImmutableList<ChildNumber> m16p8p = ImmutableList.of(
                new ChildNumber(16, true), new ChildNumber(8, true));
        DeterministicKey branch = deriveChild(m16p8p, false, true, new ChildNumber(0, false));
        pubRoot = branch.getPubOnly();
        branchPath = branch.getChildNumberPath();
        assertArrayEquals(branch.getPubKeyBytes(), pubRoot.getPubKeyBytes());
        checkQuickSpvStore();
    }
    
    public static PrivateTree get() { return priv;}

    /**@return the Public key in this hierarchy that the public keychain will branch off of*/
    public DeterministicKey getPubRoot() {
        return pubRoot;
    }
    
    /**Send back coins received on WatchWallet in tx*/
    public void sendBack(PublicNode fromPub, Transaction tx) {
        DeterministicKey sync = getSyncKeyForPub(fromPub.getHDKey());
        try {
            SpendsWallet wallet = new SpendsWallet(sync, tx, fromPub);
            new Thread(wallet).start();
        } catch (BlockStoreException e) {
            log.error(e.getMessage(), e);
            fromPub.setMessage(e.getMessage());
        }
    }

    /**@param fromPub
     * @return a private key for the corresponding public child tree key */ 
    DeterministicKey getSyncKeyForPub(DeterministicKey fromPub) {
        DeterministicKey sync = deriveChild(branchPath, false, false, fromPub.getChildNumber());
        assertArrayEquals(sync.getPubKeyBytes(), fromPub.getPubKeyBytes());
        assertNotNull(sync.getPrivKeyBytes());
        return sync;
    }
    
    /**@param fromPub
     * @return the path in this master tree for the corresponding public child tree*/
    public String getMasterPath(DeterministicKey fromPub) {
        DeterministicKey sync = getSyncKeyForPub(fromPub);
        return sync.getPath();
    }
    
    private void checkQuickSpvStore() {
        try {
            log.info(QUICk_SPV.getAbsolutePath());
            SPVBlockStore store = new SPVBlockStore(Main.NET, QUICk_SPV);
            BlockChain chain = new BlockChain(Main.NET, store);
            PeerGroup peerGroup = new PeerGroup(Main.NET, chain);
            peerGroup.setFastCatchupTimeSecs(System.currentTimeMillis());
            Main.addPeers(peerGroup); 
            peerGroup.startAndWait();
            peerGroup.downloadBlockChain();
            peerGroup.stopAndWait();
            store.close();
        } catch (Exception e) {
            log.error(e.getLocalizedMessage(), e);
        }
    }
    
}
