package org.judahmu.jbitgate;

import java.math.BigInteger;
import java.util.List;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;
import com.google.bitcoin.script.Script;

/**Watch-only wallet, watches all the open PubNodes, 
 * contacts Private-key Hierarchy on coins received
 * @author Jeff Masty (judah_mu@yahoo.com) Aug 29, 2013  */
public class WatchWallet extends Wallet implements WalletEventListener {
    
    private static final long serialVersionUID = 1L;
    static final Logger log = LoggerFactory.getLogger(WatchWallet.class);
    
    final PublicTree pub;
    
    /**new wallet/wallet listener
     * @param pub */
    public WatchWallet(PublicTree pub) {
        super(Main.NET);
        this.pub = pub;
        Assert.assertTrue(getKeychainSize() == 0);
    }

    /** add the key to the watched addresses */
    public void watch(PublicNode node) {
        addKey(node.getECKey());
        pub.openWallets.add(node);
    }
    
    /** drop the PubNode from watched addresses */
    public void drop(PublicNode node) {
        pub.openWallets.remove(node);
        pub.wallet.removeKey(node.getECKey());
        log.warn("dropping key " + node.getPubPath() + " " + node.getAddress());
    }
    
    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx,
            BigInteger prevBalance, BigInteger newBalance) {
                
        log.info("onCoinsReceived " + newBalance);
        
        ECKey key = null;
        TransactionOutput coins = null;
        for (TransactionOutput out : tx.getOutputs()) {
            try {
                Script script = out.getScriptPubKey();
                if (script.isSentToRawPubKey()) {
                    byte[] pubkey = script.getPubKey();
                    key = findKeyFromPubKey(pubkey);
                    if (key != null) {
                        coins = out;
                        break;    
                    }
                    
                } else {
                    byte[] pubkeyHash = script.getPubKeyHash();
                    key = findKeyFromPubHash(pubkeyHash);
                    if (key != null) {
                        coins = out;
                        break;
                    }
                }
            } catch (ScriptException e) {
                // Just means we didn't understand the output of this transaction: ignore it.
                log.debug("Could not parse tx output script: {}", e.toString());
            }
        }
        if (key == null) {
            log.error("Could not find target PubNode for " + tx);
            return;
        }
        log.info(key.toAddress(Main.NET) + " received " + coins.getValue() + " coins!");
        PublicNode node = pub.getOpenNodeFromKey(key);
        if (node == null) {
            log.error("failed to find ECKey on address " + key.toAddress(Main.NET));
            return;
       }
        
        // stop watching this address
        drop(node);
        
        // Send back coins
        PrivateTree.get().sendBack(node, tx);
    }

    @Override public void onCoinsSent(Wallet wallet, Transaction tx,
            BigInteger prevBalance, BigInteger newBalance) {  }

    @Override public void onReorganize(Wallet wallet) {  }

    @Override public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {  }

    @Override public void onWalletChanged(Wallet wallet) {
        log.debug("tx size: " + wallet.getTransactions(false).size());
    }

    @Override public void onKeysAdded(Wallet wallet, List<ECKey> keys) {  }

}
