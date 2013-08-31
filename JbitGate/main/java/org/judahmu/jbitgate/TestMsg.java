package org.judahmu.jbitgate;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**Async Servlet response to AJAX request for information when coins have been received
 * @author Jeff Masty (judah_mu@yahoo.com) Aug 27, 2013 */
@WebServlet(description = "ajax response for received coins", urlPatterns = { "/TestMsg" }, asyncSupported=true)
public class TestMsg extends HttpServlet {
	private static final long serialVersionUID = 1L;
	static Logger log = LoggerFactory.getLogger(TestMsg.class);
	
	/** twenty minutes in milliseconds */
    public static final long THIRTY_MINS = 1000 * 60 * 30; // in millis

       
	/**[Async] give status update to user when coins received */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	        throws ServletException, IOException {
	    String addr = request.getParameter("addr");
	    log.info("doGet " + addr);
	    if (addr == null) {
	        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
	        return;
	    }
	    PublicNode target = null;
	    for (PublicNode node : PublicTree.get().openWallets) {
	        if (node.getAddress().equals(addr)) {
	            target = node;
	            break;
	        }
	    }
	    if (target == null) {
	        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
	        return;
	    }
	    
	    AsyncContext ctx = request.startAsync();
	    ctx.setTimeout(THIRTY_MINS);
	    Listener listener = new Listener(ctx, target); 
	    ctx.addListener(listener);
	    ctx.start(listener);
	}

	class Listener extends Thread implements AsyncListener {
	    
	    final AsyncContext ctx;
	    final PublicNode node;
	    
	    Listener(AsyncContext ctx, PublicNode node) {
	        this.ctx = ctx;
	        this.node = node;
	    }
        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            if (node.getMessage() == null) {
                return;
            }
            log.info(">>>>>>>> onComplete " + ctx.getRequest().getParameter("addr"));
            HttpServletResponse response = (HttpServletResponse)ctx.getResponse();
            response.setStatus(HttpServletResponse.SC_OK);
            PrintWriter out = response.getWriter();
            out.write(node.getMessage());
            response.flushBuffer();
            
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            PublicTree.get().getWallet().drop(node);
            log.info(">>>>>>>> onTimeout " + ctx.getRequest().getParameter("addr"));
            HttpServletResponse response = (HttpServletResponse)ctx.getResponse();
            response.setStatus(HttpServletResponse.SC_OK);
            PrintWriter out = response.getWriter();
            out.write("This address has timed out and is no longer watching for incoming coins.<br/>");
            out.write("Please try again.<br/>");
            response.flushBuffer();
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            log.info(">>>>>>>> onError " + ctx.getRequest().getParameter("addr"));
            HttpServletResponse response = (HttpServletResponse)ctx.getResponse();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
        }
        
        public void run() {
            log.info("service for " + node.getAddress() + " started");
            
            // start checking for a message that coins have been received, 12sec polling 
            while (node.getMessage() == null) {
                try {
                    sleep(12000); 
                } catch (InterruptedException e) {
                }
            }
            
            if (node.getMessage() != null) {
                ctx.complete();
            }
        }

	}

}
