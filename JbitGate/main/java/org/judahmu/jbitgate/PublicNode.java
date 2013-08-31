package org.judahmu.jbitgate;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.crypto.DeterministicKey;

/**Contains Deterministic Key (and it's ECKey) with getters for Address, childNum, etc
 * @author Jeff Masty (judah_mu@yahoo.com) Aug 26, 2013 */
public class PublicNode {

    private final DeterministicKey key;
    /** caller's IP address */
    public final String ip;
    private String message;
    
    public PublicNode(DeterministicKey key, String remoteIp) {
        this.key = key;
        this.ip = remoteIp;
    }
    
    public int getWalletNum() {
        return key.getChildNumber().getChildNumber();
    }
    
    public String getAddress() {
        return key.toECKey().toAddress(Main.NET).toString();
    }
    
    public String getPubPath() {
        return key.getPath();
    }
    
    public String getPrivPath() {
        return PrivateTree.get().getMasterPath(key);
    }

    /**@return the IP address of the customer*/
    public Object getIp() {
        return ip;
    }
    
    public ECKey getECKey() {
        return key.toECKey();
    }
    
    public DeterministicKey getHDKey() {
        return key;
    }

    /**@param message to the user (after coins received) */
    public void setMessage(String message) {
        this.message = message;
    }
    
    /**@return message to the user (after coins received) or null if not set */
    public String getMessage() {
        return message;
    }
    
}
