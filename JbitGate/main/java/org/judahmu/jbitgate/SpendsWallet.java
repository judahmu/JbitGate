package org.judahmu.jbitgate;

import static com.google.bitcoin.core.Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WrongNetworkException;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.SPVBlockStore;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

/**Sends coins back to sender
 * @author Jeff Masty (judah_mu@yahoo.com) Aug 27, 2013 */
public class SpendsWallet extends Wallet implements Runnable {
    
    private static final HashSet<SpendsWallet> openWallets = new HashSet<>();
    
    private static final long serialVersionUID = 1L;
    static Logger log = LoggerFactory.getLogger(SpendsWallet.class);
    
    private final BlockStore store; // SPV or MemoryBlockStore
    
    private final BlockChain chain; // created when download starts
    private PeerGroup peerGroup; // created when download starts
    private final DeterministicKey key;
    private final PublicNode node;
    private Address returnAddress;
    
    
    @SuppressWarnings("deprecation") 
    public SpendsWallet(DeterministicKey key, Transaction incoming, PublicNode node) throws BlockStoreException {
        
        super(Main.NET);
        this.key = key;
        this.node = node;

        // Add private key to wallet
        ECKey ec = key.toECKey();
        ec.setCreationTimeSeconds(node.getECKey().getCreationTimeSeconds());
        addKey(ec);
        
        // Get return address
        // It's impossible to pick one specific identity that you receive coins from 
        // in Bitcoin as there could be inputs from many addresses. So instead we 
        // just pick the first and assume they were all owned by the same person.
        for (TransactionInput in :incoming.getInputs()) {
            try {
                returnAddress = in.getFromAddress();
                break;
            } catch (ScriptException e) {
                log.warn(e.getMessage(), e);
            }
        }
        if (returnAddress == null) {
            // return address not found, send to my TestNet3 coin dump
            try {
                returnAddress = new Address(Main.NET, Main.TestNetCoinsDump);
            } catch (WrongNetworkException e) {
                e.printStackTrace();
            } catch (AddressFormatException e) {
                e.printStackTrace();
            }
        }
        
        
        File storeFile = new File(incoming.getHashAsString() + ".spv");
        if (PrivateTree.QUICk_SPV.isFile()) {
            try {
                Files.copy(PrivateTree.QUICk_SPV, storeFile);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        storeFile.deleteOnExit(); // note: delete the BlockStore when JVM exits
        store = new SPVBlockStore(Main.NET, storeFile);
        chain = new BlockChain(Main.NET, store);
        setLastBlockSeenHeight(chain.getBestChainHeight());
        chain.addWallet(this);
        peerGroup = new PeerGroup(Main.NET, chain);
        peerGroup.setFastCatchupTimeSecs(node.getECKey().getCreationTimeSeconds());
        peerGroup.setMinBroadcastConnections(2);
        Main.addPeers(peerGroup);
        
        // add the private key to the wallet
    }


    @Override
    public void run() {
        openWallets.add(this);
        peerGroup.startAndWait();
        peerGroup.downloadBlockChain();
        
        
        final BigInteger balance = getBalance(BalanceType.ESTIMATED);
        log.info("---------Spends Wallet Synced, going to send coins " + balance + "------------");
        
        if (balance.compareTo(BigInteger.valueOf(1000000)) <= 0) {
            return; // nothing to send
        }
        BigInteger fee = BigInteger.valueOf(2).multiply(REFERENCE_DEFAULT_MIN_TX_FEE);
        BigInteger three = BigInteger.valueOf(3).multiply(REFERENCE_DEFAULT_MIN_TX_FEE);
        // Now send the coins back! Send with a fee
        final BigInteger amountToSend = balance.subtract(three);
        
        try {
            SendRequest req = SendRequest.to(returnAddress, amountToSend);
            req.fee = fee;
            final Wallet.SendResult sendResult = sendCoins(peerGroup, req); 
            if (sendResult == null) {
                node.setMessage("Tried to send more coins than exist in wallet O.o");
                close();
                return;
            }
            String msg = "Thank you for your generous donation.<br/>";
            msg += Utils.bitcoinValueToFriendlyString(amountToSend);
            msg += " coins will be sent back to " + returnAddress + " as blocks are solved.";
            msg += "<br/><br/>&nbsp;&nbsp;&nbsp;&nbsp;tx_hash: " + sendResult.tx.getHashAsString();
            node.setMessage(msg); // informs user
            
            Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>() {
                public void onSuccess(Transaction transaction) {
                    log.info("Sent coins back! tx_hash:" + sendResult.tx.getHashAsString());
                    close();
                }
                public void onFailure(Throwable throwable) {
                    log.error("Failed to send coins :(", throwable);
                    close();
                }
            });
            
        } catch (Exception e) { //KeyCrypterException, WrongNetworkException
            log.error(e.getMessage(), e);
            node.setMessage(e.getLocalizedMessage());
            close();
        }
    }
    
    /**Stop peer-to-peer/blockstore */
    void close() {
        if (peerGroup != null && peerGroup.isRunning()) {
            peerGroup.stopAndWait();
        }
        peerGroup = null;
        log.info("SpendsWallet " + key.getPath() + " peering stopped");
        try {
            store.close();
        } catch (BlockStoreException e) {
            log.error(e.getMessage(), e);
        }
        openWallets.remove(this);
    }

    /** Application shutdown, close any open peers/blockstores */
    public static void shutdown() {
        for (SpendsWallet wallet : new HashSet<SpendsWallet>(openWallets)) {
            wallet.close();
        }
    }
 
}
