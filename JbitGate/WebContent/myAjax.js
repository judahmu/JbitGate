	
var ajax;	
	
function update() {
	if (ajax.readyState != 4) {
		return;
	}
	if (ajax.status != 200) {
		console.log("ERROR");
		// TODO
		return;
	}
	document.getElementById("test_content").innerHTML = ajax.responseText;
	var oldTitle = document.title;
	document.title = "(Update) " + oldTitle;
}

function openAjax() {
	try { // if IE
		ajax = new ActiveXObject("Microsoft.XMLHTTP"); 
	} catch(e) {   // use standard object 
		ajax = new XMLHttpRequest(); 
	}	
	
	ajax.onreadystatechange = update;
	var url = "/JbitGate/TestMsg?addr=" + ADDRESS;
	ajax.open("GET", url, true);
	ajax.send(null);
	
}
	
	
