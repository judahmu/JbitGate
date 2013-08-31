<%@page import="java.util.TooManyListenersException"%>
<%@page import="org.judahmu.jbitgate.PublicTree"%>
<%@page import="org.judahmu.jbitgate.PublicNode"%>
<%@page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>HD wallet test</title>
<script src="myAjax.js"> </script>
</head>

<body onload="openAjax();">

<%
    try {
      PublicNode node = PublicTree.get().next(request.getRemoteAddr());
%>
<script>var ADDRESS="<%= node.getAddress() %>";</script>

<div id="test_content">
Hi, I'm a Deterministic watch-only SPV Wallet created just for you.<br/>
My TestNet3 address is<b> 
<%= node.getAddress() %></b><br/>
<br/>
I'll just sit here for about 15 minutes and try to catch TestNet3 coins.<br/>
If I receive coins, I'll wake up my master tree and toss them back to you.<br/>
<br/>
My public tree path: <%= node.getPubPath() %>.
(Private tree path: <%= node.getPrivPath() %>)
<%  } catch (TooManyListenersException e) {
        out.write(e.getMessage());
    } %>
</div>


<br/>
Please take the time to read [The Proposal] this test supports and visit my [Kickstarter]<br/>
This test is on GitHub at <a href="https://github.com/judahmu/JbitGate/tree/master/JbitGate">https://github.com/judahmu/JbitGate/tree/master/JbitGate</a><br/>
Note this test does not include hardening or load-balancing, its just a proof-of-concept.<br/>
<br/>
My bitcoin address if you would like to help make this project happen is 1JudahAbZGJhN9cWjTd9M1uGpfS4pfLZvs<br/> 
Contact <a href="mailto:judah_mu@yahoo.com">judah_mu@yahoo.com</a> if you have any questions or need some TestNet3 coins.<br/>
<br/>
<br/>
<br/>
</body>
</html>